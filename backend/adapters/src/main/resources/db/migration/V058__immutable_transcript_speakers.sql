-- A human label is immutable. Renaming/merging creates a different speaker ID
-- and a new WORKING; historical confirmations continue to reference the old ID.
CREATE TABLE transcript_speaker_snapshot (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 label VARCHAR(256) NOT NULL,
 enterprise_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
 identity_source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 CONSTRAINT fk_speaker_snapshot_identity FOREIGN KEY(id) REFERENCES speaker(id),
 CONSTRAINT fk_speaker_snapshot_meeting FOREIGN KEY(meeting_id) REFERENCES meeting(id),
 CONSTRAINT ck_speaker_snapshot_source CHECK(identity_source='HUMAN'),
 CONSTRAINT ck_speaker_snapshot_label CHECK(CHAR_LENGTH(label) BETWEEN 1 AND 256)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE transcript_speaker_change (
 revision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 segment_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 before_speaker_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 after_speaker_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 PRIMARY KEY(revision_id,segment_id),
 CONSTRAINT fk_speaker_change_segment FOREIGN KEY(revision_id,segment_id) REFERENCES transcript_segment(revision_id,segment_id),
 CONSTRAINT fk_speaker_change_before FOREIGN KEY(before_speaker_id) REFERENCES transcript_speaker_snapshot(id),
 CONSTRAINT fk_speaker_change_after FOREIGN KEY(after_speaker_id) REFERENCES transcript_speaker_snapshot(id),
 CONSTRAINT ck_speaker_change_distinct CHECK(NOT (before_speaker_id <=> after_speaker_id))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
