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
    private final WordWriterService wordWriterService;
    private final DocumentMetricsService documentMetricsService;
    private final JobMetricsService jobMetricsService;
    private final JobEtaCalculator jobEtaCalculator;

    public ConversionWorker(
            JobService jobService,
            FileStorageService fileStorageService,
            PdfExtractionService pdfExtractionService,
            WordWriterService wordWriterService,
            DocumentMetricsService documentMetricsService,
            JobMetricsService jobMetricsService,
            JobEtaCalculator jobEtaCalculator
    ) {
        this.jobService = jobService;
        this.fileStorageService = fileStorageService;
        this.pdfExtractionService = pdfExtractionService;
        this.wordWriterService = wordWriterService;
        this.documentMetricsService = documentMetricsService;
        this.jobMetricsService = jobMetricsService;
        this.jobEtaCalculator = jobEtaCalculator;
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
                    jobEtaCalculator.calculateProgressPercentForPhase(
                            JobPhase.EXTRACTING_CONTENT
                    )
            );

            PdfExtractionResult extractionResult;

            try (InputStream inputStream = Files.newInputStream(sourcePath)) {
                extractionResult = pdfExtractionService.extract(inputStream);
            }


            int sourceWordCount =
                    documentMetricsService.countSourceWords(
                            extractionResult
                    );

            ListCountResult sourceLists =
                    documentMetricsService.countSourceLists(
                            extractionResult
                    );

            int headingsDetected =
                    documentMetricsService.countHeadings(
                            extractionResult
                    );

            HeadingLevelCountResult headingLevels =
                    documentMetricsService.countHeadingLevels(
                            extractionResult
                    );

            int tablesDetected =
                    documentMetricsService.countTables(
                            extractionResult
                    );

            int imagesDetected =
                    documentMetricsService.countImages(
                            extractionResult
                    );

            int multiColumnPageCount =
                    documentMetricsService.countMultiColumnPages(
                            extractionResult
                    );

            jobService.updateProgress(
                    jobId,
                    JobPhase.GENERATING_DOCUMENT,
                    jobEtaCalculator.calculateProgressPercentForPhase(
                            JobPhase.GENERATING_DOCUMENT
                    )
            );

            byte[] docx =
                    wordWriterService.write(extractionResult);

            Integer outputWordCount = null;
            Integer orderedListsReconstructed = null;
            Integer unorderedListsReconstructed = null;

            try {

                int measuredOutputWordCount =
                        documentMetricsService.countOutputWords(
                                docx
                        );

                ListCountResult measuredOutputLists =
                        documentMetricsService.countOutputLists(
                                docx
                        );

                outputWordCount =
                        measuredOutputWordCount;

                orderedListsReconstructed =
                        measuredOutputLists.ordered();

                unorderedListsReconstructed =
                        measuredOutputLists.unordered();

            } catch (Exception e) {

                log.warn(
                        "Could not calculate output metrics for job {}. "
                                + "Output metrics will remain unavailable.",
                        jobId,
                        e
                );
            }

            jobService.updateProgress(
                    jobId,
                    JobPhase.SAVING_OUTPUT,
                    jobEtaCalculator.calculateProgressPercentForPhase(
                            JobPhase.SAVING_OUTPUT
                    )
            );
            Path outputPath =
                    fileStorageService.storeOutputDocx(
                            jobId,
                            docx
                    );

            long sizeBytes =
                    Files.size(outputPath);

            jobMetricsService.saveMetrics(
                    jobId,
                    new ConversionMetrics(
                            sourceWordCount,
                            outputWordCount,
                            sourceLists.ordered(),
                            sourceLists.unordered(),
                            orderedListsReconstructed,
                            unorderedListsReconstructed,
                            headingsDetected,
                            headingLevels.levelOne(),
                            headingLevels.levelTwo(),
                            headingLevels.levelThree(),
                            tablesDetected,
                            imagesDetected,
                            multiColumnPageCount
                    )
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