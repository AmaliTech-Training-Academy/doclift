package com.amalitech.backend.service;


import com.amalitech.backend.exception.JobNotFoundException;
import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobFile;
import com.amalitech.backend.model.JobStatus;
import com.amalitech.backend.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Registers a new job for an uploaded PDF. Status starts as
     * QUEUED (set automatically by Job's @PrePersist).
     */
    @Transactional
    public Job createJob(String sourceFilename, Integer pageCount) {
        Job job = new Job(sourceFilename, pageCount);
        return jobRepository.save(job);
    }

    /**
     * Marks a job as actively being converted. Call this right before
     * handing the PDF off to the PDF conversion engine.
     */
    @Transactional
    public Job markProcessing(Long jobId) {
        Job job = getJobOrThrow(jobId);
        job.setStatus(JobStatus.PROCESSING);
        return job; // managed entity; flushed on commit, no explicit save needed
    }

    /**
     * This is called when the .docx has been written to disk or storage. It attaches the
     * output file record and sets the job status to DONE in one transaction.
     */
    @Transactional
    public Job markCompleted(Long jobId, String outputPath, long sizeBytes) {
        Job job = getJobOrThrow(jobId);
        JobFile file = new JobFile(job, outputPath, sizeBytes);
        job.setFile(file); // cascades the insert via Job's OneToOne mapping
        job.setStatus(JobStatus.DONE);
        return job;
    }

    /**
     * Called if conversion throws (encrypted PDF, empty content, engine
     * timeout, etc.)
     */
    @Transactional
    public Job markFailed(Long jobId) {
        Job job = getJobOrThrow(jobId);
        job.setStatus(JobStatus.FAILED);
        return job;
    }

    @Transactional(readOnly = true)
    public Job getJobWithFile(Long jobId) {
        return jobRepository.findByIdWithFile(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }

    private Job getJobOrThrow(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }
}