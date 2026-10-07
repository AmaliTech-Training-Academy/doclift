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
                    JobPhase.LOADING_SOURCE, 0.01,
                    JobPhase.EXTRACTING_CONTENT, 0.63,
                    JobPhase.RECOVERING_STRUCTURE, 0.03,
                    JobPhase.GENERATING_DOCUMENT, 0.32,
                    JobPhase.SAVING_OUTPUT, 0.01
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
                || job.getPhase() == null) {
            return null;
        }

        if (job.getPhase() == JobPhase.COMPLETED) {
            return calculateDurationSeconds(job);
        }

        if (job.getPhaseStartedAt() == null) {
            return calculateProgressBasedBaseline(
                    job,
                    now
            );
        }

        double completedWeight =
                calculateCompletedPhaseWeight(
                        job.getPhase()
                );

        if (completedWeight <= 0.0) {
            return calculateProgressBasedBaseline(
                    job,
                    now
            );
        }

        double completedSeconds =
                Duration.between(
                        job.getStartedAt(),
                        job.getPhaseStartedAt()
                ).toMillis() / 1000.0;

        if (completedSeconds <= 0.0) {
            return calculateProgressBasedBaseline(
                    job,
                    now
            );
        }

        double completedPhaseBaseline =
                completedSeconds / completedWeight;

        Double progressBaseline =
                calculateProgressBasedBaseline(
                        job,
                        now
                );

        if (progressBaseline == null) {
            return roundToTwoDecimals(
                    completedPhaseBaseline
            );
        }

        return roundToTwoDecimals(
                Math.max(
                        completedPhaseBaseline,
                        progressBaseline
                )
        );
    }

    private double calculateCompletedPhaseWeight(
            JobPhase currentPhase
    ) {
        return switch (currentPhase) {
            case QUEUED,
                 LOADING_SOURCE ->
                    0.0;

            case EXTRACTING_CONTENT ->
                    weightOf(
                            JobPhase.LOADING_SOURCE
                    );

            case RECOVERING_STRUCTURE ->
                    weightOf(
                            JobPhase.LOADING_SOURCE,
                            JobPhase.EXTRACTING_CONTENT
                    );

            case GENERATING_DOCUMENT ->
                    weightOf(
                            JobPhase.LOADING_SOURCE,
                            JobPhase.EXTRACTING_CONTENT,
                            JobPhase.RECOVERING_STRUCTURE
                    );

            case SAVING_OUTPUT ->
                    weightOf(
                            JobPhase.LOADING_SOURCE,
                            JobPhase.EXTRACTING_CONTENT,
                            JobPhase.RECOVERING_STRUCTURE,
                            JobPhase.GENERATING_DOCUMENT
                    );

            case COMPLETED ->
                    1.0;
        };
    }

    private Double calculateProgressBasedBaseline(
            Job job,
            Instant now
    ) {
        if (job.getProgressPercent() == null
                || job.getProgressPercent() <= 0) {
            return null;
        }

        double elapsedSeconds =
                Duration.between(
                        job.getStartedAt(),
                        now
                ).toMillis() / 1000.0;

        double progressFraction =
                job.getProgressPercent() / 100.0;

        return roundToTwoDecimals(
                elapsedSeconds / progressFraction
        );
    }

    public int calculateProgressPercentForPhase(
            JobPhase phase
    ) {
        if (phase == null || phase == JobPhase.QUEUED) {
            return 0;
        }

        if (phase == JobPhase.COMPLETED) {
            return 100;
        }

        double completedWeight = 0.0;

        for (JobPhase currentPhase : JobPhase.values()) {

            if (currentPhase == phase) {
                break;
            }

            completedWeight +=
                    PHASE_WEIGHTS.getOrDefault(
                            currentPhase,
                            0.0
                    );
        }

        return (int) Math.round(
                completedWeight * 100
        );
    }
}