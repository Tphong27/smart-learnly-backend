WITH latest_completed_draft AS (
    SELECT DISTINCT ON (
        content.lesson_id,
        content.lesson_scope,
        COALESCE(content.class_id, '00000000-0000-0000-0000-000000000000'::uuid),
        content.source_version
    ) content.id
    FROM public.video_ai_contents content
    JOIN public.video_ai_jobs job
        ON job.content_id = content.id
       AND job.job_type = 'VIDEO_ARTIFACTS'
       AND job.status = 'completed'
    WHERE content.status = 'draft'
      AND NOT EXISTS (
          SELECT 1
          FROM public.video_ai_contents published
          WHERE published.lesson_id = content.lesson_id
            AND published.lesson_scope = content.lesson_scope
            AND COALESCE(published.class_id, '00000000-0000-0000-0000-000000000000'::uuid)
                = COALESCE(content.class_id, '00000000-0000-0000-0000-000000000000'::uuid)
            AND published.source_version = content.source_version
            AND published.status = 'published'
      )
    ORDER BY
        content.lesson_id,
        content.lesson_scope,
        COALESCE(content.class_id, '00000000-0000-0000-0000-000000000000'::uuid),
        content.source_version,
        content.updated_at DESC,
        content.id DESC
)
UPDATE public.video_ai_contents content
SET status = 'published',
    published_by = COALESCE(content.published_by, content.created_by),
    published_at = COALESCE(content.published_at, now()),
    updated_at = now()
FROM latest_completed_draft latest
WHERE content.id = latest.id;
