-- The admin UI publishes a course and its curriculum as one action. Repair existing records where
-- the course is public but the latest MASTER curriculum was left in DRAFT.

WITH latest_draft AS (
    SELECT DISTINCT ON (version.course_id)
        version.id,
        version.course_id
    FROM public.curriculum_versions version
    JOIN public.courses course ON course.id = version.course_id
    WHERE version.scope = 'MASTER'
      AND course.status = 'published'::public.course_status
    ORDER BY version.course_id, version.version_number DESC, version.created_at DESC
),
draft_to_publish AS (
    SELECT latest.id, latest.course_id
    FROM latest_draft latest
    JOIN public.curriculum_versions version ON version.id = latest.id
    WHERE version.status = 'DRAFT'
)
UPDATE public.curriculum_versions published
SET status = 'ARCHIVED',
    archived_at = now(),
    updated_at = now()
FROM draft_to_publish latest
WHERE published.course_id = latest.course_id
  AND published.scope = 'MASTER'
  AND published.status = 'PUBLISHED'
  AND published.id <> latest.id;

WITH latest_draft AS (
    SELECT DISTINCT ON (version.course_id)
        version.id
    FROM public.curriculum_versions version
    JOIN public.courses course ON course.id = version.course_id
    WHERE version.scope = 'MASTER'
      AND version.status = 'DRAFT'
      AND course.status = 'published'::public.course_status
    ORDER BY version.course_id, version.version_number DESC, version.created_at DESC
)
UPDATE public.curriculum_versions version
SET status = 'PUBLISHED',
    published_at = COALESCE(version.published_at, now()),
    archived_at = NULL,
    updated_at = now()
FROM latest_draft latest
WHERE version.id = latest.id;
