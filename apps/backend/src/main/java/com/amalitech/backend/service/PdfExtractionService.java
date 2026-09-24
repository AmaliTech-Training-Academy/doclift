package com.amalitech.backend.service;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.io.InputStream;

public interface PdfExtractionService {

    PdfExtractionResult extract(byte[] pdfBytes) throws IOException;

    PdfExtractionResult extract(InputStream inputStream) throws IOException;

    PdfExtractionResult extract(PDDocument document) throws IOException;
}
