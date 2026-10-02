package com.amalitech.backend.service.impl;

import com.amalitech.backend.exception.JobNotFoundException;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobMetrics;
import com.amalitech.backend.repository.JobMetricsRepository;
import com.amalitech.backend.repository.JobRepository;
import com.amalitech.backend.service.JobMetricsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class JobMetricsServiceImpl implements JobMetricsService {

    private final JobRepository jobRepository;
    private final JobMetricsRepository jobMetricsRepository;

    public JobMetricsServiceImpl(
            JobRepository jobRepository,
            JobMetricsRepository jobMetricsRepository
    ) {
        this.jobRepository = jobRepository;
        this.jobMetricsRepository = jobMetricsRepository;
    }

    @Override
    @Transactional
    public JobMetrics saveWordCounts(
            UUID jobId,
            int sourceWordCount,
            int outputWordCount
    ) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(JobNotFoundException::new);

        JobMetrics metrics = job.getMetrics();

        if (metrics == null) {
            metrics = new JobMetrics(
                    job,
                    sourceWordCount,
                    outputWordCount
            );

            job.setMetrics(metrics);
        } else {
            metrics.setSourceWordCount(sourceWordCount);
            metrics.setOutputWordCount(outputWordCount);
        }

        return jobMetricsRepository.save(metrics);
    }
}