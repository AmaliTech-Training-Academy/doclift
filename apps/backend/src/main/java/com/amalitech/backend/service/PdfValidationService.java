package com.amalitech.backend.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface PdfValidationService {

    int validateAndGetPageCount(Path filePath);

    int validateAndGetPageCount(MultipartFile file);
}
