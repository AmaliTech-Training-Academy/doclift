package com.amalitech.backend.service;

import com.amalitech.backend.exception.JobFailedException;
import com.amalitech.backend.exception.JobFileExpiredException;
import com.amalitech.backend.exception.JobNotReadyException;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobFile;
import com.amalitech.backend.model.JobStatus;
import com.amalitech.backend.service.impl.JobDownloadService;
import com.amalitech.backend.service.impl.JobFilenameResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JobDownloadServiceTest {

    private JobDownloadService jobDownloadService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        JobFilenameResolver filenameResolver =
                new JobFilenameResolver();

        jobDownloadService =
                new JobDownloadService(
                        filenameResolver
                );
    }

    @Test
    void shouldPrepareDownloadForCompletedJob()
            throws Exception {

        Path outputPath =
                tempDir.resolve(
                        "output.docx"
                );

        byte[] content =
                "docx-content".getBytes();

        Files.write(
                outputPath,
                content
        );

        Job job =
                new Job();

        job.setStatus(
                JobStatus.DONE
        );

        job.setSourceFilename(
                "sample.pdf"
        );

        JobFile jobFile =
                new JobFile(
                        job,
                        outputPath.toString(),
                        (long) content.length
                );

        job.setFile(
                jobFile
        );

        JobDownloadService.DownloadResult result =
                jobDownloadService
                        .prepareDownload(job);

        assertNotNull(
                result
        );

        assertEquals(
                "sample.docx",
                result.filename()
        );

        assertEquals(
                content.length,
                result.size()
        );

        assertTrue(
                result.resource().exists()
        );
    }

    @Test
    void shouldRejectFailedJob() {

        Job job =
                new Job();

        job.setStatus(
                JobStatus.FAILED
        );

        assertThrows(
                JobFailedException.class,
                () ->
                        jobDownloadService
                                .prepareDownload(job)
        );
    }

    @Test
    void shouldRejectProcessingJob() {

        Job job =
                new Job();

        job.setStatus(
                JobStatus.PROCESSING
        );

        assertThrows(
                JobNotReadyException.class,
                () ->
                        jobDownloadService
                                .prepareDownload(job)
        );
    }

    @Test
    void shouldRejectCompletedJobWithoutFile() {

        Job job =
                new Job();

        job.setStatus(
                JobStatus.DONE
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                jobDownloadService
                                        .prepareDownload(job)
                );

        assertEquals(
                "Completed job has no output file.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectCompletedJobWhenOutputFileIsMissing() {

        Path missingPath =
                tempDir.resolve(
                        "missing-output.docx"
                );

        Job job =
                new Job();

        job.setStatus(
                JobStatus.DONE
        );

        job.setSourceFilename(
                "sample.pdf"
        );

        JobFile jobFile =
                new JobFile(
                        job,
                        missingPath.toString(),
                        123L
                );

        job.setFile(
                jobFile
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                jobDownloadService
                                        .prepareDownload(job)
                );

        assertEquals(
                "Converted output file is missing.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectJobWhoseFilesWereCleanedUp()
            throws Exception {

        Path outputPath =
                tempDir.resolve(
                        "output.docx"
                );

        Files.write(
                outputPath,
                "docx-content".getBytes()
        );

        Job job =
                new Job();

        job.setStatus(
                JobStatus.DONE
        );

        job.setSourceFilename(
                "sample.pdf"
        );

        JobFile jobFile =
                new JobFile(
                        job,
                        outputPath.toString(),
                        123L
                );

        job.setFile(
                jobFile
        );

        job.setFilesDeletedAt(
                Instant.now()
        );

        assertThrows(
                JobFileExpiredException.class,
                () ->
                        jobDownloadService
                                .prepareDownload(job)
        );
    }
}