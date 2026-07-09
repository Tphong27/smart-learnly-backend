-- V42: Cho phép hls_lessons.lesson_id tham chiếu tới CẢ `lessons` (legacy master) lẫn
-- `curriculum_lessons` (versioned) — trainer edit CurriculumLesson và cần trigger HLS pipeline
-- trên lesson.id thuộc bảng đó. FK cứng vào `lessons` chặn insert.
-- Đổi sang: bỏ FK, giữ PRIMARY KEY (lesson_id UUID). Ownership + tính hợp lệ được service tự kiểm.

ALTER TABLE public.hls_lessons
    DROP CONSTRAINT IF EXISTS hls_lessons_lesson_id_fkey;
