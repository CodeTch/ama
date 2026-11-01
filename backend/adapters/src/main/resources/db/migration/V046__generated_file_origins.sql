-- D06: generated artifacts share file_object/file_reference and the normal authorized read path.
-- This origin is written only by a genuine task completion, after immutable bytes are durable.
-- It never grants an upload session or permits reopening published meeting writes.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE generated_file_origin (
 file_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 origin_kind VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 origin_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 purpose VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 cancel_epoch BIGINT UNSIGNED NOT NULL,
 backend_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 namespace VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 object_key VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 size_bytes BIGINT UNSIGNED NOT NULL,
 sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(file_id),
 UNIQUE KEY generated_origin(origin_kind,origin_id),
 UNIQUE KEY generated_task(task_id),
 KEY generated_meeting(meeting_id,file_id),
 CONSTRAINT ck_generated_file_kind CHECK(origin_kind='MINUTES_EXPORT' AND purpose='MINUTES_EXPORT'),
 CONSTRAINT fk_generated_file FOREIGN KEY(file_id) REFERENCES file_object(id),
 CONSTRAINT fk_generated_meeting FOREIGN KEY(meeting_id) REFERENCES meeting(id),
 CONSTRAINT fk_generated_task FOREIGN KEY(task_id) REFERENCES durable_task(id),
 CONSTRAINT fk_generated_backend FOREIGN KEY(backend_id) REFERENCES storage_backend(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
