package com.memcyco.scheduling.controller;

import com.memcyco.scheduling.dto.ScheduleRequest;
import com.memcyco.scheduling.dto.ScheduleResponse;
import com.memcyco.scheduling.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {
    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public List<ScheduleResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ScheduleResponse findById(@PathVariable long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<ScheduleResponse> create(@Valid @RequestBody ScheduleRequest request) {
        ScheduleResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/schedules/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ScheduleResponse update(@PathVariable long id,
                                   @Valid @RequestBody ScheduleRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
