CREATE TABLE offline_material_processing (
 task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id VARCHAR(255) NOT NULL,
 snapshot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
 generation_key CHAR(36) NOT NULL, decision_key CHAR(36) NOT NULL,
 stage VARCHAR(20) NOT NULL DEFAULT 'TRANSCRIBING', error_code VARCHAR(128),
 lease_id CHAR(36), lease_until DATETIME(6),
 INDEX offline_work(stage,lease_until), FOREIGN KEY(task_id) REFERENCES durable_task(id),
 FOREIGN KEY(snapshot_id) REFERENCES material_snapshot(id)
) ENGINE=InnoDB;
