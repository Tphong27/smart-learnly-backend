-- Transcription runs automatically after video processing. Generative learning aids
-- are created separately only after an author explicitly requests AI suggestions.
ALTER TABLE public.video_ai_jobs
    DROP CONSTRAINT IF EXISTS chk_video_ai_jobs_type;

ALTER TABLE public.video_ai_jobs
    ADD CONSTRAINT chk_video_ai_jobs_type
        CHECK (job_type IN ('VIDEO_TRANSCRIPT', 'VIDEO_ARTIFACTS', 'FLASHCARD_CANDIDATES'));
