-- Allow VIDEO media type in the question_media_attachments table
ALTER TABLE public.question_media_attachments
DROP CONSTRAINT IF EXISTS chk_question_media_type;

ALTER TABLE public.question_media_attachments
ADD CONSTRAINT chk_question_media_type
    CHECK (media_type IN ('IMAGE', 'AUDIO', 'VIDEO'));
