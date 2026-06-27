ALTER TABLE public.lesson_progress
ADD COLUMN IF NOT EXISTS student_id UUID;

ALTER TABLE public.lesson_progress
ADD COLUMN IF NOT EXISTS course_id UUID;

ALTER TABLE public.lesson_progress
ADD COLUMN IF NOT EXISTS lesson_id UUID;

ALTER TABLE public.lesson_progress
ADD COLUMN IF NOT EXISTS completed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.lesson_progress
ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ NULL;

ALTER TABLE public.lesson_progress
ADD COLUMN IF NOT EXISTS last_accessed_at TIMESTAMPTZ NULL;

ALTER TABLE public.lesson_progress
ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE public.lesson_progress
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

UPDATE public.lesson_progress
SET completed = FALSE
WHERE completed IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_lesson_progress_student_lesson
ON public.lesson_progress(student_id, lesson_id);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_student_course
ON public.lesson_progress(student_id, course_id);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_course
ON public.lesson_progress(course_id);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_lesson
ON public.lesson_progress(lesson_id);