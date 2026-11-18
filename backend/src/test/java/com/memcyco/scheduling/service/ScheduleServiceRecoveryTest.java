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

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleServiceRecoveryTest {
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
    void restoresPersistedStateWhenQuartzUpdateFails() {
        Schedule schedule = schedule(7L, "Original", 5);
        when(repository.findById(7L)).thenReturn(Optional.of(schedule));
        doThrow(new IllegalStateException("Quartz unavailable"))
                .doNothing().when(quartz).synchronize(any(Schedule.class));

        assertThatThrownBy(() -> service.update(7L, request("Changed", 10)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(schedule.getName()).isEqualTo("Original");
        assertThat(schedule.getIntervalValue()).isEqualTo(5);
        verify(repository, times(2)).saveAndFlush(schedule);
        verify(quartz, times(2)).synchronize(schedule);
    }

    @Test
    void restoresQuartzJobWhenDatabaseDeletionFails() {
        Schedule schedule = schedule(8L, "Delete recovery", 5);
        when(repository.findById(8L)).thenReturn(Optional.of(schedule));
        doThrow(new IllegalStateException("Database unavailable")).when(repository).deleteById(8L);

        assertThatThrownBy(() -> service.delete(8L))
                .isInstanceOf(IllegalStateException.class);

        verify(quartz).delete(8L);
        verify(quartz).synchronize(schedule);
    }

    private ScheduleRequest request(String name, int minutes) {
        return new ScheduleRequest(name, TaskType.LOG, ScheduleType.INTERVAL, null, minutes,
                IntervalUnit.MINUTES, null, null, null, Map.of("message", "hello"), true);
    }

    private Schedule schedule(long id, String name, int minutes) {
        Schedule schedule = new Schedule();
        schedule.setId(id);
        schedule.setName(name);
        schedule.setTaskType(TaskType.LOG);
        schedule.setScheduleType(ScheduleType.INTERVAL);
        schedule.setIntervalValue(minutes);
        schedule.setIntervalUnit(IntervalUnit.MINUTES);
        schedule.setParameters(Map.of("message", "hello"));
        schedule.setEnabled(true);
        return schedule;
    }
}
