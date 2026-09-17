package com.amalitech.backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface PdfValidationService {

    int validateAndGetPageCount(MultipartFile file);
}