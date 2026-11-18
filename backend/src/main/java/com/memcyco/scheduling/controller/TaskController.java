package com.memcyco.scheduling.controller;

import com.memcyco.scheduling.dto.TaskDefinitionResponse;
import com.memcyco.scheduling.service.TaskDefinitionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskDefinitionService service;

    public TaskController(TaskDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaskDefinitionResponse> findAll() {
        return service.findAll();
    }
}
