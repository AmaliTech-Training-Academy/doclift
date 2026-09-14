package com.amalitech.backend.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_files")
@Setter @Getter
@NoArgsConstructor
public class JobFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private Job job;

    @Column(name = "output_path", nullable = false)
    private String outputPath;

    @Column(name = "size")
    private Long size;

    public JobFile(Job job, String outputPath, Long size) {
        this.job = job;
        this.outputPath = outputPath;
        this.size = size;
    }
}
