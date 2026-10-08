package com.amalitech.backend.controller;

import com.amalitech.backend.dto.response.JobMetricsResponse;
import com.amalitech.backend.dto.response.JobOutputResponse;
import com.amalitech.backend.dto.response.JobStatusResponse;
import com.amalitech.backend.exception.JobFailedException;
import com.amalitech.backend.exception.JobNotFoundException;
import com.amalitech.backend.exception.JobNotReadyException;
import com.amalitech.backend.model.*;
import com.amalitech.backend.service.JobService;
import com.amalitech.backend.service.impl.JobDownloadService;
import com.amalitech.backend.service.impl.JobStatusResponseMapper;
import org.springframework.core.io.FileSystemResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobController.class)
class JobControllerTest {

    private static final UUID JOB_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private JobStatusResponseMapper jobStatusResponseMapper;

    @MockitoBean
    private JobDownloadService jobDownloadService;

    @TempDir
    Path tempDir;



    @Test
    void shouldReturnProcessingJobStatus() throws Exception {

        Job job = new Job();
        job.setId(JOB_ID);
        job.setStatus(JobStatus.PROCESSING);
        job.setSourceFilename("sample.pdf");
        job.setPageCount(3);
        job.setStartedAt(Instant.parse("2026-10-02T08:00:01Z"));
        job.setPhase(JobPhase.RECOVERING_STRUCTURE);
        job.setProgressPercent(55);
        job.setCreatedAt(
                Instant.parse(
                        "2026-09-30T18:00:00Z"
                )
        );

        JobStatusResponse response =
                new JobStatusResponse(
                        JOB_ID,
                        JobStatus.PROCESSING,
                        "sample.pdf",
                        3,
                        Instant.parse("2026-09-30T18:00:00Z"),
                        Instant.parse("2026-10-02T08:00:01Z"),
                        null,
                        JobPhase.RECOVERING_STRUCTURE,
                        55,
                        null,
                        20.0,
                        44.44,
                        8.0,
                        null,
                        null
                );

        when(jobStatusResponseMapper.toResponse(job))
                .thenReturn(response);

        when(jobService.getJobWithFile(JOB_ID))
                .thenReturn(job);

        mockMvc.perform(
                        get("/api/v1/jobs/{jobId}", JOB_ID)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.jobId")
                        .value(JOB_ID.toString()))
                .andExpect(jsonPath("$.status")
                        .value("PROCESSING"))
                .andExpect(jsonPath("$.sourceFilename")
                        .value("sample.pdf"))
                .andExpect(jsonPath("$.phase").value("RECOVERING_STRUCTURE"))
                .andExpect(jsonPath("$.progressPercent").value(55))
                .andExpect(jsonPath("$.startedAt").value("2026-10-02T08:00:01Z"))
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.output").doesNotExist())
                .andExpect(jsonPath("$.pageCount")
                        .value(3))
                .andExpect(jsonPath("$.createdAt")
                        .value("2026-09-30T18:00:00Z"));

    }

    @Test
    void shouldReturnDoneJobStatus() throws Exception {

        Job job = new Job();
        job.setId(JOB_ID);
        job.setStatus(JobStatus.DONE);
        job.setSourceFilename("sample.pdf");
        job.setPageCount(3);
        job.setStartedAt(Instant.parse("2026-10-02T08:00:01Z"));
        job.setCompletedAt(Instant.parse("2026-10-02T08:00:12Z"));
        job.setPhase(JobPhase.COMPLETED);
        job.setProgressPercent(100);
        job.setCreatedAt(
                Instant.parse(
                        "2026-09-30T18:00:00Z"
                )
        );

        JobFile jobFile = new JobFile(
                job,
                "/tmp/output.docx",
                245120L
        );

        job.setFile(jobFile);

        JobMetrics jobMetrics =
                new JobMetrics(
                        job,
                        120,
                        118
                );

        job.setMetrics(jobMetrics);

        when(jobService.getJobWithFile(JOB_ID))
                .thenReturn(job);

        JobOutputResponse output =
                new JobOutputResponse(
                        "sample.docx",
                        245120L,
                        "/api/v1/jobs/" + JOB_ID + "/download"
                );

        JobMetricsResponse metrics =
                new JobMetricsResponse(
                        120,
                        118,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        3
                );

        JobStatusResponse response =
                new JobStatusResponse(
                        JOB_ID,
                        JobStatus.DONE,
                        "sample.pdf",
                        3,
                        Instant.parse("2026-09-30T18:00:00Z"),
                        Instant.parse("2026-10-02T08:00:01Z"),
                        Instant.parse("2026-10-02T08:00:12Z"),
                        JobPhase.COMPLETED,
                        100,
                        11.0,
                        0.0,
                        11.0,
                        0.0,
                        output,
                        metrics
                );

        when(jobStatusResponseMapper.toResponse(job))
                .thenReturn(response);
        mockMvc.perform(
                        get("/api/v1/jobs/{jobId}", JOB_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId")
                        .value(JOB_ID.toString()))
                .andExpect(jsonPath("$.phase").value("COMPLETED"))
                .andExpect(jsonPath("$.progressPercent").value(100))
                .andExpect(jsonPath("$.durationSeconds").value(11.0))
                .andExpect(jsonPath("$.output.filename")
                        .value("sample.docx"))
                .andExpect(jsonPath("$.output.sizeBytes")
                        .value(245120))
                .andExpect(jsonPath("$.output.downloadUrl")
                        .value("/api/v1/jobs/" + JOB_ID + "/download"))
                .andExpect(jsonPath("$.metrics.sourceWordCount")
                        .value(120))
                .andExpect(jsonPath("$.metrics.outputWordCount")
                        .value(118))
                .andExpect(jsonPath("$.status")
                        .value("DONE"));
    }

    @Test
    void shouldReturnNotFoundForUnknownJob() throws Exception {

        when(jobService.getJobWithFile(JOB_ID))
                .thenThrow(
                        new JobNotFoundException()
                );

        mockMvc.perform(
                        get("/api/v1/jobs/{jobId}", JOB_ID)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("Job not found."));
    }

    @Test
    void shouldDownloadCompletedJob() throws Exception {

        Path outputPath =
                tempDir.resolve("output.docx");

        byte[] content =
                "docx-content".getBytes();

        Files.write(
                outputPath,
                content
        );

        Job job = new Job();
        job.setId(JOB_ID);
        job.setStatus(JobStatus.DONE);
        job.setSourceFilename("sample.pdf");

        JobFile jobFile =
                new JobFile(
                        job,
                        outputPath.toString(),
                        (long) content.length
                );

        job.setFile(jobFile);

        JobDownloadService.DownloadResult download =
                new JobDownloadService.DownloadResult(
                        new FileSystemResource(
                                outputPath.toFile()
                        ),
                        "sample.docx",
                        content.length
                );

        when(jobDownloadService.prepareDownload(job))
                .thenReturn(download);

        when(jobService.getJobWithFile(JOB_ID))
                .thenReturn(job);

        mockMvc.perform(
                        get(
                                "/api/v1/jobs/{jobId}/download",
                                JOB_ID
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"sample.docx\""
                        )
                )
                .andExpect(
                        content().contentType(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                )
                .andExpect(
                        content().bytes(content)
                );
    }

    @Test
    void shouldBlockDownloadWhenJobIsProcessing() throws Exception {

        Job job = new Job();
        job.setId(JOB_ID);
        job.setStatus(JobStatus.PROCESSING);

        when(jobService.getJobWithFile(JOB_ID))
                .thenReturn(job);
        when(jobDownloadService.prepareDownload(job))
                .thenThrow(
                        new JobNotReadyException()
                );
        mockMvc.perform(
                        get(
                                "/api/v1/jobs/{jobId}/download",
                                JOB_ID
                        )
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.metrics").doesNotExist())
                .andExpect(
                        jsonPath("$.error")
                                .value("JOB_NOT_READY")
                );
    }

    @Test
    void shouldBlockDownloadWhenJobFailed() throws Exception {

        Job job = new Job();
        job.setId(JOB_ID);
        job.setStatus(JobStatus.FAILED);

        when(jobService.getJobWithFile(JOB_ID))
                .thenReturn(job);
        when(jobDownloadService.prepareDownload(job))
                .thenThrow(
                        new JobFailedException()
                );

        mockMvc.perform(
                        get(
                                "/api/v1/jobs/{jobId}/download",
                                JOB_ID
                        )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.error")
                                .value("JOB_FAILED")
                );
    }

    @Test
    void shouldReturnServerErrorWhenCompletedJobHasNoFile()
            throws Exception {

        Job job = new Job();
        job.setId(JOB_ID);
        job.setStatus(JobStatus.DONE);

        when(jobService.getJobWithFile(JOB_ID))
                .thenReturn(job);
        when(jobDownloadService.prepareDownload(job))
                .thenThrow(
                        new IllegalStateException(
                                "Completed job has no output file."
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/jobs/{jobId}/download",
                                JOB_ID
                        )
                )
                .andExpect(
                        status().isInternalServerError()
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("STORAGE_ERROR")
                );
    }

    @Test
    void shouldReturnServerErrorWhenOutputFileIsMissing()
            throws Exception {

        Path missingOutputPath =
                tempDir.resolve("missing-output.docx");

        Job job = new Job();
        job.setId(JOB_ID);
        job.setStatus(JobStatus.DONE);
        job.setSourceFilename("sample.pdf");

        JobFile jobFile =
                new JobFile(
                        job,
                        missingOutputPath.toString(),
                        123L
                );

        job.setFile(jobFile);

        when(jobService.getJobWithFile(JOB_ID))
                .thenReturn(job);
        when(jobDownloadService.prepareDownload(job))
                .thenThrow(
                        new IllegalStateException(
                                "Converted output file is missing."
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/jobs/{jobId}/download",
                                JOB_ID
                        )
                )
                .andExpect(
                        status().isInternalServerError()
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("STORAGE_ERROR")
                );
    }
}