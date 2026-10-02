package com.amalitech.backend.service;

import com.amalitech.backend.model.JobPhase;
import com.amalitech.backend.service.impl.ConversionWorker;
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

        conversionWorker =
                new ConversionWorker(
                        jobService,
                        fileStorageService,
                        pdfExtractionService,
                        structureRecoveryService,
                        wordWriterService
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

        when(
                wordWriterService.write(
                        extractionResult
                )
        )
                .thenReturn(docxContent);

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

        verify(pdfExtractionService)
                .extract(
                        any(InputStream.class)
                );

        verify(structureRecoveryService)
                .recoverStructure(page);

        verify(wordWriterService)
                .write(extractionResult);

        verify(fileStorageService)
                .storeOutputDocx(
                        eq(JOB_ID),
                        same(docxContent)
                );

        verify(jobService)
                .markCompleted(
                        JOB_ID,
                        outputPath.toString(),
                        docxContent.length
                );

        verify(jobService, never())
                .markFailed(JOB_ID);

        verify(jobService).markProcessing(JOB_ID);

        verify(jobService).updateProgress(
                JOB_ID,
                JobPhase.EXTRACTING_CONTENT,
                25
        );

        verify(jobService).updateProgress(
                JOB_ID,
                JobPhase.RECOVERING_STRUCTURE,
                55
        );

        verify(jobService).updateProgress(
                JOB_ID,
                JobPhase.GENERATING_DOCUMENT,
                75
        );

        verify(jobService).updateProgress(
                JOB_ID,
                JobPhase.SAVING_OUTPUT,
                90
        );

        verify(jobService).markCompleted(
                JOB_ID,
                outputPath.toString(),
                docxContent.length
        );
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
                25
        );

        verify(jobService)
                .markFailed(JOB_ID);

        verifyNoInteractions(
                structureRecoveryService,
                wordWriterService
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