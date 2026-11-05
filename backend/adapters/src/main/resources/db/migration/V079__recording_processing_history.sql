ALTER TABLE recording_processing ADD adoption_started TINYINT NOT NULL DEFAULT 0;
CREATE TABLE recording_processing_history (
 run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 session_id CHAR(36) NOT NULL, task_id CHAR(36), snapshot_id CHAR(36),source_id CHAR(36),
 base_revision BIGINT NOT NULL,state VARCHAR(24) NOT NULL,error_code VARCHAR(128),
 archived_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;
