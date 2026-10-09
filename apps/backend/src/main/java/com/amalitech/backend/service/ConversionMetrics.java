package com.amalitech.backend.service;

public record ConversionMetrics(
        int sourceWordCount,
        Integer outputWordCount,
        int orderedListsDetected,
        int unorderedListsDetected,
        Integer orderedListsReconstructed,
        Integer unorderedListsReconstructed,
        int headingsDetected,
        int h1HeadingCount,
        int h2HeadingCount,
        int h3HeadingCount,
        int tablesDetected,
        int imagesDetected,
        int multiColumnPageCount
) {}