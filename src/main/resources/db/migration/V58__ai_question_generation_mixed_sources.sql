-- Sprint 5: mixed AI question generation sources, audit storage metadata, and backend-owned RLS hardening.

ALTER TABLE public.ai_question_generation_sources
    ADD COLUMN IF NOT EXISTS mime_type character varying(128),
    ADD COLUMN IF NOT EXISTS file_size_bytes bigint,
    ADD COLUMN IF NOT EXISTS normalized_char_count integer,
    ADD COLUMN IF NOT EXISTS transcript_content_id uuid REFERENCES public.video_ai_contents(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS lesson_id uuid,
    ADD COLUMN IF NOT EXISTS downloadable boolean NOT NULL DEFAULT false;

ALTER TABLE public.ai_question_generation_sources
    DROP CONSTRAINT IF EXISTS chk_ai_question_generation_sources_material_mvp,
    ADD CONSTRAINT chk_ai_question_generation_sources_material_required
        CHECK (source_kind <> 'material' OR material_snapshot_id IS NOT NULL),
    ADD CONSTRAINT chk_ai_question_generation_sources_file_required
        CHECK (source_kind <> 'temporary_file' OR source_payload_ref IS NOT NULL),
    ADD CONSTRAINT chk_ai_question_generation_sources_transcript_required
        CHECK (source_kind <> 'transcript' OR transcript_content_id IS NOT NULL),
    ADD CONSTRAINT chk_ai_question_generation_sources_chars_non_negative
        CHECK (normalized_char_count IS NULL OR normalized_char_count >= 0),
    ADD CONSTRAINT chk_ai_question_generation_sources_file_size_non_negative
        CHECK (file_size_bytes IS NULL OR file_size_bytes >= 0);

CREATE TABLE IF NOT EXISTS public.ai_question_generation_source_chunks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    generation_source_id uuid NOT NULL REFERENCES public.ai_question_generation_sources(id) ON DELETE CASCADE,
    material_chunk_id uuid REFERENCES public.rag_material_chunks(id) ON DELETE RESTRICT,
    chunk_index integer NOT NULL,
    chunk_reference character varying(255) NOT NULL,
    content_excerpt text NOT NULL,
    content_checksum character varying(128) NOT NULL,
    start_ms bigint,
    end_ms bigint,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_question_generation_source_chunks_source_index UNIQUE (generation_source_id, chunk_index),
    CONSTRAINT uq_ai_question_generation_source_chunks_source_ref UNIQUE (generation_source_id, chunk_reference),
    CONSTRAINT chk_ai_question_generation_source_chunks_index_non_negative CHECK (chunk_index >= 0),
    CONSTRAINT chk_ai_question_generation_source_chunks_time_order CHECK (
        start_ms IS NULL OR end_ms IS NULL OR end_ms >= start_ms
    )
);

CREATE INDEX IF NOT EXISTS idx_ai_question_generation_source_chunks_source
    ON public.ai_question_generation_source_chunks (generation_source_id, chunk_index);

ALTER TABLE public.ai_question_generation_evidences
    ADD COLUMN IF NOT EXISTS source_chunk_id uuid REFERENCES public.ai_question_generation_source_chunks(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS start_ms bigint,
    ADD COLUMN IF NOT EXISTS end_ms bigint;

CREATE INDEX IF NOT EXISTS idx_ai_question_generation_evidences_source_chunk
    ON public.ai_question_generation_evidences (source_chunk_id);

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'ai-question-source-files',
    'ai-question-source-files',
    false,
    26214400,
    ARRAY[
        'application/pdf',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'text/plain'
    ]
)
ON CONFLICT (id) DO UPDATE
SET public = false,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

ALTER TABLE public.question_media_attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.question_answer_media_attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.video_ai_contents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.video_ai_transcript_segments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.video_ai_chapters ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.video_ai_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rag_material_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rag_material_chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_question_generation_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_question_generation_sources ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_question_generation_source_chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_question_generation_drafts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_question_generation_evidences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_question_generation_draft_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_question_generation_usage ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.learner_video_ai_artifacts ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.question_media_attachments FROM anon, authenticated;
REVOKE ALL ON TABLE public.question_answer_media_attachments FROM anon, authenticated;
REVOKE ALL ON TABLE public.video_ai_contents FROM anon, authenticated;
REVOKE ALL ON TABLE public.video_ai_transcript_segments FROM anon, authenticated;
REVOKE ALL ON TABLE public.video_ai_chapters FROM anon, authenticated;
REVOKE ALL ON TABLE public.video_ai_jobs FROM anon, authenticated;
REVOKE ALL ON TABLE public.rag_material_snapshots FROM anon, authenticated;
REVOKE ALL ON TABLE public.rag_material_chunks FROM anon, authenticated;
REVOKE ALL ON TABLE public.ai_question_generation_batches FROM anon, authenticated;
REVOKE ALL ON TABLE public.ai_question_generation_sources FROM anon, authenticated;
REVOKE ALL ON TABLE public.ai_question_generation_source_chunks FROM anon, authenticated;
REVOKE ALL ON TABLE public.ai_question_generation_drafts FROM anon, authenticated;
REVOKE ALL ON TABLE public.ai_question_generation_evidences FROM anon, authenticated;
REVOKE ALL ON TABLE public.ai_question_generation_draft_revisions FROM anon, authenticated;
REVOKE ALL ON TABLE public.ai_question_generation_usage FROM anon, authenticated;
REVOKE ALL ON TABLE public.learner_video_ai_artifacts FROM anon, authenticated;

COMMENT ON TABLE public.ai_question_generation_source_chunks IS
    'Immutable per-batch AI source chunks. Retry and evidence read these snapshots rather than mutable upstream material/transcript sources.';
