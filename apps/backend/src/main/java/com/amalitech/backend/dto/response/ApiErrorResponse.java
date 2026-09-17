package com.amalitech.backend.dto;

public record ApiErrorResponse(
        String error,
        String message
) {
}