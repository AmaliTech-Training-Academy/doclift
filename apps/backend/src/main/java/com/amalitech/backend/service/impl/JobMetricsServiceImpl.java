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
    public JobMetrics saveMetrics(
            UUID jobId,
            int sourceWordCount,
            int outputWordCount,
            int orderedListsDetected,
            int unorderedListsDetected,
            int orderedListsReconstructed,
            int unorderedListsReconstructed
    ) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(JobNotFoundException::new);

        JobMetrics metrics = job.getMetrics();

        if (metrics == null) {
            metrics = new JobMetrics();
            metrics.setJob(job);
            job.setMetrics(metrics);
        }

        metrics.setSourceWordCount(sourceWordCount);
        metrics.setOutputWordCount(outputWordCount);

        metrics.setOrderedListsDetected(
                orderedListsDetected
        );

        metrics.setUnorderedListsDetected(
                unorderedListsDetected
        );

        metrics.setOrderedListsReconstructed(
                orderedListsReconstructed
        );

        metrics.setUnorderedListsReconstructed(
                unorderedListsReconstructed
        );

        return jobMetricsRepository.save(metrics);
    }
}