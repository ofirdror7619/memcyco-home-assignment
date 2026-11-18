package com.memcyco.scheduling.exception;

public class SchedulingOperationException extends RuntimeException {
    public SchedulingOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
