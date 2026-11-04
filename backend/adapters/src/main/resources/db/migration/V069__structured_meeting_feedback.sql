SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Legacy submissions remain legacy: never infer fields by parsing comment.
ALTER TABLE feedback
  ADD COLUMN feedback_type varchar(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD COLUMN original_value text NULL,
  ADD COLUMN suggested_value text NULL,
  ADD COLUMN note text NULL,
  ADD CONSTRAINT ck_feedback_structured CHECK (
    (feedback_type IS NULL AND original_value IS NULL AND suggested_value IS NULL AND note IS NULL)
    OR (feedback_type IN ('PERSON','DEPARTMENT','PRODUCT','FINANCIAL_TERM','PRODUCT_ALIAS','TERM_PRONUNCIATION')
        AND original_value IS NOT NULL AND suggested_value IS NOT NULL AND note IS NOT NULL));
ALTER TABLE corpus_feedback_binding
  ADD COLUMN transcript_revision_id char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD COLUMN adoption_state varchar(16) NOT NULL DEFAULT 'NOT_APPLIED',
  ADD COLUMN adoption_version varchar(128) NULL,
  ADD COLUMN adoption_fingerprint char(64) CHARACTER SET ascii NULL,
  ADD COLUMN adoption_sequence bigint NOT NULL DEFAULT 0,
  ADD COLUMN adoption_pending json NULL,
  ADD COLUMN adoption_checked_at datetime(6) NULL;

CREATE TABLE meeting_voiceprint_application (
 id char(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 meeting_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 applicant_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 subject_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 subject_name varchar(256) NOT NULL,
 transcript_revision_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_revision bigint unsigned NOT NULL,
 segment_ids json NOT NULL,
 consent_reference varchar(2048) NOT NULL,
 expires_at datetime(6) NOT NULL,
 policy_version varchar(128) NOT NULL,
 model_version varchar(128) NOT NULL,
 dimension int NOT NULL,
 sample_sha char(64) CHARACTER SET ascii NOT NULL,
 sample_count bigint NOT NULL,
 encrypted_embedding longblob NULL,
 state varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
 revision bigint unsigned NOT NULL DEFAULT 0,
 review_reason text NULL,
 reviewed_by varchar(255) COLLATE utf8mb4_0900_bin NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX voice_application_applicant(applicant_id,created_at,id),
 CONSTRAINT fk_voice_application_meeting FOREIGN KEY(meeting_id) REFERENCES meeting(id),
 CONSTRAINT fk_voice_application_applicant FOREIGN KEY(applicant_id) REFERENCES enterprise_identity(enterprise_id),
 CONSTRAINT ck_voice_application_state CHECK(state IN ('PENDING','ACCEPTED','REJECTED','NEEDS_MORE')),
 CONSTRAINT ck_voice_application_sample CHECK(sample_count BETWEEN 48000 AND 800000)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE meeting_voiceprint_history (
 id char(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 application_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 action varchar(32) NOT NULL,
 revision_after bigint unsigned NOT NULL,
 state_after varchar(32) NOT NULL,
 text text NOT NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY voice_history_revision(application_id,revision_after),
 CONSTRAINT fk_voice_history_application FOREIGN KEY(application_id) REFERENCES meeting_voiceprint_application(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE governed_voiceprint DROP CHECK ck_voice_vector;
ALTER TABLE governed_voiceprint ADD CONSTRAINT ck_voice_vector CHECK(dimension BETWEEN 1 AND 16384 AND sample_count BETWEEN 48000 AND 800000 AND (state<>'REVOKED' OR encrypted_embedding IS NULL));
