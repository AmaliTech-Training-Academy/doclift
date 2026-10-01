package com.amalitech.backend.controller;

import com.amalitech.backend.dto.response.UploadResponse;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.service.UploadService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "PDF Upload",
        description = "Endpoints for uploading PDF documents"
)
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }


    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "upload PDF file")
    public ResponseEntity<UploadResponse> uploadPdf(
            @RequestPart("file") MultipartFile file
    ) {
        Job job = uploadService.handleUpload(file);

        return ResponseEntity
                .accepted()
                .body(new UploadResponse(job.getId()));
    }
}