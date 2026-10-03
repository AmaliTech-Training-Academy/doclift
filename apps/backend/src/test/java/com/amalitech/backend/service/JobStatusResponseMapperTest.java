package com.amalitech.backend.service;

import com.amalitech.backend.dto.response.JobStatusResponse;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobFile;
import com.amalitech.backend.model.JobMetrics;
import com.amalitech.backend.model.JobPhase;
import com.amalitech.backend.model.JobStatus;
import com.amalitech.backend.service.impl.JobEtaCalculator;
import com.amalitech.backend.service.impl.JobFilenameResolver;
import com.amalitech.backend.service.impl.JobStatusResponseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobStatusResponseMapperTest {

    private JobStatusResponseMapper mapper;

    @BeforeEach
    void setUp() {
        JobEtaCalculator etaCalculator =
                new JobEtaCalculator();

        JobFilenameResolver filenameResolver =
                new JobFilenameResolver();

        mapper =
                new JobStatusResponseMapper(
                        etaCalculator,
                        filenameResolver
                );
    }

    @Test
    void shouldMapProcessingJobWithoutOutputOrMetrics() {

        Job job = new Job();

        UUID jobId =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        job.setId(jobId);
        job.setStatus(JobStatus.PROCESSING);
        job.setSourceFilename("sample.pdf");
        job.setPageCount(3);
        job.setCreatedAt(
                Instant.parse(
                        "2026-10-03T10:00:00Z"
                )
        );
        job.setStartedAt(
                Instant.parse(
                        "2026-10-03T10:00:01Z"
                )
        );
        job.setPhase(
                JobPhase.RECOVERING_STRUCTURE
        );
        job.setProgressPercent(55);

        JobStatusResponse response =
                mapper.toResponse(job);

        assertEquals(
                jobId,
                response.jobId()
        );

        assertEquals(
                JobStatus.PROCESSING,
                response.status()
        );

        assertEquals(
                "sample.pdf",
                response.sourceFilename()
        );

        assertEquals(
                3,
                response.pageCount()
        );

        assertEquals(
                JobPhase.RECOVERING_STRUCTURE,
                response.phase()
        );

        assertEquals(
                55,
                response.progressPercent()
        );

        assertNull(
                response.completedAt()
        );

        assertNull(
                response.output()
        );

        assertNull(
                response.metrics()
        );
    }

    @Test
    void shouldMapCompletedJobWithOutputAndMetrics() {

        Job job = new Job();

        UUID jobId =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        job.setId(jobId);
        job.setStatus(JobStatus.DONE);
        job.setSourceFilename("sample.pdf");
        job.setPageCount(3);

        job.setCreatedAt(
                Instant.parse(
                        "2026-10-03T10:00:00Z"
                )
        );

        job.setStartedAt(
                Instant.parse(
                        "2026-10-03T10:00:01Z"
                )
        );

        job.setCompletedAt(
                Instant.parse(
                        "2026-10-03T10:00:12Z"
                )
        );

        job.setPhase(
                JobPhase.COMPLETED
        );

        job.setProgressPercent(100);

        JobFile jobFile =
                new JobFile(
                        job,
                        "/tmp/output.docx",
                        245120L
                );

        job.setFile(
                jobFile
        );

        JobMetrics metrics =
                new JobMetrics(
                        job,
                        120,
                        118
                );

        metrics.setOrderedListsDetected(2);
        metrics.setUnorderedListsDetected(3);
        metrics.setOrderedListsReconstructed(2);
        metrics.setUnorderedListsReconstructed(3);

        job.setMetrics(
                metrics
        );

        JobStatusResponse response =
                mapper.toResponse(job);

        assertEquals(
                JobStatus.DONE,
                response.status()
        );

        assertEquals(
                JobPhase.COMPLETED,
                response.phase()
        );

        assertEquals(
                100,
                response.progressPercent()
        );

        assertEquals(
                11.0,
                response.durationSeconds()
        );

        assertEquals(
                0.0,
                response.estimatedRemainingSeconds()
        );

        assertEquals(
                11.0,
                response.estimatedTotalSeconds()
        );

        assertNotNull(
                response.output()
        );

        assertEquals(
                "sample.docx",
                response.output().filename()
        );

        assertEquals(
                245120L,
                response.output().sizeBytes()
        );

        assertEquals(
                "/api/v1/jobs/"
                        + jobId
                        + "/download",
                response.output().downloadUrl()
        );

        assertNotNull(
                response.metrics()
        );

        assertEquals(
                120,
                response.metrics().sourceWordCount()
        );

        assertEquals(
                118,
                response.metrics().outputWordCount()
        );

        assertEquals(
                2,
                response.metrics().orderedListsDetected()
        );

        assertEquals(
                3,
                response.metrics().unorderedListsDetected()
        );

        assertEquals(
                2,
                response.metrics().orderedListsReconstructed()
        );

        assertEquals(
                3,
                response.metrics().unorderedListsReconstructed()
        );
    }
}