ALTER TABLE issue
    ADD COLUMN IF NOT EXISTS last_detected_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_yolo_analyzed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_vlm_analyzed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_notified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS max_box_area_ratio NUMERIC(8,6),
    ADD COLUMN IF NOT EXISTS last_box_area_ratio NUMERIC(8,6),
    ADD COLUMN IF NOT EXISTS snapshot_count INT NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_issue_open_tracking
ON issue (cctv_id, issue_status, last_detected_at);
