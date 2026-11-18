package com.memcyco.scheduling.dto;

import com.memcyco.scheduling.entity.IntervalUnit;
import com.memcyco.scheduling.entity.ScheduleType;
import com.memcyco.scheduling.entity.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

public record ScheduleRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull TaskType taskType,
        @NotNull ScheduleType scheduleType,
        OffsetDateTime executionTime,
        Integer intervalValue,
        IntervalUnit intervalUnit,
        Set<DayOfWeek> daysOfWeek,
        LocalTime timeOfDay,
        String cronExpression,
        Map<String, String> parameters,
        @NotNull Boolean enabled
) {
}
