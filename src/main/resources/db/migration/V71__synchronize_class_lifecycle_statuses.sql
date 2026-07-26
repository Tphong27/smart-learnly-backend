/*
 * Backfill class lifecycle statuses from start_date and end_date.
 *
 * Business rules:
 * - start_date > business date: upcoming
 * - start_date <= business date <= end_date: ongoing
 * - end_date < business date: completed
 * - cancelled is an explicit terminal state and must be preserved
 *
 * Supabase database timezone is UTC, therefore calculate the business
 * date explicitly in Asia/Ho_Chi_Minh.
 */
WITH lifecycle_context AS (
    SELECT
        (
            CURRENT_TIMESTAMP
            AT TIME ZONE 'Asia/Ho_Chi_Minh'
        )::DATE AS business_date
),
desired_statuses AS (
    SELECT
        class_offering.id,
        CASE
            WHEN class_offering.end_date <
                 lifecycle_context.business_date
                THEN 'completed'::public.class_status

            WHEN class_offering.start_date >
                 lifecycle_context.business_date
                THEN 'upcoming'::public.class_status

            ELSE 'ongoing'::public.class_status
        END AS desired_status
    FROM public.classes class_offering
    CROSS JOIN lifecycle_context
    WHERE class_offering.deleted_at IS NULL
      AND class_offering.status <>
          'cancelled'::public.class_status
)
UPDATE public.classes class_offering
SET status = desired.desired_status
FROM desired_statuses desired
WHERE class_offering.id = desired.id
  AND class_offering.status IS DISTINCT FROM
      desired.desired_status;