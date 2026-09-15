package com.amalitech.backend.exception;



public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(Long jobId) {
        super("Job not found: " + jobId);
    }
}
