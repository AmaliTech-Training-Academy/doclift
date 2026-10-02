package com.amalitech.backend.model;

public enum JobPhase {
    QUEUED,
    LOADING_SOURCE,
    EXTRACTING_CONTENT,
    RECOVERING_STRUCTURE,
    GENERATING_DOCUMENT,
    SAVING_OUTPUT,
    COMPLETED
}