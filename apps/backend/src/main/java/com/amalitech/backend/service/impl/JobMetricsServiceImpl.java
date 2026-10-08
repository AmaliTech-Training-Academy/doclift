package com.amalitech.backend.service.impl;

import com.amalitech.backend.exception.JobNotFoundException;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobMetrics;
import com.amalitech.backend.repository.JobMetricsRepository;
import com.amalitech.backend.repository.JobRepository;
import com.amalitech.backend.service.ConversionMetrics;
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
            ConversionMetrics metrics
    ) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(JobNotFoundException::new);

        JobMetrics jobMetrics = job.getMetrics();

        if (jobMetrics == null) {
            jobMetrics = new JobMetrics();
            jobMetrics.setJob(job);
            job.setMetrics(jobMetrics);
        }

        jobMetrics.setSourceWordCount(metrics.sourceWordCount());
        jobMetrics.setOutputWordCount(metrics.outputWordCount());

        jobMetrics.setOrderedListsDetected(
                metrics.orderedListsDetected()
        );

        jobMetrics.setUnorderedListsDetected(
                metrics.unorderedListsDetected()
        );

        jobMetrics.setOrderedListsReconstructed(
                metrics.orderedListsReconstructed()
        );

        jobMetrics.setUnorderedListsReconstructed(
                metrics.unorderedListsReconstructed()
        );

        jobMetrics.setHeadingsDetected(metrics.headingsDetected());
        jobMetrics.setH1HeadingCount(metrics.h1HeadingCount());
        jobMetrics.setH2HeadingCount(metrics.h2HeadingCount());
        jobMetrics.setH3HeadingCount(metrics.h3HeadingCount());
        jobMetrics.setTablesDetected(metrics.tablesDetected());
        jobMetrics.setImagesDetected(metrics.imagesDetected());
        jobMetrics.setMultiColumnPageCount(metrics.multiColumnPageCount());
        jobMetrics.setOutputPageCount(job.getPageCount());

        return jobMetricsRepository.save(jobMetrics);
    }
}
