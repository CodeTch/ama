SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE corpus_feedback_binding (
 feedback_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 source_revision bigint unsigned NOT NULL,
 segment_ids json NOT NULL,
 withdrawn boolean NOT NULL DEFAULT false,
 withdrawn_at datetime(6) NULL,
 CONSTRAINT fk_corpus_feedback FOREIGN KEY (feedback_id) REFERENCES feedback(id)
) ENGINE=InnoDB;

CREATE TABLE governance_command (
 scope_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 actor_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 operation varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result_json json NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_operations_actor FOREIGN KEY (actor_id) REFERENCES enterprise_identity(enterprise_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE governed_voiceprint (
 id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 subject_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 consent_reference varchar(1024) COLLATE utf8mb4_0900_bin NOT NULL,
 purpose varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 model_version varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 dimension int NOT NULL,
 sample_sha char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 sample_count int NOT NULL,
 expires_at datetime(6) NOT NULL,
 state varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 revision bigint unsigned NOT NULL DEFAULT 0,
 encrypted_embedding mediumblob NULL,
 reviewer_id varchar(255) COLLATE utf8mb4_0900_bin NULL,
 review_reason varchar(2048) COLLATE utf8mb4_0900_bin NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_voice_subject FOREIGN KEY (subject_id) REFERENCES enterprise_identity(enterprise_id),
 CONSTRAINT ck_voice_state CHECK (state IN ('PENDING','APPROVED','REJECTED','REVOKED')),
 CONSTRAINT ck_voice_vector CHECK (dimension BETWEEN 1 AND 16384 AND sample_count BETWEEN 48000 AND 480000 AND (state<>'REVOKED' OR encrypted_embedding IS NULL)),
 KEY governed_voice_match (state,model_version,expires_at),
 KEY governed_voice_subject (subject_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE governance_dataset (
 id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 version varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 owner_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 samples_json json NOT NULL,
 content_sha char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_dataset_actor FOREIGN KEY (owner_id) REFERENCES enterprise_identity(enterprise_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE governance_evaluation (
 id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 dataset_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 config_json json NOT NULL,
 actor_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 state varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 lease_owner char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until datetime(6) NULL,
 review_reason varchar(2048) COLLATE utf8mb4_0900_bin NULL,
 reviewed_by varchar(255) COLLATE utf8mb4_0900_bin NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 finished_at datetime(6) NULL,
 CONSTRAINT fk_evaluation_dataset FOREIGN KEY (dataset_id) REFERENCES governance_dataset(id),
 CONSTRAINT fk_evaluation_actor FOREIGN KEY (actor_id) REFERENCES enterprise_identity(enterprise_id),
 CONSTRAINT ck_evaluation_state CHECK (state IN ('PENDING','RUNNING','COMPLETED','CANCELLED')),
 KEY operations_evaluation_due (state,lease_until,created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE governance_evaluation_sample (
 run_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 sample_id varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result_json json NOT NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY (run_id,sample_id),
 CONSTRAINT fk_evaluation_sample FOREIGN KEY (run_id) REFERENCES governance_evaluation(id)
) ENGINE=InnoDB;

CREATE TABLE governance_configuration (
 revision bigint unsigned NOT NULL PRIMARY KEY,
 config_json json NOT NULL,
 evaluation_id char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 actor_id varchar(255) COLLATE utf8mb4_0900_bin NULL,
 rollback_of bigint unsigned NULL,
 reason varchar(2048) COLLATE utf8mb4_0900_bin NOT NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_config_eval FOREIGN KEY (evaluation_id) REFERENCES governance_evaluation(id),
 CONSTRAINT fk_config_actor FOREIGN KEY (actor_id) REFERENCES enterprise_identity(enterprise_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
INSERT INTO governance_configuration(revision,config_json,reason) VALUES (0,'{"modelVersion":"harness-embed-v1","promptVersion":"summary-v1","voiceThreshold":null}','Initial matching disabled; no calibrated threshold');

CREATE TABLE minutes_mail_job (
 id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 meeting_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 publication_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_revision bigint unsigned NOT NULL,
 actor_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 recipient_ids json NOT NULL,
 request_json json NULL,
 state varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 message_id varchar(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
 last_error varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_owner char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until datetime(6) NULL,
 next_check_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 finished_at datetime(6) NULL,
 CONSTRAINT fk_mail_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id),
 CONSTRAINT fk_mail_publication FOREIGN KEY (publication_id) REFERENCES publication(id),
 CONSTRAINT fk_mail_actor FOREIGN KEY (actor_id) REFERENCES enterprise_identity(enterprise_id),
 CONSTRAINT ck_mail_job_state CHECK (state IN ('PENDING','UNKNOWN','SENT','FAILED','BLOCKED')),
 KEY operations_mail_due (state,next_check_at,lease_until),
 KEY operations_mail_history (meeting_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
