package com.amalitech.backend.repository;

import com.amalitech.backend.model.JobFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobFileRepository extends JpaRepository<JobFile, Long> {

    Optional<JobFile> findByJobId(Long jobId);
}