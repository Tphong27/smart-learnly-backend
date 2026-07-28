CREATE TABLE IF NOT EXISTS public.system_settings (
    setting_key varchar(120) PRIMARY KEY,
    setting_value text,
    is_secret boolean NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid
);

CREATE INDEX IF NOT EXISTS idx_system_settings_updated_at
    ON public.system_settings (updated_at DESC);
