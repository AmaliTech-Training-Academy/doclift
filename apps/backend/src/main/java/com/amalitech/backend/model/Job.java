package com.amalitech.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Getter @Setter
@NoArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    @Column(name = "source_filename", nullable = false)
    private String sourceFilename;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase")
    private JobPhase phase;

    @Column(name = "progress_percent")
    private Integer progressPercent;

    @Column(name = "phase_started_at")
    private Instant phaseStartedAt;

    @Column(name = "files_deleted_at")
    private Instant filesDeletedAt;

    @OneToOne(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private JobFile file;

    @OneToOne(
            mappedBy = "job",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private JobMetrics metrics;

    public Job(String sourceFilename, Integer pageCount) {
        this.sourceFilename = sourceFilename;
        this.pageCount = pageCount;
        this.status = JobStatus.QUEUED;
        this.phase = JobPhase.QUEUED;
        this.progressPercent = 0;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (status == null) {
            status = JobStatus.QUEUED;
        }

        if (phase == null) {
            phase = JobPhase.QUEUED;
        }

        if (progressPercent == null) {
            progressPercent = 0;
        }
    }

}