CREATE TABLE transcript_correction_operation (
  operation_id CHAR(36) PRIMARY KEY,
  meeting_id CHAR(36) NOT NULL,
  base_revision BIGINT NOT NULL,
  state VARCHAR(16) NOT NULL,
  before_text MEDIUMTEXT NOT NULL,
  after_text MEDIUMTEXT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE transcript_speaker_access (
  meeting_id CHAR(36) NOT NULL,
  speaker_id VARCHAR(128) NOT NULL,
  enterprise_id VARCHAR(255) NOT NULL,
  PRIMARY KEY (meeting_id, speaker_id, enterprise_id)
);
