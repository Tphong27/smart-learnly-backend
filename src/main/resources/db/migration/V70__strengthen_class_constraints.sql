/*
 * Required for every class, including historical classes.
 * Run the data audit before applying this migration.
 */
ALTER TABLE public.classes
    ALTER COLUMN start_date SET NOT NULL,
    ALTER COLUMN end_date SET NOT NULL,
    ALTER COLUMN schedule_description SET NOT NULL;

/*
 * General class invariants.
 */
ALTER TABLE public.classes
    ADD CONSTRAINT chk_classes_name_not_blank_v70
        CHECK (
            NULLIF(BTRIM(class_name), '') IS NOT NULL
        ),

    ADD CONSTRAINT chk_classes_schedule_not_blank_v70
        CHECK (
            NULLIF(BTRIM(schedule_description), '') IS NOT NULL
        ),

    ADD CONSTRAINT chk_classes_meeting_url_format_v70
        CHECK (
            meeting_url IS NULL
            OR BTRIM(meeting_url) ~
               '^https://meet\.google\.com/[a-z]{3}-[a-z]{4}-[a-z]{3}/?$'
        ),

    ADD CONSTRAINT chk_classes_capacity_range_v70
        CHECK (
            max_students BETWEEN 1 AND 500
        ),

    ADD CONSTRAINT chk_classes_price_range_v70
        CHECK (
            price IS NULL
            OR (
                price >= 0
                AND price <= 9999999999.99
            )
        );

/*
 * Legacy completed/cancelled rows may lack trainer, Meet URL or price.
 * Every active class must contain those values.
 */
ALTER TABLE public.classes
    ADD CONSTRAINT chk_classes_active_required_fields_v70
        CHECK (
            deleted_at IS NOT NULL
            OR status::TEXT IN ('completed', 'cancelled')
            OR (
                trainer_id IS NOT NULL
                AND NULLIF(BTRIM(meeting_url), '') IS NOT NULL
                AND price IS NOT NULL
            )
        );