-- DESIGN CANDIDATE ONLY. UNASSIGNED. DO NOT place in a Flyway location or execute.
-- Task 2 forward runtime metadata; source baseline 34cb226aefe99c67d3681af68d748b0aeea4e073.
-- Requires independent review, root version/order ruling, complete SQL/SHA freeze,
-- separate exact-SHA ACK, and a NEW owned initial-zero fixture before first apply.
-- No historical row backfill, inferred profile, guard initialization, seed, trigger,
-- IF NOT EXISTS, old-table ALTER, or runtime activation is authorized by this file.
-- Existing 12 formal scripts and reserved integers 014..027 remain immutable.
-- MySQL multi-statement DDL is not transactionally rollbackable. Preserve any
-- partial failure and history; no reset, repair, outOfOrder, or old SQL rewrite.

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
