package com.amalitech.backend.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.UUID;

public interface FileStorageService {

    Path storeTemporaryFile(MultipartFile file);

    Path moveToJobDirectory(Path temporaryFile, UUID jobId);

    void deleteIfExists(Path path);
}