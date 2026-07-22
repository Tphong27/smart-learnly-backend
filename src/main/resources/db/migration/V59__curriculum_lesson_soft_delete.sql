-- Separate an intentionally inactive lesson from a lesson removed through the authoring UI.
-- Hibernate filters rows with deleted_at set, while retaining related HLS/AI records for audit
-- and safe storage cleanup.

ALTER TABLE public.curriculum_lessons
    ADD COLUMN IF NOT EXISTS deleted_at timestamp with time zone;

-- Before deleted_at existed, the delete endpoint marked a lesson INACTIVE and recorded this audit
-- action. Backfill only those rows so deliberately inactive lessons remain visible to authors.
UPDATE public.curriculum_lessons lesson
SET deleted_at = deleted.occurred_at
FROM (
    SELECT target_id, max(occurred_at) AS occurred_at
    FROM public.audit_logs
    WHERE action = 'LESSON_DEACTIVATED'
      AND target_type = 'CURRICULUM_LESSON'
      AND target_id IS NOT NULL
    GROUP BY target_id
) deleted
WHERE lesson.id::text = deleted.target_id
  AND lesson.status = 'inactive'::public.lesson_status;

CREATE INDEX IF NOT EXISTS idx_curriculum_lessons_active_section_order
    ON public.curriculum_lessons (curriculum_section_id, sort_order, created_at)
    WHERE deleted_at IS NULL;
