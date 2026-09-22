package com.amalitech.backend.service;

import com.amalitech.backend.exception.InvalidPdfException;
import com.amalitech.backend.service.impl.UploadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class UploadServiceTest {

    private JobService jobService;
    private PdfValidationService pdfValidationService;
    private FileStorageService fileStorageService;
    private UploadServiceImpl uploadService;

    @BeforeEach
    void setUp() {
        jobService = mock(JobService.class);
        pdfValidationService = mock(PdfValidationService.class);
        fileStorageService = mock(FileStorageService.class);

        uploadService = new UploadServiceImpl(
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
                "dummy-content".getBytes()
        );

        when(pdfValidationService.validateAndGetPageCount(file))
                .thenReturn(1);

        assertThrows(
                InvalidPdfException.class,
                () -> uploadService.handleUpload(file)
        );

        verify(jobService, never())
                .createJob(any(), any());

        verify(fileStorageService, never())
                .storeSourcePdf(any(), any());
    }

    @Test
    void shouldRejectUploadWithBlankFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "   ",
                "application/pdf",
                "dummy-content".getBytes()
        );

        when(pdfValidationService.validateAndGetPageCount(file))
                .thenReturn(1);

        assertThrows(
                InvalidPdfException.class,
                () -> uploadService.handleUpload(file)
        );

        verify(jobService, never())
                .createJob(any(), any());

        verify(fileStorageService, never())
                .storeSourcePdf(any(), any());
    }
}