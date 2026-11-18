package com.memcyco.scheduling.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public class ScheduleValidationException extends RuntimeException {
    private final Map<String, String> fieldErrors;

    public ScheduleValidationException(Map<String, String> fieldErrors) {
        super("Schedule validation failed");
        this.fieldErrors = new LinkedHashMap<>(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
