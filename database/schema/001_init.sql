-- Temporary PostgreSQL schema. Replace or migrate after the final DB decision.
CREATE TABLE IF NOT EXISTS fire_events (
    id BIGSERIAL PRIMARY KEY,
    camera_id VARCHAR(100) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confidence NUMERIC(5, 4) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DETECTED',
    snapshot_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
