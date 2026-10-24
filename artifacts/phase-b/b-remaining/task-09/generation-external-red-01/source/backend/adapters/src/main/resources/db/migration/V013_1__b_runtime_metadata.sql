-- V013_1__b_runtime_metadata.sql; owner: Phase B remaining Task 2.
-- Reviewed runtime metadata: immutable batch profile and sticky editor intent guard.
-- Root allocated 013.1 in 46a1adad4dd4afda3272e34669235d4126c480d6.
-- Source baseline a3b2933ef83035e858d2d3336decba4617f22a1f.
-- Apply only after committed complete-byte SHA freeze and separate exact-SHA ACK.
-- No historical backfill, inferred profile, legacy guard initialization or defaults.
-- Existing twelve scripts and reserved integers 014..027 remain immutable.
-- MySQL DDL can partially commit: preserve partial failure/history; no reset,
-- repair, outOfOrder, old SQL rewrite or runtime activation.

CREATE TABLE input_batch_fingerprint_profile (
  `batch_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `profile` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (batch_id),
  CONSTRAINT ck_input_batch_fingerprint_profile_0
    CHECK (OCTET_LENGTH(profile) = 18 AND profile = 'ama.input-batch.v1')
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE meeting_minutes_editor_guard (
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `intent_epoch` bigint NOT NULL,
  PRIMARY KEY (meeting_id),
  CONSTRAINT ck_meeting_minutes_editor_guard_0 CHECK (intent_epoch BETWEEN 0 AND 9223372036854775806)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE input_batch_fingerprint_profile
  ADD CONSTRAINT fk_input_batch_fingerprint_profile_0
  FOREIGN KEY (batch_id) REFERENCES input_batch (id)
  ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE meeting_minutes_editor_guard
  ADD CONSTRAINT fk_meeting_minutes_editor_guard_0
  FOREIGN KEY (meeting_id) REFERENCES meeting (id)
  ON UPDATE RESTRICT ON DELETE RESTRICT;
