package com.amalitech.backend.service;

import java.time.Instant;

public interface FileCleanupService {

    void cleanupExpiredFiles(Instant now);
}
