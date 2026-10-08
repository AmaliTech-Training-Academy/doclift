package com.amalitech.backend.service;

public interface DocumentMetricsService {

    int countWords(String text);

    int countSourceWords(PdfExtractionResult extractionResult);

    int countOutputWords(byte[] docxBytes);

    ListCountResult countSourceLists(
            PdfExtractionResult extractionResult
    );

    ListCountResult countOutputLists(
            byte[] docxBytes
    );

    int countHeadings(PdfExtractionResult extractionResult);

    HeadingLevelCountResult countHeadingLevels(
            PdfExtractionResult extractionResult
    );

    int countTables(PdfExtractionResult extractionResult);

    int countImages(PdfExtractionResult extractionResult);

    int countMultiColumnPages(PdfExtractionResult extractionResult);
}