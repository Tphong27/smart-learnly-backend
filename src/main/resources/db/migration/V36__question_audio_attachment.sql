ALTER TABLE public.questions
    ADD COLUMN IF NOT EXISTS audio_url text,
    ADD COLUMN IF NOT EXISTS audio_object_key text;
