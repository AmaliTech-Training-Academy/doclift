package com.amalitech.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class PdfValidationService {

    /**
     * Validates an uploaded PDF and returns its page count.
     *
     * @param file uploaded file
     * @return number of pages in the PDF
     */
    public int validateAndGetPageCount(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty.");
        }

        String contentType = file.getContentType();

        if (!"application/pdf".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("Only PDF files are supported.");
        }

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {

            if (document.isEncrypted()) {
                throw new IllegalArgumentException(
                        "Encrypted PDFs are not supported."
                );
            }

            int pageCount = document.getNumberOfPages();

            if (pageCount == 0) {
                throw new IllegalArgumentException(
                        "The uploaded PDF contains no pages."
                );
            }

            return pageCount;

        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "The uploaded file is not a valid PDF.",
                    e
            );
        }
    }
}