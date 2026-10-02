package com.amalitech.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_metrics")
@Getter
@Setter
@NoArgsConstructor
public class JobMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private Job job;

    @Column(name = "source_word_count")
    private Integer sourceWordCount;

    @Column(name = "output_word_count")
    private Integer outputWordCount;

    @Column(name = "ordered_lists_detected")
    private Integer orderedListsDetected;

    @Column(name = "unordered_lists_detected")
    private Integer unorderedListsDetected;

    @Column(name = "ordered_lists_reconstructed")
    private Integer orderedListsReconstructed;

    @Column(name = "unordered_lists_reconstructed")
    private Integer unorderedListsReconstructed;


    public JobMetrics(
            Job job,
            Integer sourceWordCount,
            Integer outputWordCount
    ) {
        this.job = job;
        this.sourceWordCount = sourceWordCount;
        this.outputWordCount = outputWordCount;
    }
}