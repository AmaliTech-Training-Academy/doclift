package com.amalitech.backend.dto.response;

public record JobMetricsResponse(
        Integer sourceWordCount,
        Integer outputWordCount
) {}