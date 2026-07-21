-- Follow-up to V53. Always select the latest MASTER version first, then promote it only when that
-- exact version is still a draft. Keeping this as a new migration preserves V53's Flyway checksum.

WITH latest_draft AS (
    SELECT DISTINCT ON (version.course_id)
        version.id,
        version.course_id,
        version.status
    FROM public.curriculum_versions version
    JOIN public.courses course ON course.id = version.course_id
    WHERE version.scope = 'MASTER'
      AND course.status = 'published'::public.course_status
    ORDER BY version.course_id, version.version_number DESC, version.created_at DESC
),
draft_to_publish AS (
    SELECT id, course_id
    FROM latest_draft
    WHERE status = 'DRAFT'
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
        version.id,
        version.status
    FROM public.curriculum_versions version
    JOIN public.courses course ON course.id = version.course_id
    WHERE version.scope = 'MASTER'
      AND course.status = 'published'::public.course_status
    ORDER BY version.course_id, version.version_number DESC, version.created_at DESC
)
UPDATE public.curriculum_versions version
SET status = 'PUBLISHED',
    published_at = COALESCE(version.published_at, now()),
    archived_at = NULL,
    updated_at = now()
FROM latest_draft latest
WHERE version.id = latest.id
  AND latest.status = 'DRAFT';
