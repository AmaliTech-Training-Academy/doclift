package com.amalitech.backend.service.impl;

import com.amalitech.backend.exception.JobFailedException;
import com.amalitech.backend.exception.JobFileExpiredException;
import com.amalitech.backend.exception.JobNotReadyException;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobFile;
import com.amalitech.backend.model.JobStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class JobDownloadService {

    private final JobFilenameResolver jobFilenameResolver;

    public JobDownloadService(
            JobFilenameResolver jobFilenameResolver
    ) {
        this.jobFilenameResolver =
                jobFilenameResolver;
    }

    public DownloadResult prepareDownload(
            Job job
    ) {
        if (job.getStatus() == JobStatus.FAILED) {
            throw new JobFailedException();
        }

        if (job.getStatus() != JobStatus.DONE) {
            throw new JobNotReadyException();
        }

        JobFile jobFile =
                job.getFile();

        if (jobFile == null) {
            throw new IllegalStateException(
                    "Completed job has no output file."
            );
        }

        if (job.getFilesDeletedAt() != null) {
            throw new JobFileExpiredException();
        }

        Path outputPath =
                Path.of(
                        jobFile.getOutputPath()
                );

        if (!Files.exists(outputPath)) {
            throw new IllegalStateException(
                    "Converted output file is missing."
            );
        }

        Resource resource =
                new FileSystemResource(
                        outputPath.toFile()
                );

        String filename =
                jobFilenameResolver
                        .buildOutputFilename(
                                job.getSourceFilename()
                        );

        return new DownloadResult(
                resource,
                filename,
                jobFile.getSize()
        );
    }

    public record DownloadResult(
            Resource resource,
            String filename,
            long size
    ) {}
}