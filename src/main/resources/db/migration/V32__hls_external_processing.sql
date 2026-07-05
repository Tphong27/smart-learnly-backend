ALTER TABLE public.hls_lessons
    ADD COLUMN IF NOT EXISTS processing_job_id UUID,
    ADD COLUMN IF NOT EXISTS source_object_key VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS processing_output_prefix VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS processing_provider VARCHAR(32),
    ADD COLUMN IF NOT EXISTS workflow_dispatched_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS processing_completed_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_hls_lessons_processing_job_id
    ON public.hls_lessons (processing_job_id);
