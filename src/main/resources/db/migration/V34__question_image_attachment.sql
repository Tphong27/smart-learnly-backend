ALTER TABLE public.questions
    ADD COLUMN IF NOT EXISTS image_url text,
    ADD COLUMN IF NOT EXISTS image_object_key text;
