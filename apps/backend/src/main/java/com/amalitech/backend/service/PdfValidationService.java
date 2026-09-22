package com.amalitech.backend.service;


import java.nio.file.Path;

public interface PdfValidationService {

    int validateAndGetPageCount(Path filePath);
}