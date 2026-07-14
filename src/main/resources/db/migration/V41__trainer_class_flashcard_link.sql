-- V41: allow flashcard_sets to link to a class-draft curriculum lesson so trainers
-- can author per-class flashcards without touching the master lesson content.
-- Admin flashcards continue to use lesson_id; trainer flashcards use curriculum_lesson_id.

ALTER TABLE public.flashcard_sets
    ADD COLUMN IF NOT EXISTS curriculum_lesson_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'flashcard_sets_curriculum_lesson_id_fkey'
          AND conrelid = 'public.flashcard_sets'::regclass
    ) THEN
        ALTER TABLE public.flashcard_sets
            ADD CONSTRAINT flashcard_sets_curriculum_lesson_id_fkey
            FOREIGN KEY (curriculum_lesson_id)
            REFERENCES public.curriculum_lessons(id)
            ON DELETE CASCADE;
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_flashcard_sets_curriculum_lesson_id
    ON public.flashcard_sets (curriculum_lesson_id)
    WHERE curriculum_lesson_id IS NOT NULL AND deleted_at IS NULL;
