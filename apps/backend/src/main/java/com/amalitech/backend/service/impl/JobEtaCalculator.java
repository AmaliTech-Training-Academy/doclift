package com.amalitech.backend.service.impl;

import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobPhase;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class JobEtaCalculator {


    private static final Map<JobPhase, Double> PHASE_WEIGHTS =
            Map.of(
                    JobPhase.LOADING_SOURCE, 0.05,
                    JobPhase.EXTRACTING_CONTENT, 0.35,
                    JobPhase.RECOVERING_STRUCTURE, 0.20,
                    JobPhase.GENERATING_DOCUMENT, 0.30,
                    JobPhase.SAVING_OUTPUT, 0.10
            );

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

    Double calculateEstimatedTotalSeconds(
            Job job,
            Instant now
    ) {
        if (job == null
                || job.getStartedAt() == null) {
            return null;
        }

        if (job.getProgressPercent() != null
                && job.getProgressPercent() >= 100) {
            return calculateDurationSeconds(job);
        }

        Double remaining =
                calculateEstimatedRemainingSeconds(
                        job,
                        now
                );

        if (remaining == null) {
            return null;
        }

        double elapsedSeconds =
                Duration.between(
                        job.getStartedAt(),
                        now
                ).toMillis() / 1000.0;

        return roundToTwoDecimals(
                elapsedSeconds + remaining
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

    Double calculateEstimatedRemainingSeconds(
            Job job,
            Instant now
    ) {
        if (job == null
                || job.getPhase() == null
                || job.getStartedAt() == null) {
            return null;
        }

        if (job.getPhase() == JobPhase.COMPLETED) {
            return 0.0;
        }

        Double baselineTotal =
                calculateBaselineTotalSeconds(
                        job,
                        now
                );

        if (baselineTotal == null) {
            return null;
        }


        Double currentPhaseRemaining =
                calculateCurrentPhaseEstimatedRemainingSeconds(
                        job,
                        now
                );

        if (currentPhaseRemaining == null) {
            return null;
        }

        double futurePhaseSeconds =
                calculateFuturePhaseSeconds(
                        job.getPhase(),
                        baselineTotal
                );

        return roundToTwoDecimals(
                currentPhaseRemaining
                        + futurePhaseSeconds
        );
    }

    private double calculateFuturePhaseSeconds(
            JobPhase currentPhase,
            double estimatedTotalSeconds
    ) {
        double futureWeight =
                switch (currentPhase) {
                    case LOADING_SOURCE ->
                            weightOf(
                                    JobPhase.EXTRACTING_CONTENT,
                                    JobPhase.RECOVERING_STRUCTURE,
                                    JobPhase.GENERATING_DOCUMENT,
                                    JobPhase.SAVING_OUTPUT
                            );

                    case EXTRACTING_CONTENT ->
                            weightOf(
                                    JobPhase.RECOVERING_STRUCTURE,
                                    JobPhase.GENERATING_DOCUMENT,
                                    JobPhase.SAVING_OUTPUT
                            );

                    case RECOVERING_STRUCTURE ->
                            weightOf(
                                    JobPhase.GENERATING_DOCUMENT,
                                    JobPhase.SAVING_OUTPUT
                            );

                    case GENERATING_DOCUMENT ->
                            weightOf(
                                    JobPhase.SAVING_OUTPUT
                            );

                    case SAVING_OUTPUT,
                         COMPLETED ->
                            0.0;

                    case QUEUED ->
                            1.0;
                };

        return estimatedTotalSeconds
                * futureWeight;
    }

    private double weightOf(
            JobPhase... phases
    ) {
        double total = 0.0;

        for (JobPhase phase : phases) {
            total += PHASE_WEIGHTS.getOrDefault(
                    phase,
                    0.0
            );
        }

        return total;
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

    public Double calculateCurrentPhaseEstimatedRemainingSeconds(
            Job job
    ) {
        return calculateCurrentPhaseEstimatedRemainingSeconds(
                job,
                Instant.now()
        );
    }

    Double calculateCurrentPhaseEstimatedRemainingSeconds(
            Job job,
            Instant now
    ) {
        if (job == null
                || job.getPhase() == null) {
            return null;
        }

        if (job.getPhase() == JobPhase.COMPLETED) {
            return 0.0;
        }

        if (job.getPhaseStartedAt() == null) {
            return null;
        }

        Double weight =
                PHASE_WEIGHTS.get(
                        job.getPhase()
                );

        if (weight == null) {
            return null;
        }

        Double baselineTotal =
                calculateBaselineTotalSeconds(
                        job,
                        now
                );

        if (baselineTotal == null) {
            return null;
        }

        double estimatedPhaseDuration =
                baselineTotal * weight;

        double phaseElapsedSeconds =
                Duration.between(
                        job.getPhaseStartedAt(),
                        now
                ).toMillis() / 1000.0;

        double remaining =
                Math.max(
                        0.0,
                        estimatedPhaseDuration
                                - phaseElapsedSeconds
                );

        return roundToTwoDecimals(
                remaining
        );
    }

    private Double calculateBaselineTotalSeconds(
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

        return roundToTwoDecimals(
                elapsedSeconds / progressFraction
        );
    }

}