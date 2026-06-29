ALTER TABLE public.assignments
    ADD COLUMN IF NOT EXISTS instruction_file_url TEXT,
    ADD COLUMN IF NOT EXISTS instruction_file_name VARCHAR(255);
