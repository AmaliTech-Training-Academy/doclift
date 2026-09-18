package com.amalitech.backend.service.interfaces;

import org.springframework.web.multipart.MultipartFile;

public interface PdfValidationService {

    int validateAndGetPageCount(MultipartFile file);
}
