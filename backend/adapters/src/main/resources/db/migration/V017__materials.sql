-- D01 reserved V017. Coordinator owns freeze/application.
CREATE TABLE material_reference (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `external_id` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (id),
  INDEX material_meeting (meeting_id,state,id),
  CONSTRAINT ck_material_reference_0 CHECK (kind IN ('LOCAL','KNOWLEDGE')),
  CONSTRAINT ck_material_reference_1 CHECK (state IN ('ACTIVE','REMOVED')),
  CONSTRAINT ck_material_reference_2 CHECK ((kind='LOCAL' AND file_id IS NOT NULL AND external_id IS NULL) OR (kind='KNOWLEDGE' AND provider_id IS NOT NULL AND external_id IS NOT NULL AND file_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner D-01; planned V017: 固定源版本；外部引用永不跟随最新

CREATE TABLE material_revision (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `reference_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `external_version` longtext NULL,
  `content_file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `parse_state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX material_version (reference_id,revision),
  CONSTRAINT ck_material_revision_0 CHECK (parse_state IN ('PENDING','READY','FAILED','UNSUPPORTED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner D-01; planned V017: 已明确应用材料集合；待修订不能改有效集合

CREATE TABLE material_snapshot (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `input_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `applied_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX material_snapshot_revision (meeting_id,revision)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner D-01; planned V017: 冻结材料修订清单

CREATE TABLE material_snapshot_item (
  `snapshot_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `material_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `ordinal` bigint unsigned NOT NULL,
  PRIMARY KEY (snapshot_id,material_revision_id),
  UNIQUE INDEX snapshot_order (snapshot_id,ordinal)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner D-01; planned V017: 解析版本及能力状态；不把空解析当成功

CREATE TABLE parsed_document (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `material_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `parser_version` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `text_file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `unit_count` bigint unsigned NOT NULL,
  `error_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_parsed_document_0 CHECK (state IN ('READY','FAILED','UNSUPPORTED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner D-01; planned V017: 文本与页/段映射保留

CREATE TABLE parsed_unit (
  `document_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `ordinal` bigint unsigned NOT NULL,
  `kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `page_no` bigint unsigned NULL,
  `paragraph_no` bigint unsigned NULL,
  `section_path` longtext NULL,
  `body` longtext NOT NULL,
  PRIMARY KEY (document_id,ordinal),
  CONSTRAINT ck_parsed_unit_0 CHECK (kind IN ('PAGE','PARAGRAPH','SECTION'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;


CREATE TABLE parsed_document_profile (
 document_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 format varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 parser_config_version varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 parser_config_sha256 char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 CONSTRAINT ck_material_parse_format CHECK (format IN ('PDF','PPT','WORD','TXT')),
 CONSTRAINT fk_material_parse_profile FOREIGN KEY (document_id) REFERENCES parsed_document(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE material_command_receipt (
 meeting_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 scope_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 material_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 material_revision_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(meeting_id,scope_hash),
 CONSTRAINT fk_material_receipt_meeting FOREIGN KEY(meeting_id) REFERENCES meeting(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
 CONSTRAINT fk_material_receipt_reference FOREIGN KEY(material_id) REFERENCES material_reference(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
 CONSTRAINT fk_material_receipt_revision FOREIGN KEY(material_revision_id) REFERENCES material_revision(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE source_context (
 id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 meeting_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 origin_kind varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 generation_input_id char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 transcript_confirmation_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 material_snapshot_id char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 catalog_version varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 catalog_sha256 char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at datetime(6) NOT NULL,
 UNIQUE INDEX source_context_generation(generation_input_id),
 INDEX source_context_meeting(meeting_id,id),
 CONSTRAINT ck_source_context_kind CHECK ((origin_kind='GENERATION_INPUT' AND generation_input_id IS NOT NULL) OR (origin_kind='MANUAL_SELECTION' AND generation_input_id IS NULL)),
 CONSTRAINT fk_source_context_meeting FOREIGN KEY(meeting_id) REFERENCES meeting(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
 CONSTRAINT fk_source_context_generation FOREIGN KEY(generation_input_id) REFERENCES generation_input(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
 CONSTRAINT fk_source_context_confirmation FOREIGN KEY(transcript_confirmation_id) REFERENCES transcript_confirmation(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
 CONSTRAINT fk_source_context_snapshot FOREIGN KEY(material_snapshot_id) REFERENCES material_snapshot(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE source_context_material (
 context_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 material_revision_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 parsed_document_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(context_id,material_revision_id),
 CONSTRAINT fk_source_context_material_context FOREIGN KEY(context_id) REFERENCES source_context(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
 CONSTRAINT fk_source_context_material_revision FOREIGN KEY(material_revision_id) REFERENCES material_revision(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
 CONSTRAINT fk_source_context_material_document FOREIGN KEY(parsed_document_id) REFERENCES parsed_document(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE generation_input ADD CONSTRAINT fk_generation_input_5 FOREIGN KEY (material_snapshot_id) REFERENCES material_snapshot (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE source_locator ADD CONSTRAINT fk_source_locator_3 FOREIGN KEY (material_revision_id) REFERENCES material_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_9 FOREIGN KEY (material_snapshot_id) REFERENCES material_snapshot (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE material_reference ADD CONSTRAINT fk_material_reference_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE material_reference ADD CONSTRAINT fk_material_reference_1 FOREIGN KEY (file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE material_revision ADD CONSTRAINT fk_material_revision_0 FOREIGN KEY (reference_id) REFERENCES material_reference (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE material_revision ADD CONSTRAINT fk_material_revision_1 FOREIGN KEY (content_file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE material_snapshot ADD CONSTRAINT fk_material_snapshot_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE material_snapshot_item ADD CONSTRAINT fk_material_snapshot_item_0 FOREIGN KEY (snapshot_id) REFERENCES material_snapshot (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE material_snapshot_item ADD CONSTRAINT fk_material_snapshot_item_1 FOREIGN KEY (material_revision_id) REFERENCES material_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE parsed_document ADD CONSTRAINT fk_parsed_document_0 FOREIGN KEY (material_revision_id) REFERENCES material_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE parsed_document ADD CONSTRAINT fk_parsed_document_1 FOREIGN KEY (text_file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE parsed_unit ADD CONSTRAINT fk_parsed_unit_0 FOREIGN KEY (document_id) REFERENCES parsed_document (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE transcription_input ADD CONSTRAINT fk_transcription_input_3 FOREIGN KEY (material_snapshot_id) REFERENCES material_snapshot (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE source_context ADD CONSTRAINT fk_source_context_3 FOREIGN KEY (material_snapshot_id) REFERENCES material_snapshot (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE source_context_material ADD CONSTRAINT fk_source_context_material_1 FOREIGN KEY (material_revision_id) REFERENCES material_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE minutes_element ADD COLUMN source_context_id char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD INDEX minutes_element_context(source_context_id,id),
 ADD CONSTRAINT fk_minutes_element_context FOREIGN KEY(source_context_id) REFERENCES source_context(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE evidence_link ADD COLUMN source_context_id char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD INDEX evidence_link_context(source_context_id,id),
 ADD CONSTRAINT fk_evidence_link_context FOREIGN KEY(source_context_id) REFERENCES source_context(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
-- Additive projections keep the historical seven-column rowset hash byte-compatible.
CREATE VIEW evidence_source_context AS SELECT id,source_context_id FROM evidence_link WHERE source_context_id IS NOT NULL;
CREATE VIEW element_source_context AS SELECT id,source_context_id FROM minutes_element WHERE source_context_id IS NOT NULL;
