CREATE TABLE IF NOT EXISTS public.lesson_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    student_id UUID NOT NULL,
    course_id UUID NOT NULL,
    lesson_id UUID NOT NULL,

    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ NULL,
    last_accessed_at TIMESTAMPTZ NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_lesson_progress_student_lesson UNIQUE (student_id, lesson_id)
);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_student_course
ON public.lesson_progress(student_id, course_id);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_course
ON public.lesson_progress(course_id);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_lesson
ON public.lesson_progress(lesson_id);