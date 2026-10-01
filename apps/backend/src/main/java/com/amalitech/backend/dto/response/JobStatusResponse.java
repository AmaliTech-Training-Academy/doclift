package com.amalitech.backend.dto.response;

import com.amalitech.backend.model.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record JobStatusResponse(
        UUID jobId,
        JobStatus status,
        String sourceFilename,
        Integer pageCount,
        Instant createdAt
) {
}