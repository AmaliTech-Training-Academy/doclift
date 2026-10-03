package com.amalitech.backend.service.impl;

import com.amalitech.backend.dto.response.JobMetricsResponse;
import com.amalitech.backend.dto.response.JobOutputResponse;
import com.amalitech.backend.dto.response.JobStatusResponse;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobStatus;
import org.springframework.stereotype.Component;

@Component
public class JobStatusResponseMapper {

    private final JobEtaCalculator jobEtaCalculator;
    private final JobFilenameResolver jobFilenameResolver;

    public JobStatusResponseMapper(
            JobEtaCalculator jobEtaCalculator,
            JobFilenameResolver jobFilenameResolver
    ) {
        this.jobEtaCalculator =
                jobEtaCalculator;

        this.jobFilenameResolver =
                jobFilenameResolver;
    }

    public JobStatusResponse toResponse(
            Job job
    ) {
        return new JobStatusResponse(
                job.getId(),
                job.getStatus(),
                job.getSourceFilename(),
                job.getPageCount(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getPhase(),
                job.getProgressPercent(),
                jobEtaCalculator
                        .calculateDurationSeconds(job),
                jobEtaCalculator
                        .calculateEstimatedRemainingSeconds(job),
                jobEtaCalculator
                        .calculateEstimatedTotalSeconds(job),
                buildOutput(job),
                buildMetrics(job)
        );
    }

    private JobOutputResponse buildOutput(
            Job job
    ) {
        if (job.getStatus() != JobStatus.DONE
                || job.getFile() == null) {
            return null;
        }

        return new JobOutputResponse(
                jobFilenameResolver
                        .buildOutputFilename(
                                job.getSourceFilename()
                        ),
                job.getFile().getSize(),
                "/api/v1/jobs/"
                        + job.getId()
                        + "/download"
        );
    }

    private JobMetricsResponse buildMetrics(
            Job job
    ) {
        if (job.getMetrics() == null) {
            return null;
        }

        return new JobMetricsResponse(
                job.getMetrics().getSourceWordCount(),
                job.getMetrics().getOutputWordCount(),
                job.getMetrics().getOrderedListsDetected(),
                job.getMetrics().getUnorderedListsDetected(),
                job.getMetrics().getOrderedListsReconstructed(),
                job.getMetrics().getUnorderedListsReconstructed()
        );
    }
}