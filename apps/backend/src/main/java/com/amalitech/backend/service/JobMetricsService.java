package com.amalitech.backend.service;

import com.amalitech.backend.model.JobMetrics;

import java.util.UUID;

public interface JobMetricsService {

    JobMetrics saveWordCounts(
            UUID jobId,
            int sourceWordCount,
            int outputWordCount
    );
}