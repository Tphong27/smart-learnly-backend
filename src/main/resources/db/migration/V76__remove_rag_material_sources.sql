-- RAG material ingestion is no longer part of AI question generation.
-- Keep pasted text, temporary file, and transcript sources intact.

DELETE FROM public.ai_question_generation_sources
WHERE source_kind = 'material';

ALTER TABLE public.ai_question_generation_evidences
    DROP CONSTRAINT IF EXISTS ai_question_generation_evidences_material_chunk_id_fkey,
    DROP COLUMN IF EXISTS material_chunk_id;

ALTER TABLE public.ai_question_generation_source_chunks
    DROP CONSTRAINT IF EXISTS ai_question_generation_source_chunks_material_chunk_id_fkey,
    DROP COLUMN IF EXISTS material_chunk_id;

ALTER TABLE public.ai_question_generation_sources
    DROP CONSTRAINT IF EXISTS ai_question_generation_sources_material_snapshot_id_fkey,
    DROP CONSTRAINT IF EXISTS chk_ai_question_generation_sources_material_mvp,
    DROP CONSTRAINT IF EXISTS chk_ai_question_generation_sources_material_required,
    DROP CONSTRAINT IF EXISTS chk_ai_question_generation_sources_kind,
    DROP COLUMN IF EXISTS material_id,
    DROP COLUMN IF EXISTS material_snapshot_id,
    DROP COLUMN IF EXISTS rag_status,
    ADD CONSTRAINT chk_ai_question_generation_sources_kind
        CHECK (source_kind IN ('pasted_text', 'temporary_file', 'transcript'));

DROP TABLE IF EXISTS public.rag_material_chunks;
DROP TABLE IF EXISTS public.rag_material_snapshots;
