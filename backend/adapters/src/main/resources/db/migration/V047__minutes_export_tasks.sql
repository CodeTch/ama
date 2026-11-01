-- D06: immutable, content-free requests and individually owned storage attempts.
-- Unregistered/late artifact intents remain cleanup inventory; never attach by key existence.
SET NAMES utf8mb4;
SET time_zone = '+00:00';
CREATE TABLE minutes_export_input (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 requested_by VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
 request_key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_revision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 format VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 base_revision BIGINT UNSIGNED NOT NULL,
 access_revision BIGINT UNSIGNED NOT NULL,
 cancel_epoch BIGINT UNSIGNED NOT NULL,
 final_confirmation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 backend_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 input_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 producer_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY export_request(meeting_id,requested_by,request_key_hash),
 UNIQUE KEY export_producer(producer_key),
 CONSTRAINT ck_export_format CHECK(format IN ('md','pdf','docx')),
 CONSTRAINT fk_export_meeting FOREIGN KEY(meeting_id) REFERENCES meeting(id),
 CONSTRAINT fk_export_source FOREIGN KEY(source_revision_id) REFERENCES minutes_revision(id),
 CONSTRAINT fk_export_backend FOREIGN KEY(backend_id) REFERENCES storage_backend(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE minutes_export_artifact (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 input_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 attempt BIGINT UNSIGNED NOT NULL,
 file_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 backend_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 namespace VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 object_key VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 size_bytes BIGINT UNSIGNED NOT NULL,
 sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(6) NOT NULL,
 retired_at DATETIME(6) NULL,
 PRIMARY KEY(id),
 UNIQUE KEY export_attempt(task_id,attempt),
 UNIQUE KEY export_file(file_id),
 UNIQUE KEY export_location(backend_id,namespace,object_key),
 KEY export_cleanup(state,created_at,id),
 CONSTRAINT ck_export_artifact_state CHECK(state IN ('INTENT','PREPARED','REGISTERED','RETIRE_PENDING','RETIRED')),
 CONSTRAINT fk_export_artifact_input FOREIGN KEY(input_id) REFERENCES minutes_export_input(id),
 CONSTRAINT fk_export_artifact_task FOREIGN KEY(task_id) REFERENCES durable_task(id),
 CONSTRAINT fk_export_artifact_backend FOREIGN KEY(backend_id) REFERENCES storage_backend(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
