CREATE TABLE material_adjustment (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id VARCHAR(255) NOT NULL,
 source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 snapshot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
 meeting_revision BIGINT NOT NULL,
 stage VARCHAR(20) NOT NULL, candidate_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
 error_code VARCHAR(128),lease_id CHAR(36),lease_until DATETIME(6),
 INDEX material_adjustment_meeting(meeting_id,meeting_revision),
 INDEX material_adjustment_worker(stage,lease_until),
 FOREIGN KEY(meeting_id) REFERENCES meeting(id),
 FOREIGN KEY(snapshot_id) REFERENCES material_snapshot(id),
 FOREIGN KEY(source_id) REFERENCES transcript_revision(id)
) ENGINE=InnoDB;
