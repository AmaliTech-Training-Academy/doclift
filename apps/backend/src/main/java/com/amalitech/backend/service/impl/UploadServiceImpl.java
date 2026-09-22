package com.amalitech.backend.service.impl;

import com.amalitech.backend.exception.InvalidPdfException;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.service.FileStorageService;
import com.amalitech.backend.service.JobService;
import com.amalitech.backend.service.PdfValidationService;
import com.amalitech.backend.service.UploadService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.amalitech.backend.exception.FileTooLargeException;
import com.amalitech.backend.exception.InvalidPdfException;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Path;

@Service
public class UploadServiceImpl implements UploadService {

    private final JobService jobService;
    private final PdfValidationService pdfValidationService;
    private final FileStorageService fileStorageService;
    private final long maxSizeBytes;

    public UploadServiceImpl(
            JobService jobService,
            PdfValidationService pdfValidationService,
            FileStorageService fileStorageService,
            @Value("${app.upload.max-size-bytes:10485760}")
            long maxSizeBytes
    ) {
        this.jobService = jobService;
        this.pdfValidationService = pdfValidationService;
        this.fileStorageService = fileStorageService;
        this.maxSizeBytes = maxSizeBytes;
    }

    @Override
    public Job handleUpload(MultipartFile file) {

        // Basic request-level validation
        if (file == null || file.isEmpty()) {
            throw new InvalidPdfException("The uploaded file is empty.");
        }

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidPdfException(
                    "The uploaded file must include a filename."
            );
        }

        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new InvalidPdfException(
                    "Only PDF files are supported."
            );
        }

        // Defense-in-depth: Spring normally rejects oversized multipart
        // requests before reaching this service.
        if (file.getSize() > maxSizeBytes) {
            throw new FileTooLargeException(
                    "The uploaded PDF exceeds the maximum allowed size."
            );
        }

        Path temporaryFile = null;

        try {
            temporaryFile =
                    fileStorageService.storeTemporaryFile(file);

            int pageCount =
                    pdfValidationService.validateAndGetPageCount(
                            temporaryFile
                    );

            Job job = jobService.createJob(
                    originalFilename,
                    pageCount
            );

            fileStorageService.moveToJobDirectory(
                    temporaryFile,
                    job.getId()
            );

            temporaryFile = null;

            return job;

        } catch (RuntimeException e) {
            if (temporaryFile != null) {
                fileStorageService.deleteIfExists(temporaryFile);
            }

            throw e;
        }
    }
}