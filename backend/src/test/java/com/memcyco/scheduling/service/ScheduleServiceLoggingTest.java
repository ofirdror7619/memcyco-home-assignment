package com.memcyco.scheduling.service;

import com.memcyco.scheduling.dto.ScheduleRequest;
import com.memcyco.scheduling.entity.IntervalUnit;
import com.memcyco.scheduling.entity.Schedule;
import com.memcyco.scheduling.entity.ScheduleType;
import com.memcyco.scheduling.entity.TaskType;
import com.memcyco.scheduling.repository.ScheduleRepository;
import com.memcyco.scheduling.scheduler.QuartzScheduleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ScheduleServiceLoggingTest {
    private ScheduleRepository repository;
    private QuartzScheduleManager quartz;
    private ScheduleService service;

    @BeforeEach
    void setUp() {
        repository = mock(ScheduleRepository.class);
        quartz = mock(QuartzScheduleManager.class);
        service = new ScheduleService(repository, mock(ScheduleValidator.class), quartz);
    }

    @Test
    void logsCreatedScheduleWithoutParameters(CapturedOutput output) {
        when(repository.saveAndFlush(any(Schedule.class))).thenAnswer(invocation -> {
            Schedule schedule = invocation.getArgument(0);
            schedule.setId(42L);
            return schedule;
        });

        service.create(request("Sensitive message"));

        assertThat(output).contains("event=schedule_created scheduleId=42")
                .contains("scheduleName='Lifecycle test'")
                .contains("taskType=LOG scheduleType=INTERVAL enabled=true")
                .doesNotContain("Sensitive message");
    }

    @Test
    void doesNotLogSuccessWhenQuartzSynchronizationFails(CapturedOutput output) {
        when(repository.saveAndFlush(any(Schedule.class))).thenAnswer(invocation -> {
            Schedule schedule = invocation.getArgument(0);
            schedule.setId(43L);
            return schedule;
        });
        doThrow(new IllegalStateException("Quartz unavailable")).when(quartz).synchronize(any());

        assertThatThrownBy(() -> service.create(request("Do not log")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(output).doesNotContain("event=schedule_created");
    }

    @Test
    void logsDeletionOnlyAfterSuccessfulOperations(CapturedOutput output) {
        Schedule schedule = entity(44L);
        when(repository.findById(44L)).thenReturn(Optional.of(schedule));

        service.delete(44L);

        assertThat(output).contains("event=schedule_deleted scheduleId=44")
                .contains("scheduleName='Lifecycle test'")
                .doesNotContain("Sensitive message");
    }

    private ScheduleRequest request(String message) {
        return new ScheduleRequest("Lifecycle test", TaskType.LOG, ScheduleType.INTERVAL,
                null, 5, IntervalUnit.MINUTES, null, null, null,
                Map.of("message", message), true);
    }

    private Schedule entity(long id) {
        Schedule schedule = new Schedule();
        schedule.setId(id);
        schedule.setName("Lifecycle test");
        schedule.setTaskType(TaskType.LOG);
        schedule.setScheduleType(ScheduleType.INTERVAL);
        schedule.setIntervalValue(5);
        schedule.setIntervalUnit(IntervalUnit.MINUTES);
        schedule.setParameters(Map.of("message", "Sensitive message"));
        schedule.setEnabled(true);
        return schedule;
    }
}
