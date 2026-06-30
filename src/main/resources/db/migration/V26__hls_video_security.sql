-- HLS Video Security: Token revocation list
-- Stores active HLS tokens with session binding for replay attack prevention

CREATE TABLE IF NOT EXISTS public.hls_active_tokens (
    user_id UUID NOT NULL,
    lesson_id UUID NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(64),
    ip_hash VARCHAR(64),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, lesson_id, session_id)
);

-- Index for token cleanup (deleting expired tokens)
CREATE INDEX IF NOT EXISTS idx_hls_active_tokens_expires_at 
    ON public.hls_active_tokens (expires_at);

-- Index for session lookup during validation
CREATE INDEX IF NOT EXISTS idx_hls_active_tokens_user_lesson 
    ON public.hls_active_tokens (user_id, lesson_id);

-- Function to cleanup expired tokens (can be called via scheduler)
CREATE OR REPLACE FUNCTION cleanup_expired_hls_tokens()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM public.hls_active_tokens 
    WHERE expires_at < NOW();
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- HLS Lesson storage paths (stores R2 paths for HLS-processed videos)
CREATE TABLE IF NOT EXISTS public.hls_lessons (
    lesson_id UUID PRIMARY KEY REFERENCES public.lessons(id) ON DELETE CASCADE,
    r2_base_path VARCHAR(512),
    hls_status VARCHAR(32) DEFAULT 'pending', -- pending, processing, ready, failed
    encryption_key_path VARCHAR(512),
    qualities VARCHAR(128) DEFAULT '480p,720p,1080p',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for status-based queries
CREATE INDEX IF NOT EXISTS idx_hls_lessons_status 
    ON public.hls_lessons (hls_status);
