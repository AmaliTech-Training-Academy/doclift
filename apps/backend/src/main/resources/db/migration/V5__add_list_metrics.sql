ALTER TABLE job_metrics
    ADD COLUMN ordered_lists_detected INTEGER;

ALTER TABLE job_metrics
    ADD COLUMN unordered_lists_detected INTEGER;

ALTER TABLE job_metrics
    ADD COLUMN ordered_lists_reconstructed INTEGER;

ALTER TABLE job_metrics
    ADD COLUMN unordered_lists_reconstructed INTEGER;