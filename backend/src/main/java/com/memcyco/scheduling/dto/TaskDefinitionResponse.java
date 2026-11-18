package com.memcyco.scheduling.dto;

import com.memcyco.scheduling.entity.TaskType;

import java.util.Set;

public record TaskDefinitionResponse(
        TaskType type,
        String name,
        String description,
        Set<String> requiredParameters
) {
}
