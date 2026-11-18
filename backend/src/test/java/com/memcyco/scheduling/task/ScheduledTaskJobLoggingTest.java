package com.memcyco.scheduling.task;

import com.memcyco.scheduling.entity.Schedule;
import com.memcyco.scheduling.entity.ScheduleType;
import com.memcyco.scheduling.entity.TaskType;
import com.memcyco.scheduling.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ScheduledTaskJobLoggingTest {
    private ScheduleRepository repository;
    private TaskExecutionService taskService;
    private ScheduledTaskJob job;
    private JobExecutionContext context;

    @BeforeEach
    void setUp() {
        repository = mock(ScheduleRepository.class);
        taskService = mock(TaskExecutionService.class);
        job = new ScheduledTaskJob(repository, taskService);
        context = mock(JobExecutionContext.class);
        JobDataMap data = new JobDataMap();
        data.put(ScheduledTaskJob.SCHEDULE_ID, 77L);
        when(context.getMergedJobDataMap()).thenReturn(data);
        when(context.getFireInstanceId()).thenReturn("fire-123");
        when(repository.findById(77L)).thenReturn(Optional.of(schedule()));
    }

    @Test
    void logsExecutionStartAndCompletionWithoutParameters(CapturedOutput output) throws Exception {
        job.execute(context);

        assertThat(output).contains("event=task_execution_started scheduleId=77 taskType=DUMMY_EMAIL executionId=fire-123")
                .contains("event=task_execution_completed scheduleId=77 taskType=DUMMY_EMAIL executionId=fire-123 durationMs=")
                .doesNotContain("private@example.test")
                .doesNotContain("Confidential body");
    }

    @Test
    void logsFailedExecutionAndDoesNotReportCompletion(CapturedOutput output) {
        doThrow(new IllegalStateException("Task failed"))
                .when(taskService).execute(eq(TaskType.DUMMY_EMAIL), any());

        assertThatThrownBy(() -> job.execute(context))
                .isInstanceOf(JobExecutionException.class);

        assertThat(output).contains("event=task_execution_failed scheduleId=77 taskType=DUMMY_EMAIL executionId=fire-123 durationMs=")
                .contains("Task failed")
                .doesNotContain("event=task_execution_completed");
    }

    private Schedule schedule() {
        Schedule schedule = new Schedule();
        schedule.setId(77L);
        schedule.setName("Email task");
        schedule.setTaskType(TaskType.DUMMY_EMAIL);
        schedule.setScheduleType(ScheduleType.INTERVAL);
        schedule.setEnabled(true);
        schedule.setParameters(Map.of(
                "recipient", "private@example.test",
                "subject", "Private subject",
                "body", "Confidential body"));
        return schedule;
    }
}
