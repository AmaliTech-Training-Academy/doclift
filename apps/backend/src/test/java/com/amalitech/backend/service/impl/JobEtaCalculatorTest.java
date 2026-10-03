package com.amalitech.backend.service.impl;

import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JobEtaCalculatorTest {

    private JobEtaCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new JobEtaCalculator();
    }

    @Test
    void shouldReturnNullEtaWhenJobHasNotStarted() {

        Job job = new Job();
        job.setProgressPercent(50);

        assertNull(
                calculator.calculateEstimatedTotalSeconds(job)
        );

        assertNull(
                calculator.calculateEstimatedRemainingSeconds(job)
        );
    }

    @Test
    void shouldReturnNullEtaWhenProgressIsZero() {

        Job job = new Job();
        job.setStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );
        job.setProgressPercent(0);

        Instant now =
                Instant.parse("2026-10-03T10:00:30Z");

        assertNull(
                calculator.calculateEstimatedTotalSeconds(
                        job,
                        now
                )
        );

        assertNull(
                calculator.calculateEstimatedRemainingSeconds(
                        job,
                        now
                )
        );
    }

    @Test
    void shouldCalculatePhaseAwareRemainingEta() {

        Job job = new Job();

        job.setStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );

        job.setProgressPercent(75);

        job.setPhase(
                JobPhase.GENERATING_DOCUMENT
        );

        job.setPhaseStartedAt(
                Instant.parse("2026-10-03T10:00:20Z")
        );

        Instant now =
                Instant.parse("2026-10-03T10:00:30Z");

        Double remaining =
                calculator.calculateEstimatedRemainingSeconds(
                        job,
                        now
                );

        assertEquals(
                6.0,
                remaining
        );
    }

    @Test
    void shouldReturnZeroRemainingEtaWhenCompleted() {

        Job job = new Job();

        job.setStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );

        job.setCompletedAt(
                Instant.parse("2026-10-03T10:01:04.440Z")
        );

        job.setProgressPercent(100);

        job.setPhase(JobPhase.COMPLETED);

        Instant now =
                Instant.parse("2026-10-03T10:02:00Z");

        assertEquals(
                64.44,
                calculator.calculateEstimatedTotalSeconds(
                        job,
                        now
                )
        );

        assertEquals(
                0.0,
                calculator.calculateEstimatedRemainingSeconds(
                        job,
                        now
                )
        );
    }

    @Test
    void shouldEstimateRemainingTimeForCurrentPhase() {

        Job job = new Job();

        job.setStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );

        job.setProgressPercent(50);

        job.setPhase(
                JobPhase.GENERATING_DOCUMENT
        );

        job.setPhaseStartedAt(
                Instant.parse("2026-10-03T10:00:20Z")
        );

        Instant now =
                Instant.parse("2026-10-03T10:00:30Z");

        Double result =
                calculator
                        .calculateCurrentPhaseEstimatedRemainingSeconds(
                                job,
                                now
                        );

        assertEquals(
                8.0,
                result
        );
    }

    @Test
    void shouldReturnZeroPhaseEtaWhenCompleted() {

        Job job = new Job();

        job.setStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );

        job.setCompletedAt(
                Instant.parse("2026-10-03T10:01:00Z")
        );

        job.setProgressPercent(100);

        job.setPhase(
                JobPhase.COMPLETED
        );

        job.setPhaseStartedAt(
                Instant.parse("2026-10-03T10:01:00Z")
        );

        Instant now =
                Instant.parse("2026-10-03T10:01:10Z");

        assertEquals(
                0.0,
                calculator
                        .calculateCurrentPhaseEstimatedRemainingSeconds(
                                job,
                                now
                        )
        );
    }

    @Test
    void shouldReturnNullPhaseEtaWhenPhaseStartTimeIsMissing() {

        Job job = new Job();

        job.setStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );

        job.setProgressPercent(50);

        job.setPhase(
                JobPhase.EXTRACTING_CONTENT
        );

        Instant now =
                Instant.parse("2026-10-03T10:00:30Z");

        assertNull(
                calculator
                        .calculateCurrentPhaseEstimatedRemainingSeconds(
                                job,
                                now
                        )
        );
    }

    @Test
    void shouldReturnNullPhaseEtaForQueuedPhase() {

        Job job = new Job();

        job.setStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );

        job.setProgressPercent(10);

        job.setPhase(
                JobPhase.QUEUED
        );

        job.setPhaseStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );

        Instant now =
                Instant.parse("2026-10-03T10:00:05Z");

        assertNull(
                calculator
                        .calculateCurrentPhaseEstimatedRemainingSeconds(
                                job,
                                now
                        )
        );
    }

    @Test
    void shouldCalculateEstimatedTotalFromElapsedAndRemaining() {

        Job job = new Job();

        job.setStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );

        job.setProgressPercent(75);

        job.setPhase(
                JobPhase.GENERATING_DOCUMENT
        );

        job.setPhaseStartedAt(
                Instant.parse("2026-10-03T10:00:20Z")
        );

        Instant now =
                Instant.parse("2026-10-03T10:00:30Z");

        Double total =
                calculator.calculateEstimatedTotalSeconds(
                        job,
                        now
                );

        assertEquals(
                36.0,
                total
        );
    }

    @Test
    void shouldCalculateDurationForCompletedJob() {

        Job job = new Job();

        job.setStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );

        job.setCompletedAt(
                Instant.parse("2026-10-03T10:00:11Z")
        );

        assertEquals(
                11.0,
                calculator.calculateDurationSeconds(job)
        );
    }

    @Test
    void shouldHideEtaBeforeConfidenceThreshold() {

        Job job = new Job();

        job.setStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );

        job.setProgressPercent(25);
        job.setPhase(JobPhase.EXTRACTING_CONTENT);

        job.setPhaseStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );

        Instant now =
                Instant.parse("2026-10-03T10:00:04Z");

        assertNull(
                calculator.calculateEstimatedRemainingSeconds(
                        job,
                        now
                )
        );

        assertNull(
                calculator.calculateEstimatedTotalSeconds(
                        job,
                        now
                )
        );
    }

    @Test
    void shouldExposeEtaAfterConfidenceThreshold() {

        Job job = new Job();

        job.setStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );

        job.setProgressPercent(25);
        job.setPhase(JobPhase.EXTRACTING_CONTENT);

        job.setPhaseStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );

        Instant now =
                Instant.parse("2026-10-03T10:00:06Z");

        assertNotNull(
                calculator.calculateEstimatedRemainingSeconds(
                        job,
                        now
                )
        );

        assertNotNull(
                calculator.calculateEstimatedTotalSeconds(
                        job,
                        now
                )
        );
    }
}