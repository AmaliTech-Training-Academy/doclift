package com.amalitech.backend.dto.response;

public record JobOutputResponse(
        String filename,
        Long sizeBytes,
        String downloadUrl
) {}