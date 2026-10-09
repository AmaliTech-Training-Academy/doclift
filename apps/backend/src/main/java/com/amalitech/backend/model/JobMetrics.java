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

    @Column(name = "headings_detected")
    private Integer headingsDetected;

    @Column(name = "h1_heading_count")
    private Integer h1HeadingCount;

    @Column(name = "h2_heading_count")
    private Integer h2HeadingCount;

    @Column(name = "h3_heading_count")
    private Integer h3HeadingCount;

    @Column(name = "tables_detected")
    private Integer tablesDetected;

    @Column(name = "images_detected")
    private Integer imagesDetected;

    @Column(name = "multi_column_page_count")
    private Integer multiColumnPageCount;

    @Column(name = "output_page_count")
    private Integer outputPageCount;


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