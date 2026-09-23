package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final Path uploadRoot;

    public FileStorageServiceImpl(
            @Value("${app.upload.directory}") String uploadDirectory
    ) {
        this.uploadRoot = Path.of(uploadDirectory)
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public Path storeTemporaryFile(MultipartFile file) {
        Path temporaryFile = null;

        try {
            Files.createDirectories(uploadRoot);

            temporaryFile = Files.createTempFile(
                    uploadRoot,
                    "upload-",
                    ".pdf"
            );

            Files.copy(
                    file.getInputStream(),
                    temporaryFile,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return temporaryFile;

        } catch (IOException e) {

            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException cleanupException) {
                    e.addSuppressed(cleanupException);
                }
            }

            throw new IllegalStateException(
                    "Failed to store uploaded PDF.",
                    e
            );
        }
    }

    @Override
    public Path moveToJobDirectory(Path temporaryFile, Long jobId) {
        Path jobDirectory = uploadRoot.resolve(jobId.toString());
        Path targetPath = jobDirectory.resolve("source.pdf");

        try {
            Files.createDirectories(jobDirectory);

            return Files.move(
                    temporaryFile,
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (IOException e) {
            try {
                Files.deleteIfExists(targetPath);
                Files.deleteIfExists(jobDirectory);
            } catch (IOException cleanupException) {
                e.addSuppressed(cleanupException);
            }

            throw new IllegalStateException(
                    "Failed to store uploaded PDF.",
                    e
            );
        }
    }

    @Override
    public void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to clean up temporary upload.",
                    e
            );
        }
    }
}