package com.amalitech.backend.dto.response;

public record JobMetricsResponse(
        Integer sourceWordCount,
        Integer outputWordCount,
        Integer orderedListsDetected,
        Integer unorderedListsDetected,
        Integer orderedListsReconstructed,
        Integer unorderedListsReconstructed,
        Integer headingsDetected,
        Integer h1HeadingCount,
        Integer h2HeadingCount,
        Integer h3HeadingCount,
        Integer tablesDetected,
        Integer imagesDetected,
        Integer multiColumnPageCount,
        Integer outputPageCount
) {}
