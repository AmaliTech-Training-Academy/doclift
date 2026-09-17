package com.amalitech.backend.exception;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException() {
        super("Job not found.");
    }
}