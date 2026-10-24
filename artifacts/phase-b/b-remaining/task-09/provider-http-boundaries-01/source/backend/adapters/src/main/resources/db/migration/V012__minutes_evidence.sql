-- V012: adopted B00-1.3 minutes/generation schema prerequisite.
-- All 19 CREATE blocks precede FK additions. Material FKs remain reserved V017.
-- Explicit indexes preserve every deferred-FK backing index in the actual oracle.

CREATE TABLE action_item (
  `element_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_enterprise_id` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `owner_display` longtext NULL,
  `due_on` date NULL,
  PRIMARY KEY (element_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE action_suggestion (
  `element_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_mention` longtext NULL,
  `due_on` date NULL,
  PRIMARY KEY (element_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE background_note (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `set_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `ordinal` bigint unsigned NOT NULL,
  `body` longtext NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX background_order (set_id,ordinal),
  CONSTRAINT ck_background_note_0 CHECK (ordinal > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE background_note_source (
  `note_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `locator_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (note_id,locator_id),
  INDEX background_source_reverse (locator_id,note_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE element_set (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX element_set_revision (meeting_id,revision)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE evidence_link (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `locator_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `element_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `minutes_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `support_state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `target_anchor` longtext NOT NULL,
  PRIMARY KEY (id),
  INDEX evidence_element (element_id,locator_id),
  INDEX evidence_minutes (minutes_revision_id,locator_id),
  CONSTRAINT ck_evidence_link_0 CHECK ((element_id IS NULL) <> (minutes_revision_id IS NULL)),
  CONSTRAINT ck_evidence_link_1 CHECK (support_state IN ('SUPPORTED','PARTIAL','UNSUPPORTED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE exclusion_revision (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX exclusion_version (meeting_id,revision)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE generation_candidate (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `task_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `minutes_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `element_set_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `base_revision` bigint unsigned NOT NULL,
  `input_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `decided_at` datetime(6) NULL,
  PRIMARY KEY (id),
  INDEX candidate_pending (meeting_id,state,created_at,id),
  UNIQUE INDEX candidate_task (task_id),
  CONSTRAINT ck_generation_candidate_0 CHECK (state IN ('PENDING','ADOPTED','REJECTED','STALE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE generation_input (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `transcript_confirmation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `material_snapshot_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `template_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `exclusion_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `element_set_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `prompt_version` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `model_config_version` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `base_revision` bigint unsigned NOT NULL,
  `input_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `scope` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `target_element_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_generation_input_0 CHECK (scope IN ('FULL','ELEMENT')),
  CONSTRAINT ck_generation_input_1 CHECK ((scope='FULL' AND target_element_id IS NULL) OR (scope='ELEMENT' AND target_element_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE minutes_element (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `set_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `category` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `ordinal` bigint unsigned NOT NULL,
  `body` longtext NOT NULL,
  `support_state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX element_order (set_id,category,ordinal),
  CONSTRAINT ck_minutes_element_0 CHECK (category IN ('OVERVIEW','DISCUSSION','CONCLUSION','ACTION','RISK','OPEN_QUESTION','DISAGREEMENT')),
  CONSTRAINT ck_minutes_element_1 CHECK (support_state IN ('SUPPORTED','PARTIAL','UNSUPPORTED','NOT_EXTRACTED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE minutes_revision (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `parent_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `title` longtext NOT NULL,
  `title_origin` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `body` longtext NOT NULL,
  `generation_input_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `created_by` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX minutes_history (meeting_id,revision),
  CONSTRAINT ck_minutes_revision_0 CHECK (title_origin IN ('HUMAN','LINKED','AI'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE minutes_template (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `name` longtext NOT NULL,
  `name_normalized` longtext COLLATE utf8mb4_0900_bin NOT NULL,
  `name_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `current_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX template_name_bucket (owner_id,name_hash,id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE result_review (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `review_no` bigint unsigned NOT NULL,
  `review_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `element_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `minutes_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `target_anchor` longtext NULL,
  `target_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_set_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `hash_profile` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `outcome` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `reviewed_by` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `reviewed_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX review_sequence (meeting_id,review_no),
  INDEX review_element (element_id,review_kind,review_no),
  INDEX review_minutes (minutes_revision_id,review_kind,review_no),
  CONSTRAINT ck_result_review_0 CHECK (review_no > 0),
  CONSTRAINT ck_result_review_1 CHECK ((element_id IS NULL) <> (minutes_revision_id IS NULL)),
  CONSTRAINT ck_result_review_2 CHECK (review_kind IN ('SOURCE_SUPPORT','ACTION_FIELDS')),
  CONSTRAINT ck_result_review_3 CHECK ((review_kind='SOURCE_SUPPORT' AND target_anchor IS NOT NULL AND outcome IN ('SUPPORTED','PARTIAL','UNSUPPORTED')) OR (review_kind='ACTION_FIELDS' AND element_id IS NOT NULL AND target_anchor IS NULL AND outcome='CONFIRMED')),
  CONSTRAINT ck_result_review_4 CHECK (REGEXP_LIKE(target_hash, '^[0-9a-f]{64}$', 'c')),
  CONSTRAINT ck_result_review_5 CHECK (REGEXP_LIKE(source_set_hash, '^[0-9a-f]{64}$', 'c'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE result_review_evidence (
  `review_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `evidence_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (review_id,evidence_id),
  INDEX review_evidence_reverse (evidence_id,review_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE sensitive_detection (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `set_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `category` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `ordinal` bigint unsigned NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX detection_order (set_id,category,ordinal),
  CONSTRAINT ck_sensitive_detection_0 CHECK (ordinal > 0),
  CONSTRAINT ck_sensitive_detection_1 CHECK (category IN ('INTERNAL_BUSINESS_DATA','POLITICAL_SENSITIVE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE sensitive_detection_source (
  `detection_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `locator_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (detection_id,locator_id),
  INDEX detection_source_reverse (locator_id,detection_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE sensitive_exclusion (
  `revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `locator_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `category` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `reason` longtext NULL,
  PRIMARY KEY (revision_id,locator_id,category),
  CONSTRAINT ck_sensitive_exclusion_0 CHECK (category IN ('INTERNAL_BUSINESS_DATA','POLITICAL_SENSITIVE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE source_locator (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `transcript_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `segment_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `material_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `page_no` bigint unsigned NULL,
  `paragraph_no` bigint unsigned NULL,
  `section_path` longtext NULL,
  `start_ms` bigint unsigned NULL,
  `end_ms` bigint unsigned NULL,
  PRIMARY KEY (id),
  INDEX locator_meeting (meeting_id,id),
  CONSTRAINT ck_source_locator_0 CHECK (kind IN ('TRANSCRIPT','AUDIO','PDF_PAGE','PPT_PAGE','WORD_PARAGRAPH','TEXT_PARAGRAPH','KNOWLEDGE_VERSION')),
  CONSTRAINT ck_source_locator_1 CHECK ((kind='TRANSCRIPT' AND transcript_revision_id IS NOT NULL AND segment_id IS NOT NULL) OR (kind='AUDIO' AND file_id IS NOT NULL AND start_ms IS NOT NULL AND end_ms IS NOT NULL AND end_ms>=start_ms) OR (kind IN ('PDF_PAGE','PPT_PAGE') AND file_id IS NOT NULL AND page_no IS NOT NULL AND page_no>0) OR (kind IN ('WORD_PARAGRAPH','TEXT_PARAGRAPH') AND file_id IS NOT NULL AND paragraph_no IS NOT NULL AND paragraph_no>0) OR (kind='KNOWLEDGE_VERSION' AND material_revision_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE template_revision (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `template_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `owner_id` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `name` longtext NOT NULL,
  `themes` longtext NOT NULL,
  `theme_count` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX template_version (template_id,revision),
  INDEX template_owner (owner_id,template_id,revision),
  CONSTRAINT ck_template_revision_0 CHECK (theme_count BETWEEN 1 AND 8)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX fk_generation_input_5 ON generation_input (material_snapshot_id);

CREATE INDEX fk_source_locator_3 ON source_locator (material_revision_id);

ALTER TABLE template_revision ADD CONSTRAINT fk_template_revision_0 FOREIGN KEY (owner_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE template_revision ADD CONSTRAINT fk_template_revision_1 FOREIGN KEY (template_id) REFERENCES minutes_template (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_input ADD CONSTRAINT fk_generation_input_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_input ADD CONSTRAINT fk_generation_input_1 FOREIGN KEY (transcript_confirmation_id) REFERENCES transcript_confirmation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_input ADD CONSTRAINT fk_generation_input_2 FOREIGN KEY (template_revision_id) REFERENCES template_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_input ADD CONSTRAINT fk_generation_input_3 FOREIGN KEY (exclusion_revision_id) REFERENCES exclusion_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_input ADD CONSTRAINT fk_generation_input_4 FOREIGN KEY (element_set_id) REFERENCES element_set (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE minutes_revision ADD CONSTRAINT fk_minutes_revision_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE minutes_revision ADD CONSTRAINT fk_minutes_revision_1 FOREIGN KEY (parent_id) REFERENCES minutes_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE minutes_revision ADD CONSTRAINT fk_minutes_revision_2 FOREIGN KEY (generation_input_id) REFERENCES generation_input (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE element_set ADD CONSTRAINT fk_element_set_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE minutes_element ADD CONSTRAINT fk_minutes_element_0 FOREIGN KEY (set_id) REFERENCES element_set (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE action_item ADD CONSTRAINT fk_action_item_0 FOREIGN KEY (element_id) REFERENCES minutes_element (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE action_item ADD CONSTRAINT fk_action_item_1 FOREIGN KEY (owner_enterprise_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE exclusion_revision ADD CONSTRAINT fk_exclusion_revision_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE sensitive_exclusion ADD CONSTRAINT fk_sensitive_exclusion_0 FOREIGN KEY (revision_id) REFERENCES exclusion_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE sensitive_exclusion ADD CONSTRAINT fk_sensitive_exclusion_1 FOREIGN KEY (locator_id) REFERENCES source_locator (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_candidate ADD CONSTRAINT fk_generation_candidate_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_candidate ADD CONSTRAINT fk_generation_candidate_1 FOREIGN KEY (input_id) REFERENCES generation_input (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_candidate ADD CONSTRAINT fk_generation_candidate_2 FOREIGN KEY (task_id) REFERENCES durable_task (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_candidate ADD CONSTRAINT fk_generation_candidate_3 FOREIGN KEY (minutes_revision_id) REFERENCES minutes_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_candidate ADD CONSTRAINT fk_generation_candidate_4 FOREIGN KEY (element_set_id) REFERENCES element_set (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE source_locator ADD CONSTRAINT fk_source_locator_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE source_locator ADD CONSTRAINT fk_source_locator_1 FOREIGN KEY (transcript_revision_id,segment_id) REFERENCES transcript_segment (revision_id,segment_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE source_locator ADD CONSTRAINT fk_source_locator_2 FOREIGN KEY (file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE evidence_link ADD CONSTRAINT fk_evidence_link_0 FOREIGN KEY (locator_id) REFERENCES source_locator (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE evidence_link ADD CONSTRAINT fk_evidence_link_1 FOREIGN KEY (element_id) REFERENCES minutes_element (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE evidence_link ADD CONSTRAINT fk_evidence_link_2 FOREIGN KEY (minutes_revision_id) REFERENCES minutes_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE minutes_template ADD CONSTRAINT fk_minutes_template_0 FOREIGN KEY (owner_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE sensitive_detection ADD CONSTRAINT fk_sensitive_detection_0 FOREIGN KEY (set_id) REFERENCES element_set (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE sensitive_detection ADD CONSTRAINT fk_sensitive_detection_1 FOREIGN KEY (input_id) REFERENCES generation_input (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE sensitive_detection_source ADD CONSTRAINT fk_sensitive_detection_source_0 FOREIGN KEY (detection_id) REFERENCES sensitive_detection (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE sensitive_detection_source ADD CONSTRAINT fk_sensitive_detection_source_1 FOREIGN KEY (locator_id) REFERENCES source_locator (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE background_note ADD CONSTRAINT fk_background_note_0 FOREIGN KEY (set_id) REFERENCES element_set (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE background_note ADD CONSTRAINT fk_background_note_1 FOREIGN KEY (input_id) REFERENCES generation_input (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE background_note_source ADD CONSTRAINT fk_background_note_source_0 FOREIGN KEY (note_id) REFERENCES background_note (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE background_note_source ADD CONSTRAINT fk_background_note_source_1 FOREIGN KEY (locator_id) REFERENCES source_locator (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE action_suggestion ADD CONSTRAINT fk_action_suggestion_0 FOREIGN KEY (element_id) REFERENCES minutes_element (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE action_suggestion ADD CONSTRAINT fk_action_suggestion_1 FOREIGN KEY (input_id) REFERENCES generation_input (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE result_review ADD CONSTRAINT fk_result_review_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE result_review ADD CONSTRAINT fk_result_review_1 FOREIGN KEY (element_id) REFERENCES minutes_element (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE result_review ADD CONSTRAINT fk_result_review_2 FOREIGN KEY (minutes_revision_id) REFERENCES minutes_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE result_review_evidence ADD CONSTRAINT fk_result_review_evidence_0 FOREIGN KEY (review_id) REFERENCES result_review (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE result_review_evidence ADD CONSTRAINT fk_result_review_evidence_1 FOREIGN KEY (evidence_id) REFERENCES evidence_link (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
