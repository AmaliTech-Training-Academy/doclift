package com.amalitech.backend.service.impl;

import com.amalitech.backend.exception.JobNotFoundException;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobFile;
import com.amalitech.backend.model.JobPhase;
import com.amalitech.backend.model.JobStatus;
import com.amalitech.backend.repository.JobRepository;
import com.amalitech.backend.service.JobService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;

    public JobServiceImpl(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    @Transactional
    public Job createJob(String sourceFilename, Integer pageCount) {
        Job job = new Job(sourceFilename, pageCount);
        return jobRepository.save(job);
    }

    @Override
    @Transactional
    public Job markProcessing(UUID jobId) {
        Job job = getJobOrThrow(jobId);

        Instant now = Instant.now();

        job.setStatus(JobStatus.PROCESSING);

        if (job.getStartedAt() == null) {
            job.setStartedAt(now);
        }


        job.setPhase(JobPhase.LOADING_SOURCE);
        job.setProgressPercent(10);
        job.setPhaseStartedAt(now);

        return job;
    }

    @Override
    @Transactional
    public Job markCompleted(
            UUID jobId,
            String outputPath,
            long sizeBytes
    ) {
        Job job = getJobOrThrow(jobId);

        Instant now = Instant.now();

        JobFile file =
                new JobFile(
                        job,
                        outputPath,
                        sizeBytes
                );

        job.setFile(file);

        job.setStatus(JobStatus.DONE);
        job.setPhase(JobPhase.COMPLETED);
        job.setProgressPercent(100);
        job.setPhaseStartedAt(now);
        job.setCompletedAt(now);

        return job;
    }

    @Override
    @Transactional
    public Job markFailed(UUID jobId) {
        Job job = getJobOrThrow(jobId);
        job.setStatus(JobStatus.FAILED);
        return job;
    }

    @Override
    @Transactional(readOnly = true)
    public Job getJob(UUID jobId) {
        return getJobOrThrow(jobId);
    }


    @Override
    @Transactional
    public Job updateProgress(
            UUID jobId,
            JobPhase phase,
            Integer progressPercent
    ) {
        Job job = getJobOrThrow(jobId);

        Instant now = Instant.now();

        job.setPhase(phase);
        job.setProgressPercent(progressPercent);
        job.setPhaseStartedAt(now);

        return job;
    }


    @Override
    @Transactional(readOnly = true)
    public Job getJobWithFile(UUID jobId) {
        return jobRepository.findByIdWithFile(jobId)
                .orElseThrow(JobNotFoundException::new);
    }

    private Job getJobOrThrow(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(JobNotFoundException::new);
    }
}