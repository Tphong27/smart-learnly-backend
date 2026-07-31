ALTER TABLE public.flashcard_staging_batches
    DROP CONSTRAINT IF EXISTS chk_flashcard_staging_batches_source_type;

ALTER TABLE public.flashcard_staging_batches
    ADD CONSTRAINT chk_flashcard_staging_batches_source_type
    CHECK (
        source_type IN (
            'QUESTION_BANK',
            'COURSE_QUESTIONS',
            'TEXT',
            'DOCX',
            'PDF',
            'VIDEO_TRANSCRIPT',
            'AI'
        )
    );
