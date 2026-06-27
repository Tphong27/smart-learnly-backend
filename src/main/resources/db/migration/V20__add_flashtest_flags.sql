ALTER TABLE public.assignments
    ADD COLUMN IF NOT EXISTS is_flashtest boolean NOT NULL DEFAULT false;

ALTER TABLE public.tests
    ADD COLUMN IF NOT EXISTS is_flashtest boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_assignments_is_flashtest
    ON public.assignments(is_flashtest);

CREATE INDEX IF NOT EXISTS idx_tests_is_flashtest
    ON public.tests(is_flashtest);
