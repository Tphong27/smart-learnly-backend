CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE OR REPLACE FUNCTION public.smartlearnly_normalized_terms(input_value text)
RETURNS text[]
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT CASE
        WHEN normalized_value = '' THEN ARRAY[]::text[]
        ELSE string_to_array(normalized_value, ' ')
    END
    FROM (
        SELECT lower(regexp_replace(btrim(coalesce(input_value, '')), E'\\s+', ' ', 'g')) AS normalized_value
    ) normalized;
$$;

CREATE OR REPLACE FUNCTION public.smartlearnly_token_jaccard(left_value text, right_value text)
RETURNS double precision
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    WITH left_terms AS (
        SELECT DISTINCT unnest(public.smartlearnly_normalized_terms(left_value)) AS term
    ),
    right_terms AS (
        SELECT DISTINCT unnest(public.smartlearnly_normalized_terms(right_value)) AS term
    ),
    intersection_count AS (
        SELECT count(*)::double precision AS value
        FROM left_terms
        JOIN right_terms USING (term)
    ),
    union_count AS (
        SELECT count(*)::double precision AS value
        FROM (
            SELECT term FROM left_terms
            UNION
            SELECT term FROM right_terms
        ) terms
    )
    SELECT coalesce(intersection_count.value / nullif(union_count.value, 0), 0)
    FROM intersection_count
    CROSS JOIN union_count;
$$;

CREATE INDEX IF NOT EXISTS idx_questions_normalized_terms_gin
    ON public.questions
    USING gin (public.smartlearnly_normalized_terms(question_text));

CREATE INDEX IF NOT EXISTS idx_questions_course_updated
    ON public.questions (course_id, updated_at DESC, id ASC);

CREATE INDEX IF NOT EXISTS idx_transactions_user_created
    ON public.transactions (user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transactions_created
    ON public.transactions (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_course_enrollments_course_status
    ON public.course_enrollments (course_id, status);

CREATE INDEX IF NOT EXISTS idx_courses_published_catalog_order
    ON public.courses (is_featured DESC, created_at DESC, id ASC)
    WHERE deleted_at IS NULL
      AND status = 'published'::public.course_status;

CREATE INDEX IF NOT EXISTS idx_courses_catalog_text_trgm
    ON public.courses
    USING gin (((title)::text || ' ' || coalesce(description, '')) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_classes_public_schedule
    ON public.classes (status, start_date, created_at DESC, id ASC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_classes_admin_created
    ON public.classes (created_at DESC, id ASC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_classes_name_trgm
    ON public.classes
    USING gin (class_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_class_student_activity
    ON public.lesson_progress (class_id, student_id, last_accessed_at DESC)
    WHERE class_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sepay_webhook_events_received
    ON public.sepay_webhook_events (received_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_active
    ON public.refresh_tokens (user_id)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_otp_verifications_user_purpose_unverified
    ON public.otp_verifications (user_id, purpose)
    WHERE verified_at IS NULL;
