-- V013: adopted B00-1.3 publication schema prerequisite.
-- All five CREATE blocks precede FK additions. Material FKs remain reserved V017.
-- Explicit indexes preserve every deferred-FK backing index in the actual oracle.

CREATE TABLE external_document_mapping (
  `publication_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `external_document_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `document_url` longtext NOT NULL,
  `authority_state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `verified_at` datetime(6) NOT NULL,
  PRIMARY KEY (publication_id),
  UNIQUE INDEX external_document_unique (provider_id,external_document_id),
  CONSTRAINT ck_external_document_mapping_0 CHECK (authority_state IN ('ACTIVE','WITHDRAWN','OBSOLETE','UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE final_confirmation (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_revision` bigint unsigned NOT NULL,
  `info_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `roster_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `transcript_confirmation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `minutes_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `material_snapshot_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `element_set_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `template_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `exclusion_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_manifest_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `confirmed_by` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `confirmed_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX final_revision (meeting_id,meeting_revision)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE final_result_review (
  `confirmation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `review_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (confirmation_id,review_id),
  INDEX final_review_reverse (review_id,confirmation_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE publication (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `confirmation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `business_key` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `library_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `folder_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `published_at` datetime(6) NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX publication_meeting (meeting_id),
  UNIQUE INDEX publication_key (business_key),
  CONSTRAINT ck_publication_0 CHECK (state IN ('READY','RUNNING','RECONCILING','FAILED','SUCCEEDED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE publication_attempt (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `publication_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `confirmation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `operation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `outcome` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) NULL,
  `execution_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (id),
  INDEX publication_attempt_history (publication_id,created_at,id),
  CONSTRAINT ck_publication_attempt_0 CHECK (kind IN ('PUBLISH','RECONCILE','WITHDRAW','OBSOLETE')),
  CONSTRAINT ck_publication_attempt_1 CHECK (outcome IN ('PENDING','SUCCESS','FAILED','UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX fk_final_confirmation_9 ON final_confirmation (material_snapshot_id);

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_1 FOREIGN KEY (info_revision_id) REFERENCES meeting_info_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_2 FOREIGN KEY (roster_revision_id) REFERENCES roster_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_3 FOREIGN KEY (transcript_confirmation_id) REFERENCES transcript_confirmation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_4 FOREIGN KEY (minutes_revision_id) REFERENCES minutes_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_5 FOREIGN KEY (element_set_id) REFERENCES element_set (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_6 FOREIGN KEY (template_revision_id) REFERENCES template_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_7 FOREIGN KEY (exclusion_revision_id) REFERENCES exclusion_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_8 FOREIGN KEY (confirmed_by) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE publication ADD CONSTRAINT fk_publication_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE publication ADD CONSTRAINT fk_publication_1 FOREIGN KEY (confirmation_id) REFERENCES final_confirmation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE publication_attempt ADD CONSTRAINT fk_publication_attempt_0 FOREIGN KEY (publication_id) REFERENCES publication (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE publication_attempt ADD CONSTRAINT fk_publication_attempt_1 FOREIGN KEY (confirmation_id) REFERENCES final_confirmation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE publication_attempt ADD CONSTRAINT fk_publication_attempt_2 FOREIGN KEY (operation_id) REFERENCES external_operation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE publication_attempt ADD CONSTRAINT fk_publication_attempt_3 FOREIGN KEY (execution_id) REFERENCES external_execution (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE external_document_mapping ADD CONSTRAINT fk_external_document_mapping_0 FOREIGN KEY (publication_id) REFERENCES publication (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_result_review ADD CONSTRAINT fk_final_result_review_0 FOREIGN KEY (confirmation_id) REFERENCES final_confirmation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_result_review ADD CONSTRAINT fk_final_result_review_1 FOREIGN KEY (review_id) REFERENCES result_review (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
