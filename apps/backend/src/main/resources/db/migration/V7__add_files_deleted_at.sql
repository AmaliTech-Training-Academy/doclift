ALTER TABLE jobs
    ADD COLUMN files_deleted_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_jobs_cleanup_eligibility
    ON jobs (status, completed_at, files_deleted_at);
