package com.memcyco.scheduling.task;

import com.memcyco.scheduling.entity.Schedule;
import com.memcyco.scheduling.repository.ScheduleRepository;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
public class ScheduledTaskJob implements Job {
    public static final String SCHEDULE_ID = "scheduleId";
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTaskJob.class);

    private final ScheduleRepository scheduleRepository;
    private final TaskExecutionService taskExecutionService;

    public ScheduledTaskJob(ScheduleRepository scheduleRepository, TaskExecutionService taskExecutionService) {
        this.scheduleRepository = scheduleRepository;
        this.taskExecutionService = taskExecutionService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long scheduleId = context.getMergedJobDataMap().getLong(SCHEDULE_ID);
        String executionId = context.getFireInstanceId();
        Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            LOGGER.warn("event=task_execution_skipped scheduleId={} executionId={} reason=schedule_not_found",
                    scheduleId, executionId);
            return;
        }
        if (!schedule.isEnabled()) {
            LOGGER.info("event=task_execution_skipped scheduleId={} taskType={} executionId={} reason=schedule_disabled",
                    scheduleId, schedule.getTaskType(), executionId);
            return;
        }
        long startedAt = System.nanoTime();
        LOGGER.info("event=task_execution_started scheduleId={} taskType={} executionId={}",
                scheduleId, schedule.getTaskType(), executionId);
        try {
            taskExecutionService.execute(schedule.getTaskType(), schedule.getParameters());
            LOGGER.info("event=task_execution_completed scheduleId={} taskType={} executionId={} durationMs={}",
                    scheduleId, schedule.getTaskType(), executionId, elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            LOGGER.error("event=task_execution_failed scheduleId={} taskType={} executionId={} durationMs={}",
                    scheduleId, schedule.getTaskType(), executionId, elapsedMillis(startedAt), exception);
            throw new JobExecutionException("Task execution failed for schedule " + scheduleId, exception, false);
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
