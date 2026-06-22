ALTER TABLE public.classes
    ADD COLUMN IF NOT EXISTS price numeric(12,2);

UPDATE public.classes class_offering
SET price = COALESCE(course.discounted_price, course.price, 0)
FROM public.courses course
WHERE course.id = class_offering.course_id
  AND class_offering.price IS NULL;

UPDATE public.classes
SET price = 0
WHERE price IS NULL;

ALTER TABLE public.classes
    ALTER COLUMN price SET DEFAULT 0,
    ALTER COLUMN price SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_classes_price_non_negative'
          AND conrelid = 'public.classes'::regclass
    ) THEN
        ALTER TABLE public.classes
            ADD CONSTRAINT chk_classes_price_non_negative CHECK (price >= 0);
    END IF;
END
$$;

COMMENT ON COLUMN public.classes.price IS
    'Current tuition for future class purchases; completed orders keep immutable price snapshots.';
