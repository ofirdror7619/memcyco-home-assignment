package com.memcyco.scheduling.service;

import com.memcyco.scheduling.dto.ScheduleRequest;
import com.memcyco.scheduling.dto.ScheduleResponse;
import com.memcyco.scheduling.entity.Schedule;
import com.memcyco.scheduling.exception.ResourceNotFoundException;
import com.memcyco.scheduling.repository.ScheduleRepository;
import com.memcyco.scheduling.scheduler.QuartzScheduleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ScheduleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduleService.class);

    private final ScheduleRepository repository;
    private final ScheduleValidator validator;
    private final QuartzScheduleManager quartz;

    public ScheduleService(ScheduleRepository repository, ScheduleValidator validator,
                           QuartzScheduleManager quartz) {
        this.repository = repository;
        this.validator = validator;
        this.quartz = quartz;
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse findById(long id) {
        return toResponse(findEntity(id));
    }

    public ScheduleResponse create(ScheduleRequest request) {
        validator.validate(request);
        Schedule schedule = new Schedule();
        apply(schedule, request);
        repository.saveAndFlush(schedule);
        try {
            quartz.synchronize(schedule);
        } catch (RuntimeException exception) {
            try {
                quartz.delete(schedule.getId());
            } finally {
                repository.deleteById(schedule.getId());
            }
            throw exception;
        }
        LOGGER.info("event=schedule_created scheduleId={} scheduleName='{}' taskType={} scheduleType={} enabled={}",
                schedule.getId(), schedule.getName(), schedule.getTaskType(),
                schedule.getScheduleType(), schedule.isEnabled());
        return toResponse(schedule);
    }

    public ScheduleResponse update(long id, ScheduleRequest request) {
        validator.validate(request);
        Schedule schedule = findEntity(id);
        ScheduleRequest previous = toRequest(schedule);
        apply(schedule, request);
        repository.saveAndFlush(schedule);
        try {
            quartz.synchronize(schedule);
        } catch (RuntimeException exception) {
            Schedule restored = findEntity(id);
            apply(restored, previous);
            repository.saveAndFlush(restored);
            quartz.synchronize(restored);
            throw exception;
        }
        LOGGER.info("event=schedule_updated scheduleId={} scheduleName='{}' taskType={} scheduleType={} enabled={}",
                schedule.getId(), schedule.getName(), schedule.getTaskType(),
                schedule.getScheduleType(), schedule.isEnabled());
        return toResponse(schedule);
    }

    public void delete(long id) {
        Schedule schedule = findEntity(id);
        quartz.delete(id);
        try {
            repository.deleteById(id);
        } catch (RuntimeException exception) {
            quartz.synchronize(schedule);
            throw exception;
        }
        LOGGER.info("event=schedule_deleted scheduleId={} scheduleName='{}' taskType={} scheduleType={} enabled={}",
                schedule.getId(), schedule.getName(), schedule.getTaskType(),
                schedule.getScheduleType(), schedule.isEnabled());
    }

    private Schedule findEntity(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule " + id + " was not found"));
    }

    private void apply(Schedule target, ScheduleRequest request) {
        target.setName(request.name().trim());
        target.setTaskType(request.taskType());
        target.setScheduleType(request.scheduleType());
        target.setExecutionTime(request.executionTime() == null ? null : request.executionTime().toInstant());
        target.setIntervalValue(request.intervalValue());
        target.setIntervalUnit(request.intervalUnit());
        target.setDaysOfWeek(request.daysOfWeek() == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(request.daysOfWeek()));
        target.setTimeOfDay(request.timeOfDay());
        target.setCronExpression(request.cronExpression() == null ? null : request.cronExpression().trim());
        target.setParameters(request.parameters() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(request.parameters()));
        target.setEnabled(request.enabled());
    }

    private ScheduleRequest toRequest(Schedule source) {
        return new ScheduleRequest(source.getName(), source.getTaskType(), source.getScheduleType(),
                source.getExecutionTime() == null ? null : source.getExecutionTime().atOffset(ZoneOffset.UTC),
                source.getIntervalValue(), source.getIntervalUnit(),
                new LinkedHashSet<>(source.getDaysOfWeek()), source.getTimeOfDay(),
                source.getCronExpression(), new LinkedHashMap<>(source.getParameters()), source.isEnabled());
    }

    private ScheduleResponse toResponse(Schedule schedule) {
        return new ScheduleResponse(schedule.getId(), schedule.getName(), schedule.getTaskType(),
                schedule.getScheduleType(), schedule.getExecutionTime() == null ? null
                : schedule.getExecutionTime().atOffset(ZoneOffset.UTC), schedule.getIntervalValue(),
                schedule.getIntervalUnit(), new LinkedHashSet<>(schedule.getDaysOfWeek()),
                schedule.getTimeOfDay(), schedule.getCronExpression(),
                new LinkedHashMap<>(schedule.getParameters()), schedule.isEnabled(),
                schedule.getCreatedAt(), schedule.getUpdatedAt());
    }
}
