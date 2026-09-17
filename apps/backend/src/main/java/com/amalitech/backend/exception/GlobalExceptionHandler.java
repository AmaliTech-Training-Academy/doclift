package com.amalitech.backend.exception;

import com.amalitech.backend.dto.response.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidPdfException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidPdf(
            InvalidPdfException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "INVALID_PDF",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(EncryptedPdfException.class)
    public ResponseEntity<ApiErrorResponse> handleEncryptedPdf(
            EncryptedPdfException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "ENCRYPTED_PDF",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiErrorResponse(
                        "FILE_TOO_LARGE",
                        "The uploaded PDF exceeds the maximum allowed size."
                ));
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<ApiErrorResponse> handleFileTooLarge(
            FileTooLargeException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiErrorResponse(
                        "FILE_TOO_LARGE",
                        ex.getMessage()
                ));
    }
}