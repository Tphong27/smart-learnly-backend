DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public' AND type.typname = 'question_type'
    ) THEN
        CREATE TYPE public.question_type AS ENUM ('single_choice', 'multiple_choice', 'essay', 'short_answer');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public' AND type.typname = 'bloom_level'
    ) THEN
        CREATE TYPE public.bloom_level AS ENUM ('remember', 'understand', 'apply', 'analyze', 'evaluate', 'create');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public' AND type.typname = 'question_status'
    ) THEN
        CREATE TYPE public.question_status AS ENUM ('draft', 'pending_review', 'approved', 'rejected');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public' AND type.typname = 'test_type'
    ) THEN
        CREATE TYPE public.test_type AS ENUM ('quiz', 'midterm', 'final', 'assignment');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_type type
        JOIN pg_namespace namespace ON namespace.oid = type.typnamespace
        WHERE namespace.nspname = 'public' AND type.typname = 'attempt_status'
    ) THEN
        CREATE TYPE public.attempt_status AS ENUM ('in_progress', 'submitted', 'graded', 'timeout');
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS public.questions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_bank_id uuid NOT NULL,
    course_id uuid NOT NULL REFERENCES public.courses(id),
    clo_id uuid,
    question_text text NOT NULL,
    question_type public.question_type NOT NULL,
    bloom_level public.bloom_level,
    difficulty smallint,
    explanation text,
    is_ai_generated boolean NOT NULL DEFAULT false,
    status public.question_status NOT NULL DEFAULT 'draft',
    created_by uuid REFERENCES public.users(id),
    reviewed_by uuid REFERENCES public.users(id),
    reviewed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_questions_difficulty CHECK (difficulty IS NULL OR difficulty BETWEEN 1 AND 5)
);

ALTER TABLE public.questions
    ADD COLUMN IF NOT EXISTS question_bank_id uuid,
    ADD COLUMN IF NOT EXISTS course_id uuid,
    ADD COLUMN IF NOT EXISTS clo_id uuid,
    ADD COLUMN IF NOT EXISTS question_text text,
    ADD COLUMN IF NOT EXISTS question_type public.question_type,
    ADD COLUMN IF NOT EXISTS bloom_level public.bloom_level,
    ADD COLUMN IF NOT EXISTS difficulty smallint,
    ADD COLUMN IF NOT EXISTS explanation text,
    ADD COLUMN IF NOT EXISTS is_ai_generated boolean DEFAULT false,
    ADD COLUMN IF NOT EXISTS status public.question_status DEFAULT 'draft',
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS reviewed_by uuid,
    ADD COLUMN IF NOT EXISTS reviewed_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS created_at timestamp with time zone DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone DEFAULT now();

CREATE TABLE IF NOT EXISTS public.question_answers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id uuid NOT NULL REFERENCES public.questions(id) ON DELETE CASCADE,
    answer_text text NOT NULL,
    is_correct boolean NOT NULL DEFAULT false,
    order_index integer NOT NULL DEFAULT 0
);

ALTER TABLE public.question_answers
    ADD COLUMN IF NOT EXISTS question_id uuid,
    ADD COLUMN IF NOT EXISTS answer_text text,
    ADD COLUMN IF NOT EXISTS is_correct boolean DEFAULT false,
    ADD COLUMN IF NOT EXISTS order_index integer DEFAULT 0;

CREATE TABLE IF NOT EXISTS public.tests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id uuid REFERENCES public.course_sections(id),
    class_id uuid REFERENCES public.classes(id),
    course_id uuid REFERENCES public.courses(id),
    title character varying(255) NOT NULL,
    description text,
    test_type public.test_type NOT NULL DEFAULT 'quiz',
    duration_minutes integer,
    max_attempts integer,
    pass_score numeric(5,2),
    shuffle_questions boolean NOT NULL DEFAULT false,
    shuffle_answers boolean NOT NULL DEFAULT false,
    show_answers_after boolean NOT NULL DEFAULT true,
    is_published boolean NOT NULL DEFAULT false,
    is_archived boolean NOT NULL DEFAULT false,
    created_by uuid REFERENCES public.users(id),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT chk_tests_duration_positive CHECK (duration_minutes IS NULL OR duration_minutes > 0),
    CONSTRAINT chk_tests_max_attempts_positive CHECK (max_attempts IS NULL OR max_attempts > 0),
    CONSTRAINT chk_tests_pass_score_range CHECK (pass_score IS NULL OR pass_score BETWEEN 0 AND 100)
);

ALTER TABLE public.tests
    ADD COLUMN IF NOT EXISTS module_id uuid,
    ADD COLUMN IF NOT EXISTS class_id uuid,
    ADD COLUMN IF NOT EXISTS course_id uuid,
    ADD COLUMN IF NOT EXISTS title character varying(255),
    ADD COLUMN IF NOT EXISTS description text,
    ADD COLUMN IF NOT EXISTS test_type public.test_type DEFAULT 'quiz',
    ADD COLUMN IF NOT EXISTS duration_minutes integer,
    ADD COLUMN IF NOT EXISTS max_attempts integer,
    ADD COLUMN IF NOT EXISTS pass_score numeric(5,2),
    ADD COLUMN IF NOT EXISTS shuffle_questions boolean DEFAULT false,
    ADD COLUMN IF NOT EXISTS shuffle_answers boolean DEFAULT false,
    ADD COLUMN IF NOT EXISTS show_answers_after boolean DEFAULT true,
    ADD COLUMN IF NOT EXISTS is_published boolean DEFAULT false,
    ADD COLUMN IF NOT EXISTS is_archived boolean DEFAULT false,
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS created_at timestamp with time zone DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone DEFAULT now();

CREATE TABLE IF NOT EXISTS public.test_questions (
    test_id uuid NOT NULL REFERENCES public.tests(id) ON DELETE CASCADE,
    question_id uuid NOT NULL REFERENCES public.questions(id) ON DELETE RESTRICT,
    order_index integer NOT NULL DEFAULT 0,
    marks numeric(8,2) NOT NULL DEFAULT 1,
    PRIMARY KEY (test_id, question_id),
    CONSTRAINT chk_test_questions_marks_positive CHECK (marks > 0)
);

ALTER TABLE public.test_questions
    ADD COLUMN IF NOT EXISTS test_id uuid,
    ADD COLUMN IF NOT EXISTS question_id uuid,
    ADD COLUMN IF NOT EXISTS order_index integer DEFAULT 0,
    ADD COLUMN IF NOT EXISTS marks numeric(8,2) DEFAULT 1;

CREATE TABLE IF NOT EXISTS public.test_attempts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id uuid NOT NULL REFERENCES public.tests(id) ON DELETE CASCADE,
    student_id uuid NOT NULL REFERENCES public.users(id),
    start_time timestamp with time zone NOT NULL DEFAULT now(),
    end_time timestamp with time zone,
    score numeric(8,2),
    status public.attempt_status NOT NULL DEFAULT 'in_progress',
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    assignment_id uuid REFERENCES public.assignments(id),
    CONSTRAINT chk_test_attempts_score_non_negative CHECK (score IS NULL OR score >= 0)
);

ALTER TABLE public.test_attempts
    ADD COLUMN IF NOT EXISTS test_id uuid,
    ADD COLUMN IF NOT EXISTS student_id uuid,
    ADD COLUMN IF NOT EXISTS start_time timestamp with time zone DEFAULT now(),
    ADD COLUMN IF NOT EXISTS end_time timestamp with time zone,
    ADD COLUMN IF NOT EXISTS score numeric(8,2),
    ADD COLUMN IF NOT EXISTS status public.attempt_status DEFAULT 'in_progress',
    ADD COLUMN IF NOT EXISTS created_at timestamp with time zone DEFAULT now(),
    ADD COLUMN IF NOT EXISTS assignment_id uuid;

CREATE TABLE IF NOT EXISTS public.student_test_answers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id uuid NOT NULL REFERENCES public.test_attempts(id) ON DELETE CASCADE,
    question_id uuid NOT NULL REFERENCES public.questions(id) ON DELETE RESTRICT,
    selected_answer_id uuid REFERENCES public.question_answers(id),
    essay_answer text,
    is_correct boolean,
    score_awarded numeric(8,2),
    issue_reported text,
    CONSTRAINT chk_student_test_answers_score_non_negative CHECK (score_awarded IS NULL OR score_awarded >= 0)
);

ALTER TABLE public.student_test_answers
    ADD COLUMN IF NOT EXISTS attempt_id uuid,
    ADD COLUMN IF NOT EXISTS question_id uuid,
    ADD COLUMN IF NOT EXISTS selected_answer_id uuid,
    ADD COLUMN IF NOT EXISTS essay_answer text,
    ADD COLUMN IF NOT EXISTS is_correct boolean,
    ADD COLUMN IF NOT EXISTS score_awarded numeric(8,2),
    ADD COLUMN IF NOT EXISTS issue_reported text;

ALTER TABLE public.assignments
    ADD COLUMN IF NOT EXISTS test_id uuid;

ALTER TABLE public.lessons
    ADD COLUMN IF NOT EXISTS test_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'assignments_test_id_fkey'
          AND conrelid = 'public.assignments'::regclass
    ) THEN
        ALTER TABLE public.assignments
            ADD CONSTRAINT assignments_test_id_fkey
            FOREIGN KEY (test_id)
            REFERENCES public.tests(id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'lessons_test_id_fkey'
          AND conrelid = 'public.lessons'::regclass
    ) THEN
        ALTER TABLE public.lessons
            ADD CONSTRAINT lessons_test_id_fkey
            FOREIGN KEY (test_id)
            REFERENCES public.tests(id)
            ON DELETE SET NULL;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_questions_course
    ON public.questions(course_id);
CREATE INDEX IF NOT EXISTS idx_questions_status
    ON public.questions(status);
CREATE INDEX IF NOT EXISTS idx_question_answers_question
    ON public.question_answers(question_id);
CREATE INDEX IF NOT EXISTS idx_tests_course
    ON public.tests(course_id) WHERE course_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tests_class
    ON public.tests(class_id) WHERE class_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tests_module
    ON public.tests(module_id) WHERE module_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_test_questions_test
    ON public.test_questions(test_id);
CREATE INDEX IF NOT EXISTS idx_test_questions_question
    ON public.test_questions(question_id);
CREATE INDEX IF NOT EXISTS idx_test_attempts_test_student
    ON public.test_attempts(test_id, student_id);
CREATE INDEX IF NOT EXISTS idx_student_test_answers_attempt
    ON public.student_test_answers(attempt_id);
CREATE INDEX IF NOT EXISTS idx_assignments_test_id
    ON public.assignments(test_id) WHERE test_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_lessons_test_id
    ON public.lessons(test_id) WHERE test_id IS NOT NULL;
