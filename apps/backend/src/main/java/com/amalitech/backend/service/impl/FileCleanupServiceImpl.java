package com.amalitech.backend.service.impl;

import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobFile;
import com.amalitech.backend.model.JobStatus;
import com.amalitech.backend.repository.JobRepository;
import com.amalitech.backend.service.FileCleanupService;
import com.amalitech.backend.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FileCleanupServiceImpl implements FileCleanupService {

    private static final List<JobStatus> ELIGIBLE_STATUSES =
            List.of(JobStatus.DONE, JobStatus.FAILED);

    private static final Logger log =
            LoggerFactory.getLogger(FileCleanupServiceImpl.class);

    private final JobRepository jobRepository;
    private final FileStorageService fileStorageService;
    private final Duration retention;

    public FileCleanupServiceImpl(
            JobRepository jobRepository,
            FileStorageService fileStorageService,
            @Value("${app.cleanup.retention}") Duration retention
    ) {
        this.jobRepository = jobRepository;
        this.fileStorageService = fileStorageService;
        this.retention = retention;
    }

    @Scheduled(cron = "${app.cleanup.cron}")
    @Transactional
    public void cleanupExpiredFiles() {
        cleanupExpiredFiles(Instant.now());
    }

    @Override
    public void cleanupExpiredFiles(Instant now) {
        Instant threshold = now.minus(retention);

        List<Job> eligibleJobs =
                jobRepository.findByStatusInAndCompletedAtBeforeAndFilesDeletedAtIsNull(
                        ELIGIBLE_STATUSES,
                        threshold
                );

        log.info(
                "FILE_CLEANUP_START eligibleJobs={} threshold={}",
                eligibleJobs.size(),
                threshold
        );

        int cleaned = 0;

        for (Job job : eligibleJobs) {
            if (cleanupJob(job, now)) {
                cleaned++;
            }
        }

        log.info(
                "FILE_CLEANUP_END cleaned={} failed={} total={}",
                cleaned,
                eligibleJobs.size() - cleaned,
                eligibleJobs.size()
        );
    }

    private boolean cleanupJob(Job job, Instant now) {
        UUID jobId = job.getId();

        boolean sourceDeleted = deleteQuietly(
                fileStorageService.resolveSourcePdfPath(jobId),
                jobId,
                "source"
        );

        JobFile file = job.getFile();
        boolean outputDeleted;

        if (file != null) {
            outputDeleted = deleteQuietly(
                    Path.of(file.getOutputPath()),
                    jobId,
                    "output"
            );
        } else {
            outputDeleted = true;

            if (job.getStatus() == JobStatus.DONE) {
                log.warn(
                        "FILE_CLEANUP_MISSING_JOB_FILE jobId={} status={}",
                        jobId,
                        job.getStatus()
                );
            }
        }

        if (!sourceDeleted || !outputDeleted) {
            return false;
        }

        job.setFilesDeletedAt(now);
        jobRepository.save(job);

        log.info("FILE_CLEANUP_JOB_DONE jobId={}", jobId);

        return true;
    }

    private boolean deleteQuietly(Path path, UUID jobId, String kind) {
        try {
            fileStorageService.deleteIfExists(path);
            return true;
        } catch (RuntimeException e) {
            log.warn(
                    "FILE_CLEANUP_DELETE_FAILED jobId={} kind={} path={}",
                    jobId,
                    kind,
                    path,
                    e
            );
            return false;
        }
    }
}
