ALTER TABLE public.assignments
    ADD COLUMN IF NOT EXISTS access_code VARCHAR(12),
    ADD COLUMN IF NOT EXISTS access_code_expires_at TIMESTAMPTZ;

ALTER TABLE public.tests
    ADD COLUMN IF NOT EXISTS access_code VARCHAR(12),
    ADD COLUMN IF NOT EXISTS access_code_expires_at TIMESTAMPTZ;
