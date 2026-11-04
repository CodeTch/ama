SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE governance_sample_asset (
 id char(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 name varchar(160) NOT NULL,
 content_sha char(64) CHARACTER SET ascii NOT NULL,
 sample_count int NOT NULL,
 wav mediumblob NOT NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_sample_asset_actor FOREIGN KEY(owner_id) REFERENCES enterprise_identity(enterprise_id),
 CONSTRAINT ck_sample_asset_bound CHECK(sample_count BETWEEN 16000 AND 1920000),
 INDEX sample_assets_owner(owner_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

ALTER TABLE governance_evaluation ADD COLUMN rules_json json NULL,
 ADD COLUMN candidate_id char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD COLUMN candidate_revision bigint unsigned NULL,
 ADD COLUMN parent_run_id char(36) CHARACTER SET ascii COLLATE ascii_bin NULL;

CREATE TABLE governance_sample_review (
 run_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 sample_id varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reviewer_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 rubric_json json NOT NULL,
 reason varchar(2048) NOT NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(run_id,sample_id),
 CONSTRAINT fk_sample_review_run FOREIGN KEY(run_id,sample_id) REFERENCES governance_evaluation_sample(run_id,sample_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE governance_candidate (
 id char(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 title varchar(160) NOT NULL,
 revision bigint unsigned NOT NULL DEFAULT 0,
 configuration json NOT NULL,
 rules_json json NOT NULL,
 content_sha char(64) CHARACTER SET ascii NOT NULL,
 creator_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 state varchar(16) NOT NULL DEFAULT 'DRAFT',
 evaluation_id char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 approved_by varchar(255) COLLATE utf8mb4_0900_bin NULL,
 approval_reason varchar(2048) NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT ck_candidate_state CHECK(state IN ('DRAFT','APPROVED','PUBLISHED')),
 CONSTRAINT fk_candidate_creator FOREIGN KEY(creator_id) REFERENCES enterprise_identity(enterprise_id),
 CONSTRAINT fk_candidate_evaluation FOREIGN KEY(evaluation_id) REFERENCES governance_evaluation(id),
 INDEX candidates_page(updated_at,id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE governance_candidate_history (
 candidate_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 revision bigint unsigned NOT NULL,
 snapshot json NOT NULL,
 actor_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 action varchar(32) NOT NULL,
 reason varchar(2048) NOT NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(candidate_id,revision),
 CONSTRAINT fk_candidate_history FOREIGN KEY(candidate_id) REFERENCES governance_candidate(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE governance_runtime_snapshot (
 input_id char(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 configuration_revision bigint unsigned NOT NULL,
 configuration json NOT NULL,
 corpus_json json NOT NULL,
 snapshot_sha char(64) CHARACTER SET ascii NOT NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_runtime_config FOREIGN KEY(configuration_revision) REFERENCES governance_configuration(revision)
) ENGINE=InnoDB;
CREATE TABLE governance_speaker_candidate (
 input_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 file_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 start_sample bigint unsigned NOT NULL,
 speaker_id varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 candidates_json json NOT NULL,
 configuration_revision bigint unsigned NOT NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(input_id,file_id,start_sample,speaker_id),
 CONSTRAINT fk_candidate_runtime FOREIGN KEY(input_id) REFERENCES governance_runtime_snapshot(input_id)
) ENGINE=InnoDB;

CREATE TABLE governed_voiceprint_history (
 voiceprint_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 revision bigint unsigned NOT NULL,
 actor_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 state varchar(16) NOT NULL,
 reason varchar(2048) NOT NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(voiceprint_id,revision),
 CONSTRAINT fk_voice_history FOREIGN KEY(voiceprint_id) REFERENCES governed_voiceprint(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

