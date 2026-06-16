DO $$
BEGIN
    IF to_regclass('public.courses') IS NULL THEN
        RAISE EXCEPTION 'Cannot add courses.is_featured: public.courses table does not exist';
    END IF;
END $$;

ALTER TABLE public.courses
    ADD COLUMN IF NOT EXISTS is_featured boolean NOT NULL DEFAULT false;

UPDATE public.courses
SET is_featured = false
WHERE is_featured IS NULL;

ALTER TABLE public.courses
    ALTER COLUMN is_featured SET DEFAULT false,
    ALTER COLUMN is_featured SET NOT NULL;
