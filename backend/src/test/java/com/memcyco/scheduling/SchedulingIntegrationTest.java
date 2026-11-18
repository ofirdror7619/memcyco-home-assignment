package com.memcyco.scheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memcyco.scheduling.dto.ScheduleRequest;
import com.memcyco.scheduling.dto.ScheduleResponse;
import com.memcyco.scheduling.entity.IntervalUnit;
import com.memcyco.scheduling.entity.Schedule;
import com.memcyco.scheduling.entity.ScheduleType;
import com.memcyco.scheduling.entity.TaskType;
import com.memcyco.scheduling.repository.ScheduleRepository;
import com.memcyco.scheduling.scheduler.QuartzScheduleManager;
import com.memcyco.scheduling.scheduler.ScheduleReconciler;
import com.memcyco.scheduling.service.ScheduleService;
import com.memcyco.scheduling.task.TaskExecutionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchedulingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ScheduleService scheduleService;
    @Autowired ScheduleRepository repository;
    @Autowired Scheduler scheduler;
    @Autowired ScheduleReconciler reconciler;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactionTemplate;
    @MockBean TaskExecutionService taskExecutionService;

    @BeforeEach
    void cleanDatabaseAndScheduler() throws Exception {
        scheduler.clear();
        repository.deleteAll();
        reset(taskExecutionService);
    }

    @Test
    void exposesReadOnlyTaskDefinitions() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("LOG"))
                .andExpect(jsonPath("$[0].requiredParameters[0]").value("message"))
                .andExpect(jsonPath("$[1].type").value("DUMMY_EMAIL"));
    }

    @Test
    void performsCrudThroughRestApi() throws Exception {
        ScheduleRequest create = intervalRequest("Initial", 5, true);
        String body = mvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(create)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/schedules/\\d+")))
                .andExpect(jsonPath("$.name").value("Initial"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        mvc.perform(get("/api/schedules/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.intervalValue").value(5));
        mvc.perform(get("/api/schedules"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));

        mvc.perform(put("/api/schedules/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(intervalRequest("Updated", 10, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(delete("/api/schedules/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/schedules/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void returnsConsistentValidationErrors() throws Exception {
        ScheduleRequest invalid = new ScheduleRequest("Bad", TaskType.LOG, ScheduleType.INTERVAL,
                null, 0, null, null, null, null, Map.of(), true);
        mvc.perform(post("/api/schedules").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Schedule validation failed"))
                .andExpect(jsonPath("$.fieldErrors.intervalValue").exists())
                .andExpect(jsonPath("$.fieldErrors.intervalUnit").exists())
                .andExpect(jsonPath("$.fieldErrors['parameters.message']").exists());

        mvc.perform(post("/api/schedules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"taskType\":\"UNKNOWN\",\"scheduleType\":\"CRON\",\"enabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request or unsupported enum/date value"));
    }

    @Test
    void registersUpdatesAndDeletesQuartzJob() throws Exception {
        ScheduleResponse created = scheduleService.create(intervalRequest("Quartz", 5, true));
        assertThat(scheduler.checkExists(QuartzScheduleManager.jobKey(created.id()))).isTrue();
        SimpleTrigger initial = (SimpleTrigger) scheduler.getTrigger(QuartzScheduleManager.triggerKey(created.id()));
        assertThat(initial.getRepeatInterval()).isEqualTo(300_000L);

        scheduleService.update(created.id(), intervalRequest("Quartz", 2, true));
        SimpleTrigger updated = (SimpleTrigger) scheduler.getTrigger(QuartzScheduleManager.triggerKey(created.id()));
        assertThat(updated.getRepeatInterval()).isEqualTo(120_000L);

        scheduleService.delete(created.id());
        assertThat(scheduler.checkExists(QuartzScheduleManager.jobKey(created.id()))).isFalse();
    }

    @Test
    void disabledScheduleHasNoQuartzJob() throws Exception {
        ScheduleResponse created = scheduleService.create(intervalRequest("Disabled", 5, false));
        assertThat(scheduler.checkExists(QuartzScheduleManager.jobKey(created.id()))).isFalse();
    }

    @Test
    void executesScheduledTaskWithPersistedParametersAndDoesNotRecreateCompletedOneTimeTrigger()
            throws InterruptedException {
        Map<String, String> parameters = Map.of("message", "executed by quartz");
        ScheduleRequest request = new ScheduleRequest("Execute", TaskType.LOG, ScheduleType.ONE_TIME,
                OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(2), null, null, null, null,
                null, parameters, true);
        scheduleService.create(request);
        verify(taskExecutionService, timeout(7_000)).execute(eq(TaskType.LOG), eq(parameters));

        reset(taskExecutionService);
        reconciler.reconcile();
        Thread.sleep(500);
        verifyNoInteractions(taskExecutionService);
    }

    @Test
    void persistsParametersAndWeeklyDaysInDatabase() {
        Long id = transactionTemplate.execute(status -> {
            Schedule schedule = new Schedule();
            schedule.setName("Persistence");
            schedule.setTaskType(TaskType.DUMMY_EMAIL);
            schedule.setScheduleType(ScheduleType.WEEKLY);
            schedule.setDaysOfWeek(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
            schedule.setTimeOfDay(java.time.LocalTime.of(8, 15));
            schedule.setParameters(Map.of("recipient", "a@b.test", "subject", "Hi", "body", "Body"));
            schedule.setEnabled(false);
            return repository.saveAndFlush(schedule).getId();
        });

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.clear();
            Schedule loaded = repository.findById(id).orElseThrow();
            assertThat(loaded.getParameters()).containsEntry("subject", "Hi");
            assertThat(loaded.getDaysOfWeek())
                    .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        });
    }

    private ScheduleRequest intervalRequest(String name, int minutes, boolean enabled) {
        return new ScheduleRequest(name, TaskType.LOG, ScheduleType.INTERVAL, null, minutes,
                IntervalUnit.MINUTES, null, null, null, Map.of("message", "hello"), enabled);
    }
}
