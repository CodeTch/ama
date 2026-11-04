CREATE TABLE material_enhancement_candidate (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
 request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 expected_revision BIGINT NOT NULL,
 source_revision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 material_snapshot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 profile_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 input_body MEDIUMTEXT NOT NULL,
 source_body MEDIUMTEXT NOT NULL,
 output_body MEDIUMTEXT NULL,
 decision_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 receipt_body MEDIUMTEXT NULL,
 created_at DATETIME(6) NOT NULL,
 UNIQUE KEY material_enhancement_request(meeting_id, actor_id, request_key),
 CONSTRAINT material_enhancement_meeting FOREIGN KEY(meeting_id) REFERENCES meeting(id)
);
