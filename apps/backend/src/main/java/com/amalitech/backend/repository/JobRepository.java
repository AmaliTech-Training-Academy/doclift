package com.amalitech.backend.repository;



import com.amalitech.backend.model.Job;
import com.amalitech.backend.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByStatus(JobStatus status);

    Optional<Job> findBySourceFilename(String sourceFilename);

    // Fetch the job together with its file in one query, avoiding a second
    // lazy-load round trip when you know you'll need both (e.g. building a
    // download response right after conversion finishes).
    @Query("SELECT j FROM Job j LEFT JOIN FETCH j.file WHERE j.id = :id")
    Optional<Job> findByIdWithFile(@Param("id") Long id);

    List<Job> findByStatusOrderByCreatedAtAsc(JobStatus status);
}