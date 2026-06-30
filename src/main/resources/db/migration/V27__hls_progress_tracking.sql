-- V27__hls_progress_tracking.sql
-- Add progress tracking columns for HLS video processing

ALTER TABLE public.hls_lessons
ADD COLUMN IF NOT EXISTS progress_percent INTEGER DEFAULT 0;

ALTER TABLE public.hls_lessons
ADD COLUMN IF NOT EXISTS current_step VARCHAR(100);

ALTER TABLE public.hls_lessons
ADD COLUMN IF NOT EXISTS error_message TEXT;
