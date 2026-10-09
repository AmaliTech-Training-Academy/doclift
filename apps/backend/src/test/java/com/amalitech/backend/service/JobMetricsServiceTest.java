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

    private static final ConversionMetrics METRICS =
            new ConversionMetrics(
                    120,
                    118,
                    2,
                    1,
                    2,
                    1,
                    5,
                    2,
                    2,
                    1,
                    1,
                    3,
                    0
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
        job.setPageCount(7);

        when(jobRepository.findById(JOB_ID))
                .thenReturn(Optional.of(job));

        when(jobMetricsRepository.save(any(JobMetrics.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        JobMetrics result =
                jobMetricsService.saveMetrics(
                        JOB_ID,
                        METRICS
                );

        assertNotNull(result);

        assertEquals(
                120,
                result.getSourceWordCount()
        );

        assertEquals(
                118,
                result.getOutputWordCount()
        );

        assertEquals(
                2,
                result.getOrderedListsDetected()
        );

        assertEquals(
                1,
                result.getUnorderedListsDetected()
        );

        assertEquals(
                2,
                result.getOrderedListsReconstructed()
        );

        assertEquals(
                1,
                result.getUnorderedListsReconstructed()
        );

        assertEquals(
                5,
                result.getHeadingsDetected()
        );

        assertEquals(
                2,
                result.getH1HeadingCount()
        );

        assertEquals(
                2,
                result.getH2HeadingCount()
        );

        assertEquals(
                1,
                result.getH3HeadingCount()
        );

        assertEquals(
                1,
                result.getTablesDetected()
        );

        assertEquals(
                3,
                result.getImagesDetected()
        );

        assertEquals(
                0,
                result.getMultiColumnPageCount()
        );

        assertNull(
                result.getOutputPageCount()
        );

        assertSame(
                job,
                result.getJob()
        );

        assertSame(
                result,
                job.getMetrics()
        );

        verify(jobRepository)
                .findById(JOB_ID);

        verify(jobMetricsRepository)
                .save(result);
    }

    @Test
    void shouldUpdateExistingMetrics() {

        Job job = new Job();
        job.setId(JOB_ID);
        job.setPageCount(4);

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
                jobMetricsService.saveMetrics(
                        JOB_ID,
                        METRICS
                );

        assertSame(
                existingMetrics,
                result
        );

        assertEquals(
                120,
                result.getSourceWordCount()
        );

        assertEquals(
                118,
                result.getOutputWordCount()
        );

        assertEquals(
                2,
                result.getOrderedListsDetected()
        );

        assertEquals(
                1,
                result.getUnorderedListsDetected()
        );

        assertEquals(
                2,
                result.getOrderedListsReconstructed()
        );

        assertEquals(
                1,
                result.getUnorderedListsReconstructed()
        );

        assertEquals(
                5,
                result.getHeadingsDetected()
        );

        assertEquals(
                1,
                result.getTablesDetected()
        );

        assertEquals(
                3,
                result.getImagesDetected()
        );

        assertNull(
                result.getOutputPageCount()
        );

        verify(jobMetricsRepository)
                .save(existingMetrics);
    }

    @Test
    void shouldThrowWhenJobDoesNotExist() {

        when(jobRepository.findById(JOB_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                JobNotFoundException.class,
                () -> jobMetricsService.saveMetrics(
                        JOB_ID,
                        METRICS
                )
        );

        verify(jobRepository)
                .findById(JOB_ID);

        verifyNoInteractions(
                jobMetricsRepository
        );
    }
}
