package com.amalitech.backend.service.impl;

import com.amalitech.backend.service.JobDispatcher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AsyncJobDispatcher implements JobDispatcher {

    private final ConversionWorker conversionWorker;

    public AsyncJobDispatcher(
            ConversionWorker conversionWorker
    ) {
        this.conversionWorker = conversionWorker;
    }

    @Override
    public void dispatch(UUID jobId) {
        conversionWorker.process(jobId);
    }
}