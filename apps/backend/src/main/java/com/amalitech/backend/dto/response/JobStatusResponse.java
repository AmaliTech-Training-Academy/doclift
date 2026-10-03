package com.amalitech.backend.dto.response;

import com.amalitech.backend.model.JobPhase;
import com.amalitech.backend.model.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record JobStatusResponse(
        UUID jobId,
        JobStatus status,
        String sourceFilename,
        Integer pageCount,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        JobPhase phase,
        Integer progressPercent,
        Double durationSeconds,
        Double estimatedRemainingSeconds,
        Double estimatedTotalSeconds,
        Double currentPhaseEstimatedRemainingSeconds,
        JobOutputResponse output,
        JobMetricsResponse metrics
) {}