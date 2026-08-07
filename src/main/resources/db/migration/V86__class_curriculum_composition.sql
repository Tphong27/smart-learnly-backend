-- V86: Class curriculum reference + delta composition.
--
-- Class-scoped curriculum versions no longer deep-clone the master tree. A class version
-- now holds a thin set of "entries" (class_curriculum_entries) that reference master lesson
-- rows. Content of an inherited lesson resolves against the current published master at read
-- time, so classes follow master content updates until the trainer edits the lesson or
-- attaches an artifact (flashcard/test/...) — which materializes a real curriculum_lessons
-- row (entry.materialized_lesson_id).
--
-- This migration is additive and idempotent: it creates the new table and converts existing
-- CLASS-scoped cloned versions to entries WITHOUT deleting any curriculum rows. Shadow
-- curriculum_lessons rows are left in place and cleaned up by a later migration.

CREATE TABLE IF NOT EXISTS public.class_curriculum_entries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_version_id uuid NOT NULL REFERENCES public.curriculum_versions(id) ON DELETE CASCADE,
    section_id uuid NOT NULL REFERENCES public.curriculum_sections(id) ON DELETE CASCADE,
    source_curriculum_lesson_id uuid,          -- master lesson row id; NULL = class-only lesson
    lesson_identity_id uuid NOT NULL,          -- stable logical identity across versions
    sort_order integer NOT NULL DEFAULT 0,
    hidden boolean NOT NULL DEFAULT false,
    materialized_lesson_id uuid REFERENCES public.curriculum_lessons(id) ON DELETE SET NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    deleted_at timestamp with time zone,
    CONSTRAINT chk_class_curriculum_entries_sort_non_negative CHECK (sort_order >= 0)
);

-- Non-unique: legacy class data may contain lessons with equal sort_order within a section
-- (the old curriculum_lessons model did not enforce uniqueness). The editor reorder flow
-- assigns sequential unique values, so uniqueness is enforced at write time.
CREATE INDEX IF NOT EXISTS idx_class_curriculum_entries_version_section_sort
    ON public.class_curriculum_entries (class_version_id, section_id, sort_order)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_class_curriculum_entries_version_source
    ON public.class_curriculum_entries (class_version_id, source_curriculum_lesson_id)
    WHERE source_curriculum_lesson_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_class_curriculum_entries_version_section
    ON public.class_curriculum_entries (class_version_id, section_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_class_curriculum_entries_source_lesson
    ON public.class_curriculum_entries (source_curriculum_lesson_id)
    WHERE source_curriculum_lesson_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_class_curriculum_entries_identity
    ON public.class_curriculum_entries (lesson_identity_id);

CREATE INDEX IF NOT EXISTS idx_class_curriculum_entries_materialized
    ON public.class_curriculum_entries (materialized_lesson_id)
    WHERE materialized_lesson_id IS NOT NULL;

-- Backfill: convert every existing CLASS-scoped cloned version to the composition model.
--
-- For each class lesson decide whether it is "materialized" (owns a real curriculum_lessons
-- row in the class version) or "inherited" (content resolves from the current published
-- master by lesson_identity_id). A lesson is materialized when:
--   - it carries a pinned artifact (test, flashcard set, staging batch, CLASS video AI,
--     assignment) that needs a real row, or
--   - no published-master twin exists for its identity (class-only lesson), or
--   - its content differs from the master twin (trainer edited it).
-- Otherwise it is inherited: the entry stores the master row id as source and
-- materialized_lesson_id stays NULL.
INSERT INTO public.class_curriculum_entries (
    class_version_id,
    section_id,
    source_curriculum_lesson_id,
    lesson_identity_id,
    sort_order,
    hidden,
    materialized_lesson_id,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    derived.version_id,
    derived.section_id,
    derived.master_lesson_id,
    derived.lesson_identity_id,
    derived.sort_order,
    false,
    CASE WHEN derived.materialize THEN derived.lesson_row_id ELSE NULL END,
    now(),
    now(),
    derived.deleted_at
FROM (
    SELECT
        cl.curriculum_version_id  AS version_id,
        cl.curriculum_section_id  AS section_id,
        cl.id                     AS lesson_row_id,
        cl.lesson_identity_id,
        cl.sort_order,
        cl.deleted_at,
        mt.id                     AS master_lesson_id,
        (
            cl.test_id IS NOT NULL
            OR EXISTS (
                SELECT 1 FROM public.flashcard_sets fs
                WHERE fs.curriculum_lesson_id = cl.id AND fs.deleted_at IS NULL
            )
            OR EXISTS (
                SELECT 1 FROM public.flashcard_staging_batches fsb
                WHERE fsb.curriculum_lesson_id = cl.id
            )
            OR EXISTS (
                SELECT 1 FROM public.video_ai_contents vac
                WHERE vac.lesson_id = cl.id AND vac.lesson_scope = 'CLASS'
            )
            OR EXISTS (
                SELECT 1 FROM public.assignments a
                WHERE a.lesson_id = cl.id
            )
            OR mt.id IS NULL
            OR NOT (
                cl.title              IS NOT DISTINCT FROM mt.title
                AND cl.lesson_type    IS NOT DISTINCT FROM mt.lesson_type
                AND cl.video_url      IS NOT DISTINCT FROM mt.video_url
                AND cl.content        IS NOT DISTINCT FROM mt.content
                AND cl.attachment_url IS NOT DISTINCT FROM mt.attachment_url
                AND cl.duration_seconds IS NOT DISTINCT FROM mt.duration_seconds
                AND cl.is_preview     IS NOT DISTINCT FROM mt.is_preview
                AND cl.status         IS NOT DISTINCT FROM mt.status
            )
        ) AS materialize
    FROM public.curriculum_lessons cl
    JOIN public.curriculum_versions cv ON cv.id = cl.curriculum_version_id
    LEFT JOIN LATERAL (
        SELECT mt.id, mt.title, mt.lesson_type, mt.video_url, mt.content,
               mt.attachment_url, mt.duration_seconds, mt.is_preview, mt.status
        FROM public.curriculum_versions mv
        JOIN public.curriculum_lessons mt
          ON mt.curriculum_version_id = mv.id
         AND mt.lesson_identity_id = cl.lesson_identity_id
         AND mt.deleted_at IS NULL
        WHERE mv.course_id = cv.course_id
          AND mv.scope = 'MASTER'
          AND mv.status = 'PUBLISHED'
        ORDER BY mv.version_number DESC, mv.created_at DESC
        LIMIT 1
    ) mt ON true
    WHERE cv.scope = 'CLASS'
) derived
WHERE NOT EXISTS (
    SELECT 1 FROM public.class_curriculum_entries existing
    WHERE existing.class_version_id = derived.version_id
      AND existing.section_id = derived.section_id
      AND existing.lesson_identity_id = derived.lesson_identity_id
);

-- Remap references that pointed at now-inherited shadow rows to the master lesson id.
-- Inherited lessons resolve by master id, so progress and assignments must point at the
-- master row (shadow rows are cleaned up later and carry no FK).
UPDATE public.lesson_progress lp
SET lesson_id = e.source_curriculum_lesson_id,
    lesson_identity_id = e.lesson_identity_id
FROM public.class_curriculum_entries e
JOIN public.curriculum_lessons cl
  ON cl.curriculum_version_id = e.class_version_id
 AND cl.curriculum_section_id = e.section_id
 AND cl.lesson_identity_id = e.lesson_identity_id
WHERE e.materialized_lesson_id IS NULL
  AND e.source_curriculum_lesson_id IS NOT NULL
  AND lp.lesson_id = cl.id;

UPDATE public.assignments a
SET lesson_id = e.source_curriculum_lesson_id
FROM public.class_curriculum_entries e
JOIN public.curriculum_lessons cl
  ON cl.curriculum_version_id = e.class_version_id
 AND cl.curriculum_section_id = e.section_id
 AND cl.lesson_identity_id = e.lesson_identity_id
WHERE e.materialized_lesson_id IS NULL
  AND e.source_curriculum_lesson_id IS NOT NULL
  AND a.lesson_id = cl.id;

-- Safety guard: no class-scoped curriculum_lesson that carries a pin or test may be left
-- un-materialized. Guarantees a later shadow-row cleanup cannot cascade-delete live
-- flashcards, staging batches, video AI rows or assignments.
DO $$
DECLARE
    bad_count integer;
BEGIN
    SELECT count(*) INTO bad_count
    FROM public.curriculum_lessons cl
    JOIN public.curriculum_versions cv ON cv.id = cl.curriculum_version_id
    WHERE cv.scope = 'CLASS'
      AND cl.deleted_at IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM public.class_curriculum_entries e
          WHERE e.class_version_id = cv.id
            AND e.section_id = cl.curriculum_section_id
            AND e.lesson_identity_id = cl.lesson_identity_id
            AND e.materialized_lesson_id = cl.id
      )
      AND (
          cl.test_id IS NOT NULL
          OR EXISTS (
              SELECT 1 FROM public.flashcard_sets fs
              WHERE fs.curriculum_lesson_id = cl.id AND fs.deleted_at IS NULL
          )
          OR EXISTS (
              SELECT 1 FROM public.flashcard_staging_batches fsb
              WHERE fsb.curriculum_lesson_id = cl.id
          )
          OR EXISTS (
              SELECT 1 FROM public.video_ai_contents vac
              WHERE vac.lesson_id = cl.id AND vac.lesson_scope = 'CLASS'
          )
          OR EXISTS (
              SELECT 1 FROM public.assignments a
              WHERE a.lesson_id = cl.id
          )
      );
    IF bad_count > 0 THEN
        RAISE EXCEPTION
            'Class-scoped curriculum_lesson has a pinned artifact but was not materialized (count=%)',
            bad_count;
    END IF;
END
$$;
