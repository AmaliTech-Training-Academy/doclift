package com.amalitech.backend.service;

import com.amalitech.backend.model.JobMetrics;

import java.util.UUID;

public interface JobMetricsService {

    JobMetrics saveMetrics(
            UUID jobId,
            ConversionMetrics metrics
    );
}
