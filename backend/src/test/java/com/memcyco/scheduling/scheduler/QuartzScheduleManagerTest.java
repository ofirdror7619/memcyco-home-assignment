package com.memcyco.scheduling.scheduler;

import com.memcyco.scheduling.entity.IntervalUnit;
import com.memcyco.scheduling.entity.Schedule;
import com.memcyco.scheduling.entity.ScheduleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.quartz.CronTrigger;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class QuartzScheduleManagerTest {
    private final QuartzScheduleManager manager = new QuartzScheduleManager(mock(Scheduler.class));
    private final JobKey jobKey = JobKey.jobKey("test");

    @Test
    void buildsOneTimeTrigger() {
        Schedule schedule = schedule(ScheduleType.ONE_TIME);
        schedule.setExecutionTime(Instant.parse("2035-01-01T10:00:00Z"));
        Trigger trigger = manager.buildTrigger(schedule, jobKey);
        assertThat(trigger).isInstanceOf(SimpleTrigger.class);
        assertThat(trigger.getStartTime()).isEqualTo(java.util.Date.from(schedule.getExecutionTime()));
        assertThat(((SimpleTrigger) trigger).getRepeatCount()).isZero();
    }

    @Test
    void buildsIntervalTrigger() {
        Schedule schedule = schedule(ScheduleType.INTERVAL);
        schedule.setIntervalValue(2);
        schedule.setIntervalUnit(IntervalUnit.HOURS);
        SimpleTrigger trigger = (SimpleTrigger) manager.buildTrigger(schedule, jobKey);
        assertThat(trigger.getRepeatInterval()).isEqualTo(Duration.ofHours(2).toMillis());
        assertThat(trigger.getRepeatCount()).isEqualTo(SimpleTrigger.REPEAT_INDEFINITELY);
    }

    @Test
    void buildsWeeklyTriggerInUtc() {
        Schedule schedule = schedule(ScheduleType.WEEKLY);
        schedule.setDaysOfWeek(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        schedule.setTimeOfDay(LocalTime.of(9, 30));
        CronTrigger trigger = (CronTrigger) manager.buildTrigger(schedule, jobKey);
        assertThat(trigger.getCronExpression()).isEqualTo("0 30 9 ? * MON,WED");
        assertThat(trigger.getTimeZone().getID()).isEqualTo("UTC");
    }

    @Test
    void buildsCronTriggerInUtc() {
        Schedule schedule = schedule(ScheduleType.CRON);
        schedule.setCronExpression("0 0/5 * ? * *");
        CronTrigger trigger = (CronTrigger) manager.buildTrigger(schedule, jobKey);
        assertThat(trigger.getCronExpression()).isEqualTo("0 0/5 * ? * *");
        assertThat(trigger.getTimeZone().getID()).isEqualTo("UTC");
    }

    @Test
    void logsSuccessfulRegistrationWithKeysAndNextFireTime(CapturedOutput output) throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        QuartzScheduleManager loggingManager = new QuartzScheduleManager(scheduler);
        Schedule schedule = schedule(ScheduleType.INTERVAL);
        schedule.setEnabled(true);
        schedule.setIntervalValue(5);
        schedule.setIntervalUnit(IntervalUnit.MINUTES);
        when(scheduler.checkExists(any(JobKey.class))).thenReturn(false);
        when(scheduler.checkExists(any(TriggerKey.class))).thenReturn(false);
        when(scheduler.scheduleJob(any(Trigger.class))).thenReturn(java.util.Date.from(Instant.parse("2035-01-01T10:00:00Z")));

        loggingManager.synchronize(schedule);

        assertThat(output).contains("event=quartz_job_registered scheduleId=42")
                .contains("jobKey=schedules.schedule-42")
                .contains("triggerKey=schedules.schedule-42")
                .contains("nextFireTime=");
    }

    @Test
    void logsFailureWithoutReportingRegistrationSuccess(CapturedOutput output) throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        QuartzScheduleManager loggingManager = new QuartzScheduleManager(scheduler);
        Schedule schedule = schedule(ScheduleType.INTERVAL);
        schedule.setEnabled(true);
        schedule.setIntervalValue(5);
        schedule.setIntervalUnit(IntervalUnit.MINUTES);
        doThrow(new org.quartz.SchedulerException("Unavailable"))
                .when(scheduler).addJob(any(), org.mockito.ArgumentMatchers.eq(true));

        assertThatThrownBy(() -> loggingManager.synchronize(schedule))
                .isInstanceOf(com.memcyco.scheduling.exception.SchedulingOperationException.class);

        assertThat(output).contains("event=quartz_synchronization_failed scheduleId=42")
                .doesNotContain("event=quartz_job_registered");
    }

    @Test
    void removesExistingJobWhenScheduleIsDisabled() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        QuartzScheduleManager disabledManager = new QuartzScheduleManager(scheduler);
        Schedule schedule = schedule(ScheduleType.INTERVAL);
        schedule.setEnabled(false);
        when(scheduler.checkExists(QuartzScheduleManager.jobKey(42L))).thenReturn(true);
        when(scheduler.deleteJob(QuartzScheduleManager.jobKey(42L))).thenReturn(true);

        disabledManager.synchronize(schedule);

        verify(scheduler).deleteJob(QuartzScheduleManager.jobKey(42L));
    }

    @Test
    void restoresMissingQuartzStateDuringReconciliation() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        QuartzScheduleManager reconciliationManager = spy(new QuartzScheduleManager(scheduler));
        Schedule schedule = schedule(ScheduleType.INTERVAL);
        schedule.setEnabled(true);
        doNothing().when(reconciliationManager).synchronize(schedule);
        when(scheduler.checkExists(QuartzScheduleManager.jobKey(42L))).thenReturn(false);

        reconciliationManager.reconcile(schedule);

        verify(reconciliationManager).synchronize(schedule);
        verify(scheduler).getTrigger(QuartzScheduleManager.triggerKey(42L));
    }

    private Schedule schedule(ScheduleType type) {
        Schedule schedule = new Schedule();
        schedule.setId(42L);
        schedule.setScheduleType(type);
        return schedule;
    }
}
