package com.amalitech.backend.controller;

import com.amalitech.backend.dto.response.JobStatusResponse;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.service.JobService;
import com.amalitech.backend.service.impl.JobDownloadService;
import com.amalitech.backend.service.impl.JobStatusResponseMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(
        name = "Conversion Jobs",
        description = "Endpoints for checking conversion job status"
)
public class JobController {

    private final JobService jobService;
    private final JobStatusResponseMapper jobStatusResponseMapper;
    private final JobDownloadService jobDownloadService;

    public JobController(
            JobService jobService,
            JobStatusResponseMapper jobStatusResponseMapper,
            JobDownloadService jobDownloadService
    ) {
        this.jobService = jobService;
        this.jobStatusResponseMapper = jobStatusResponseMapper;
        this.jobDownloadService = jobDownloadService;
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get Job Status")
    public ResponseEntity<JobStatusResponse> getJobStatus(
            @PathVariable UUID jobId
    ) {
        Job job =
                jobService.getJobWithFile(jobId);

        return ResponseEntity.ok(
                jobStatusResponseMapper
                        .toResponse(job)
        );
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

        JobDownloadService.DownloadResult download =
                jobDownloadService
                        .prepareDownload(job);

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
                                .filename(
                                        download.filename()
                                )
                                .build()
                                .toString()
                )
                .contentLength(
                        download.size()
                )
                .body(
                        download.resource()
                );
    }
}