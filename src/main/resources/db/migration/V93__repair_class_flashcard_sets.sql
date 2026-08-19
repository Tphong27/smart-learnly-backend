-- Repair class-only flashcard lessons left behind when the former two-request
-- frontend flow created the lesson but failed before creating its flashcard set.
-- Legacy class versions can miss created_by; prefer trainer/course ownership before
-- falling back to one active staff account for the repair insert.
WITH fallback_actor AS (
    SELECT "user".id
    FROM public.users "user"
    WHERE "user".deleted_at IS NULL
      AND "user".status = 'active'::public.user_status
      AND "user".role IN (
          'ADMIN'::public.user_role,
          'TMO'::public.user_role,
          'SME'::public.user_role,
          'TRAINER'::public.user_role
      )
    ORDER BY
        CASE "user".role
            WHEN 'ADMIN'::public.user_role THEN 0
            WHEN 'TMO'::public.user_role THEN 1
            WHEN 'SME'::public.user_role THEN 2
            ELSE 3
        END,
        "user".created_at,
        "user".id
    LIMIT 1
),
repair_source AS (
    SELECT
        lesson.id AS curriculum_lesson_id,
        version.course_id,
        COALESCE(version.created_by, class_offering.trainer_id, course.creator_id, course.assigned_sme_id, fallback_actor.id) AS created_by,
        lesson.title
    FROM public.curriculum_lessons lesson
    JOIN public.curriculum_versions version
        ON version.id = lesson.curriculum_version_id
    JOIN public.courses course
        ON course.id = version.course_id
    LEFT JOIN public.classes class_offering
        ON class_offering.id = version.class_id
    JOIN public.class_curriculum_entries entry
        ON entry.materialized_lesson_id = lesson.id
       AND entry.source_curriculum_lesson_id IS NULL
       AND entry.deleted_at IS NULL
    LEFT JOIN fallback_actor ON true
    WHERE lesson.lesson_type = 'flashcard'::public.lesson_type
      AND lesson.deleted_at IS NULL
      AND version.scope = 'CLASS'
      AND NOT EXISTS (
          SELECT 1
          FROM public.flashcard_sets flashcard_set
          WHERE flashcard_set.curriculum_lesson_id = lesson.id
            AND flashcard_set.deleted_at IS NULL
      )
)
INSERT INTO public.flashcard_sets (
    curriculum_lesson_id,
    course_id,
    created_by,
    title,
    description,
    is_public,
    is_official
)
SELECT
    repair_source.curriculum_lesson_id,
    repair_source.course_id,
    repair_source.created_by,
    repair_source.title,
    NULL,
    false,
    false
FROM repair_source
WHERE repair_source.created_by IS NOT NULL;
