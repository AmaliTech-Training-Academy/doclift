package com.amalitech.backend.exception;

public class JobFileExpiredException extends RuntimeException {

    public JobFileExpiredException() {
        super("The converted file has expired and is no longer available for download.");
    }
}
