-- Question Bank thuộc phạm vi khóa học; giữ module_id cũ để bảo toàn lịch sử,
-- nhưng câu hỏi và AI draft mới không còn bắt buộc thuộc một module.
ALTER TABLE public.questions
    ALTER COLUMN module_id DROP NOT NULL;

ALTER TABLE public.ai_question_generation_drafts
    ALTER COLUMN module_id DROP NOT NULL;
