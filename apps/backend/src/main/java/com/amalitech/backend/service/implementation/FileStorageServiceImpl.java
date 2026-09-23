package com.amalitech.backend.service.implementation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class FileStorageServiceImpl implements com.amalitech.backend.service.interfaces.FileStorageService {

    private final Path uploadRoot;

    public FileStorageServiceImpl(
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
    @Override
    public Path storeSourcePdf(MultipartFile file, Long jobId) {
        Path jobDirectory = uploadRoot.resolve(jobId.toString());
        Path targetPath = jobDirectory.resolve("source.pdf");

        try {
            Files.createDirectories(jobDirectory);

            Files.copy(
                    file.getInputStream(),
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return targetPath;

        } catch (IOException e) {
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException cleanupException) {
                e.addSuppressed(cleanupException);
            }

            throw new IllegalStateException(
                    "Failed to store uploaded PDF.",
                    e
            );
        }
    }
}