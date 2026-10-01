package com.amalitech.backend.exception;

public class JobNotReadyException extends RuntimeException {

    public JobNotReadyException() {
        super("The conversion is not ready for download yet.");
    }
}