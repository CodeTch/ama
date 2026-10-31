CREATE TABLE recording_session (
  session_id CHAR(36) PRIMARY KEY,
  meeting_id CHAR(36) NOT NULL,
  part_id CHAR(36) NOT NULL,
  writer_epoch BIGINT NOT NULL,
  backend_id VARCHAR(128) NOT NULL,
  state VARCHAR(32) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  UNIQUE KEY uq_recording_part (session_id, part_id)
);
CREATE TABLE recording_chunk (
  session_id CHAR(36) NOT NULL,
  part_id CHAR(36) NOT NULL,
  seq BIGINT NOT NULL,
  sample_start BIGINT NOT NULL,
  sample_count BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  bytes BIGINT NOT NULL,
  persisted_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (session_id, part_id, seq),
  CONSTRAINT fk_recording_chunk_session FOREIGN KEY (session_id) REFERENCES recording_session(session_id)
);
