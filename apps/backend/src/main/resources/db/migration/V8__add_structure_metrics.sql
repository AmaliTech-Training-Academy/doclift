ALTER TABLE job_metrics
    ADD COLUMN headings_detected INTEGER;

ALTER TABLE job_metrics
    ADD COLUMN h1_heading_count INTEGER;

ALTER TABLE job_metrics
    ADD COLUMN h2_heading_count INTEGER;

ALTER TABLE job_metrics
    ADD COLUMN h3_heading_count INTEGER;

ALTER TABLE job_metrics
    ADD COLUMN tables_detected INTEGER;

ALTER TABLE job_metrics
    ADD COLUMN images_detected INTEGER;

ALTER TABLE job_metrics
    ADD COLUMN multi_column_page_count INTEGER;

ALTER TABLE job_metrics
    ADD COLUMN output_page_count INTEGER;
