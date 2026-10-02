package com.amalitech.backend.controller;

import com.amalitech.backend.exception.JobNotFoundException;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobPhase;
import com.amalitech.backend.model.JobStatus;
import com.amalitech.backend.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.amalitech.backend.model.JobFile;
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

        when(jobService.getJob(JOB_ID))
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

        when(jobService.getJob(JOB_ID))
                .thenReturn(job);

        mockMvc.perform(
                        get("/api/v1/jobs/{jobId}", JOB_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId")
                        .value(JOB_ID.toString()))
                .andExpect(jsonPath("$.phase").value("COMPLETED"))
                .andExpect(jsonPath("$.progressPercent").value(100))
                .andExpect(jsonPath("$.durationSeconds").value(11))
                .andExpect(jsonPath("$.status")
                        .value("DONE"));
    }

    @Test
    void shouldReturnNotFoundForUnknownJob() throws Exception {

        when(jobService.getJob(JOB_ID))
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

        mockMvc.perform(
                        get(
                                "/api/v1/jobs/{jobId}/download",
                                JOB_ID
                        )
                )
                .andExpect(status().isConflict())
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