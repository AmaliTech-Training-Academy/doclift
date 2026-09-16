package com.amalitech.backend.service;

import com.amalitech.backend.model.Job;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {

    private final JobService jobService;
    private final PdfValidationService pdfValidationService;
    private final FileStorageService fileStorageService;

    public UploadService(
            JobService jobService,
            PdfValidationService pdfValidationService,
            FileStorageService fileStorageService
    ) {
        this.jobService = jobService;
        this.pdfValidationService = pdfValidationService;
        this.fileStorageService = fileStorageService;
    }

    public Job handleUpload(MultipartFile file) {
        int pageCount = pdfValidationService.validateAndGetPageCount(file);

        Job job = jobService.createJob(
                file.getOriginalFilename(),
                pageCount
        );

        fileStorageService.storeSourcePdf(file, job.getId());

        return job;
    }
}