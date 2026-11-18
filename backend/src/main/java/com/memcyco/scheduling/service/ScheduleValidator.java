package com.memcyco.scheduling.service;

import com.memcyco.scheduling.dto.ScheduleRequest;
import com.memcyco.scheduling.entity.TaskType;
import com.memcyco.scheduling.exception.ScheduleValidationException;
import org.quartz.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class ScheduleValidator {
    private final Clock clock;

    public ScheduleValidator() {
        this(Clock.systemUTC());
    }

    ScheduleValidator(Clock clock) {
        this.clock = clock;
    }

    public void validate(ScheduleRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (request.taskType() != null) validateParameters(request.taskType(), request.parameters(), errors);
        if (request.scheduleType() != null) {
            switch (request.scheduleType()) {
                case ONE_TIME -> {
                    if (request.executionTime() == null) {
                        errors.put("executionTime", "is required for ONE_TIME schedules");
                    } else if (!request.executionTime().toInstant().isAfter(clock.instant())) {
                        errors.put("executionTime", "must be in the future");
                    }
                }
                case INTERVAL -> {
                    if (request.intervalValue() == null || request.intervalValue() <= 0) {
                        errors.put("intervalValue", "must be greater than zero for INTERVAL schedules");
                    }
                    if (request.intervalUnit() == null) {
                        errors.put("intervalUnit", "is required for INTERVAL schedules");
                    }
                }
                case WEEKLY -> {
                    if (request.daysOfWeek() == null || request.daysOfWeek().isEmpty()) {
                        errors.put("daysOfWeek", "must contain at least one day for WEEKLY schedules");
                    }
                    if (request.timeOfDay() == null) {
                        errors.put("timeOfDay", "is required for WEEKLY schedules");
                    }
                }
                case CRON -> {
                    if (request.cronExpression() == null || request.cronExpression().isBlank()) {
                        errors.put("cronExpression", "is required for CRON schedules");
                    } else if (!CronExpression.isValidExpression(request.cronExpression())) {
                        errors.put("cronExpression", "must be a valid Quartz cron expression");
                    }
                }
            }
        }
        if (!errors.isEmpty()) throw new ScheduleValidationException(errors);
    }

    private void validateParameters(TaskType taskType, Map<String, String> parameters,
                                    Map<String, String> errors) {
        Set<String> required = switch (taskType) {
            case LOG -> Set.of("message");
            case DUMMY_EMAIL -> Set.of("recipient", "subject", "body");
        };
        Map<String, String> safeParameters = parameters == null ? Map.of() : parameters;
        for (String key : required) {
            if (safeParameters.get(key) == null || safeParameters.get(key).isBlank()) {
                errors.put("parameters." + key, "is required for " + taskType);
            }
        }
    }
}
