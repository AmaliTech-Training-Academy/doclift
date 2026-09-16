package com.amalitech.backend.service;

import com.amalitech.backend.exception.EncryptedPdfException;
import com.amalitech.backend.exception.FileTooLargeException;
import com.amalitech.backend.exception.InvalidPdfException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import java.io.IOException;

@Service
public class PdfValidationService {

    private final long maxSizeBytes;

    public PdfValidationService(
            @Value("${app.upload.max-size-bytes:10485760}") long maxSizeBytes
    ) {
        this.maxSizeBytes = maxSizeBytes;
    }

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

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {

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

        } catch (EncryptedPdfException e) {
            throw e;

        } catch (IOException e) {
            throw new InvalidPdfException(
                    "The uploaded file is not a valid PDF.",
                    e
            );
        }
    }
}