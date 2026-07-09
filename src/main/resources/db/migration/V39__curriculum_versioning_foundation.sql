DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public' AND type.typname = 'curriculum_scope'
    ) THEN
        CREATE TYPE public.curriculum_scope AS ENUM ('MASTER', 'CLASS');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public' AND type.typname = 'curriculum_status'
    ) THEN
        CREATE TYPE public.curriculum_status AS ENUM ('DRAFT', 'PUBLISHED', 'ARCHIVED');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public' AND type.typname = 'curriculum_customization_state'
    ) THEN
        CREATE TYPE public.curriculum_customization_state AS ENUM ('INHERITED', 'DRAFT', 'PUBLISHED');
    END IF;
END
$$;

ALTER TABLE public.lessons
    ADD COLUMN IF NOT EXISTS lesson_identity_id uuid;

UPDATE public.lessons
SET lesson_identity_id = id
WHERE lesson_identity_id IS NULL;

ALTER TABLE public.lessons
    ALTER COLUMN lesson_identity_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_lessons_lesson_identity_id
    ON public.lessons (lesson_identity_id);

CREATE INDEX IF NOT EXISTS idx_lessons_course_lesson_identity
    ON public.lessons (course_id, lesson_identity_id);

CREATE TABLE IF NOT EXISTS public.curriculum_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid NOT NULL REFERENCES public.courses(id) ON DELETE CASCADE,
    class_id uuid,
    scope public.curriculum_scope NOT NULL,
    status public.curriculum_status NOT NULL DEFAULT 'DRAFT',
    version_number integer NOT NULL DEFAULT 1,
    title character varying(255),
    source_version_id uuid REFERENCES public.curriculum_versions(id) ON DELETE SET NULL,
    created_by uuid REFERENCES public.users(id),
    published_at timestamp with time zone,
    archived_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_curriculum_versions_scope_class CHECK (
        (scope = 'MASTER'::public.curriculum_scope AND class_id IS NULL)
        OR (scope = 'CLASS'::public.curriculum_scope AND class_id IS NOT NULL)
    ),
    CONSTRAINT chk_curriculum_versions_version_number_positive CHECK (version_number > 0),
    CONSTRAINT chk_curriculum_versions_published_at CHECK (
        status <> 'PUBLISHED'::public.curriculum_status OR published_at IS NOT NULL
    ),
    CONSTRAINT chk_curriculum_versions_archived_at CHECK (
        status <> 'ARCHIVED'::public.curriculum_status OR archived_at IS NOT NULL
    )
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_curriculum_versions_class_course'
          AND conrelid = 'public.curriculum_versions'::regclass
    ) THEN
        ALTER TABLE public.curriculum_versions
            ADD CONSTRAINT fk_curriculum_versions_class_course
            FOREIGN KEY (class_id, course_id)
            REFERENCES public.classes(id, course_id)
            ON DELETE CASCADE;
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_versions_master_number
    ON public.curriculum_versions (course_id, version_number)
    WHERE scope = 'MASTER'::public.curriculum_scope;

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_versions_class_number
    ON public.curriculum_versions (class_id, version_number)
    WHERE scope = 'CLASS'::public.curriculum_scope;

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_versions_published_master
    ON public.curriculum_versions (course_id)
    WHERE scope = 'MASTER'::public.curriculum_scope
      AND status = 'PUBLISHED'::public.curriculum_status;

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_versions_published_class
    ON public.curriculum_versions (class_id)
    WHERE scope = 'CLASS'::public.curriculum_scope
      AND status = 'PUBLISHED'::public.curriculum_status;

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_versions_draft_class
    ON public.curriculum_versions (class_id)
    WHERE scope = 'CLASS'::public.curriculum_scope
      AND status = 'DRAFT'::public.curriculum_status;

CREATE INDEX IF NOT EXISTS idx_curriculum_versions_course_scope_status
    ON public.curriculum_versions (course_id, scope, status);

CREATE INDEX IF NOT EXISTS idx_curriculum_versions_class_status
    ON public.curriculum_versions (class_id, status)
    WHERE class_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.curriculum_sections (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    curriculum_version_id uuid NOT NULL REFERENCES public.curriculum_versions(id) ON DELETE CASCADE,
    source_section_id uuid REFERENCES public.course_sections(id) ON DELETE SET NULL,
    source_curriculum_section_id uuid REFERENCES public.curriculum_sections(id) ON DELETE SET NULL,
    title character varying(255) NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_curriculum_sections_id_version UNIQUE (id, curriculum_version_id),
    CONSTRAINT chk_curriculum_sections_sort_order_non_negative CHECK (sort_order >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_sections_version_source_section
    ON public.curriculum_sections (curriculum_version_id, source_section_id)
    WHERE source_section_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_curriculum_sections_version_sort
    ON public.curriculum_sections (curriculum_version_id, sort_order);

CREATE TABLE IF NOT EXISTS public.curriculum_lessons (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    curriculum_version_id uuid NOT NULL REFERENCES public.curriculum_versions(id) ON DELETE CASCADE,
    curriculum_section_id uuid NOT NULL,
    lesson_identity_id uuid NOT NULL,
    source_lesson_id uuid REFERENCES public.lessons(id) ON DELETE SET NULL,
    source_curriculum_lesson_id uuid REFERENCES public.curriculum_lessons(id) ON DELETE SET NULL,
    title character varying(255) NOT NULL,
    lesson_type public.lesson_type NOT NULL,
    video_url character varying(500),
    content text,
    attachment_url character varying(500),
    duration_seconds integer,
    is_preview boolean NOT NULL DEFAULT false,
    status public.lesson_status NOT NULL DEFAULT 'draft',
    sort_order integer NOT NULL DEFAULT 0,
    test_id uuid REFERENCES public.tests(id) ON DELETE SET NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_curriculum_lessons_section_version
        FOREIGN KEY (curriculum_section_id, curriculum_version_id)
        REFERENCES public.curriculum_sections(id, curriculum_version_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_curriculum_lessons_duration_non_negative CHECK (
        duration_seconds IS NULL OR duration_seconds >= 0
    ),
    CONSTRAINT chk_curriculum_lessons_sort_order_non_negative CHECK (sort_order >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_lessons_version_identity
    ON public.curriculum_lessons (curriculum_version_id, lesson_identity_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_lessons_version_source_lesson
    ON public.curriculum_lessons (curriculum_version_id, source_lesson_id)
    WHERE source_lesson_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_curriculum_lessons_section_sort
    ON public.curriculum_lessons (curriculum_section_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_curriculum_lessons_version_status
    ON public.curriculum_lessons (curriculum_version_id, status);

CREATE INDEX IF NOT EXISTS idx_curriculum_lessons_identity
    ON public.curriculum_lessons (lesson_identity_id);

CREATE TABLE IF NOT EXISTS public.curriculum_lesson_resources (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    curriculum_lesson_id uuid NOT NULL REFERENCES public.curriculum_lessons(id) ON DELETE CASCADE,
    source_resource_id uuid REFERENCES public.lesson_resources(id) ON DELETE SET NULL,
    source_curriculum_resource_id uuid REFERENCES public.curriculum_lesson_resources(id) ON DELETE SET NULL,
    resource_url character varying(1000) NOT NULL,
    object_path character varying(1000),
    file_name character varying(255) NOT NULL,
    file_size bigint,
    content_type character varying(255),
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_curriculum_lesson_resources_file_size_non_negative CHECK (
        file_size IS NULL OR file_size >= 0
    ),
    CONSTRAINT chk_curriculum_lesson_resources_sort_order_non_negative CHECK (sort_order >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_curriculum_lesson_resources_lesson_source
    ON public.curriculum_lesson_resources (curriculum_lesson_id, source_resource_id)
    WHERE source_resource_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_curriculum_lesson_resources_lesson_sort
    ON public.curriculum_lesson_resources (curriculum_lesson_id, sort_order);

CREATE TABLE IF NOT EXISTS public.class_curriculum_bindings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id uuid NOT NULL,
    course_id uuid NOT NULL,
    base_master_version_id uuid NOT NULL REFERENCES public.curriculum_versions(id),
    draft_version_id uuid REFERENCES public.curriculum_versions(id),
    published_version_id uuid REFERENCES public.curriculum_versions(id),
    customization_state public.curriculum_customization_state NOT NULL DEFAULT 'INHERITED',
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_class_curriculum_bindings_class UNIQUE (class_id),
    CONSTRAINT fk_class_curriculum_bindings_class_course
        FOREIGN KEY (class_id, course_id)
        REFERENCES public.classes(id, course_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_class_curriculum_bindings_state CHECK (
        (customization_state = 'INHERITED'::public.curriculum_customization_state
            AND draft_version_id IS NULL
            AND published_version_id IS NULL)
        OR (customization_state = 'DRAFT'::public.curriculum_customization_state
            AND draft_version_id IS NOT NULL)
        OR (customization_state = 'PUBLISHED'::public.curriculum_customization_state
            AND draft_version_id IS NULL
            AND published_version_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_class_curriculum_bindings_course
    ON public.class_curriculum_bindings (course_id);

CREATE INDEX IF NOT EXISTS idx_class_curriculum_bindings_base_master
    ON public.class_curriculum_bindings (base_master_version_id);

CREATE INDEX IF NOT EXISTS idx_class_curriculum_bindings_draft
    ON public.class_curriculum_bindings (draft_version_id)
    WHERE draft_version_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_class_curriculum_bindings_published
    ON public.class_curriculum_bindings (published_version_id)
    WHERE published_version_id IS NOT NULL;

INSERT INTO public.curriculum_versions (
    course_id,
    scope,
    status,
    version_number,
    title,
    published_at
)
SELECT
    course.id,
    'MASTER'::public.curriculum_scope,
    'PUBLISHED'::public.curriculum_status,
    1,
    concat(course.title, ' - Master Curriculum'),
    now()
FROM public.courses course
WHERE NOT EXISTS (
    SELECT 1
    FROM public.curriculum_versions existing_version
    WHERE existing_version.course_id = course.id
      AND existing_version.scope = 'MASTER'::public.curriculum_scope
      AND existing_version.status = 'PUBLISHED'::public.curriculum_status
)
ON CONFLICT DO NOTHING;

WITH master_versions AS (
    SELECT DISTINCT ON (course_id)
        id,
        course_id
    FROM public.curriculum_versions
    WHERE scope = 'MASTER'::public.curriculum_scope
      AND status = 'PUBLISHED'::public.curriculum_status
    ORDER BY course_id, version_number DESC, created_at DESC
)
INSERT INTO public.curriculum_sections (
    curriculum_version_id,
    source_section_id,
    title,
    sort_order,
    created_at,
    updated_at
)
SELECT
    master_version.id,
    section.id,
    section.title,
    section.sort_order,
    section.created_at,
    section.updated_at
FROM public.course_sections section
JOIN master_versions master_version ON master_version.course_id = section.course_id
ON CONFLICT DO NOTHING;

WITH master_versions AS (
    SELECT DISTINCT ON (course_id)
        id,
        course_id
    FROM public.curriculum_versions
    WHERE scope = 'MASTER'::public.curriculum_scope
      AND status = 'PUBLISHED'::public.curriculum_status
    ORDER BY course_id, version_number DESC, created_at DESC
)
INSERT INTO public.curriculum_lessons (
    curriculum_version_id,
    curriculum_section_id,
    lesson_identity_id,
    source_lesson_id,
    title,
    lesson_type,
    video_url,
    content,
    attachment_url,
    duration_seconds,
    is_preview,
    status,
    sort_order,
    test_id,
    created_at,
    updated_at
)
SELECT
    master_version.id,
    curriculum_section.id,
    lesson.lesson_identity_id,
    lesson.id,
    lesson.title,
    lesson.lesson_type,
    lesson.video_url,
    lesson.content,
    lesson.attachment_url,
    lesson.duration_seconds,
    lesson.is_preview,
    lesson.status,
    lesson.sort_order,
    lesson.test_id,
    lesson.created_at,
    lesson.updated_at
FROM public.lessons lesson
JOIN master_versions master_version ON master_version.course_id = lesson.course_id
JOIN public.curriculum_sections curriculum_section
  ON curriculum_section.curriculum_version_id = master_version.id
 AND curriculum_section.source_section_id = lesson.section_id
ON CONFLICT DO NOTHING;

WITH master_versions AS (
    SELECT DISTINCT ON (course_id)
        id,
        course_id
    FROM public.curriculum_versions
    WHERE scope = 'MASTER'::public.curriculum_scope
      AND status = 'PUBLISHED'::public.curriculum_status
    ORDER BY course_id, version_number DESC, created_at DESC
)
INSERT INTO public.curriculum_lesson_resources (
    curriculum_lesson_id,
    source_resource_id,
    resource_url,
    object_path,
    file_name,
    file_size,
    content_type,
    sort_order,
    created_at,
    updated_at
)
SELECT
    curriculum_lesson.id,
    resource.id,
    resource.resource_url,
    resource.object_path,
    resource.file_name,
    resource.file_size,
    resource.content_type,
    resource.sort_order,
    resource.created_at,
    resource.updated_at
FROM public.lesson_resources resource
JOIN public.lessons lesson ON lesson.id = resource.lesson_id
JOIN master_versions master_version ON master_version.course_id = lesson.course_id
JOIN public.curriculum_lessons curriculum_lesson
  ON curriculum_lesson.curriculum_version_id = master_version.id
 AND curriculum_lesson.source_lesson_id = lesson.id
ON CONFLICT DO NOTHING;

INSERT INTO public.class_curriculum_bindings (
    class_id,
    course_id,
    base_master_version_id,
    customization_state
)
SELECT
    cls.id,
    cls.course_id,
    master_version.id,
    'INHERITED'::public.curriculum_customization_state
FROM public.classes cls
JOIN public.curriculum_versions master_version
  ON master_version.course_id = cls.course_id
 AND master_version.scope = 'MASTER'::public.curriculum_scope
 AND master_version.status = 'PUBLISHED'::public.curriculum_status
ON CONFLICT (class_id) DO NOTHING;

ALTER TABLE public.lesson_progress
    ADD COLUMN IF NOT EXISTS class_id uuid,
    ADD COLUMN IF NOT EXISTS lesson_identity_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'lesson_progress_class_id_fkey'
          AND conrelid = 'public.lesson_progress'::regclass
    ) THEN
        ALTER TABLE public.lesson_progress
            ADD CONSTRAINT lesson_progress_class_id_fkey
            FOREIGN KEY (class_id)
            REFERENCES public.classes(id)
            ON DELETE SET NULL;
    END IF;
END
$$;

UPDATE public.lesson_progress progress
SET lesson_identity_id = lesson.lesson_identity_id
FROM public.lessons lesson
WHERE progress.lesson_id = lesson.id
  AND progress.lesson_identity_id IS NULL;

WITH candidate_classes AS (
    SELECT DISTINCT
        enrollment.student_id,
        cls.course_id,
        cls.id AS class_id
    FROM public.class_enrollments enrollment
    JOIN public.classes cls ON cls.id = enrollment.class_id
    WHERE enrollment.status IN ('active'::public.enroll_status, 'completed'::public.enroll_status)
      AND cls.deleted_at IS NULL
),
unambiguous_classes AS (
    SELECT
        student_id,
        course_id,
        min(class_id) AS class_id,
        count(*) AS class_count
    FROM candidate_classes
    GROUP BY student_id, course_id
)
UPDATE public.lesson_progress progress
SET class_id = unambiguous_class.class_id
FROM unambiguous_classes unambiguous_class
WHERE progress.student_id = unambiguous_class.student_id
  AND progress.course_id = unambiguous_class.course_id
  AND progress.class_id IS NULL
  AND unambiguous_class.class_count = 1;

CREATE INDEX IF NOT EXISTS idx_lesson_progress_student_class
    ON public.lesson_progress (student_id, class_id)
    WHERE class_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lesson_progress_lesson_identity
    ON public.lesson_progress (lesson_identity_id)
    WHERE lesson_identity_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lesson_progress_class_identity
    ON public.lesson_progress (class_id, lesson_identity_id)
    WHERE class_id IS NOT NULL
      AND lesson_identity_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_lesson_progress_student_class_identity
    ON public.lesson_progress (student_id, class_id, lesson_identity_id)
    WHERE class_id IS NOT NULL
      AND lesson_identity_id IS NOT NULL;

ALTER TABLE public.curriculum_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.curriculum_sections ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.curriculum_lessons ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.curriculum_lesson_resources ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.class_curriculum_bindings ENABLE ROW LEVEL SECURITY;

COMMENT ON COLUMN public.lessons.lesson_identity_id IS
    'Stable logical lesson identity preserved when lessons are copied into curriculum versions.';

COMMENT ON TABLE public.curriculum_versions IS
    'Versioned master and class-scoped curriculum snapshots separate from course catalog metadata.';

COMMENT ON TABLE public.class_curriculum_bindings IS
    'Per-class curriculum resolution state: inherited master, active draft, or class-published version.';

COMMENT ON TABLE public.curriculum_sections IS
    'Ordered section snapshots owned by a curriculum version.';

COMMENT ON TABLE public.curriculum_lessons IS
    'Ordered lesson snapshots owned by a curriculum version with stable logical lesson identity.';

COMMENT ON TABLE public.curriculum_lesson_resources IS
    'Resource snapshots attached to curriculum lessons.';

COMMENT ON COLUMN public.lesson_progress.class_id IS
    'Optional class context for lesson progress; backfilled only where class enrollment was unambiguous.';

COMMENT ON COLUMN public.lesson_progress.lesson_identity_id IS
    'Logical lesson identity used for class-aware progress across curriculum row copies.';
