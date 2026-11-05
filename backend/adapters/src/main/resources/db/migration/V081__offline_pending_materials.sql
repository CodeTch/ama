ALTER TABLE offline_material_processing
 ADD pending_snapshot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
 ADD pending_changed TINYINT NOT NULL DEFAULT 0,
 ADD pending_failed TINYINT NOT NULL DEFAULT 0,
 ADD INDEX offline_meeting_stage(meeting_id,stage);
