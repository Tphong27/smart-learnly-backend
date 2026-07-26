CREATE INDEX IF NOT EXISTS
    idx_class_sessions_trainer_date_time
    ON public.class_sessions (
        trainer_id,
        session_date,
        start_time,
        end_time
    );

CREATE INDEX IF NOT EXISTS
    idx_class_sessions_date_time
    ON public.class_sessions (
        session_date,
        start_time,
        end_time
    );