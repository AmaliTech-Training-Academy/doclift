package com.amalitech.backend.service.impl;

import com.amalitech.backend.model.Job;
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
    void shouldEstimateTotalAndRemainingTimeAtFiftyPercent() {

        Job job = new Job();

        job.setStartedAt(
                Instant.parse("2026-10-03T10:00:00Z")
        );

        job.setProgressPercent(50);

        Instant now =
                Instant.parse("2026-10-03T10:00:30Z");

        Double estimatedTotal =
                calculator.calculateEstimatedTotalSeconds(
                        job,
                        now
                );

        Double estimatedRemaining =
                calculator.calculateEstimatedRemainingSeconds(
                        job,
                        now
                );

        assertEquals(
                60.0,
                estimatedTotal
        );

        assertEquals(
                30.0,
                estimatedRemaining
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
}