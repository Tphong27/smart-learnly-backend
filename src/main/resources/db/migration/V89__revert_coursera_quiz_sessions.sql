-- V88 has already been applied in the database. Keep V88 unchanged for Flyway validation,
-- then remove its isolated quiz-session schema in this forward-only rollback migration.
DROP TABLE IF EXISTS public.quiz_question_states;
DROP TABLE IF EXISTS public.quiz_sessions;
