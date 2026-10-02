package com.amalitech.backend.service;

import com.amalitech.backend.exception.JobNotFoundException;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobMetrics;
import com.amalitech.backend.repository.JobMetricsRepository;
import com.amalitech.backend.repository.JobRepository;
import com.amalitech.backend.service.impl.JobMetricsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobMetricsServiceTest {

    private JobRepository jobRepository;
    private JobMetricsRepository jobMetricsRepository;
    private JobMetricsService jobMetricsService;

    private static final UUID JOB_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    @BeforeEach
    void setUp() {
        jobRepository =
                mock(JobRepository.class);

        jobMetricsRepository =
                mock(JobMetricsRepository.class);

        jobMetricsService =
                new JobMetricsServiceImpl(
                        jobRepository,
                        jobMetricsRepository
                );
    }

    @Test
    void shouldCreateMetricsWhenNoneExist() {

        Job job = new Job();
        job.setId(JOB_ID);

        when(jobRepository.findById(JOB_ID))
                .thenReturn(Optional.of(job));

        when(jobMetricsRepository.save(any(JobMetrics.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        JobMetrics result =
                jobMetricsService.saveWordCounts(
                        JOB_ID,
                        120,
                        118
                );

        assertNotNull(result);
        assertEquals(120, result.getSourceWordCount());
        assertEquals(118, result.getOutputWordCount());
        assertSame(job, result.getJob());
        assertSame(result, job.getMetrics());

        verify(jobRepository)
                .findById(JOB_ID);

        verify(jobMetricsRepository)
                .save(result);
    }

    @Test
    void shouldUpdateExistingMetrics() {

        Job job = new Job();
        job.setId(JOB_ID);

        JobMetrics existingMetrics =
                new JobMetrics(
                        job,
                        50,
                        45
                );

        job.setMetrics(existingMetrics);

        when(jobRepository.findById(JOB_ID))
                .thenReturn(Optional.of(job));

        when(jobMetricsRepository.save(existingMetrics))
                .thenReturn(existingMetrics);

        JobMetrics result =
                jobMetricsService.saveWordCounts(
                        JOB_ID,
                        120,
                        118
                );

        assertSame(existingMetrics, result);
        assertEquals(120, result.getSourceWordCount());
        assertEquals(118, result.getOutputWordCount());

        verify(jobMetricsRepository)
                .save(existingMetrics);
    }

    @Test
    void shouldThrowWhenJobDoesNotExist() {

        when(jobRepository.findById(JOB_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                JobNotFoundException.class,
                () -> jobMetricsService.saveWordCounts(
                        JOB_ID,
                        120,
                        118
                )
        );

        verify(jobRepository)
                .findById(JOB_ID);

        verifyNoInteractions(jobMetricsRepository);
    }
}