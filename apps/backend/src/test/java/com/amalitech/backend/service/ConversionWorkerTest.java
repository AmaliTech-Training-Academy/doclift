package com.amalitech.backend.service;

import com.amalitech.backend.model.JobPhase;
import com.amalitech.backend.service.impl.ConversionWorker;
import com.amalitech.backend.service.impl.JobEtaCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class ConversionWorkerTest {

    private JobService jobService;
    private FileStorageService fileStorageService;
    private PdfExtractionService pdfExtractionService;
    private StructureRecoveryService structureRecoveryService;
    private WordWriterService wordWriterService;
    private ConversionWorker conversionWorker;
    private DocumentMetricsService documentMetricsService;
    private JobMetricsService jobMetricsService;
    private JobEtaCalculator jobEtaCalculator;

    @TempDir
    Path tempDir;

    private static final UUID JOB_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    @BeforeEach
    void setUp() {

        jobService = mock(JobService.class);

        fileStorageService =
                mock(FileStorageService.class);

        pdfExtractionService =
                mock(PdfExtractionService.class);

        structureRecoveryService =
                mock(StructureRecoveryService.class);

        wordWriterService =
                mock(WordWriterService.class);

        documentMetricsService =
                mock(DocumentMetricsService.class);

        jobMetricsService =
                mock(JobMetricsService.class);

        jobEtaCalculator =
                mock(JobEtaCalculator.class);

        when(jobEtaCalculator.calculateProgressPercentForPhase(
                JobPhase.EXTRACTING_CONTENT
        )).thenReturn(1);

        when(jobEtaCalculator.calculateProgressPercentForPhase(
                JobPhase.RECOVERING_STRUCTURE
        )).thenReturn(64);

        when(jobEtaCalculator.calculateProgressPercentForPhase(
                JobPhase.GENERATING_DOCUMENT
        )).thenReturn(67);

        when(jobEtaCalculator.calculateProgressPercentForPhase(
                JobPhase.SAVING_OUTPUT
        )).thenReturn(99);

        conversionWorker =
                new ConversionWorker(
                        jobService,
                        fileStorageService,
                        pdfExtractionService,
                        structureRecoveryService,
                        wordWriterService,
                        documentMetricsService,
                        jobMetricsService,
                        jobEtaCalculator
                );
    }

    @Test
    void shouldCompleteConversionSuccessfully()
            throws Exception {

        Path sourcePath =
                tempDir.resolve("source.pdf");

        Files.write(
                sourcePath,
                "dummy-pdf".getBytes()
        );

        Path outputPath =
                tempDir.resolve("output.docx");

        byte[] docxContent =
                "docx-content".getBytes();

        Files.write(
                outputPath,
                docxContent
        );

        PdfExtractionResult extractionResult =
                mock(PdfExtractionResult.class);

        PageExtraction page =
                mock(PageExtraction.class);

        when(
                fileStorageService
                        .getSourcePdfPath(JOB_ID)
        )
                .thenReturn(sourcePath);

        when(
                pdfExtractionService.extract(
                        any(InputStream.class)
                )
        )
                .thenReturn(extractionResult);

        when(extractionResult.getPages())
                .thenReturn(
                        List.of(page)
                );
        when(documentMetricsService.countSourceWords(extractionResult))
                .thenReturn(120);

        when(documentMetricsService.countOutputWords(docxContent))
                .thenReturn(118);

        when(
                wordWriterService.write(
                        extractionResult
                )
        )
                .thenReturn(docxContent);
        when(
                documentMetricsService.countSourceLists(
                        extractionResult
                )
        ).thenReturn(
                new ListCountResult(
                        1,
                        1
                )
        );

        when(
                documentMetricsService.countOutputLists(
                        docxContent
                )
        ).thenReturn(
                new ListCountResult(
                        1,
                        1
                )
        );

        when(
                fileStorageService
                        .storeOutputDocx(
                                eq(JOB_ID),
                                any(byte[].class)
                        )
        )
                .thenReturn(outputPath);

        conversionWorker.process(JOB_ID);

        verify(jobService)
                .markProcessing(JOB_ID);

        verify(jobService).updateProgress(
                JOB_ID,
                JobPhase.EXTRACTING_CONTENT,
                1
        );

        verify(pdfExtractionService)
                .extract(any(InputStream.class));

        verify(jobService).updateProgress(
                JOB_ID,
                JobPhase.RECOVERING_STRUCTURE,
                64
        );

        verify(structureRecoveryService)
                .recoverStructure(page);

        verify(documentMetricsService)
                .countSourceWords(extractionResult);

        verify(documentMetricsService)
                .countSourceLists(extractionResult);

        verify(jobService).updateProgress(
                JOB_ID,
                JobPhase.GENERATING_DOCUMENT,
                67
        );

        verify(wordWriterService)
                .write(extractionResult);

        verify(documentMetricsService)
                .countOutputWords(docxContent);

        verify(documentMetricsService)
                .countOutputLists(docxContent);

        verify(jobService).updateProgress(
                JOB_ID,
                JobPhase.SAVING_OUTPUT,
                99
        );

        verify(fileStorageService)
                .storeOutputDocx(
                        eq(JOB_ID),
                        same(docxContent)
                );

        verify(jobMetricsService)
                .saveMetrics(
                        JOB_ID,
                        120,
                        118,
                        1,
                        1,
                        1,
                        1
                );

        verify(jobService)
                .markCompleted(
                        JOB_ID,
                        outputPath.toString(),
                        docxContent.length
                );

        verify(jobService, never())
                .markFailed(JOB_ID);
    }

    @Test
    void shouldMarkJobFailedWhenConversionFails()
            throws Exception {

        Path sourcePath =
                tempDir.resolve("source.pdf");

        Files.write(
                sourcePath,
                "dummy-pdf".getBytes()
        );

        when(
                fileStorageService
                        .getSourcePdfPath(JOB_ID)
        )
                .thenReturn(sourcePath);

        when(
                pdfExtractionService.extract(
                        any(InputStream.class)
                )
        )
                .thenThrow(
                        new RuntimeException(
                                "Extraction failed"
                        )
                );

        conversionWorker.process(JOB_ID);

        verify(jobService)
                .markProcessing(JOB_ID);

        verify(jobService).updateProgress(
                JOB_ID,
                JobPhase.EXTRACTING_CONTENT,
                1
        );

        verify(jobService)
                .markFailed(JOB_ID);

        verifyNoInteractions(
                structureRecoveryService,
                wordWriterService,
                documentMetricsService,
                jobMetricsService
        );

        verify(
                fileStorageService,
                never()
        )
                .storeOutputDocx(
                        any(),
                        any()
                );

        verify(
                jobService,
                never()
        )
                .markCompleted(
                        any(),
                        anyString(),
                        anyLong()
                );
    }
}