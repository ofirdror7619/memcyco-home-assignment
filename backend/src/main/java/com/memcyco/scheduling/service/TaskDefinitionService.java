package com.memcyco.scheduling.service;

import com.memcyco.scheduling.dto.TaskDefinitionResponse;
import com.memcyco.scheduling.entity.TaskType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class TaskDefinitionService {
    public List<TaskDefinitionResponse> findAll() {
        return List.of(
                new TaskDefinitionResponse(TaskType.LOG, "Log message",
                        "Writes a message to the application log", Set.of("message")),
                new TaskDefinitionResponse(TaskType.DUMMY_EMAIL, "Dummy email",
                        "Logs a simulated outgoing email", Set.of("recipient", "subject", "body"))
        );
    }
}
