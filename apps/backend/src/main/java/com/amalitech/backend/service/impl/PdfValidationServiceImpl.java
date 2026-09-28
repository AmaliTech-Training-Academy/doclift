package com.amalitech.backend.service.impl;

import com.amalitech.backend.exception.EncryptedPdfException;
import com.amalitech.backend.exception.FileTooLargeException;
import com.amalitech.backend.exception.InvalidPdfException;
import com.amalitech.backend.service.PdfValidationService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PdfValidationServiceImpl implements PdfValidationService {

    private final long maxSizeBytes;

    @Autowired
    public PdfValidationServiceImpl(
            @Value("${app.upload.max-size-bytes:10485760}") long maxSizeBytes
    ) {
        this.maxSizeBytes = maxSizeBytes;
    }

    @Override
    public int validateAndGetPageCount(Path filePath) {
        try {
            long actualSize = Files.size(filePath);
            if (actualSize > maxSizeBytes) {
                throw new FileTooLargeException(
                        "The uploaded PDF exceeds the maximum allowed size."
                );
            }
        } catch (IOException e) {
            throw new InvalidPdfException("Unable to read the uploaded file.", e);
        }

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
}
