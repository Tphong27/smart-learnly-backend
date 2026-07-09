-- V40: convert curriculum enum columns to varchar so Hibernate derived queries
-- (WHERE scope = ?) can bind plain strings without needing per-parameter casts.
-- Drop the CHECK constraints that reference the native enum types, alter the
-- columns to varchar, then re-add value guards as string CHECKs. Finally drop
-- the enum types (they are no longer referenced anywhere).

-- 1) curriculum_versions.scope / status
ALTER TABLE public.curriculum_versions
    DROP CONSTRAINT IF EXISTS chk_curriculum_versions_scope_class,
    DROP CONSTRAINT IF EXISTS chk_curriculum_versions_published_at,
    DROP CONSTRAINT IF EXISTS chk_curriculum_versions_archived_at;

DROP INDEX IF EXISTS public.uq_curriculum_versions_master_number;
DROP INDEX IF EXISTS public.uq_curriculum_versions_class_number;
DROP INDEX IF EXISTS public.uq_curriculum_versions_published_master;
DROP INDEX IF EXISTS public.uq_curriculum_versions_published_class;
DROP INDEX IF EXISTS public.uq_curriculum_versions_draft_class;
DROP INDEX IF EXISTS public.idx_curriculum_versions_course_scope_status;

ALTER TABLE public.curriculum_versions
    ALTER COLUMN scope TYPE varchar(32) USING scope::text,
    ALTER COLUMN status TYPE varchar(32) USING status::text;

ALTER TABLE public.curriculum_versions
    ALTER COLUMN status SET DEFAULT 'DRAFT';

ALTER TABLE public.curriculum_versions
    ADD CONSTRAINT chk_curriculum_versions_scope_values
        CHECK (scope IN ('MASTER', 'CLASS')),
    ADD CONSTRAINT chk_curriculum_versions_status_values
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    ADD CONSTRAINT chk_curriculum_versions_scope_class
        CHECK (
            (scope = 'MASTER' AND class_id IS NULL)
            OR (scope = 'CLASS' AND class_id IS NOT NULL)
        ),
    ADD CONSTRAINT chk_curriculum_versions_published_at
        CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),
    ADD CONSTRAINT chk_curriculum_versions_archived_at
        CHECK (status <> 'ARCHIVED' OR archived_at IS NOT NULL);

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_versions_master_number
    ON public.curriculum_versions (course_id, version_number)
    WHERE scope = 'MASTER';

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_versions_class_number
    ON public.curriculum_versions (class_id, version_number)
    WHERE scope = 'CLASS';

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_versions_published_master
    ON public.curriculum_versions (course_id)
    WHERE scope = 'MASTER' AND status = 'PUBLISHED';

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_versions_published_class
    ON public.curriculum_versions (class_id)
    WHERE scope = 'CLASS' AND status = 'PUBLISHED';

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_versions_draft_class
    ON public.curriculum_versions (class_id)
    WHERE scope = 'CLASS' AND status = 'DRAFT';

CREATE INDEX IF NOT EXISTS idx_curriculum_versions_course_scope_status
    ON public.curriculum_versions (course_id, scope, status);

-- 2) class_curriculum_bindings.customization_state
ALTER TABLE public.class_curriculum_bindings
    DROP CONSTRAINT IF EXISTS chk_class_curriculum_bindings_state;

ALTER TABLE public.class_curriculum_bindings
    ALTER COLUMN customization_state TYPE varchar(32) USING customization_state::text;

ALTER TABLE public.class_curriculum_bindings
    ALTER COLUMN customization_state SET DEFAULT 'INHERITED';

ALTER TABLE public.class_curriculum_bindings
    ADD CONSTRAINT chk_class_curriculum_bindings_customization_values
        CHECK (customization_state IN ('INHERITED', 'DRAFT', 'PUBLISHED')),
    ADD CONSTRAINT chk_class_curriculum_bindings_state
        CHECK (
            (customization_state = 'INHERITED'
                AND draft_version_id IS NULL
                AND published_version_id IS NULL)
            OR (customization_state = 'DRAFT'
                AND draft_version_id IS NOT NULL)
            OR (customization_state = 'PUBLISHED'
                AND draft_version_id IS NULL
                AND published_version_id IS NOT NULL)
        );

-- 3) drop the now-unused enum types (safe: no columns reference them anymore)
DROP TYPE IF EXISTS public.curriculum_scope;
DROP TYPE IF EXISTS public.curriculum_status;
DROP TYPE IF EXISTS public.curriculum_customization_state;
