package com.amalitech.backend.service.impl;

import com.amalitech.backend.exception.FileTooLargeException;
import com.amalitech.backend.exception.InvalidPdfException;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.service.FileStorageService;
import com.amalitech.backend.service.JobDispatcher;
import com.amalitech.backend.service.JobService;
import com.amalitech.backend.service.PdfValidationService;
import com.amalitech.backend.service.UploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Locale;

@Service
public class UploadServiceImpl implements UploadService {

    private final JobService jobService;
    private final PdfValidationService pdfValidationService;
    private final FileStorageService fileStorageService;
    private final JobDispatcher jobDispatcher;
    private final long maxSizeBytes;

    public UploadServiceImpl(
            JobService jobService,
            PdfValidationService pdfValidationService,
            FileStorageService fileStorageService,
            JobDispatcher jobDispatcher,
            @Value("${app.upload.max-size-bytes:15728640}")
            long maxSizeBytes
    ) {
        this.jobService = jobService;
        this.pdfValidationService = pdfValidationService;
        this.fileStorageService = fileStorageService;
        this.jobDispatcher = jobDispatcher;
        this.maxSizeBytes = maxSizeBytes;
    }

    @Override
    public Job handleUpload(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidPdfException(
                    "The uploaded file is empty."
            );
        }

        String originalFilename =
                file.getOriginalFilename();

        if (originalFilename == null
                || originalFilename.isBlank()) {
            throw new InvalidPdfException(
                    "The uploaded file must include a filename."
            );
        }

        if (!isPdfUpload(file)) {
            throw new InvalidPdfException(
                    "Only PDF files are supported."
            );
        }

        if (file.getSize() > maxSizeBytes) {
            throw new FileTooLargeException(
                    "The uploaded PDF exceeds the maximum allowed size."
            );
        }

        Path temporaryFile = null;
        Job job = null;

        try {
            temporaryFile =
                    fileStorageService
                            .storeTemporaryFile(file);

            int pageCount =
                    pdfValidationService
                            .validateAndGetPageCount(
                                    temporaryFile
                            );

            job =
                    jobService.createJob(
                            originalFilename,
                            pageCount
                    );

            fileStorageService
                    .moveToJobDirectory(
                            temporaryFile,
                            job.getId()
                    );

            temporaryFile = null;

            jobDispatcher.dispatch(
                    job.getId()
            );

            return job;

        } catch (RuntimeException e) {

            if (job != null) {
                jobService.markFailed(
                        job.getId()
                );
            }

            if (temporaryFile != null) {
                fileStorageService
                        .deleteIfExists(
                                temporaryFile
                        );
            }

            throw e;
        }
    }

    /**
     * Accepts a part whose content type is {@code application/pdf}, or whose
     * filename ends in {@code .pdf} when the client sends a generic type.
     * A non-PDF that only has a {@code .pdf} name is still rejected later
     * when the file is parsed.
     *
     * @param file uploaded part that already has a filename
     * @return true when the part should continue into PDF validation
     */
    private boolean isPdfUpload(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && "application/pdf".equalsIgnoreCase(contentType)) {
            return true;
        }
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }
}