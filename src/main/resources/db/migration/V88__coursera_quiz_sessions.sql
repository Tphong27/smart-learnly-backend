CREATE TABLE IF NOT EXISTS public.quiz_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id uuid NOT NULL REFERENCES public.tests(id) ON DELETE RESTRICT,
    student_id uuid NOT NULL REFERENCES public.users(id) ON DELETE RESTRICT,
    lesson_id uuid REFERENCES public.curriculum_lessons(id) ON DELETE SET NULL,
    class_id uuid REFERENCES public.classes(id) ON DELETE SET NULL,
    status character varying(20) NOT NULL DEFAULT 'IN_PROGRESS',
    current_question_index integer NOT NULL DEFAULT 0,
    total_questions integer NOT NULL,
    answered_count integer NOT NULL DEFAULT 0,
    flagged_count integer NOT NULL DEFAULT 0,
    correct_count integer,
    score numeric(5,2),
    started_at timestamp with time zone NOT NULL DEFAULT now(),
    expires_at timestamp with time zone,
    submitted_at timestamp with time zone,
    last_activity_at timestamp with time zone NOT NULL DEFAULT now(),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_quiz_sessions_status CHECK (
        status IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED')
    ),
    CONSTRAINT chk_quiz_sessions_question_counts CHECK (
        total_questions > 0
        AND current_question_index >= 0
        AND current_question_index < total_questions
        AND answered_count >= 0
        AND flagged_count >= 0
    ),
    CONSTRAINT chk_quiz_sessions_score CHECK (
        score IS NULL OR score BETWEEN 0 AND 100
    )
);

CREATE TABLE IF NOT EXISTS public.quiz_question_states (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id uuid NOT NULL REFERENCES public.quiz_sessions(id) ON DELETE CASCADE,
    question_id uuid NOT NULL REFERENCES public.questions(id) ON DELETE RESTRICT,
    order_index integer NOT NULL,
    selected_answer_ids uuid[],
    essay_answer text,
    is_correct boolean,
    is_flagged boolean NOT NULL DEFAULT false,
    answered_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_quiz_question_states_session_question UNIQUE (session_id, question_id),
    CONSTRAINT uq_quiz_question_states_session_order UNIQUE (session_id, order_index),
    CONSTRAINT chk_quiz_question_states_order CHECK (order_index >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_quiz_sessions_active_test_student
    ON public.quiz_sessions (test_id, student_id)
    WHERE status = 'IN_PROGRESS';

CREATE INDEX IF NOT EXISTS idx_quiz_sessions_expiration
    ON public.quiz_sessions (expires_at)
    WHERE status = 'IN_PROGRESS' AND expires_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_quiz_question_states_session_order
    ON public.quiz_question_states (session_id, order_index);
