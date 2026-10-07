package com.amalitech.backend.service;

import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobFile;
import com.amalitech.backend.model.JobStatus;
import com.amalitech.backend.repository.JobRepository;
import com.amalitech.backend.service.impl.FileCleanupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileCleanupServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final Duration RETENTION = Duration.ofMinutes(5);

    private JobRepository jobRepository;
    private FileStorageService fileStorageService;
    private FileCleanupService fileCleanupService;

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobRepository.class);
        fileStorageService = mock(FileStorageService.class);

        fileCleanupService = new FileCleanupServiceImpl(
                jobRepository,
                fileStorageService,
                RETENTION
        );
    }

    private Job doneJob(UUID id, String outputPath) {
        Job job = new Job();
        job.setId(id);
        job.setStatus(JobStatus.DONE);
        job.setCompletedAt(NOW.minus(RETENTION).minusSeconds(1));

        if (outputPath != null) {
            job.setFile(new JobFile(job, outputPath, 100L));
        }

        return job;
    }

    @Test
    void shouldOnlyQueryTerminalStatuses() {
        when(jobRepository.findByStatusInAndCompletedAtBeforeAndFilesDeletedAtIsNull(any(), any()))
                .thenReturn(List.of());

        fileCleanupService.cleanupExpiredFiles(NOW);

        ArgumentCaptor<List<JobStatus>> statusCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<Instant> thresholdCaptor = ArgumentCaptor.captor();

        verify(jobRepository).findByStatusInAndCompletedAtBeforeAndFilesDeletedAtIsNull(
                statusCaptor.capture(),
                thresholdCaptor.capture()
        );

        assertEquals(List.of(JobStatus.DONE, JobStatus.FAILED), statusCaptor.getValue());
        assertEquals(NOW.minus(RETENTION), thresholdCaptor.getValue());
    }

    @Test
    void shouldDeleteSourceAndOutputAndMarkJobCleanedWhenBothSucceed() {
        UUID jobId = UUID.randomUUID();
        Job job = doneJob(jobId, "/data/" + jobId + "/output.docx");

        Path sourcePath = Path.of("/data/" + jobId + "/source.pdf");

        when(fileStorageService.resolveSourcePdfPath(jobId)).thenReturn(sourcePath);

        when(jobRepository.findByStatusInAndCompletedAtBeforeAndFilesDeletedAtIsNull(any(), any()))
                .thenReturn(List.of(job));

        fileCleanupService.cleanupExpiredFiles(NOW);

        verify(fileStorageService).deleteIfExists(sourcePath);
        verify(fileStorageService).deleteIfExists(Path.of(job.getFile().getOutputPath()));

        assertEquals(NOW, job.getFilesDeletedAt());
        verify(jobRepository).save(job);
    }

    @Test
    void shouldTolerateJobWithNoOutputFile() {
        UUID jobId = UUID.randomUUID();
        Job job = doneJob(jobId, null);

        when(fileStorageService.resolveSourcePdfPath(jobId))
                .thenReturn(Path.of("/data/" + jobId + "/source.pdf"));

        when(jobRepository.findByStatusInAndCompletedAtBeforeAndFilesDeletedAtIsNull(any(), any()))
                .thenReturn(List.of(job));

        fileCleanupService.cleanupExpiredFiles(NOW);

        assertEquals(NOW, job.getFilesDeletedAt());
        verify(jobRepository).save(job);
    }

    @Test
    void shouldTolerateMissingFileOnDisk() {
        UUID jobId = UUID.randomUUID();
        Job job = doneJob(jobId, "/data/" + jobId + "/output.docx");

        when(fileStorageService.resolveSourcePdfPath(jobId))
                .thenReturn(Path.of("/data/" + jobId + "/source.pdf"));

        doNothing().when(fileStorageService).deleteIfExists(any());

        when(jobRepository.findByStatusInAndCompletedAtBeforeAndFilesDeletedAtIsNull(any(), any()))
                .thenReturn(List.of(job));

        fileCleanupService.cleanupExpiredFiles(NOW);

        assertEquals(NOW, job.getFilesDeletedAt());
    }

    @Test
    void shouldContinueCleanupWhenOneJobsDeletionFails() {
        UUID failingJobId = UUID.randomUUID();
        UUID succeedingJobId = UUID.randomUUID();

        Job failingJob = doneJob(failingJobId, "/data/" + failingJobId + "/output.docx");
        Job succeedingJob = doneJob(succeedingJobId, "/data/" + succeedingJobId + "/output.docx");

        Path failingSourcePath = Path.of("/data/" + failingJobId + "/source.pdf");
        Path succeedingSourcePath = Path.of("/data/" + succeedingJobId + "/source.pdf");

        when(fileStorageService.resolveSourcePdfPath(failingJobId)).thenReturn(failingSourcePath);
        when(fileStorageService.resolveSourcePdfPath(succeedingJobId)).thenReturn(succeedingSourcePath);

        doThrow(new IllegalStateException("disk error"))
                .when(fileStorageService).deleteIfExists(failingSourcePath);

        when(jobRepository.findByStatusInAndCompletedAtBeforeAndFilesDeletedAtIsNull(any(), any()))
                .thenReturn(List.of(failingJob, succeedingJob));

        fileCleanupService.cleanupExpiredFiles(NOW);

        assertNull(failingJob.getFilesDeletedAt());
        verify(jobRepository, never()).save(failingJob);

        assertEquals(NOW, succeedingJob.getFilesDeletedAt());
        verify(jobRepository).save(succeedingJob);
    }
}
