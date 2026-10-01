package com.amalitech.backend.controller;

import com.amalitech.backend.dto.response.JobStatusResponse;
import com.amalitech.backend.exception.JobFailedException;
import com.amalitech.backend.exception.JobNotReadyException;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobFile;
import com.amalitech.backend.model.JobStatus;
import com.amalitech.backend.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(
        name = "Conversion Jobs",
        description = "Endpoints for checking conversion job status"
)
public class JobController {

    private final JobService jobService;

    public JobController(
            JobService jobService
    ) {
        this.jobService = jobService;
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get Job Status")
    public ResponseEntity<JobStatusResponse> getJobStatus(
            @PathVariable UUID jobId
    ) {

        Job job =
                jobService.getJob(jobId);

        JobStatusResponse response =
                new JobStatusResponse(
                        job.getId(),
                        job.getStatus(),
                        job.getSourceFilename(),
                        job.getPageCount(),
                        job.getCreatedAt()
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{jobId}/download")
    @Operation(
            summary = "Download converted Word document",
            description =
                    "Streams the generated DOCX file when the conversion job is complete."
    )
    public ResponseEntity<Resource> downloadResult(
            @PathVariable UUID jobId
    ) {

        Job job =
                jobService.getJobWithFile(jobId);

        if (job.getStatus() == JobStatus.FAILED) {
            throw new JobFailedException();
        }

        if (job.getStatus() != JobStatus.DONE) {
            throw new JobNotReadyException();
        }

        JobFile jobFile =
                job.getFile();

        if (jobFile == null) {
            throw new IllegalStateException(
                    "Completed job has no output file."
            );
        }

        Path outputPath =
                Path.of(
                        jobFile.getOutputPath()
                );

        if (!Files.exists(outputPath)) {
            throw new IllegalStateException(
                    "Converted output file is missing."
            );
        }
        Resource resource =
                new FileSystemResource(
                        outputPath.toFile()
                );

        String downloadFilename =
                buildOutputFilename(
                        job.getSourceFilename()
                );

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition
                                .attachment()
                                .filename(downloadFilename)
                                .build()
                                .toString()
                )
                .contentLength(
                        jobFile.getSize()
                )
                .body(resource);
    }

    private String buildOutputFilename(String sourceFilename) {

        if (sourceFilename == null
                || sourceFilename.isBlank()) {
            return "converted.docx";
        }

        int lastDot =
                sourceFilename.lastIndexOf('.');

        String baseName =
                lastDot > 0
                        ? sourceFilename.substring(0, lastDot)
                        : sourceFilename;

        return baseName + ".docx";
    }
}