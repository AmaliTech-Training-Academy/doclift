package com.amalitech.backend.service;

public interface DocumentMetricsService {

    int countWords(String text);

    int countSourceWords(PdfExtractionResult extractionResult);

    int countOutputWords(byte[] docxBytes);
}