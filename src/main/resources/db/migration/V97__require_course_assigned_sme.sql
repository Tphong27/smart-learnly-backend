DO $$
DECLARE
    missing_sme_count bigint;
BEGIN
    SELECT COUNT(*)
    INTO missing_sme_count
    FROM public.courses
    WHERE assigned_sme_id IS NULL;

    IF missing_sme_count > 0 THEN
        RAISE EXCEPTION
            'Cannot require courses.assigned_sme_id: % course(s) still have no assigned SME',
            missing_sme_count;
    END IF;
END
$$;

ALTER TABLE public.courses
    DROP CONSTRAINT IF EXISTS fk_courses_assigned_sme;

ALTER TABLE public.courses
    ALTER COLUMN assigned_sme_id SET NOT NULL;

ALTER TABLE public.courses
    ADD CONSTRAINT fk_courses_assigned_sme
        FOREIGN KEY (assigned_sme_id)
        REFERENCES public.users(id)
        ON DELETE RESTRICT;