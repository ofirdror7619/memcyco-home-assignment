package com.memcyco.scheduling.repository;

import com.memcyco.scheduling.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
}
