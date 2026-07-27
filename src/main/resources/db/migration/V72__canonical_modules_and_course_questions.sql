-- Finalize the Course -> Module -> Lesson/Test/Question model.
-- This migration intentionally fails on ambiguous cross-course identity collisions.

ALTER TABLE public.modules
    ADD COLUMN IF NOT EXISTS is_system boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS system_key character varying(40);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_modules_system_identity'
          AND conrelid = 'public.modules'::regclass
    ) THEN
        ALTER TABLE public.modules
            ADD CONSTRAINT chk_modules_system_identity
            CHECK (
                (is_system = false AND system_key IS NULL)
                OR (is_system = true AND system_key IS NOT NULL)
            );
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_modules_course_system_key
    ON public.modules (course_id, system_key)
    WHERE system_key IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.course_sections legacy_module
        JOIN public.modules module ON module.id = legacy_module.id
        WHERE module.course_id <> legacy_module.course_id
    ) THEN
        RAISE EXCEPTION 'Cannot merge course_sections into modules: an ID belongs to different courses';
    END IF;
END
$$;

INSERT INTO public.modules (
    id,
    course_id,
    title,
    order_index,
    status,
    is_system,
    system_key,
    created_at,
    updated_at
)
SELECT
    legacy_module.id,
    legacy_module.course_id,
    legacy_module.title,
    legacy_module.sort_order,
    'active',
    false,
    NULL,
    legacy_module.created_at,
    legacy_module.updated_at
FROM public.course_sections legacy_module
ON CONFLICT (id) DO UPDATE
SET course_id = EXCLUDED.course_id,
    title = EXCLUDED.title,
    order_index = EXCLUDED.order_index,
    status = 'active',
    is_system = false,
    system_key = NULL,
    updated_at = GREATEST(public.modules.updated_at, EXCLUDED.updated_at);

ALTER TABLE public.curriculum_sections
    ADD COLUMN IF NOT EXISTS source_module_id uuid;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.curriculum_sections curriculum_module
        JOIN public.curriculum_versions version
          ON version.id = curriculum_module.curriculum_version_id
        JOIN public.modules module
          ON module.id = COALESCE(curriculum_module.source_section_id, curriculum_module.id)
        WHERE version.scope = 'MASTER'
          AND module.course_id <> version.course_id
    ) THEN
        RAISE EXCEPTION 'Cannot link master curriculum modules: an ID belongs to a different course';
    END IF;
END
$$;

INSERT INTO public.modules (
    id,
    course_id,
    title,
    order_index,
    status,
    is_system,
    system_key,
    created_at,
    updated_at
)
SELECT
    COALESCE(curriculum_module.source_section_id, curriculum_module.id),
    version.course_id,
    curriculum_module.title,
    curriculum_module.sort_order,
    'active',
    false,
    NULL,
    curriculum_module.created_at,
    curriculum_module.updated_at
FROM public.curriculum_sections curriculum_module
JOIN public.curriculum_versions version
  ON version.id = curriculum_module.curriculum_version_id
WHERE version.scope = 'MASTER'
ON CONFLICT (id) DO UPDATE
SET title = EXCLUDED.title,
    order_index = EXCLUDED.order_index,
    status = 'active',
    updated_at = GREATEST(public.modules.updated_at, EXCLUDED.updated_at);

UPDATE public.curriculum_sections curriculum_module
SET source_module_id = COALESCE(curriculum_module.source_section_id, curriculum_module.id)
FROM public.curriculum_versions version
WHERE version.id = curriculum_module.curriculum_version_id
  AND version.scope = 'MASTER'
  AND curriculum_module.source_module_id IS NULL;

DO $$
DECLARE
    updated_count integer;
BEGIN
    LOOP
        UPDATE public.curriculum_sections child
        SET source_module_id = parent.source_module_id
        FROM public.curriculum_sections parent
        WHERE child.source_module_id IS NULL
          AND child.source_curriculum_section_id = parent.id
          AND parent.source_module_id IS NOT NULL;

        GET DIAGNOSTICS updated_count = ROW_COUNT;
        EXIT WHEN updated_count = 0;
    END LOOP;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_modules_id_course'
          AND conrelid = 'public.modules'::regclass
    ) THEN
        ALTER TABLE public.modules
            ADD CONSTRAINT uq_modules_id_course UNIQUE (id, course_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'curriculum_sections_source_module_id_fkey'
          AND conrelid = 'public.curriculum_sections'::regclass
    ) THEN
        ALTER TABLE public.curriculum_sections
            ADD CONSTRAINT curriculum_sections_source_module_id_fkey
            FOREIGN KEY (source_module_id)
            REFERENCES public.modules(id)
            ON DELETE SET NULL;
    END IF;
END
$$;

DROP INDEX IF EXISTS public.uq_curriculum_sections_version_source_section;

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_sections_version_source_module
    ON public.curriculum_sections (curriculum_version_id, source_module_id)
    WHERE source_module_id IS NOT NULL;

UPDATE public.lessons
SET module_id = section_id
WHERE module_id IS DISTINCT FROM section_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.lessons lesson
        LEFT JOIN public.modules module
          ON module.id = lesson.module_id
         AND module.course_id = lesson.course_id
        WHERE module.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot finalize lessons.module_id: orphan or cross-course module detected';
    END IF;
END
$$;

ALTER TABLE public.lessons
    DROP CONSTRAINT IF EXISTS fk_lessons_section_course,
    DROP CONSTRAINT IF EXISTS lessons_module_id_fkey;

ALTER TABLE public.lessons
    ALTER COLUMN module_id SET NOT NULL;

ALTER TABLE public.lessons
    ADD CONSTRAINT fk_lessons_module_course
    FOREIGN KEY (module_id, course_id)
    REFERENCES public.modules(id, course_id)
    ON DELETE CASCADE;

DROP INDEX IF EXISTS public.idx_lessons_section_sort;

CREATE INDEX IF NOT EXISTS idx_lessons_module_sort
    ON public.lessons (module_id, sort_order);

UPDATE public.tests test
SET module_id = curriculum_module.source_module_id
FROM public.curriculum_sections curriculum_module
WHERE test.curriculum_section_id = curriculum_module.id
  AND curriculum_module.source_module_id IS NOT NULL
  AND test.module_id IS NULL;

-- The legacy question module FK targets curriculum_sections and must be removed
-- before stable module IDs (including Unassigned) can be backfilled.
ALTER TABLE public.questions
    DROP CONSTRAINT IF EXISTS questions_module_id_fkey,
    DROP CONSTRAINT IF EXISTS fk_questions_module_course,
    DROP CONSTRAINT IF EXISTS questions_question_bank_id_fkey;

UPDATE public.questions question
SET module_id = curriculum_module.source_module_id
FROM public.curriculum_sections curriculum_module
WHERE question.module_id = curriculum_module.id
  AND curriculum_module.source_module_id IS NOT NULL;

INSERT INTO public.modules (
    course_id,
    title,
    order_index,
    status,
    is_system,
    system_key
)
SELECT
    missing.course_id,
    'Unassigned',
    2147483647,
    'inactive',
    true,
    'unassigned'
FROM (
    SELECT question.course_id
    FROM public.questions question
    WHERE question.module_id IS NULL
    UNION
    SELECT batch.course_id
    FROM public.ai_question_generation_drafts draft
    JOIN public.ai_question_generation_batches batch ON batch.id = draft.batch_id
    WHERE draft.module_id IS NULL
) missing
ON CONFLICT (course_id, system_key) WHERE system_key IS NOT NULL DO NOTHING;

UPDATE public.questions question
SET module_id = module.id
FROM public.modules module
WHERE question.module_id IS NULL
  AND module.course_id = question.course_id
  AND module.system_key = 'unassigned';

UPDATE public.ai_question_generation_drafts draft
SET module_id = curriculum_module.source_module_id
FROM public.curriculum_sections curriculum_module
WHERE draft.module_id = curriculum_module.id
  AND curriculum_module.source_module_id IS NOT NULL;

UPDATE public.ai_question_generation_drafts draft
SET module_id = module.id
FROM public.ai_question_generation_batches batch,
     public.modules module
WHERE batch.id = draft.batch_id
  AND draft.module_id IS NULL
  AND module.course_id = batch.course_id
  AND module.system_key = 'unassigned';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.questions question
        LEFT JOIN public.modules module
          ON module.id = question.module_id
         AND module.course_id = question.course_id
        WHERE module.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot finalize questions.module_id: orphan or cross-course module detected';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.ai_question_generation_drafts draft
        JOIN public.ai_question_generation_batches batch ON batch.id = draft.batch_id
        LEFT JOIN public.modules module
          ON module.id = draft.module_id
         AND module.course_id = batch.course_id
        WHERE module.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot finalize AI drafts.module_id: orphan or cross-course module detected';
    END IF;
END
$$;

ALTER TABLE public.questions
    ALTER COLUMN module_id SET NOT NULL;

ALTER TABLE public.questions
    ADD CONSTRAINT fk_questions_module_course
    FOREIGN KEY (module_id, course_id)
    REFERENCES public.modules(id, course_id);

ALTER TABLE public.ai_question_generation_drafts
    ALTER COLUMN module_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ai_question_generation_drafts_module_id_fkey'
          AND conrelid = 'public.ai_question_generation_drafts'::regclass
    ) THEN
        ALTER TABLE public.ai_question_generation_drafts
            ADD CONSTRAINT ai_question_generation_drafts_module_id_fkey
            FOREIGN KEY (module_id)
            REFERENCES public.modules(id);
    END IF;
END
$$;

ALTER TABLE public.ai_question_generation_batches
    DROP CONSTRAINT IF EXISTS ai_question_generation_batches_question_bank_id_fkey;

DROP INDEX IF EXISTS public.idx_ai_question_generation_batches_bank_status;
DROP INDEX IF EXISTS public.idx_ai_question_generation_batches_bank;
DROP INDEX IF EXISTS public.idx_question_banks_course_status;
DROP INDEX IF EXISTS public.idx_question_banks_updated_at;

ALTER TABLE public.questions
    DROP COLUMN IF EXISTS question_bank_id;

ALTER TABLE public.ai_question_generation_batches
    DROP COLUMN IF EXISTS question_bank_id;

ALTER TABLE public.curriculum_sections
    DROP CONSTRAINT IF EXISTS curriculum_sections_source_section_id_fkey,
    DROP COLUMN IF EXISTS source_section_id;

ALTER TABLE public.lessons
    DROP COLUMN IF EXISTS section_id;

DROP TABLE public.question_banks;
DROP TABLE public.course_sections;

CREATE INDEX IF NOT EXISTS idx_questions_course_module
    ON public.questions (course_id, module_id);

CREATE INDEX IF NOT EXISTS idx_ai_question_generation_drafts_module
    ON public.ai_question_generation_drafts (module_id);

COMMENT ON TABLE public.modules IS
    'Canonical course module identities. Curriculum versions reference these identities through source_module_id.';

COMMENT ON COLUMN public.modules.system_key IS
    'Reserved identity for migration-only modules such as unassigned; system modules are never valid for new content.';

COMMENT ON COLUMN public.curriculum_sections.source_module_id IS
    'Stable course module identity represented by this curriculum-version snapshot.';
