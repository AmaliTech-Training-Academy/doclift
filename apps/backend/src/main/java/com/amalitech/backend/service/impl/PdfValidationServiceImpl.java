package com.amalitech.backend.service.impl;

import com.amalitech.backend.exception.EncryptedPdfException;
import com.amalitech.backend.exception.FileTooLargeException;
import com.amalitech.backend.exception.InvalidPdfException;
import com.amalitech.backend.service.PdfValidationService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import java.io.IOException;
import java.nio.file.Path;

@Service
public class PdfValidationServiceImpl implements PdfValidationService {

    private final long maxSizeBytes;

    public PdfValidationServiceImpl(
            @Value("${app.upload.max-size-bytes:10485760}") long maxSizeBytes
    ) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public PdfValidationServiceImpl() {
        this(10 * 1024 * 1024);
    }

    @Override
    public int validateAndGetPageCount(Path filePath) {
        try (PDDocument document =
                     Loader.loadPDF(filePath.toFile())) {

            if (document.isEncrypted()) {
                throw new EncryptedPdfException(
                        "Encrypted PDFs are not supported."
                );
            }

            int pageCount = document.getNumberOfPages();

            if (pageCount == 0) {
                throw new InvalidPdfException(
                        "The uploaded PDF contains no pages."
                );
            }

            return pageCount;

        } catch (InvalidPasswordException e) {
            throw new EncryptedPdfException(
                    "Encrypted or password-protected PDFs are not supported."
            );

        } catch (InvalidPdfException | EncryptedPdfException e) {
            throw e;

        } catch (IOException | RuntimeException e) {
            throw new InvalidPdfException(
                    "The uploaded file is not a valid PDF.",
                    e
            );
        }
    }

    @Override
    public int validateAndGetPageCount(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidPdfException("The uploaded file is empty.");
        }

        if (file.getSize() > maxSizeBytes) {
            throw new FileTooLargeException(
                    "The uploaded PDF exceeds the maximum allowed size."
            );
        }

        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new InvalidPdfException("Only PDF files are supported.");
        }

        try {
            return validateBytes(file.getBytes());
        } catch (IOException e) {
            throw new InvalidPdfException(
                    "The uploaded file is not a valid PDF.",
                    e
            );
        }
    }

    private int validateBytes(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.isEncrypted()) {
                throw new EncryptedPdfException(
                        "Encrypted PDFs are not supported."
                );
            }

            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new InvalidPdfException(
                        "The uploaded PDF contains no pages."
                );
            }

            return pageCount;
        } catch (InvalidPasswordException e) {
            throw new EncryptedPdfException(
                    "Encrypted or password-protected PDFs are not supported."
            );
        }
    }
}