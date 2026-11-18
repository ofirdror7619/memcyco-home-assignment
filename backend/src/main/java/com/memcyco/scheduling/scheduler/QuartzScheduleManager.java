package com.memcyco.scheduling.scheduler;

import com.memcyco.scheduling.entity.IntervalUnit;
import com.memcyco.scheduling.entity.Schedule;
import com.memcyco.scheduling.exception.SchedulingOperationException;
import com.memcyco.scheduling.task.ScheduledTaskJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Collectors;

@Component
public class QuartzScheduleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuartzScheduleManager.class);
    public static final String GROUP = "schedules";
    public static final TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC");

    private final Scheduler scheduler;

    public QuartzScheduleManager(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void synchronize(Schedule schedule) {
        JobKey jobKey = jobKey(schedule.getId());
        TriggerKey triggerKey = triggerKey(schedule.getId());
        try {
            if (!schedule.isEnabled()) {
                if (scheduler.checkExists(jobKey) && scheduler.deleteJob(jobKey)) {
                    LOGGER.info("event=quartz_job_removed scheduleId={} jobKey={} triggerKey={} reason=schedule_disabled",
                            schedule.getId(), jobKey, triggerKey);
                }
                LOGGER.info("event=quartz_schedule_skipped scheduleId={} jobKey={} triggerKey={} reason=schedule_disabled",
                        schedule.getId(), jobKey, triggerKey);
                return;
            }

            if (schedule.getScheduleType() == com.memcyco.scheduling.entity.ScheduleType.ONE_TIME
                    && !schedule.getExecutionTime().isAfter(Instant.now())) {
                LOGGER.debug("event=quartz_schedule_skipped scheduleId={} jobKey={} triggerKey={} reason=past_one_time",
                        schedule.getId(), jobKey, triggerKey);
                return;
            }

            boolean jobExisted = scheduler.checkExists(jobKey);
            JobDetail job = JobBuilder.newJob(ScheduledTaskJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(ScheduledTaskJob.SCHEDULE_ID, schedule.getId())
                    .storeDurably(true)
                    .requestRecovery(false)
                    .build();
            scheduler.addJob(job, true);

            Trigger trigger = buildTrigger(schedule, jobKey);
            if (scheduler.checkExists(triggerKey)) {
                Date nextFireTime = scheduler.rescheduleJob(triggerKey, trigger);
                LOGGER.info("event=quartz_trigger_rescheduled scheduleId={} jobKey={} triggerKey={} nextFireTime={}",
                        schedule.getId(), jobKey, triggerKey, nextFireTime);
            } else {
                Date nextFireTime = scheduler.scheduleJob(trigger);
                LOGGER.info("event=quartz_job_registered scheduleId={} jobKey={} triggerKey={} nextFireTime={} jobReplaced={}",
                        schedule.getId(), jobKey, triggerKey, nextFireTime, jobExisted);
            }
        } catch (SchedulerException | RuntimeException exception) {
            LOGGER.error("event=quartz_synchronization_failed scheduleId={} jobKey={} triggerKey={}",
                    schedule.getId(), jobKey, triggerKey, exception);
            throw new SchedulingOperationException(
                    "Could not synchronize schedule " + schedule.getId() + " with Quartz", exception);
        }
    }

    public void reconcile(Schedule schedule) {
        JobKey jobKey = jobKey(schedule.getId());
        TriggerKey triggerKey = triggerKey(schedule.getId());
        try {
            if (!schedule.isEnabled()) {
                if (scheduler.checkExists(jobKey)) {
                    synchronize(schedule);
                    LOGGER.info("event=quartz_schedule_reconciled scheduleId={} jobKey={} triggerKey={} nextFireTime={} change=disabled_job_removed",
                            schedule.getId(), jobKey, triggerKey, null);
                }
                return;
            }
            if (schedule.getScheduleType() == com.memcyco.scheduling.entity.ScheduleType.ONE_TIME
                    && !schedule.getExecutionTime().isAfter(Instant.now())) {
                return;
            }
            if (!scheduler.checkExists(jobKey) || !scheduler.checkExists(triggerKey)) {
                synchronize(schedule);
                Trigger trigger = scheduler.getTrigger(triggerKey);
                LOGGER.info("event=quartz_schedule_reconciled scheduleId={} jobKey={} triggerKey={} nextFireTime={} change=missing_state_restored",
                        schedule.getId(), jobKey, triggerKey,
                        trigger == null ? null : trigger.getNextFireTime());
            }
        } catch (SchedulerException exception) {
            LOGGER.error("event=quartz_reconciliation_failed scheduleId={} jobKey={} triggerKey={}",
                    schedule.getId(), jobKey, triggerKey, exception);
            throw new SchedulingOperationException(
                    "Could not reconcile schedule " + schedule.getId() + " with Quartz", exception);
        }
    }

    public void delete(long id) {
        JobKey jobKey = jobKey(id);
        TriggerKey triggerKey = triggerKey(id);
        try {
            if (scheduler.deleteJob(jobKey)) {
                LOGGER.info("event=quartz_job_removed scheduleId={} jobKey={} triggerKey={} reason=schedule_deleted",
                        id, jobKey, triggerKey);
            }
        } catch (SchedulerException exception) {
            LOGGER.error("event=quartz_removal_failed scheduleId={} jobKey={} triggerKey={}",
                    id, jobKey, triggerKey, exception);
            throw new SchedulingOperationException("Could not delete schedule " + id + " from Quartz", exception);
        }
    }

    public Trigger buildTrigger(Schedule schedule, JobKey jobKey) {
        TriggerBuilder<Trigger> builder = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey(schedule.getId()))
                .forJob(jobKey);

        return switch (schedule.getScheduleType()) {
            case ONE_TIME -> builder
                    .startAt(Date.from(schedule.getExecutionTime()))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withRepeatCount(0)
                            .withMisfireHandlingInstructionFireNow())
                    .build();
            case INTERVAL -> {
                Duration interval = schedule.getIntervalUnit() == IntervalUnit.MINUTES
                        ? Duration.ofMinutes(schedule.getIntervalValue())
                        : Duration.ofHours(schedule.getIntervalValue());
                yield builder.startNow()
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInMilliseconds(interval.toMillis())
                                .repeatForever()
                                .withMisfireHandlingInstructionNextWithRemainingCount())
                        .build();
            }
            case WEEKLY -> {
                String days = schedule.getDaysOfWeek().stream()
                        .sorted()
                        .map(this::quartzDay)
                        .collect(Collectors.joining(","));
                String expression = "%d %d %d ? * %s".formatted(
                        schedule.getTimeOfDay().getSecond(), schedule.getTimeOfDay().getMinute(),
                        schedule.getTimeOfDay().getHour(), days);
                yield builder.withSchedule(CronScheduleBuilder.cronSchedule(expression)
                                .inTimeZone(TIME_ZONE)
                                .withMisfireHandlingInstructionDoNothing())
                        .build();
            }
            case CRON -> builder.withSchedule(CronScheduleBuilder.cronSchedule(schedule.getCronExpression())
                            .inTimeZone(TIME_ZONE)
                            .withMisfireHandlingInstructionDoNothing())
                    .build();
        };
    }

    public static JobKey jobKey(long id) {
        return JobKey.jobKey("schedule-" + id, GROUP);
    }

    public static TriggerKey triggerKey(long id) {
        return TriggerKey.triggerKey("schedule-" + id, GROUP);
    }

    private String quartzDay(DayOfWeek day) {
        return day.name().substring(0, 3).toUpperCase(Locale.ROOT);
    }
}
