package com.amalitech.backend.service;

import java.util.UUID;

public interface JobDispatcher {

    void dispatch(UUID jobId);
}
