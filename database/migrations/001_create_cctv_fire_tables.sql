-- Migration: create CCTV fire detection MVP tables.
-- Source: cctv_fire_postgresql_table_definition.xlsx, 07_DDL sheet.
CREATE TABLE cctv (
    cctv_id BIGSERIAL PRIMARY KEY,
    cctv_name VARCHAR(100) NOT NULL,
    cctv_num VARCHAR(50) NOT NULL,
    location TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_cctv_name_num UNIQUE (cctv_name, cctv_num)
);

CREATE TABLE snapshot_image (
    image_id BIGSERIAL PRIMARY KEY,
    cctv_id BIGINT NOT NULL REFERENCES cctv(cctv_id),
    storage_provider VARCHAR(30) NOT NULL DEFAULT 'AZURE_BLOB',
    storage_container VARCHAR(100),
    storage_key TEXT NOT NULL,
    image_url TEXT NOT NULL,
    content_type VARCHAR(50),
    file_size_bytes BIGINT,
    width_px INT,
    height_px INT,
    snapshot_time TIMESTAMPTZ NOT NULL,
    raw_metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_snapshot_cctv_time
ON snapshot_image (cctv_id, snapshot_time);

CREATE TABLE yolo_result (
    yolo_result_id BIGSERIAL PRIMARY KEY,
    image_id BIGINT NOT NULL REFERENCES snapshot_image(image_id),
    model_name VARCHAR(100) NOT NULL DEFAULT 'YOLO',
    model_version VARCHAR(50),
    analysis_round INT NOT NULL DEFAULT 1,
    is_fire BOOLEAN NOT NULL DEFAULT false,
    is_smoke BOOLEAN NOT NULL DEFAULT false,
    fire_confidence NUMERIC(5,4),
    smoke_confidence NUMERIC(5,4),
    raw_response JSONB,
    analyzed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_yolo_image_round UNIQUE (image_id, analysis_round)
);

CREATE INDEX idx_yolo_image
ON yolo_result (image_id);

CREATE INDEX idx_yolo_fire_smoke
ON yolo_result (is_fire, is_smoke, analyzed_at);

CREATE TABLE detection_box (
    box_id BIGSERIAL PRIMARY KEY,
    yolo_result_id BIGINT NOT NULL REFERENCES yolo_result(yolo_result_id),
    box_order INT NOT NULL DEFAULT 1,
    detection_type VARCHAR(20) NOT NULL,
    confidence NUMERIC(5,4),
    x1 NUMERIC(10,4) NOT NULL,
    y1 NUMERIC(10,4) NOT NULL,
    x2 NUMERIC(10,4) NOT NULL,
    y2 NUMERIC(10,4) NOT NULL,
    x3 NUMERIC(10,4) NOT NULL,
    y3 NUMERIC(10,4) NOT NULL,
    x4 NUMERIC(10,4) NOT NULL,
    y4 NUMERIC(10,4) NOT NULL,
    coordinate_type VARCHAR(20) NOT NULL DEFAULT 'PIXEL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_detection_type CHECK (detection_type IN ('FIRE', 'SMOKE')),
    CONSTRAINT chk_coordinate_type CHECK (coordinate_type IN ('PIXEL', 'NORMALIZED'))
);

CREATE INDEX idx_box_result_type
ON detection_box (yolo_result_id, detection_type);

CREATE TABLE issue (
    issue_id BIGSERIAL PRIMARY KEY,
    cctv_id BIGINT NOT NULL REFERENCES cctv(cctv_id),
    trigger_image_id BIGINT NOT NULL REFERENCES snapshot_image(image_id),
    yolo_result_id BIGINT NOT NULL REFERENCES yolo_result(yolo_result_id),
    issue_type VARCHAR(20) NOT NULL,
    issue_status VARCHAR(30) NOT NULL DEFAULT 'CANDIDATE',
    detected_at TIMESTAMPTZ NOT NULL,
    vlm_input_start_time TIMESTAMPTZ,
    vlm_input_end_time TIMESTAMPTZ,
    latest_is_real_fire BOOLEAN,
    latest_level SMALLINT,
    latest_message TEXT,
    vlm_analyzed_at TIMESTAMPTZ,
    last_detected_at TIMESTAMPTZ,
    last_yolo_analyzed_at TIMESTAMPTZ,
    last_vlm_analyzed_at TIMESTAMPTZ,
    last_notified_at TIMESTAMPTZ,
    max_box_area_ratio NUMERIC(8,6),
    last_box_area_ratio NUMERIC(8,6),
    snapshot_count INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_issue_type CHECK (issue_type IN ('FIRE', 'SMOKE', 'FIRE_SMOKE')),
    CONSTRAINT chk_issue_status CHECK (issue_status IN (
        'CANDIDATE',
        'VLM_ANALYZING',
        'REAL_FIRE',
        'FALSE_ALARM',
        'REPORTED',
        'CLOSED'
    )),
    CONSTRAINT chk_issue_latest_level CHECK (latest_level IS NULL OR latest_level BETWEEN 1 AND 4)
);

CREATE INDEX idx_issue_cctv_detected
ON issue (cctv_id, detected_at);

CREATE INDEX idx_issue_type_status
ON issue (issue_type, issue_status, detected_at);

CREATE INDEX idx_issue_latest_level
ON issue (latest_level, detected_at);

CREATE INDEX idx_issue_open_tracking
ON issue (cctv_id, issue_status, last_detected_at);

CREATE TABLE issue_snapshot (
    issue_snapshot_id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL REFERENCES issue(issue_id),
    image_id BIGINT NOT NULL REFERENCES snapshot_image(image_id),
    sequence_no INT NOT NULL,
    relative_seconds INT,
    is_trigger_image BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_issue_image UNIQUE (issue_id, image_id),
    CONSTRAINT ux_issue_sequence UNIQUE (issue_id, sequence_no)
);

CREATE INDEX idx_issue_snapshot_issue_seq
ON issue_snapshot (issue_id, sequence_no);

CREATE TABLE vlm_result (
    vlm_result_id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL REFERENCES issue(issue_id),
    analysis_round INT NOT NULL DEFAULT 1,
    model_name VARCHAR(100),
    model_version VARCHAR(50),
    is_real_fire BOOLEAN NOT NULL,
    fire_start TEXT,
    fire_reason TEXT,
    situation_summary TEXT,
    level SMALLINT NOT NULL,
    message TEXT NOT NULL,
    confidence NUMERIC(5,4),
    raw_response JSONB,
    analyzed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_vlm_issue_round UNIQUE (issue_id, analysis_round),
    CONSTRAINT chk_vlm_level CHECK (level BETWEEN 1 AND 4)
);

CREATE INDEX idx_vlm_issue_round
ON vlm_result (issue_id, analysis_round);

CREATE INDEX idx_vlm_real_fire_level
ON vlm_result (is_real_fire, level, analyzed_at);

CREATE TABLE emergency_report (
    report_id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL REFERENCES issue(issue_id),
    vlm_result_id BIGINT NOT NULL REFERENCES vlm_result(vlm_result_id),
    report_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    report_message TEXT NOT NULL,
    receiver VARCHAR(100) NOT NULL DEFAULT '119',
    approved_by VARCHAR(100),
    approved_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    response_code VARCHAR(50),
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_report_status CHECK (report_status IN (
        'DRAFT',
        'APPROVED',
        'SENT',
        'FAILED',
        'CANCELED'
    ))
);

CREATE INDEX idx_report_issue
ON emergency_report (issue_id, created_at);

CREATE INDEX idx_report_status
ON emergency_report (report_status, created_at);
