package com.amalitech.backend.service;

public record ConversionMetrics(
        int sourceWordCount,
        int outputWordCount,
        int orderedListsDetected,
        int unorderedListsDetected,
        int orderedListsReconstructed,
        int unorderedListsReconstructed,
        int headingsDetected,
        int h1HeadingCount,
        int h2HeadingCount,
        int h3HeadingCount,
        int tablesDetected,
        int imagesDetected,
        int multiColumnPageCount
) {}
