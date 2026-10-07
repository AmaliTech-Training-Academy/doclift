package com.amalitech.backend.repository;



import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findByStatus(JobStatus status);

    Optional<Job> findBySourceFilename(String sourceFilename);


    @Query("""
    SELECT j
    FROM Job j
    LEFT JOIN FETCH j.file
    LEFT JOIN FETCH j.metrics
    WHERE j.id = :id
""")
    Optional<Job> findByIdWithFile(@Param("id") UUID id);
    List<Job> findByStatusOrderByCreatedAtAsc(JobStatus status);

    List<Job> findByStatusInAndCompletedAtBeforeAndFilesDeletedAtIsNull(
            List<JobStatus> statuses,
            Instant threshold
    );
}