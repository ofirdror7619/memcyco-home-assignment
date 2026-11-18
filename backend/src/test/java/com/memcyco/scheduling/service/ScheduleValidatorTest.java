package com.memcyco.scheduling.service;

import com.memcyco.scheduling.dto.ScheduleRequest;
import com.memcyco.scheduling.entity.IntervalUnit;
import com.memcyco.scheduling.entity.ScheduleType;
import com.memcyco.scheduling.entity.TaskType;
import com.memcyco.scheduling.exception.ScheduleValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleValidatorTest {
    private final Instant now = Instant.parse("2030-01-01T00:00:00Z");
    private final ScheduleValidator validator = new ScheduleValidator(Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void rejectsPastOneTimeSchedule() {
        assertInvalid(request(ScheduleType.ONE_TIME, now.minusSeconds(1).atOffset(ZoneOffset.UTC),
                null, null, null, null, null, Map.of("message", "hello")), "executionTime");
    }

    @Test
    void rejectsNonPositiveInterval() {
        assertInvalid(request(ScheduleType.INTERVAL, null, 0, IntervalUnit.MINUTES,
                null, null, null, Map.of("message", "hello")), "intervalValue");
    }

    @Test
    void rejectsWeeklyScheduleWithoutDays() {
        assertInvalid(request(ScheduleType.WEEKLY, null, null, null,
                Set.of(), LocalTime.NOON, null, Map.of("message", "hello")), "daysOfWeek");
    }

    @Test
    void rejectsInvalidCronExpression() {
        assertInvalid(request(ScheduleType.CRON, null, null, null,
                null, null, "not a cron", Map.of("message", "hello")), "cronExpression");
    }

    @Test
    void rejectsMissingTaskParameters() {
        assertInvalid(new ScheduleRequest("Email", TaskType.DUMMY_EMAIL, ScheduleType.CRON,
                null, null, null, null, null, "0 0 12 ? * *", Map.of("recipient", "a@b.test"), true),
                "parameters.subject");
    }

    private ScheduleRequest request(ScheduleType type, OffsetDateTime executionTime,
                                    Integer interval, IntervalUnit unit, Set<DayOfWeek> days,
                                    LocalTime time, String cron, Map<String, String> parameters) {
        return new ScheduleRequest("Test", TaskType.LOG, type, executionTime, interval, unit,
                days, time, cron, parameters, true);
    }

    private void assertInvalid(ScheduleRequest request, String field) {
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ScheduleValidationException.class)
                .satisfies(exception -> {
                    ScheduleValidationException validation = (ScheduleValidationException) exception;
                    org.assertj.core.api.Assertions.assertThat(validation.getFieldErrors()).containsKey(field);
                });
    }
}
