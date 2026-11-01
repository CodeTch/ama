-- D07: independent, durable cleanup after the MEETING subject is permanently fenced.
-- No historical logical deletion is silently upgraded to a physical-erasure claim.
SET NAMES utf8mb4;
SET time_zone = '+00:00';
CREATE TABLE meeting_deletion (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 requested_by VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
 accepted_revision BIGINT UNSIGNED NOT NULL,
 cancel_epoch BIGINT UNSIGNED NOT NULL,
 state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 phase VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 purge_cursor INT UNSIGNED NOT NULL DEFAULT 0,
 attempt BIGINT UNSIGNED NOT NULL DEFAULT 0,
 lease_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until DATETIME(6) NULL,
 available_at DATETIME(6) NOT NULL,
 last_error VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 created_at DATETIME(6) NOT NULL,
 completed_at DATETIME(6) NULL,
 UNIQUE KEY deletion_meeting(meeting_id),
 KEY deletion_due(state,available_at,lease_until),
 CONSTRAINT fk_deletion_root FOREIGN KEY(meeting_id) REFERENCES meeting(id),
 CONSTRAINT ck_deletion_state CHECK(state IN ('PENDING','RUNNING','FAILED','COMPLETE')),
 CONSTRAINT ck_deletion_phase CHECK(phase IN ('INVENTORY','STORAGE','PURGE','COMPLETE')),
 CONSTRAINT ck_deletion_complete CHECK((state='COMPLETE')=(completed_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE meeting_deletion_item (
 deletion_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 backend_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 namespace VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 object_key VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 upload_token LONGTEXT NULL,
 state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
 PRIMARY KEY(deletion_id,kind,source_id),
 CONSTRAINT fk_deletion_item FOREIGN KEY(deletion_id) REFERENCES meeting_deletion(id),
 CONSTRAINT ck_deletion_item_kind CHECK(kind IN ('FILE','UPLOAD','EXPORT','RECORDING')),
 CONSTRAINT ck_deletion_item_state CHECK(state IN ('PENDING','COMPLETE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
