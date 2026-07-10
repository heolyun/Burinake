-- Local development seed data only.
WITH seeded_cctv AS (
    INSERT INTO cctv (cctv_name, cctv_num, location)
    VALUES ('Cheonan Training Center', 'CCTV-003', 'B1 parking lot entrance')
    ON CONFLICT (cctv_name, cctv_num) DO UPDATE
    SET location = EXCLUDED.location,
        is_active = true,
        updated_at = now()
    RETURNING cctv_id
),
seeded_image AS (
    INSERT INTO snapshot_image (
        cctv_id,
        storage_container,
        storage_key,
        image_url,
        content_type,
        width_px,
        height_px,
        snapshot_time,
        raw_metadata
    )
    SELECT
        cctv_id,
        'fire-events',
        'dev/cctv-3/snapshot-001.jpg',
        'https://example.com/dev/cctv-3/snapshot-001.jpg',
        'image/jpeg',
        1920,
        1080,
        now(),
        '{"source":"dev_seed"}'::jsonb
    FROM seeded_cctv
    RETURNING image_id, cctv_id, snapshot_time
),
seeded_yolo AS (
    INSERT INTO yolo_result (
        image_id,
        model_version,
        is_fire,
        is_smoke,
        fire_confidence,
        smoke_confidence,
        raw_response
    )
    SELECT
        image_id,
        'dev',
        true,
        false,
        0.9230,
        0.1200,
        '{"source":"dev_seed"}'::jsonb
    FROM seeded_image
    RETURNING yolo_result_id, image_id
),
seeded_issue AS (
    INSERT INTO issue (
        cctv_id,
        trigger_image_id,
        yolo_result_id,
        issue_type,
        issue_status,
        detected_at,
        vlm_input_start_time,
        vlm_input_end_time,
        latest_is_real_fire,
        latest_level,
        latest_message,
        vlm_analyzed_at,
        last_detected_at,
        last_yolo_analyzed_at,
        last_vlm_analyzed_at,
        last_notified_at,
        max_box_area_ratio,
        last_box_area_ratio,
        snapshot_count
    )
    SELECT
        i.cctv_id,
        i.image_id,
        y.yolo_result_id,
        'FIRE',
        'REAL_FIRE',
        i.snapshot_time,
        i.snapshot_time - interval '30 seconds',
        i.snapshot_time + interval '5 seconds',
        true,
        3,
        'Possible fire detected. On-site verification is required.',
        now(),
        i.snapshot_time,
        now(),
        now(),
        now(),
        0.050000,
        0.050000,
        1
    FROM seeded_image i
    JOIN seeded_yolo y ON y.image_id = i.image_id
    RETURNING issue_id, trigger_image_id
),
seeded_vlm AS (
    INSERT INTO vlm_result (
        issue_id,
        analysis_round,
        model_name,
        model_version,
        is_real_fire,
        fire_start,
        fire_reason,
        situation_summary,
        level,
        message,
        confidence,
        raw_response,
        analyzed_at
    )
    SELECT
        issue_id,
        1,
        'dev-vlm',
        'dev',
        true,
        'unknown',
        'Visible flame-like region detected near the parking lot entrance.',
        'Possible fire detected at the B1 parking lot entrance.',
        3,
        'Possible fire detected. On-site verification is required.',
        0.8800,
        '{"source":"dev_seed"}'::jsonb,
        now()
    FROM seeded_issue
    RETURNING vlm_result_id
)
INSERT INTO issue_snapshot (issue_id, image_id, sequence_no, relative_seconds, is_trigger_image)
SELECT issue_id, trigger_image_id, 1, 0, true
FROM seeded_issue
ON CONFLICT (issue_id, image_id) DO NOTHING;
