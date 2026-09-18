package com.amalitech.backend.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageService {

    Path storeTemporaryFile(MultipartFile file);

    Path moveToJobDirectory(Path temporaryFile, Long jobId);

    void deleteIfExists(Path path);
}