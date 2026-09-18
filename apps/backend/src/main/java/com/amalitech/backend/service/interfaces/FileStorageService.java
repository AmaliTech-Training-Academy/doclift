package com.amalitech.backend.service.interfaces;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageService {

    Path storeSourcePdf(MultipartFile file, Long jobId);
}
