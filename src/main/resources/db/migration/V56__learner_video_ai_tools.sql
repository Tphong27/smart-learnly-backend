ALTER TABLE public.video_ai_contents
    ADD COLUMN suggested_title character varying(255);

CREATE TABLE public.learner_video_ai_artifacts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    course_id uuid NOT NULL REFERENCES public.courses(id) ON DELETE CASCADE,
    class_id uuid REFERENCES public.classes(id) ON DELETE CASCADE,
    lesson_id uuid NOT NULL REFERENCES public.curriculum_lessons(id) ON DELETE CASCADE,
    source_version uuid NOT NULL,
    artifact_type character varying(16) NOT NULL,
    content_json jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_learner_video_ai_artifact_type
        CHECK (artifact_type IN ('FLASHCARDS', 'QUIZ')),
    CONSTRAINT uq_learner_video_ai_artifact
        UNIQUE (student_id, lesson_id, source_version, artifact_type)
);

CREATE INDEX idx_learner_video_ai_artifacts_lookup
    ON public.learner_video_ai_artifacts(student_id, lesson_id, source_version);
