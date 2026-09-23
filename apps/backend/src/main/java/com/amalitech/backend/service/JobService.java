package com.amalitech.backend.service;

import com.amalitech.backend.model.Job;

public interface JobService {

    Job createJob(String sourceFilename, Integer pageCount);

    Job markProcessing(Long jobId);

    Job markCompleted(Long jobId, String outputPath, long sizeBytes);

    Job markFailed(Long jobId);

    Job getJobWithFile(Long jobId);
}
