-- V87: Optimistic lock + identity uniqueness on class_curriculum_entries.
--
-- Added in a separate migration (not folded into V86) because V86 was already applied in
-- non-local environments. Never edit an applied migration — append a new one.
--
-- 1) `version` backs the @Version optimistic lock so concurrent trainer writers cannot
--    silently clobber each other's materialized_lesson_id (lost update).
-- 2) uq_class_curriculum_entries_version_identity enforces the invariant that each class
--    version holds at most one entry per logical lesson identity (mirrors
--    uq_curriculum_lessons_version_identity, which guarantees the backfill satisfies it).

ALTER TABLE public.class_curriculum_entries
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uq_class_curriculum_entries_version_identity
    ON public.class_curriculum_entries (class_version_id, lesson_identity_id)
    WHERE deleted_at IS NULL;
