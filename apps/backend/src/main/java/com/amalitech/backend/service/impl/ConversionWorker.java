package com.amalitech.backend.service.impl;

import com.amalitech.backend.model.JobPhase;
import com.amalitech.backend.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class ConversionWorker {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ConversionWorker.class
            );

    private final JobService jobService;
    private final FileStorageService fileStorageService;
    private final PdfExtractionService pdfExtractionService;
    private final StructureRecoveryService structureRecoveryService;
    private final WordWriterService wordWriterService;
    private final DocumentMetricsService documentMetricsService;
    private final JobMetricsService jobMetricsService;

    public ConversionWorker(
            JobService jobService,
            FileStorageService fileStorageService,
            PdfExtractionService pdfExtractionService,
            StructureRecoveryService structureRecoveryService,
            WordWriterService wordWriterService,
            DocumentMetricsService documentMetricsService,
            JobMetricsService jobMetricsService
    ) {
        this.jobService = jobService;
        this.fileStorageService = fileStorageService;
        this.pdfExtractionService = pdfExtractionService;
        this.structureRecoveryService =
                structureRecoveryService;
        this.wordWriterService = wordWriterService;
        this.documentMetricsService = documentMetricsService;
        this.jobMetricsService = jobMetricsService;
    }

    @Async("conversionExecutor")
    public void process(UUID jobId) {
        log.info("Starting conversion for job {}", jobId);

        try {
            jobService.markProcessing(jobId);

            Path sourcePath = fileStorageService.getSourcePdfPath(jobId);

            jobService.updateProgress(
                    jobId,
                    JobPhase.EXTRACTING_CONTENT,
                    25
            );

            PdfExtractionResult extractionResult;

            try (InputStream inputStream = Files.newInputStream(sourcePath)) {
                extractionResult = pdfExtractionService.extract(inputStream);
            }

            jobService.updateProgress(
                    jobId,
                    JobPhase.RECOVERING_STRUCTURE,
                    55
            );

            for (PageExtraction page : extractionResult.getPages()) {
                structureRecoveryService.recoverStructure(page);
            }

            int sourceWordCount =
                    documentMetricsService.countSourceWords(
                            extractionResult
                    );

            jobService.updateProgress(
                    jobId,
                    JobPhase.GENERATING_DOCUMENT,
                    75
            );

            byte[] docx = wordWriterService.write(extractionResult);

            int outputWordCount =
                    documentMetricsService.countOutputWords(docx);

            jobService.updateProgress(
                    jobId,
                    JobPhase.SAVING_OUTPUT,
                    90
            );

            Path outputPath =
                    fileStorageService.storeOutputDocx(jobId, docx);

            long sizeBytes = Files.size(outputPath);

            jobMetricsService.saveWordCounts(
                    jobId,
                    sourceWordCount,
                    outputWordCount
            );

            jobService.markCompleted(
                    jobId,
                    outputPath.toString(),
                    sizeBytes
            );

            log.info(
                    "Conversion completed successfully for job {}",
                    jobId
            );

        } catch (Exception e) {
            log.error(
                    "Conversion failed for job {}",
                    jobId,
                    e
            );

            try {
                jobService.markFailed(jobId);
            } catch (Exception statusUpdateException) {
                log.error(
                        "Failed to update status for job {}",
                        jobId,
                        statusUpdateException
                );
            }
        }
    }
}