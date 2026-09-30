package com.amalitech.backend.service;

import com.amalitech.backend.model.Job;

import java.util.UUID;

public interface JobService {

    Job createJob(String sourceFilename, Integer pageCount);

    Job markProcessing(UUID jobId);

    Job markCompleted(UUID jobId, String outputPath, long sizeBytes);

    Job markFailed(UUID jobId);

    Job getJobWithFile(UUID jobId);
}
