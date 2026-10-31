CREATE TABLE recording_archive_input (
 id CHAR(36) PRIMARY KEY,
 session_id CHAR(36) NOT NULL,
 meeting_id CHAR(36) NOT NULL,
 requested_by VARCHAR(255) NOT NULL,
 base_revision BIGINT NOT NULL,
 cancel_epoch BIGINT NOT NULL,
 input_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 producer_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 UNIQUE KEY uq_recording_archive_admission(session_id,base_revision,cancel_epoch),
 UNIQUE KEY uq_recording_archive_producer(producer_key),
 FOREIGN KEY(session_id) REFERENCES recording_session(session_id)
);
CREATE TABLE recording_archive_current (
 session_id CHAR(36) PRIMARY KEY,
 input_id CHAR(36) NOT NULL,
 FOREIGN KEY(input_id) REFERENCES recording_archive_input(id)
);
CREATE TABLE recording_archive (
 id CHAR(36) PRIMARY KEY,
 input_id CHAR(36) NOT NULL UNIQUE,
 session_id CHAR(36) NOT NULL,
 meeting_id CHAR(36) NOT NULL,
 state VARCHAR(16) NOT NULL,
 file_id CHAR(36) NULL,
 upload_id CHAR(36) NULL,
 backend_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 size_bytes BIGINT NULL,
 sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 sample_count BIGINT NULL,
 FOREIGN KEY(input_id) REFERENCES recording_archive_input(id),
 KEY ix_recording_archive_session(session_id)
);
