package com.amalitech.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class FileStorageService {

    private final Path uploadRoot;

    public FileStorageService(
            @Value("${app.upload.directory}") String uploadDirectory
    ) {
        this.uploadRoot = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    /**
     * Stores the uploaded source PDF using the job id as its directory.
     *
     * @param file uploaded PDF
     * @param jobId conversion job id
     * @return path to the stored source PDF
     */
    public Path storeSourcePdf(MultipartFile file, Long jobId) {
        try {
            Path jobDirectory = uploadRoot.resolve(jobId.toString());
            Files.createDirectories(jobDirectory);

            Path targetPath = jobDirectory.resolve("source.pdf");

            Files.copy(
                    file.getInputStream(),
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return targetPath;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to store uploaded PDF.",
                    e
            );
        }
    }
}