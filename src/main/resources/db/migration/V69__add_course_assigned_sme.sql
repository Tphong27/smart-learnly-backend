ALTER TABLE public.courses
    ADD COLUMN assigned_sme_id UUID;

ALTER TABLE public.courses
    ADD CONSTRAINT fk_courses_assigned_sme
        FOREIGN KEY (assigned_sme_id)
        REFERENCES public.users(id)
        ON DELETE SET NULL;

CREATE INDEX idx_courses_assigned_sme
    ON public.courses(assigned_sme_id)
    WHERE deleted_at IS NULL;