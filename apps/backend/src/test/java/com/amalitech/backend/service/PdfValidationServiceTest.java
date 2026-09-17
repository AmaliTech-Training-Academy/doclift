package com.amalitech.backend.service;

import com.amalitech.backend.exception.EncryptedPdfException;
import com.amalitech.backend.exception.FileTooLargeException;
import com.amalitech.backend.exception.InvalidPdfException;
import com.amalitech.backend.service.impl.PdfValidationServiceImpl;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PdfValidationServiceTest {

    private final PdfValidationService pdfValidationService =
            new PdfValidationServiceImpl(10 * 1024 * 1024);

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

        InvalidPdfException exception = assertThrows(
                InvalidPdfException.class,
                () -> pdfValidationService.validateAndGetPageCount(file)
        );

        assertEquals(
                "The uploaded file is empty.",
                exception.getMessage()
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

        InvalidPdfException exception = assertThrows(
                InvalidPdfException.class,
                () -> pdfValidationService.validateAndGetPageCount(file)
        );

        assertEquals(
                "Only PDF files are supported.",
                exception.getMessage()
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
                () -> pdfValidationService.validateAndGetPageCount(file)
        );
    }

    // =========================================================
    // ENCRYPTED / PASSWORD-PROTECTED PDF VALIDATION
    // =========================================================

    @Test
    void shouldRejectPasswordProtectedPdf() throws Exception {
        byte[] pdfBytes;

        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());

            AccessPermission accessPermission = new AccessPermission();

            StandardProtectionPolicy protectionPolicy =
                    new StandardProtectionPolicy(
                            "owner-password",
                            "user-password",
                            accessPermission
                    );

            protectionPolicy.setEncryptionKeyLength(128);

            document.protect(protectionPolicy);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            pdfBytes = outputStream.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "protected.pdf",
                "application/pdf",
                pdfBytes
        );

        assertThrows(
                EncryptedPdfException.class,
                () -> pdfValidationService.validateAndGetPageCount(file)
        );
    }

    // =========================================================
    // VALID PDF VALIDATION
    // =========================================================

    @Test
    void shouldAcceptValidPdfAndReturnPageCount() throws Exception {
        byte[] pdfBytes;

        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            pdfBytes = outputStream.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "valid.pdf",
                "application/pdf",
                pdfBytes
        );

        int pageCount =
                pdfValidationService.validateAndGetPageCount(file);

        assertEquals(1, pageCount);
    }

    // =========================================================
    // INVALID / CORRUPT PDF CONTENT VALIDATION
    // =========================================================

    @Test
    void shouldRejectInvalidPdfContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.pdf",
                "application/pdf",
                "this is not actually a pdf".getBytes()
        );

        assertThrows(
                InvalidPdfException.class,
                () -> pdfValidationService.validateAndGetPageCount(file)
        );
    }
}