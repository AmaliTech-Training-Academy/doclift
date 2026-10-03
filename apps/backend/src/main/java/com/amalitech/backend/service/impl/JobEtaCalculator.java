package com.amalitech.backend.service.impl;

import com.amalitech.backend.model.Job;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class JobEtaCalculator {

    public Double calculateDurationSeconds(
            Job job
    ) {
        if (job == null
                || job.getStartedAt() == null) {
            return null;
        }

        Instant endTime =
                job.getCompletedAt() != null
                        ? job.getCompletedAt()
                        : Instant.now();

        return calculateDurationSeconds(
                job.getStartedAt(),
                endTime
        );
    }

    public Double calculateEstimatedTotalSeconds(
            Job job
    ) {
        return calculateEstimatedTotalSeconds(
                job,
                Instant.now()
        );
    }

    public Double calculateEstimatedRemainingSeconds(
            Job job
    ) {
        return calculateEstimatedRemainingSeconds(
                job,
                Instant.now()
        );
    }

    Double calculateEstimatedTotalSeconds(
            Job job,
            Instant now
    ) {
        if (job == null
                || job.getStartedAt() == null
                || job.getProgressPercent() == null) {
            return null;
        }

        int progress =
                job.getProgressPercent();

        if (progress <= 0) {
            return null;
        }

        if (progress >= 100) {
            return calculateDurationSeconds(job);
        }

        double elapsedSeconds =
                Duration.between(
                        job.getStartedAt(),
                        now
                ).toMillis() / 1000.0;

        double progressFraction =
                progress / 100.0;

        double estimatedTotal =
                elapsedSeconds / progressFraction;

        return roundToTwoDecimals(
                estimatedTotal
        );
    }

    Double calculateEstimatedRemainingSeconds(
            Job job,
            Instant now
    ) {
        if (job == null
                || job.getStartedAt() == null
                || job.getProgressPercent() == null) {
            return null;
        }

        int progress =
                job.getProgressPercent();

        if (progress <= 0) {
            return null;
        }

        if (progress >= 100) {
            return 0.0;
        }

        Double estimatedTotal =
                calculateEstimatedTotalSeconds(
                        job,
                        now
                );

        if (estimatedTotal == null) {
            return null;
        }

        double elapsedSeconds =
                Duration.between(
                        job.getStartedAt(),
                        now
                ).toMillis() / 1000.0;

        double remaining =
                Math.max(
                        0.0,
                        estimatedTotal - elapsedSeconds
                );

        return roundToTwoDecimals(
                remaining
        );
    }

    private Double calculateDurationSeconds(
            Instant start,
            Instant end
    ) {
        long millis =
                Duration.between(
                        start,
                        end
                ).toMillis();

        return roundToTwoDecimals(
                millis / 1000.0
        );
    }

    private double roundToTwoDecimals(
            double value
    ) {
        return Math.round(
                value * 100.0
        ) / 100.0;
    }
}