package com.amalitech.backend.controller;

import com.amalitech.backend.dto.response.UploadResponse;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.service.interfaces.UploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/upload")
@Tag(
        name = "PDF Upload",
        description = "Endpoints for uploading PDF documents"
)
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Operation(
            summary = "Upload a PDF",
            description = "Validates and stores a PDF and creates a conversion job."
    )
    @ApiResponse(
            responseCode = "201",
            description = "PDF uploaded successfully"
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid, empty, encrypted, or missing PDF"
    )
    @ApiResponse(
            responseCode = "413",
            description = "PDF exceeds the maximum upload size"
    )
    @ApiResponse(
            responseCode = "500",
            description = "Internal storage or server error"
    )
    @PostMapping
    public ResponseEntity<UploadResponse> uploadPdf(
            @RequestParam("file") MultipartFile file
    ) {
        Job job = uploadService.handleUpload(file);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new UploadResponse(job.getId()));
    }
}