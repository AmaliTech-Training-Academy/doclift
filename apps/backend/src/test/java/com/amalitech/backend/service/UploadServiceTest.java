package com.amalitech.backend.service;

import com.amalitech.backend.exception.FileTooLargeException;
import com.amalitech.backend.exception.InvalidPdfException;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.service.impl.UploadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadServiceTest {

    private JobService jobService;
    private PdfValidationService pdfValidationService;
    private FileStorageService fileStorageService;
    private UploadServiceImpl uploadService;

    private final long maxSizeBytes = 10 * 1024 * 1024;

    private static final UUID JOB_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    @BeforeEach
    void setUp() {
        jobService = mock(JobService.class);
        pdfValidationService = mock(PdfValidationService.class);
        fileStorageService = mock(FileStorageService.class);

        uploadService = new UploadServiceImpl(
                jobService,
                pdfValidationService,
                fileStorageService,
                maxSizeBytes
        );
    }

    // =========================================================
    // EMPTY FILE VALIDATION
    // =========================================================

    @Test
    void shouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThrows(
                InvalidPdfException.class,
                () -> uploadService.handleUpload(file)
        );

        verifyNoInteractions(
                jobService,
                pdfValidationService,
                fileStorageService
        );
    }

    // =========================================================
    // FILE TYPE VALIDATION
    // =========================================================

    @Test
    void shouldRejectNonPdfFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "hello".getBytes()
        );

        assertThrows(
                InvalidPdfException.class,
                () -> uploadService.handleUpload(file)
        );

        verifyNoInteractions(
                jobService,
                pdfValidationService,
                fileStorageService
        );
    }

    // =========================================================
    // FILE SIZE VALIDATION
    // =========================================================

    @Test
    void shouldRejectOversizedFile() {
        byte[] largeFile = new byte[(10 * 1024 * 1024) + 1];

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                largeFile
        );

        assertThrows(
                FileTooLargeException.class,
                () -> uploadService.handleUpload(file)
        );

        verifyNoInteractions(
                jobService,
                pdfValidationService,
                fileStorageService
        );
    }

    // =========================================================
    // FILENAME VALIDATION
    // =========================================================

    @Test
    void shouldRejectUploadWithMissingFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                null,
                "application/pdf",
                "dummy".getBytes()
        );

        assertThrows(
                InvalidPdfException.class,
                () -> uploadService.handleUpload(file)
        );

        verifyNoInteractions(
                jobService,
                pdfValidationService,
                fileStorageService
        );
    }

    // =========================================================
    // SUCCESSFUL UPLOAD FLOW
    // =========================================================

    @Test
    void shouldValidateCreateJobAndMoveFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                "dummy".getBytes()
        );

        Path tempPath = Path.of("/tmp/upload-test.pdf");

        Job job = new Job();
        job.setId(JOB_ID);

        when(fileStorageService.storeTemporaryFile(file))
                .thenReturn(tempPath);

        when(pdfValidationService.validateAndGetPageCount(tempPath))
                .thenReturn(2);

        when(jobService.createJob("sample.pdf", 2))
                .thenReturn(job);

        Job result = uploadService.handleUpload(file);

        assertEquals(JOB_ID, result.getId());

        verify(fileStorageService)
                .storeTemporaryFile(file);

        verify(pdfValidationService)
                .validateAndGetPageCount(tempPath);

        verify(jobService)
                .createJob("sample.pdf", 2);

        verify(fileStorageService)
                .moveToJobDirectory(tempPath, JOB_ID);
    }

    @Test
    void shouldMarkJobFailedWhenFinalStorageFails() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                "dummy".getBytes()
        );

        Path tempPath = Path.of("/tmp/upload-test.pdf");

        Job job = new Job();
        job.setId(JOB_ID);

        when(fileStorageService.storeTemporaryFile(file))
                .thenReturn(tempPath);

        when(pdfValidationService.validateAndGetPageCount(tempPath))
                .thenReturn(1);

        when(jobService.createJob("sample.pdf", 1))
                .thenReturn(job);

        doThrow(new IllegalStateException("Storage failed"))
                .when(fileStorageService)
                .moveToJobDirectory(
                        tempPath,
                        JOB_ID
                );

        assertThrows(
                IllegalStateException.class,
                () -> uploadService.handleUpload(file)
        );

        verify(jobService)
                .markFailed(JOB_ID);

        verify(fileStorageService)
                .deleteIfExists(tempPath);
    }
}