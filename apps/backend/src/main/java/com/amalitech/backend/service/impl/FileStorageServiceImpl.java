package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

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
    public Path getSourcePdfPath(UUID jobId) {

        Path sourcePath = resolveSourcePdfPath(jobId);

        if (!Files.exists(sourcePath)) {
            throw new IllegalStateException(
                    "Source PDF not found for job " + jobId
            );
        }

        return sourcePath;
    }

    @Override
    public Path resolveSourcePdfPath(UUID jobId) {
        return resolveJobDirectory(jobId).resolve("source.pdf");
    }

    private Path resolveJobDirectory(UUID jobId) {
        return uploadRoot.resolve(jobId.toString());
    }

    @Override
    public Path storeOutputDocx(
            UUID jobId,
            byte[] content
    ) {

        Path jobDirectory = resolveJobDirectory(jobId);

        Path outputPath =
                jobDirectory.resolve(
                        "output.docx"
                );

        try {
            Files.createDirectories(
                    jobDirectory
            );

            Files.write(
                    outputPath,
                    content
            );

            return outputPath;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to store generated Word document.",
                    e
            );
        }
    }

    @Override
    public Path moveToJobDirectory(Path temporaryFile, UUID jobId) {
        Path jobDirectory = resolveJobDirectory(jobId);
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