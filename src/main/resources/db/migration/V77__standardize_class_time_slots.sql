CREATE TEMP TABLE class_time_slot_migration_map (
    old_start_time time NOT NULL,
    old_end_time time NOT NULL,
    new_start_time time NOT NULL,
    new_end_time time NOT NULL,
    PRIMARY KEY (old_start_time, old_end_time)
) ON COMMIT DROP;

INSERT INTO class_time_slot_migration_map (
    old_start_time,
    old_end_time,
    new_start_time,
    new_end_time
)
VALUES
    (time '08:00', time '10:00', time '07:30', time '09:30'),
    (time '09:00', time '14:03', time '09:45', time '11:45'),
    (time '10:00', time '12:00', time '09:45', time '11:45'),
    (time '11:00', time '12:00', time '09:45', time '11:45'),
    (time '14:00', time '15:00', time '13:00', time '15:00'),
    (time '14:00', time '16:00', time '13:00', time '15:00'),
    (time '19:00', time '21:00', time '19:30', time '21:30'),
    (time '07:30', time '09:30', time '07:30', time '09:30'),
    (time '09:45', time '11:45', time '09:45', time '11:45'),
    (time '13:00', time '15:00', time '13:00', time '15:00'),
    (time '15:15', time '17:15', time '15:15', time '17:15'),
    (time '19:30', time '21:30', time '19:30', time '21:30'),
    (time '21:45', time '23:45', time '21:45', time '23:45');

DO $migration$
DECLARE
    class_record record;
    schedule_json jsonb;
    normalized_schedule jsonb;
BEGIN
    FOR class_record IN
        SELECT id, class_name, schedule_description
        FROM public.classes
        WHERE schedule_description IS NOT NULL
          AND btrim(schedule_description) <> ''
        FOR UPDATE
    LOOP
        IF class_record.schedule_description =
                'Tuesday and Thursday, 19:00-21:00' THEN
            schedule_json := '[
                {
                    "dayOfWeek": "TUESDAY",
                    "slots": [
                        {"startTime": "19:00", "endTime": "21:00"}
                    ]
                },
                {
                    "dayOfWeek": "THURSDAY",
                    "slots": [
                        {"startTime": "19:00", "endTime": "21:00"}
                    ]
                }
            ]'::jsonb;
        ELSE
            BEGIN
                schedule_json := class_record.schedule_description::jsonb;
            EXCEPTION
                WHEN others THEN
                    RAISE EXCEPTION
                        'Class % (%) has a non-JSON schedule: %',
                        class_record.id,
                        class_record.class_name,
                        class_record.schedule_description;
            END;
        END IF;

        IF jsonb_typeof(schedule_json) <> 'array'
                OR jsonb_array_length(schedule_json) = 0 THEN
            RAISE EXCEPTION
                'Class % (%) must have a non-empty schedule array',
                class_record.id,
                class_record.class_name;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM jsonb_array_elements(schedule_json) AS day_entry(day_value)
            WHERE jsonb_typeof(day_value) <> 'object'
               OR jsonb_typeof(day_value -> 'slots') <> 'array'
               OR jsonb_array_length(day_value -> 'slots') = 0
        ) THEN
            RAISE EXCEPTION
                'Class % (%) has an invalid day or slots structure',
                class_record.id,
                class_record.class_name;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM jsonb_array_elements(schedule_json) AS day_entry(day_value)
            CROSS JOIN LATERAL jsonb_array_elements(
                day_value -> 'slots'
            ) AS slot_entry(slot_value)
            LEFT JOIN class_time_slot_migration_map mapping
                ON mapping.old_start_time =
                        (slot_value ->> 'startTime')::time
               AND mapping.old_end_time =
                        (slot_value ->> 'endTime')::time
            WHERE mapping.old_start_time IS NULL
        ) THEN
            RAISE EXCEPTION
                'Class % (%) contains a schedule range without a migration mapping',
                class_record.id,
                class_record.class_name;
        END IF;

        SELECT jsonb_agg(
            jsonb_build_object(
                'dayOfWeek',
                normalized_day.day_value ->> 'dayOfWeek',
                'slots',
                normalized_day.normalized_slots
            )
            ORDER BY normalized_day.day_order
        )
        INTO normalized_schedule
        FROM (
            SELECT
                day_entry.day_value,
                day_entry.day_order,
                jsonb_agg(
                    jsonb_build_object(
                        'startTime',
                        to_char(mapping.new_start_time, 'HH24:MI'),
                        'endTime',
                        to_char(mapping.new_end_time, 'HH24:MI')
                    )
                    ORDER BY slot_entry.slot_order
                ) AS normalized_slots
            FROM jsonb_array_elements(schedule_json)
                WITH ORDINALITY AS day_entry(day_value, day_order)
            CROSS JOIN LATERAL jsonb_array_elements(
                day_entry.day_value -> 'slots'
            ) WITH ORDINALITY AS slot_entry(slot_value, slot_order)
            JOIN class_time_slot_migration_map mapping
                ON mapping.old_start_time =
                        (slot_entry.slot_value ->> 'startTime')::time
               AND mapping.old_end_time =
                        (slot_entry.slot_value ->> 'endTime')::time
            GROUP BY day_entry.day_value, day_entry.day_order
        ) AS normalized_day;

        IF EXISTS (
            SELECT 1
            FROM jsonb_array_elements(normalized_schedule)
                    AS day_entry(day_value)
            CROSS JOIN LATERAL jsonb_array_elements(
                day_value -> 'slots'
            ) AS slot_entry(slot_value)
            GROUP BY
                day_value ->> 'dayOfWeek',
                slot_value ->> 'startTime',
                slot_value ->> 'endTime'
            HAVING count(*) > 1
        ) THEN
            RAISE EXCEPTION
                'Class % (%) would contain duplicate slots after migration',
                class_record.id,
                class_record.class_name;
        END IF;

        UPDATE public.classes
        SET schedule_description = normalized_schedule::text,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = class_record.id;
    END LOOP;
END
$migration$;

UPDATE public.class_sessions AS class_session
SET start_time = mapping.new_start_time,
    end_time = mapping.new_end_time
FROM class_time_slot_migration_map AS mapping
WHERE class_session.start_time = mapping.old_start_time
  AND class_session.end_time = mapping.old_end_time;

DO $verification$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.class_sessions
        WHERE (start_time, end_time) NOT IN (
            (time '07:30', time '09:30'),
            (time '09:45', time '11:45'),
            (time '13:00', time '15:00'),
            (time '15:15', time '17:15'),
            (time '19:30', time '21:30'),
            (time '21:45', time '23:45')
        )
    ) THEN
        RAISE EXCEPTION
            'class_sessions still contains unsupported time ranges';
    END IF;
END
$verification$;

ALTER TABLE public.class_sessions
    ADD CONSTRAINT chk_class_sessions_standard_time_slot
    CHECK (
        (start_time, end_time) IN (
            (time '07:30', time '09:30'),
            (time '09:45', time '11:45'),
            (time '13:00', time '15:00'),
            (time '15:15', time '17:15'),
            (time '19:30', time '21:30'),
            (time '21:45', time '23:45')
        )
    ) NOT VALID;

ALTER TABLE public.class_sessions
    VALIDATE CONSTRAINT chk_class_sessions_standard_time_slot;