-- B-04 schema prerequisite / adopted B00-1.2-review. Requires reviewed V008.
-- Stop business writers; schema creation does not enable registry/upload/API writes.
-- Backend configuration/credentials and enabled purposes are runtime-owned inputs,
-- never production seed rows. Logical backend IDs and object locations stay pinned;
-- provider_version_id is optional provenance under the immutable-key contract.
-- Existence FKs do not enforce same-meeting ownership, legal lifecycle transitions,
-- immutable accepted receipts, reference/epoch cleanup, or blob-before-DB durability.
-- Those remain the later B-04 application/common-backend obligations after V010.
-- MySQL multi-statement DDL is not transactional: preserve failed history and stop.
-- Once shared or applied this migration is immutable; fixes need a registered delta.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE storage_backend (
  `id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `storage_type` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `config_revision` bigint unsigned NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_storage_backend_0 CHECK (storage_type IN ('local','s3')),
  CONSTRAINT ck_storage_backend_1 CHECK (status IN ('WRITABLE','READ_ONLY','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE file_object (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `backend_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `storage_type` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `namespace` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `object_key` varchar(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_version_id` longtext NULL,
  `display_name` longtext NOT NULL,
  `size_bytes` bigint unsigned NOT NULL,
  `sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `location_revision` bigint unsigned NOT NULL DEFAULT 0,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `reference_epoch` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX file_location (backend_id,namespace,object_key),
  INDEX file_meeting (meeting_id,state,id),
  INDEX file_cleanup (state,created_at,id),
  CONSTRAINT ck_file_object_0 CHECK (storage_type IN ('local','s3')),
  CONSTRAINT ck_file_object_1 CHECK (state IN ('AVAILABLE','DELETE_PENDING','DELETED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE file_reference (
  `file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `purpose` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (file_id,owner_kind,owner_id,purpose),
  INDEX file_ref_owner (owner_kind,owner_id,file_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE upload_session (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `backend_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `namespace` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `object_key` varchar(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `cancel_epoch` bigint unsigned NOT NULL,
  `expected_size` bigint unsigned NOT NULL,
  `expected_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `provider_upload_token` longtext NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `completed_file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `display_name` longtext NOT NULL,
  `purpose` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (id),
  INDEX upload_meeting (meeting_id,state,id),
  UNIQUE INDEX upload_target (backend_id,namespace,object_key),
  CONSTRAINT ck_upload_session_0 CHECK (state IN ('OPEN','FINALIZING','COMPLETED','ABORTED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE upload_part (
  `upload_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `part_no` bigint unsigned NOT NULL,
  `size_bytes` bigint unsigned NOT NULL,
  `sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_token` longtext NULL,
  PRIMARY KEY (upload_id,part_no),
  CONSTRAINT ck_upload_part_0 CHECK (part_no > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE file_cleanup (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `backend_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `namespace` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `object_key` varchar(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `expected_location_revision` bigint unsigned NULL,
  `expected_reference_epoch` bigint unsigned NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `not_before` datetime(6) NOT NULL,
  `attempt` bigint unsigned NOT NULL DEFAULT 0,
  `last_error` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (id),
  INDEX cleanup_due (state,not_before,id),
  UNIQUE INDEX cleanup_target (backend_id,namespace,object_key),
  CONSTRAINT ck_file_cleanup_0 CHECK (state IN ('PENDING','RUNNING','FAILED','COMPLETE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE file_object ADD CONSTRAINT fk_file_object_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE file_object ADD CONSTRAINT fk_file_object_1 FOREIGN KEY (backend_id) REFERENCES storage_backend (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE file_reference ADD CONSTRAINT fk_file_reference_0 FOREIGN KEY (file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE upload_session ADD CONSTRAINT fk_upload_session_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE upload_session ADD CONSTRAINT fk_upload_session_1 FOREIGN KEY (backend_id) REFERENCES storage_backend (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE upload_session ADD CONSTRAINT fk_upload_session_2 FOREIGN KEY (completed_file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE upload_part ADD CONSTRAINT fk_upload_part_0 FOREIGN KEY (upload_id) REFERENCES upload_session (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE file_cleanup ADD CONSTRAINT fk_file_cleanup_0 FOREIGN KEY (file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE file_cleanup ADD CONSTRAINT fk_file_cleanup_1 FOREIGN KEY (backend_id) REFERENCES storage_backend (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
