-- B-03 schema prerequisite / adopted B00-1.2-review. Requires reviewed V007.
-- Stop business writers; keep meeting business writes disabled until the reviewed
-- subject/outbox prerequisite V010 and compatible domain services are deployed.
-- Legacy meeting_acl is preserved. No timestamp is confirmation provenance and
-- this migration never fabricates effective grants. Populated legacy ACL requires
-- an independently reviewed mapping/recovery plan before migration can proceed.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- This connection-local preflight is before ALL permanent V008 DDL. CHECK also
-- rejects in non-strict SQL mode. MySQL multi-statement DDL is not transactional;
-- a failure preserves source rows but requires explicit operator assessment of
-- the failed Flyway history. Never repair/disable checks or rewrite this script.
CREATE TEMPORARY TABLE b03_v008_acl_preflight (
    compatible TINYINT NOT NULL,
    CONSTRAINT chk_meeting_v008_legacy_acl_empty CHECK (compatible = 1)
);
INSERT INTO b03_v008_acl_preflight (compatible)
SELECT NOT EXISTS (SELECT 1 FROM meeting_acl);
DROP TEMPORARY TABLE b03_v008_acl_preflight;

-- Current version pointers intentionally have application-owned integrity, as
-- adopted. Only deployed parents are referenced; no future-domain FK is added.
CREATE TABLE meeting (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `revision` bigint unsigned NOT NULL DEFAULT 0,
  `cancel_epoch` bigint unsigned NOT NULL DEFAULT 0,
  `access_revision` bigint unsigned NOT NULL DEFAULT 0,
  `stage` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `lifecycle` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  `governance_state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  `ever_published` tinyint unsigned NOT NULL DEFAULT 0,
  `info_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `roster_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `transcript_confirmation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `minutes_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `final_confirmation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `actual_meeting_date` date NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `transcript_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `material_snapshot_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `template_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `element_set_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `exclusion_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `generation_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `needs_generation` tinyint unsigned NOT NULL DEFAULT 0,
  `materials_pending` tinyint unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  INDEX meeting_owner_date (owner_id,lifecycle,actual_meeting_date,id),
  INDEX meeting_stage_date (lifecycle,stage,actual_meeting_date,id),
  INDEX meeting_owner_updated (owner_id,lifecycle,updated_at,id),
  INDEX meeting_stage_updated (lifecycle,stage,updated_at,id),
  CONSTRAINT ck_meeting_0 CHECK (stage IN ('ENTRY','TRANSCRIPT_PENDING','TRANSCRIPT_FAILED','MINUTES_PENDING','MINUTES_FAILED','READY_TO_PUBLISH','PUBLISH_FAILED','PUBLISHED')),
  CONSTRAINT ck_meeting_1 CHECK (lifecycle IN ('ACTIVE','DELETING','DELETED')),
  CONSTRAINT ck_meeting_2 CHECK (governance_state IN ('ACTIVE','WITHDRAW_PENDING','WITHDRAWN','OBSOLETE_PENDING','OBSOLETE')),
  CONSTRAINT ck_meeting_3 CHECK (ever_published IN (0,1)),
  CONSTRAINT ck_meeting_4 CHECK (needs_generation IN (0,1)),
  CONSTRAINT ck_meeting_5 CHECK (materials_pending IN (0,1))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE meeting_info_revision (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `name` longtext NULL,
  `category_id` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `summary` longtext NULL,
  `actual_meeting_date` date NULL,
  `created_by` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX info_revision (meeting_id,revision)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE roster_revision (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `confirmed_at` datetime(6) NULL,
  `confirmed_by` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX roster_revision (meeting_id,revision),
  CONSTRAINT ck_roster_revision_0 CHECK (state IN ('DRAFT','CONFIRMED')),
  CONSTRAINT ck_roster_revision_1 CHECK ((state='DRAFT' AND confirmed_at IS NULL AND confirmed_by IS NULL) OR (state='CONFIRMED' AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE roster_member (
  `roster_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `member_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `enterprise_id` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `display_name` longtext NOT NULL,
  `member_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (roster_id,member_id),
  UNIQUE INDEX roster_person (roster_id,enterprise_id),
  CONSTRAINT ck_roster_member_0 CHECK (member_kind IN ('ENTERPRISE','EXTERNAL')),
  CONSTRAINT ck_roster_member_1 CHECK ((member_kind='ENTERPRISE' AND enterprise_id IS NOT NULL) OR (member_kind='EXTERNAL' AND enterprise_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE meeting_access_grant (
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `enterprise_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `action` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `roster_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `confirmed_at` datetime(6) NOT NULL,
  PRIMARY KEY (meeting_id,enterprise_id,action),
  INDEX access_person_meeting (enterprise_id,action,meeting_id),
  CONSTRAINT ck_meeting_access_grant_0 CHECK (action='READ')
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE idempotency_record (
  `scope_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `actor_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `resource_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `operation` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `key_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `http_status` smallint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (scope_hash),
  CONSTRAINT ck_idempotency_record_0 CHECK (state IN ('SUCCEEDED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE meeting_search_projection (
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `info_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `roster_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `name_search` longtext COLLATE utf8mb4_0900_as_ci NULL,
  `number_search` varchar(36) COLLATE utf8mb4_0900_as_ci NOT NULL,
  `category_id` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `meeting_revision` bigint unsigned NOT NULL,
  PRIMARY KEY (meeting_id),
  INDEX search_category (category_id,meeting_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE meeting_person_search (
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `roster_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `member_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `name_search` longtext COLLATE utf8mb4_0900_as_ci NOT NULL,
  PRIMARY KEY (meeting_id,member_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE meeting ADD CONSTRAINT fk_meeting_0 FOREIGN KEY (owner_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE meeting_info_revision ADD CONSTRAINT fk_meeting_info_revision_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE meeting_info_revision ADD CONSTRAINT fk_meeting_info_revision_1 FOREIGN KEY (created_by) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE roster_revision ADD CONSTRAINT fk_roster_revision_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE roster_revision ADD CONSTRAINT fk_roster_revision_1 FOREIGN KEY (confirmed_by) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE roster_member ADD CONSTRAINT fk_roster_member_0 FOREIGN KEY (roster_id) REFERENCES roster_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE roster_member ADD CONSTRAINT fk_roster_member_1 FOREIGN KEY (enterprise_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE meeting_access_grant ADD CONSTRAINT fk_meeting_access_grant_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE meeting_access_grant ADD CONSTRAINT fk_meeting_access_grant_1 FOREIGN KEY (enterprise_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE meeting_access_grant ADD CONSTRAINT fk_meeting_access_grant_2 FOREIGN KEY (roster_id) REFERENCES roster_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE meeting_search_projection ADD CONSTRAINT fk_meeting_search_projection_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE meeting_search_projection ADD CONSTRAINT fk_meeting_search_projection_1 FOREIGN KEY (info_revision_id) REFERENCES meeting_info_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE meeting_search_projection ADD CONSTRAINT fk_meeting_search_projection_2 FOREIGN KEY (roster_revision_id) REFERENCES roster_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE meeting_person_search ADD CONSTRAINT fk_meeting_person_search_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE meeting_person_search ADD CONSTRAINT fk_meeting_person_search_1 FOREIGN KEY (roster_id,member_id) REFERENCES roster_member (roster_id,member_id) ON UPDATE RESTRICT ON DELETE RESTRICT;
