package com.amalitech.backend.controller;

import com.amalitech.backend.dto.response.UploadResponse;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.service.UploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

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