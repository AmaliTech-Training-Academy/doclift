package com.amalitech.backend.service;

import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobPhase;

import java.util.UUID;

public interface JobService {

    Job createJob(String sourceFilename, Integer pageCount);

    Job markProcessing(UUID jobId);

    Job markCompleted(UUID jobId, String outputPath, long sizeBytes);

    Job markFailed(UUID jobId);

    Job getJob(UUID jobId);

    Job updateProgress(
            UUID jobId,
            JobPhase phase,
            Integer progressPercent
    );

    Job getJobWithFile(UUID jobId);
}
