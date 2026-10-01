package com.amalitech.backend.exception;

public class JobFailedException extends RuntimeException {

    public JobFailedException() {
        super("The conversion failed and no download is available.");
    }
}