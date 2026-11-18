package com.memcyco.scheduling.dto;

import com.memcyco.scheduling.entity.IntervalUnit;
import com.memcyco.scheduling.entity.ScheduleType;
import com.memcyco.scheduling.entity.TaskType;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

public record ScheduleResponse(
        Long id,
        String name,
        TaskType taskType,
        ScheduleType scheduleType,
        OffsetDateTime executionTime,
        Integer intervalValue,
        IntervalUnit intervalUnit,
        Set<DayOfWeek> daysOfWeek,
        LocalTime timeOfDay,
        String cronExpression,
        Map<String, String> parameters,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
