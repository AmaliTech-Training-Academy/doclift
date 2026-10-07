ALTER TABLE jobs
    ADD COLUMN started_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE jobs
    ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE jobs
    ADD COLUMN phase VARCHAR(50);

ALTER TABLE jobs
    ADD COLUMN progress_percent INTEGER;

UPDATE jobs
SET phase = 'QUEUED'
WHERE phase IS NULL;

UPDATE jobs
SET progress_percent = 0
WHERE progress_percent IS NULL;