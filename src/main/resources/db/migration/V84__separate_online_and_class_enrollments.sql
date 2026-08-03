-- CourseEnrollment is the entitlement for a directly purchased/enrolled
-- self-paced course. ClassEnrollment is the entitlement for one concrete
-- class. Older application versions also created CourseEnrollment while
-- enrolling in a class, which incorrectly unlocked the online learning path.

WITH class_only_course_enrollments AS (
    SELECT course_enrollment.id
    FROM public.course_enrollments course_enrollment
    WHERE course_enrollment.status IN (
              'active'::public.enroll_status,
              'completed'::public.enroll_status
          )
      AND EXISTS (
          SELECT 1
          FROM public.class_enrollments class_enrollment
          JOIN public.classes class_offering
            ON class_offering.id = class_enrollment.class_id
          WHERE class_enrollment.student_id = course_enrollment.student_id
            AND class_offering.course_id = course_enrollment.course_id
            AND class_enrollment.status IN (
                'active'::public.enroll_status,
                'completed'::public.enroll_status
            )
      )
      AND EXISTS (
          SELECT 1
          FROM public.enrollment_status_history history
          LEFT JOIN public.transactions transaction_record
            ON transaction_record.id = history.transaction_id
          WHERE history.course_enrollment_id = course_enrollment.id
            AND (
                LOWER(COALESCE(history.reason, '')) LIKE '%class enrollment%'
                OR EXISTS (
                    SELECT 1
                    FROM public.order_items order_item
                    WHERE order_item.order_id = transaction_record.order_id
                      AND order_item.course_id = course_enrollment.course_id
                      AND order_item.class_id IS NOT NULL
                )
            )
      )
      AND NOT EXISTS (
          SELECT 1
          FROM public.enrollment_status_history history
          LEFT JOIN public.transactions transaction_record
            ON transaction_record.id = history.transaction_id
          WHERE history.course_enrollment_id = course_enrollment.id
            AND (
                LOWER(COALESCE(history.reason, '')) LIKE '%online course enrollment%'
                OR EXISTS (
                    SELECT 1
                    FROM public.order_items order_item
                    WHERE order_item.order_id = transaction_record.order_id
                      AND order_item.course_id = course_enrollment.course_id
                      AND order_item.class_id IS NULL
                )
            )
      )
)
UPDATE public.course_enrollments course_enrollment
SET status = 'cancelled'::public.enroll_status,
    updated_at = now()
FROM class_only_course_enrollments legacy
WHERE course_enrollment.id = legacy.id;

