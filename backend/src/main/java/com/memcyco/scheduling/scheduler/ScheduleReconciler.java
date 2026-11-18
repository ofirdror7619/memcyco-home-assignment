package com.memcyco.scheduling.scheduler;

import com.memcyco.scheduling.repository.ScheduleRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ScheduleReconciler {
    private final ScheduleRepository repository;
    private final QuartzScheduleManager quartz;

    public ScheduleReconciler(ScheduleRepository repository, QuartzScheduleManager quartz) {
        this.repository = repository;
        this.quartz = quartz;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        repository.findAll().forEach(schedule -> {
            try {
                quartz.reconcile(schedule);
            } catch (RuntimeException exception) {
                // QuartzScheduleManager logs the failure with schedule and key context.
            }
        });
    }
}
