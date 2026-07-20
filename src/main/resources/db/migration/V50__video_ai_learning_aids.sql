ALTER TABLE public.hls_lessons
    ADD COLUMN ai_audio_object_key character varying(1024),
    ADD COLUMN ai_audio_duration_ms bigint,
    ADD CONSTRAINT chk_hls_lessons_ai_audio_duration
        CHECK (ai_audio_duration_ms IS NULL OR ai_audio_duration_ms > 0),
    ADD CONSTRAINT chk_hls_lessons_ai_audio_pair
        CHECK ((ai_audio_object_key IS NULL) = (ai_audio_duration_ms IS NULL));

CREATE TABLE public.video_ai_contents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id uuid NOT NULL,
    lesson_scope character varying(16) NOT NULL,
    course_id uuid NOT NULL REFERENCES public.courses(id),
    class_id uuid REFERENCES public.classes(id),
    source_version uuid NOT NULL,
    language character varying(16),
    transcript_text text,
    summary text,
    key_points jsonb NOT NULL DEFAULT '[]'::jsonb,
    status character varying(16) NOT NULL DEFAULT 'draft',
    revision bigint NOT NULL DEFAULT 0,
    created_by uuid NOT NULL REFERENCES public.users(id),
    published_by uuid REFERENCES public.users(id),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    published_at timestamp with time zone,
    CONSTRAINT chk_video_ai_contents_scope
        CHECK (lesson_scope IN ('MASTER', 'CLASS')),
    CONSTRAINT chk_video_ai_contents_class_scope
        CHECK ((lesson_scope = 'MASTER' AND class_id IS NULL)
            OR (lesson_scope = 'CLASS' AND class_id IS NOT NULL)),
    CONSTRAINT chk_video_ai_contents_status
        CHECK (status IN ('draft', 'published', 'superseded'))
);

CREATE TABLE public.video_ai_transcript_segments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id uuid NOT NULL REFERENCES public.video_ai_contents(id) ON DELETE CASCADE,
    segment_index integer NOT NULL,
    start_ms bigint NOT NULL,
    end_ms bigint NOT NULL,
    text text NOT NULL,
    CONSTRAINT chk_video_ai_segment_time CHECK (start_ms >= 0 AND end_ms > start_ms),
    CONSTRAINT uq_video_ai_segment_index UNIQUE (content_id, segment_index)
);

CREATE TABLE public.video_ai_chapters (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id uuid NOT NULL REFERENCES public.video_ai_contents(id) ON DELETE CASCADE,
    chapter_index integer NOT NULL,
    start_ms bigint NOT NULL,
    end_ms bigint NOT NULL,
    title character varying(255) NOT NULL,
    summary text,
    CONSTRAINT chk_video_ai_chapter_time CHECK (start_ms >= 0 AND end_ms > start_ms),
    CONSTRAINT uq_video_ai_chapter_index UNIQUE (content_id, chapter_index)
);

CREATE TABLE public.video_ai_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id uuid NOT NULL,
    lesson_scope character varying(16) NOT NULL,
    course_id uuid NOT NULL REFERENCES public.courses(id),
    class_id uuid REFERENCES public.classes(id),
    source_version uuid NOT NULL,
    job_type character varying(32) NOT NULL DEFAULT 'VIDEO_ARTIFACTS',
    status character varying(16) NOT NULL DEFAULT 'pending',
    stage character varying(32) NOT NULL DEFAULT 'queued',
    progress_percent integer NOT NULL DEFAULT 0,
    source_language character varying(16) NOT NULL DEFAULT 'auto',
    attempt_count integer NOT NULL DEFAULT 0,
    error_message text,
    content_id uuid REFERENCES public.video_ai_contents(id),
    target_lesson_id uuid,
    result_batch_id uuid REFERENCES public.flashcard_staging_batches(id),
    result_set_id uuid REFERENCES public.flashcard_sets(id),
    desired_count integer,
    difficulty character varying(16),
    requested_by uuid NOT NULL REFERENCES public.users(id),
    next_attempt_at timestamp with time zone NOT NULL DEFAULT now(),
    lease_owner uuid,
    lease_expires_at timestamp with time zone,
    lease_heartbeat_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_video_ai_jobs_scope
        CHECK (lesson_scope IN ('MASTER', 'CLASS')),
    CONSTRAINT chk_video_ai_jobs_class_scope
        CHECK ((lesson_scope = 'MASTER' AND class_id IS NULL)
            OR (lesson_scope = 'CLASS' AND class_id IS NOT NULL)),
    CONSTRAINT chk_video_ai_jobs_type
        CHECK (job_type IN ('VIDEO_ARTIFACTS', 'FLASHCARD_CANDIDATES')),
    CONSTRAINT chk_video_ai_jobs_status
        CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'superseded')),
    CONSTRAINT chk_video_ai_jobs_progress
        CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT chk_video_ai_jobs_desired_count
        CHECK (desired_count IS NULL OR desired_count BETWEEN 1 AND 30),
    CONSTRAINT chk_video_ai_jobs_difficulty
        CHECK (difficulty IS NULL OR difficulty IN ('easy', 'medium', 'hard')),
    CONSTRAINT chk_video_ai_jobs_lease
        CHECK ((status = 'processing'
                    AND lease_owner IS NOT NULL
                    AND lease_expires_at IS NOT NULL
                    AND lease_heartbeat_at IS NOT NULL)
            OR (status <> 'processing'
                    AND lease_owner IS NULL
                    AND lease_expires_at IS NULL
                    AND lease_heartbeat_at IS NULL))
);

CREATE INDEX idx_video_ai_contents_lesson
    ON public.video_ai_contents(lesson_id, lesson_scope, class_id, updated_at DESC);
CREATE UNIQUE INDEX uq_video_ai_published_current
    ON public.video_ai_contents(lesson_id, lesson_scope, COALESCE(class_id, '00000000-0000-0000-0000-000000000000'::uuid), source_version)
    WHERE status = 'published';
CREATE INDEX idx_video_ai_segments_content_time
    ON public.video_ai_transcript_segments(content_id, start_ms);
CREATE INDEX idx_video_ai_chapters_content_time
    ON public.video_ai_chapters(content_id, start_ms);
CREATE INDEX idx_video_ai_jobs_claim
    ON public.video_ai_jobs(status, next_attempt_at, created_at);
CREATE UNIQUE INDEX uq_video_ai_active_job
    ON public.video_ai_jobs(lesson_id, lesson_scope, COALESCE(class_id, '00000000-0000-0000-0000-000000000000'::uuid), source_version, job_type)
    WHERE status IN ('pending', 'processing');

ALTER TABLE public.flashcard_staging_batches
    ALTER COLUMN lesson_id DROP NOT NULL,
    ADD COLUMN curriculum_lesson_id uuid REFERENCES public.curriculum_lessons(id),
    ADD COLUMN source_video_ai_content_id uuid REFERENCES public.video_ai_contents(id),
    ADD CONSTRAINT chk_flashcard_staging_batches_lesson_target
        CHECK ((lesson_id IS NOT NULL AND curriculum_lesson_id IS NULL)
            OR (lesson_id IS NULL AND curriculum_lesson_id IS NOT NULL));

CREATE INDEX idx_flashcard_staging_batches_curriculum_lesson_id
    ON public.flashcard_staging_batches(curriculum_lesson_id);
CREATE INDEX idx_flashcard_staging_batches_source_video_ai_content_id
    ON public.flashcard_staging_batches(source_video_ai_content_id);
