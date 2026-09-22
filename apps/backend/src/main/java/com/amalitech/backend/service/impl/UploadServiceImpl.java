package com.amalitech.backend.service.impl;

import com.amalitech.backend.exception.InvalidPdfException;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.service.FileStorageService;
import com.amalitech.backend.service.JobService;
import com.amalitech.backend.service.PdfValidationService;
import com.amalitech.backend.service.UploadService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadServiceImpl implements UploadService {

    private final JobService jobService;
    private final PdfValidationService pdfValidationService;
    private final FileStorageService fileStorageService;

    public UploadServiceImpl(
            JobService jobService,
            PdfValidationService pdfValidationService,
            FileStorageService fileStorageService
    ) {
        this.jobService = jobService;
        this.pdfValidationService = pdfValidationService;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public Job handleUpload(MultipartFile file) {
        int pageCount =
                pdfValidationService.validateAndGetPageCount(file);

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidPdfException(
                    "The uploaded file must include a filename."
            );
        }

        Job job = jobService.createJob(
                originalFilename,
                pageCount
        );

        try {
            fileStorageService.storeSourcePdf(file, job.getId());
            return job;

        } catch (RuntimeException e) {
            jobService.markFailed(job.getId());
            throw e;
        }
    }
}