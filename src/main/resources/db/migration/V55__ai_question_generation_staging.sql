-- Sprint 5: AI Question Draft Generation staging + minimal RAG-ready source foundation.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_questions_import_source'
          AND conrelid = 'public.questions'::regclass
    ) THEN
        ALTER TABLE public.questions DROP CONSTRAINT chk_questions_import_source;
    END IF;

    ALTER TABLE public.questions
        ADD CONSTRAINT chk_questions_import_source
        CHECK (
            import_source IS NULL
            OR import_source IN ('manual', 'excel_import', 'json_import', 'image_import', 'ai_generation')
        );
END
$$;

DO $$
DECLARE
    duplicate_count integer;
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename = 'questions'
          AND indexname = 'uq_questions_bank_lower_text'
    ) THEN
        DROP INDEX public.uq_questions_bank_lower_text;
    END IF;

    SELECT COUNT(*) INTO duplicate_count
    FROM (
        SELECT
            question_bank_id,
            LOWER(question_text) AS normalized_text,
            COUNT(*) AS row_count
        FROM public.questions
        WHERE question_text IS NOT NULL
          AND status <> 'archived'::public.question_status
        GROUP BY question_bank_id, LOWER(question_text)
        HAVING COUNT(*) > 1
    ) duplicates;

    IF duplicate_count > 0 THEN
        RAISE NOTICE
            'ai_question_generation_staging: skipped active-only unique index because % duplicate active/non-archived group(s) exist in public.questions.',
            duplicate_count;
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uq_questions_bank_lower_text_active
            ON public.questions (
                question_bank_id,
                LOWER(question_text)
            )
            WHERE status <> 'archived'::public.question_status;
    END IF;
END
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename = 'questions'
          AND indexname = 'uq_questions_bank_lower_text_active'
    ) THEN
        COMMENT ON INDEX public.uq_questions_bank_lower_text_active
            IS 'Prevents exact duplicate active/non-archived question text within a question bank; archived duplicates are warning-only.';
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS public.rag_material_snapshots (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid NOT NULL REFERENCES public.courses(id) ON DELETE CASCADE,
    lesson_id uuid,
    curriculum_lesson_id uuid REFERENCES public.curriculum_lessons(id) ON DELETE SET NULL,
    lesson_resource_id uuid,
    curriculum_lesson_resource_id uuid REFERENCES public.curriculum_lesson_resources(id) ON DELETE SET NULL,
    source_name character varying(255) NOT NULL,
    checksum character varying(128) NOT NULL,
    version character varying(64) NOT NULL,
    status character varying(32) NOT NULL,
    extracted_at timestamp with time zone,
    chunked_at timestamp with time zone,
    error_message text,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_rag_material_snapshots_status
        CHECK (status IN ('pending', 'extracting', 'chunking', 'ready', 'failed'))
);

CREATE TABLE IF NOT EXISTS public.rag_material_chunks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id uuid NOT NULL REFERENCES public.rag_material_snapshots(id) ON DELETE CASCADE,
    chunk_index integer NOT NULL,
    chunk_reference character varying(255) NOT NULL,
    content_excerpt text NOT NULL,
    content_checksum character varying(128) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_rag_material_chunks_snapshot_index UNIQUE (snapshot_id, chunk_index),
    CONSTRAINT uq_rag_material_chunks_snapshot_ref UNIQUE (snapshot_id, chunk_reference),
    CONSTRAINT chk_rag_material_chunks_index_non_negative CHECK (chunk_index >= 0)
);

CREATE TABLE IF NOT EXISTS public.ai_question_generation_batches (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_bank_id uuid NOT NULL REFERENCES public.question_banks(id) ON DELETE CASCADE,
    course_id uuid NOT NULL REFERENCES public.courses(id) ON DELETE CASCADE,
    requested_by uuid NOT NULL REFERENCES public.users(id),
    status character varying(32) NOT NULL,
    generation_instruction text,
    instruction_snapshot text,
    requested_question_types text NOT NULL,
    requested_count integer NOT NULL,
    generated_count integer NOT NULL DEFAULT 0,
    usable_count integer NOT NULL DEFAULT 0,
    language character varying(8) NOT NULL,
    prompt_template_version character varying(64) NOT NULL,
    provider character varying(64) NOT NULL,
    model character varying(128) NOT NULL,
    idempotency_key character varying(120) NOT NULL,
    retry_count integer NOT NULL DEFAULT 0,
    quota_charged boolean NOT NULL DEFAULT false,
    error_code character varying(80),
    safe_error_message text,
    usage_prompt_tokens integer,
    usage_completion_tokens integer,
    usage_total_tokens integer,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    completed_at timestamp with time zone,
    CONSTRAINT chk_ai_question_generation_batches_status
        CHECK (status IN ('requested', 'processing', 'ready', 'failed')),
    CONSTRAINT chk_ai_question_generation_batches_count
        CHECK (requested_count IN (5, 10, 20)),
    CONSTRAINT chk_ai_question_generation_batches_language
        CHECK (language IN ('vi', 'en')),
    CONSTRAINT chk_ai_question_generation_batches_retry
        CHECK (retry_count >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_question_generation_batches_actor_idempotency
    ON public.ai_question_generation_batches (requested_by, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_ai_question_generation_batches_bank_status
    ON public.ai_question_generation_batches (question_bank_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_question_generation_batches_actor_created
    ON public.ai_question_generation_batches (requested_by, created_at DESC);

CREATE TABLE IF NOT EXISTS public.ai_question_generation_sources (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id uuid NOT NULL REFERENCES public.ai_question_generation_batches(id) ON DELETE CASCADE,
    source_kind character varying(32) NOT NULL,
    material_id uuid,
    material_snapshot_id uuid REFERENCES public.rag_material_snapshots(id) ON DELETE RESTRICT,
    source_payload_ref text,
    source_name character varying(255) NOT NULL,
    source_checksum character varying(128) NOT NULL,
    source_version character varying(64) NOT NULL,
    rag_status character varying(32) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_ai_question_generation_sources_kind
        CHECK (source_kind IN ('material', 'pasted_text', 'temporary_file', 'transcript')),
    CONSTRAINT chk_ai_question_generation_sources_material_mvp
        CHECK (
            source_kind <> 'material'
            OR material_snapshot_id IS NOT NULL
        )
);

CREATE INDEX IF NOT EXISTS idx_ai_question_generation_sources_batch
    ON public.ai_question_generation_sources (batch_id);

CREATE TABLE IF NOT EXISTS public.ai_question_generation_drafts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id uuid NOT NULL REFERENCES public.ai_question_generation_batches(id) ON DELETE CASCADE,
    status character varying(32) NOT NULL,
    validation_status character varying(32) NOT NULL,
    evidence_status character varying(32) NOT NULL,
    version integer NOT NULL DEFAULT 0,
    question_text text NOT NULL,
    question_type character varying(32) NOT NULL,
    explanation text,
    module_id uuid,
    answers_json jsonb NOT NULL,
    validation_warnings jsonb NOT NULL DEFAULT '[]'::jsonb,
    duplicate_candidates jsonb NOT NULL DEFAULT '[]'::jsonb,
    reviewed_by uuid REFERENCES public.users(id),
    reviewed_at timestamp with time zone,
    created_question_id uuid REFERENCES public.questions(id) ON DELETE SET NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_ai_question_generation_drafts_status
        CHECK (status IN ('generated_draft', 'accepted', 'rejected')),
    CONSTRAINT chk_ai_question_generation_drafts_validation
        CHECK (validation_status IN ('valid', 'warning', 'invalid')),
    CONSTRAINT chk_ai_question_generation_drafts_evidence
        CHECK (evidence_status IN ('valid', 'needs_review', 'invalid')),
    CONSTRAINT chk_ai_question_generation_drafts_type
        CHECK (question_type IN ('multiple_choice', 'true_false')),
    CONSTRAINT chk_ai_question_generation_drafts_version
        CHECK (version >= 0)
);

CREATE INDEX IF NOT EXISTS idx_ai_question_generation_drafts_batch_status
    ON public.ai_question_generation_drafts (batch_id, status, validation_status);

CREATE TABLE IF NOT EXISTS public.ai_question_generation_evidences (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    draft_id uuid NOT NULL REFERENCES public.ai_question_generation_drafts(id) ON DELETE CASCADE,
    generation_source_id uuid NOT NULL REFERENCES public.ai_question_generation_sources(id) ON DELETE CASCADE,
    material_chunk_id uuid REFERENCES public.rag_material_chunks(id) ON DELETE RESTRICT,
    chunk_reference character varying(255) NOT NULL,
    source_excerpt text NOT NULL,
    supports_correct_answer boolean NOT NULL DEFAULT false,
    evidence_status character varying(32) NOT NULL,
    reviewer_confirmed_by uuid REFERENCES public.users(id),
    reviewer_confirmed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_ai_question_generation_evidences_status
        CHECK (evidence_status IN ('valid', 'needs_review', 'invalid'))
);

CREATE INDEX IF NOT EXISTS idx_ai_question_generation_evidences_draft
    ON public.ai_question_generation_evidences (draft_id);

CREATE TABLE IF NOT EXISTS public.ai_question_generation_draft_revisions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    draft_id uuid NOT NULL REFERENCES public.ai_question_generation_drafts(id) ON DELETE CASCADE,
    changed_by uuid REFERENCES public.users(id),
    before_snapshot jsonb,
    after_snapshot jsonb,
    change_type character varying(32) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_ai_question_generation_draft_revisions_type
        CHECK (change_type IN ('generated', 'edited', 'accepted', 'rejected', 'evidence_confirmed'))
);

CREATE INDEX IF NOT EXISTS idx_ai_question_generation_draft_revisions_draft
    ON public.ai_question_generation_draft_revisions (draft_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.ai_question_generation_usage (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id uuid NOT NULL REFERENCES public.ai_question_generation_batches(id) ON DELETE CASCADE,
    provider character varying(64) NOT NULL,
    model character varying(128) NOT NULL,
    prompt_tokens integer,
    completion_tokens integer,
    total_tokens integer,
    recorded_at timestamp with time zone NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_question_generation_usage_batch
    ON public.ai_question_generation_usage (batch_id);

COMMENT ON TABLE public.rag_material_snapshots IS
    'Minimal Sprint 5 RAG-ready material snapshot foundation. A ready snapshot has checksum/version and stable chunks.';

COMMENT ON TABLE public.ai_question_generation_batches IS
    'AI question generation batch staging. Generated content must be human reviewed before creating official questions.';

COMMENT ON TABLE public.ai_question_generation_drafts IS
    'AI-generated question drafts, separate from public.questions until Add selected drafts succeeds.';
