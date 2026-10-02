package com.amalitech.backend.repository;

import com.amalitech.backend.model.JobMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobMetricsRepository
        extends JpaRepository<JobMetrics, Long> {
}