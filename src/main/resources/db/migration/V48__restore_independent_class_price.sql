ALTER TABLE public.classes
ADD COLUMN price numeric(12,2);

ALTER TABLE public.classes
ADD CONSTRAINT chk_classes_price_non_negative
CHECK (price IS NULL OR price >= 0);

COMMENT ON COLUMN public.classes.price IS
'Tuition fee for registering this offline class. Independent from courses.price.';