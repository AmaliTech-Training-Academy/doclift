package com.amalitech.backend.dto.response;

public record ApiErrorResponse(
        String error,
        String message
) {
}