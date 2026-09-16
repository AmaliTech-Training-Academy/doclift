package com.amalitech.backend.service;

import com.amalitech.backend.model.Job;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {

    private final JobService jobService;
    private final PdfValidationService pdfValidationService;

    public UploadService(
            JobService jobService,
            PdfValidationService pdfValidationService
    ) {
        this.jobService = jobService;
        this.pdfValidationService = pdfValidationService;
    }

    /**
     * Coordinates the upload flow for a PDF.
     *
     * @param file uploaded PDF file
     * @return the created conversion job
     */
    public Job handleUpload(MultipartFile file) {
        int pageCount = pdfValidationService.validateAndGetPageCount(file);

        return jobService.createJob(
                file.getOriginalFilename(),
                pageCount
        );
    }
}