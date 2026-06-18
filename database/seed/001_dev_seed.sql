-- Local development seed data only.
INSERT INTO fire_events (camera_id, confidence, status)
VALUES ('CAM-DEV-001', 0.9230, 'DETECTED')
ON CONFLICT DO NOTHING;
