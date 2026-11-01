-- Content belongs to the meeting and is physically purged with it. A candidate
-- binds one immutable raw speech clip, not an arbitrary client-supplied string.
CREATE TABLE speech_review_candidate (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 raw_revision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_source_revision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 input_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 segment_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 file_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 clip_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 engine_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 recognition_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 enhancement_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 before_text MEDIUMTEXT NOT NULL,
 after_text MEDIUMTEXT NOT NULL,
 state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 decision_revision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 undo_revision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 created_by VARCHAR(255) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 decided_at DATETIME(6) NULL,
 UNIQUE KEY speech_review_original(meeting_id,raw_revision_id,segment_id,engine_sha256),
 INDEX speech_review_pending(meeting_id,state,id),
 CONSTRAINT fk_speech_review_meeting FOREIGN KEY(meeting_id) REFERENCES meeting(id),
 CONSTRAINT fk_speech_review_raw FOREIGN KEY(raw_revision_id) REFERENCES transcript_revision(id),
 CONSTRAINT fk_speech_review_raw_segment FOREIGN KEY(raw_revision_id,segment_id) REFERENCES transcript_segment(revision_id,segment_id),
 CONSTRAINT fk_speech_review_created_source FOREIGN KEY(created_source_revision_id) REFERENCES transcript_revision(id),
 CONSTRAINT fk_speech_review_created_segment FOREIGN KEY(created_source_revision_id,segment_id) REFERENCES transcript_segment(revision_id,segment_id),
 CONSTRAINT fk_speech_review_input FOREIGN KEY(input_id) REFERENCES transcription_input(id),
 CONSTRAINT fk_speech_review_file FOREIGN KEY(file_id) REFERENCES file_object(id),
 CONSTRAINT fk_speech_review_decision FOREIGN KEY(decision_revision_id) REFERENCES transcript_revision(id),
 CONSTRAINT fk_speech_review_undo FOREIGN KEY(undo_revision_id) REFERENCES transcript_revision(id),
 CONSTRAINT ck_speech_review_state CHECK (state IN ('PENDING','ACCEPTED','REJECTED','UNDONE','STALE')),
 CONSTRAINT ck_speech_review_text CHECK (CHAR_LENGTH(before_text) BETWEEN 1 AND 65536 AND CHAR_LENGTH(after_text) BETWEEN 1 AND 65536 AND BINARY before_text<>BINARY after_text),
 CONSTRAINT ck_speech_review_decision CHECK ((state='PENDING' AND decision_revision_id IS NULL AND decided_at IS NULL) OR (state='STALE' AND decision_revision_id IS NULL AND decided_at IS NOT NULL) OR (state IN ('ACCEPTED','REJECTED','UNDONE') AND decision_revision_id IS NOT NULL AND decided_at IS NOT NULL)),
 CONSTRAINT ck_speech_review_undo CHECK ((state='UNDONE')=(undo_revision_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- A retry restores the accepted immutable list of candidate IDs. It does not
-- regenerate proposals against a later human draft or reattach old pointers.
CREATE TABLE speech_review_batch (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 scope_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_revision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 expected_revision BIGINT UNSIGNED NOT NULL,
 actor_id VARCHAR(255) NOT NULL,
 candidate_ids JSON NOT NULL,
 created_count INT UNSIGNED NOT NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_speech_review_batch_meeting FOREIGN KEY(meeting_id) REFERENCES meeting(id),
 CONSTRAINT fk_speech_review_batch_source FOREIGN KEY(source_revision_id) REFERENCES transcript_revision(id),
 CONSTRAINT ck_speech_review_batch_ids CHECK (JSON_TYPE(candidate_ids)='ARRAY' AND JSON_LENGTH(candidate_ids)<=60 AND OCTET_LENGTH(candidate_ids)<=4096 AND created_count<=JSON_LENGTH(candidate_ids))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
