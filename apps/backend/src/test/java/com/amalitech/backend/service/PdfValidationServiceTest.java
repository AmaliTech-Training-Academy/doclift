package com.amalitech.backend.service;

import com.amalitech.backend.exception.EncryptedPdfException;
import com.amalitech.backend.exception.InvalidPdfException;
import com.amalitech.backend.service.impl.PdfValidationServiceImpl;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PdfValidationServiceTest {

    private final PdfValidationServiceImpl pdfValidationService =
            new PdfValidationServiceImpl(10 * 1024 * 1024);

    // =========================================================
    // VALID PDF VALIDATION
    // =========================================================

    @Test
    void shouldAcceptValidPdfAndReturnPageCount() throws Exception {
        Path tempFile = Files.createTempFile("valid-", ".pdf");

        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(tempFile.toFile());
        }

        int pageCount =
                pdfValidationService.validateAndGetPageCount(tempFile);

        assertEquals(1, pageCount);

        Files.deleteIfExists(tempFile);
    }

    // =========================================================
    // ENCRYPTED / PASSWORD-PROTECTED PDF VALIDATION
    // =========================================================

    @Test
    void shouldRejectPasswordProtectedPdf() throws Exception {
        Path tempFile = Files.createTempFile("protected-", ".pdf");

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
            document.save(tempFile.toFile());
        }

        assertThrows(
                EncryptedPdfException.class,
                () -> pdfValidationService.validateAndGetPageCount(tempFile)
        );

        Files.deleteIfExists(tempFile);
    }

    // =========================================================
    // INVALID / CORRUPT PDF CONTENT VALIDATION
    // =========================================================

    @Test
    void shouldRejectInvalidPdfContent() throws Exception {
        Path tempFile = Files.createTempFile("invalid-", ".pdf");

        Files.writeString(
                tempFile,
                "this is not actually a pdf"
        );

        assertThrows(
                InvalidPdfException.class,
                () -> pdfValidationService.validateAndGetPageCount(tempFile)
        );

        Files.deleteIfExists(tempFile);
    }
}
