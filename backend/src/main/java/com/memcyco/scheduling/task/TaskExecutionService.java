package com.memcyco.scheduling.task;

import com.memcyco.scheduling.entity.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TaskExecutionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecutionService.class);

    public void execute(TaskType taskType, Map<String, String> parameters) {
        switch (taskType) {
            case LOG -> LOGGER.info("Scheduled LOG task: {}", parameters.get("message"));
            case DUMMY_EMAIL -> LOGGER.info("Scheduled DUMMY_EMAIL task simulated successfully");
        }
    }
}
