-- B00-1.6-reconcile-scan: REVIEW ONLY; never place this file in Flyway locations.

-- All future tables exist ONLY in the disposable review database.

SET NAMES utf8mb4;

SET time_zone = '+00:00';

-- Owner B-01; planned V006: 最小事务审计（V001 的目标增量形态）

CREATE TABLE audit_event (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `actor_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `action` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `resource_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `result` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trace_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX audit_resource_time (resource_id,occurred_at,id),
  INDEX audit_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-02; planned V007: 目录部门投影

CREATE TABLE department (
  `enterprise_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `display_name` longtext NOT NULL,
  `parent_id` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `verified_at` datetime(6) NOT NULL,
  PRIMARY KEY (enterprise_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-02; planned V007: 企业身份（架构 user_identity）

CREATE TABLE enterprise_identity (
  `enterprise_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `display_name` longtext NOT NULL,
  `department_id` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `status` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `session_generation` bigint unsigned NOT NULL DEFAULT 0,
  `verified_at` datetime(6) NOT NULL,
  PRIMARY KEY (enterprise_id),
  CONSTRAINT ck_enterprise_identity_0 CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-02; planned V007: 独立平台角色；不隐含正文访问

CREATE TABLE role_assignment (
  `enterprise_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `role` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `assigned_at` datetime(6) NOT NULL,
  PRIMARY KEY (enterprise_id,role),
  CONSTRAINT ck_role_assignment_0 CHECK (role IN ('USER','PLATFORM_ADMIN'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-02; planned V007: 共享数据库会话与即时撤销

CREATE TABLE identity_session (
  `token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `enterprise_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `session_generation` bigint unsigned NOT NULL,
  `csrf_token` char(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issued_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `revoked_at` datetime(6) NULL,
  PRIMARY KEY (token_hash),
  INDEX session_identity (enterprise_id,expires_at),
  CONSTRAINT ck_identity_session_0 CHECK (expires_at > issued_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-03; planned V008: 会议聚合根，唯一负责人和生命周期

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

-- Owner B-03; planned V008: 不可变基础信息历史

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

-- Owner B-03; planned V008: 名单草稿与已确认快照头；确认冻结该修订

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

-- Owner B-03; planned V008: 名单内容；本表绝不被授权查询使用

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

-- Owner B-02/B-03; planned V008: 独立有效 ACL；替代 V002 meeting_acl 读路径

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

-- Owner B-03; planned V008: 副作用请求重放/同键异请求冲突

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

-- Owner D-02; planned V018: AI 信息建议的独立候选

CREATE TABLE info_suggestion (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `base_revision` bigint unsigned NOT NULL,
  `input_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `proposed_info` longtext NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_info_suggestion_0 CHECK (state IN ('PENDING','ADOPTED','REJECTED','STALE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-04; planned V009: 不可变后端路由注册；凭证在外部配置

CREATE TABLE storage_backend (
  `id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `storage_type` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `config_revision` bigint unsigned NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_storage_backend_0 CHECK (storage_type IN ('local','s3')),
  CONSTRAINT ck_storage_backend_1 CHECK (status IN ('WRITABLE','READ_ONLY','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-04; planned V009: 已持久化受管文件元数据

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

-- Owner B-04; planned V009: 所有受管文件使用的统一引用账本

CREATE TABLE file_reference (
  `file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `purpose` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (file_id,owner_kind,owner_id,purpose),
  INDEX file_ref_owner (owner_kind,owner_id,file_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-04; planned V009: 固定后端的断点续传会话

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

-- Owner B-04; planned V009: 已落盘上传分片回执

CREATE TABLE upload_part (
  `upload_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `part_no` bigint unsigned NOT NULL,
  `size_bytes` bigint unsigned NOT NULL,
  `sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_token` longtext NULL,
  PRIMARY KEY (upload_id,part_no),
  CONSTRAINT ck_upload_part_0 CHECK (part_no > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-04/D-07; planned V009: 可重试删除/孤儿清理登记

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

-- Owner B-05; planned V010: 每领域聚合的版本/取消 fence（会议仅一个共享根）

CREATE TABLE task_subject (
  `subject_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `subject_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `business_version` bigint unsigned NOT NULL,
  `cancel_epoch` bigint unsigned NOT NULL,
  `deleted` tinyint unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (subject_kind,subject_id),
  CONSTRAINT ck_task_subject_0 CHECK (deleted IN (0,1))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-05; planned V010: 持久任务；外部未知结果从普通领取隔离

CREATE TABLE durable_task (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `task_type` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `pool` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `subject_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `subject_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `producer_key` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `business_version` bigint unsigned NOT NULL,
  `cancel_epoch` bigint unsigned NOT NULL,
  `priority` int NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'READY',
  `attempt` bigint unsigned NOT NULL DEFAULT 0,
  `max_attempts` bigint unsigned NOT NULL,
  `lease_owner` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `expires_at` datetime(6) NULL,
  `available_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `output_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `output_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `last_error` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX task_producer (producer_key),
  INDEX task_ready (pool,state,available_at,id),
  INDEX task_expired (pool,state,expires_at,id),
  INDEX task_subject_history (subject_kind,subject_id,created_at,id),
  INDEX task_input_lookup (input_kind,input_id,id),
  CONSTRAINT ck_durable_task_0 CHECK (state IN ('READY','RUNNING','RECONCILING','SUCCEEDED','FAILED','CANCELLED','STALE')),
  CONSTRAINT ck_durable_task_1 CHECK (max_attempts > 0),
  CONSTRAINT ck_durable_task_2 CHECK (state <> 'RUNNING' OR (lease_owner IS NOT NULL AND expires_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-05; planned V010: 每次 Worker 领取诊断历史

CREATE TABLE task_attempt (
  `task_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `attempt` bigint unsigned NOT NULL,
  `owner` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `started_at` datetime(6) NOT NULL,
  `ended_at` datetime(6) NULL,
  `outcome` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `error_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (task_id,attempt)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-05; planned V010: 任何可能产生外部效果的调用前 durable marker 及独立对账所有权

CREATE TABLE external_operation (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `operation_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `business_key` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `marked_at` datetime(6) NOT NULL,
  `next_check_at` datetime(6) NOT NULL,
  `reconcile_epoch` bigint unsigned NOT NULL DEFAULT 0,
  `reconcile_owner` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `reconcile_expires_at` datetime(6) NULL,
  `external_result_id` longtext NULL,
  `last_error` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `subject_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `subject_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `current_execution_no` bigint unsigned NOT NULL,
  `reconcile_scan_seq` bigint NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (id),
  UNIQUE INDEX external_key (operation_kind,business_key),
  INDEX external_due (state,next_check_at,id),
  UNIQUE INDEX external_reconcile_scan (reconcile_scan_seq),
  CONSTRAINT ck_external_operation_0 CHECK (state IN ('MARKED','UNKNOWN','RETRY_QUEUED','SUCCEEDED','FAILED','MANUAL_REQUIRED')),
  CONSTRAINT ck_external_operation_1 CHECK (current_execution_no > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-05; planned V010: 提交序列锁，避免 AUTO_INCREMENT 先提交高号跳漏

CREATE TABLE task_outbox_sequence (
  `id` int NOT NULL,
  `next_id` bigint unsigned NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CONSTRAINT ck_task_outbox_sequence_0 CHECK (id = 1),
  CONSTRAINT ck_task_outbox_sequence_1 CHECK (next_id > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-05; planned V010: 持久引用事件（架构 outbox_event）

CREATE TABLE task_outbox (
  `sequence_id` bigint unsigned NOT NULL,
  `event_type` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `task_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `subject_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `subject_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `subject_revision` bigint unsigned NOT NULL,
  `reference_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `reference_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  PRIMARY KEY (sequence_id),
  INDEX outbox_subject (subject_kind,subject_id,sequence_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-05; planned V010: 持久消费游标及接管 fence

CREATE TABLE outbox_consumer (
  `consumer_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `cursor` bigint unsigned NOT NULL DEFAULT 0,
  `epoch` bigint unsigned NOT NULL DEFAULT 0,
  `lease_owner` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `expires_at` datetime(6) NULL,
  PRIMARY KEY (consumer_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-05; planned V010: 已审核模型在途容量；无默认公司额度

CREATE TABLE model_capacity (
  `model` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `capacity` int unsigned NOT NULL,
  `config_revision` bigint unsigned NOT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT 0,
  `cutoff_policy` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (model),
  CONSTRAINT ck_model_capacity_0 CHECK (capacity > 0),
  CONSTRAINT ck_model_capacity_1 CHECK (enabled IN (0,1)),
  CONSTRAINT ck_model_capacity_2 CHECK (cutoff_policy IN ('PROVEN_DEADLINE','EXPLICIT_SETTLEMENT'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-05; planned V010: 独立于短任务租约的模型额度保留

CREATE TABLE model_reservation (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `model` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `execution_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `config_revision` bigint unsigned NOT NULL,
  `safe_deadline` datetime(6) NULL,
  `created_at` datetime(6) NOT NULL,
  `settled_at` datetime(6) NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX reservation_execution (execution_id),
  INDEX model_deadline (model,settled_at,safe_deadline)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-06/C-01; planned V011: 处理主文件集合快照，禁止混合音视频与纯转写

CREATE TABLE input_batch (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `base_revision` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_input_batch_0 CHECK (kind IN ('TRANSCRIPT','MEDIA'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-06/C-01; planned V011: 不可变有序源文件清单

CREATE TABLE input_batch_file (
  `batch_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `ordinal` bigint unsigned NOT NULL,
  `file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (batch_id,ordinal),
  UNIQUE INDEX batch_file_unique (batch_id,file_id),
  CONSTRAINT ck_input_batch_file_0 CHECK (ordinal > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-06; planned V011: 原始/增强/人工/候选转写不可变版本；确认另表

CREATE TABLE transcript_revision (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `batch_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `parent_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `input_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_by` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `processing_input_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `producer_task_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX transcript_history (meeting_id,revision),
  CONSTRAINT ck_transcript_revision_0 CHECK (kind IN ('RAW','ENHANCED','WORKING','CANDIDATE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-06; planned V011: 稳定片段ID跨修订保留；纯文档无虚构音频时间

CREATE TABLE transcript_segment (
  `revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `segment_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `ordinal` bigint unsigned NOT NULL,
  `body` longtext NOT NULL,
  `source_file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_paragraph` bigint unsigned NULL,
  `start_ms` bigint unsigned NULL,
  `end_ms` bigint unsigned NULL,
  `speaker_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (revision_id,segment_id),
  UNIQUE INDEX segment_order (revision_id,ordinal),
  CONSTRAINT ck_transcript_segment_0 CHECK ((start_ms IS NULL AND end_ms IS NULL) OR (start_ms IS NOT NULL AND end_ms IS NOT NULL AND end_ms >= start_ms))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-06; planned V011: 确认转写快照；只指不可变保存稿

CREATE TABLE transcript_confirmation (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `confirmed_by` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `confirmed_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX transcript_confirm_once (meeting_id,revision_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-07/D-04; planned V012: 内置/个人模板不可变版本

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

-- Owner B-07; planned V012: 纪要输入冻结集合；禁止未确认转写入队

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

-- Owner B-07; planned V012: 已保存正文；候选与当前指针分离

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

-- Owner B-07/D-03; planned V012: 独立要素集合修订，不反向改写正文

CREATE TABLE element_set (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX element_set_revision (meeting_id,revision)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-07/D-03; planned V012: 七类主体要素；敏感与背景不是额外主体类

CREATE TABLE minutes_element (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `set_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `category` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `ordinal` bigint unsigned NOT NULL,
  `body` longtext NOT NULL,
  `support_state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_context_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX element_order (set_id,category,ordinal),
  INDEX minutes_element_context (source_context_id,id),
  CONSTRAINT ck_minutes_element_0 CHECK (category IN ('OVERVIEW','DISCUSSION','CONCLUSION','ACTION','RISK','OPEN_QUESTION','DISAGREEMENT')),
  CONSTRAINT ck_minutes_element_1 CHECK (support_state IN ('SUPPORTED','PARTIAL','UNSUPPORTED','NOT_EXTRACTED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-07/D-03; planned V012: 行动项结构信息；负责人不授权

CREATE TABLE action_item (
  `element_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_enterprise_id` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `owner_display` longtext NULL,
  `due_on` date NULL,
  PRIMARY KEY (element_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-07; planned V012: 敏感排除集合版本；空集也有ID

CREATE TABLE exclusion_revision (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX exclusion_version (meeting_id,revision)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-07/D-03; planned V012: 被明确排除的来源；不删除转写/要素/录音

CREATE TABLE sensitive_exclusion (
  `revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `locator_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `category` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `reason` longtext NULL,
  PRIMARY KEY (revision_id,locator_id,category),
  CONSTRAINT ck_sensitive_exclusion_0 CHECK (category IN ('INTERNAL_BUSINESS_DATA','POLITICAL_SENSITIVE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-07; planned V012: 生成结果候选；迟到结果不直接覆盖

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

-- Owner B-07/D-01; planned V012: 有类型的不可变来源定位，不能以通用JSON抹去来源约束

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

-- Owner B-07/D-03; planned V012: 具体要素或正文版本的引用证据

CREATE TABLE evidence_link (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `locator_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `element_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `minutes_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `support_state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `target_anchor` longtext NOT NULL,
  `source_context_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (id),
  INDEX evidence_element (element_id,locator_id),
  INDEX evidence_minutes (minutes_revision_id,locator_id),
  INDEX evidence_link_context (source_context_id,id),
  CONSTRAINT ck_evidence_link_0 CHECK ((element_id IS NULL) <> (minutes_revision_id IS NULL)),
  CONSTRAINT ck_evidence_link_1 CHECK (support_state IN ('SUPPORTED','PARTIAL','UNSUPPORTED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-08; planned V013: 冻结最终已保存版本集合及校验摘要

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

-- Owner B-08; planned V013: 冻结发布操作；成功一次即永久只读

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

-- Owner B-08; planned V013: 每次发布/查回的诊断与固定确认关联

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

-- Owner B-08; planned V013: 唯一成功外部文档映射

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

-- Owner D-05; planned V020: 不可逆撤回/废止请求和确认记录

CREATE TABLE publication_governance (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `publication_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `action` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `reason` longtext NOT NULL,
  `actor_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `operation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_publication_governance_0 CHECK (action IN ('WITHDRAW','OBSOLETE')),
  CONSTRAINT ck_publication_governance_1 CHECK (state IN ('PENDING','UNKNOWN','COMPLETE','FAILED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner D-05; planned V020: 验签后外部事件去重/重放防护

CREATE TABLE external_event_receipt (
  `provider_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `payload_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `received_at` datetime(6) NOT NULL,
  `processed_at` datetime(6) NULL,
  PRIMARY KEY (provider_id,event_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner C-01; planned V014: 归一化音频采样数与多源映射

CREATE TABLE media_source (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `batch_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `normalized_file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `sample_rate` bigint unsigned NOT NULL,
  `channels` bigint unsigned NOT NULL,
  `bits_per_sample` bigint unsigned NOT NULL,
  `sample_count` bigint unsigned NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_media_source_0 CHECK (sample_rate>0 AND channels>0 AND bits_per_sample>0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner C-01; planned V014: 跨段独立源时间轴到合并轴映射

CREATE TABLE media_source_span (
  `media_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `ordinal` bigint unsigned NOT NULL,
  `source_file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_start_sample` bigint unsigned NOT NULL,
  `global_start_sample` bigint unsigned NOT NULL,
  `sample_count` bigint unsigned NOT NULL,
  PRIMARY KEY (media_id,ordinal),
  CONSTRAINT ck_media_source_span_0 CHECK (ordinal>0 AND sample_count>0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-06/C-03; planned V011: 说话人业务标签；实名与授权隔离

CREATE TABLE speaker (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `display_name` longtext NOT NULL,
  `enterprise_id` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `identity_source` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `merged_into_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `revision` bigint unsigned NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_speaker_0 CHECK (identity_source IN ('ANONYMOUS','HUMAN','VOICE_CANDIDATE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner C-03; planned V015: 与具体转写版本绑定的纠错建议

CREATE TABLE correction (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `segment_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `proposed_text` longtext NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  PRIMARY KEY (id),
  INDEX correction_pending (meeting_id,revision_id,state,id),
  CONSTRAINT ck_correction_0 CHECK (state IN ('PENDING','ACCEPTED','REJECTED','STALE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner C-03; planned V015: 可撤销编辑操作；撤销也创建新修订

CREATE TABLE edit_operation (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `before_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `after_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `reverts_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `actor_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX edit_history (meeting_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner C-04; planned V016: 唯一采集者与writerEpoch；固定后端

CREATE TABLE recording_session (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `backend_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `writer_epoch` bigint unsigned NOT NULL,
  `writer_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `writer_expires_at` datetime(6) NOT NULL,
  `cancel_epoch` bigint unsigned NOT NULL,
  `archive_file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (id),
  INDEX recording_meeting (meeting_id,state,id),
  CONSTRAINT ck_recording_session_0 CHECK (state IN ('RECORDING','PAUSED','FINISHING','ARCHIVING','AVAILABLE','FAILED','ABORTED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner C-04; planned V016: 分段独立序号与durable连续前缀

CREATE TABLE recording_part (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `session_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `ordinal` bigint unsigned NOT NULL,
  `highest_contiguous_seq` bigint unsigned NOT NULL DEFAULT 0,
  `final_seq` bigint unsigned NULL,
  `sample_count` bigint unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE INDEX recording_part_order (session_id,ordinal),
  CONSTRAINT ck_recording_part_0 CHECK (ordinal>0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner C-04; planned V016: 落盘ACK事实来源，重复序号异哈希冲突

CREATE TABLE recording_chunk (
  `part_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `seq` bigint unsigned NOT NULL,
  `writer_epoch` bigint unsigned NOT NULL,
  `sample_start` bigint unsigned NOT NULL,
  `sample_count` bigint unsigned NOT NULL,
  `sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `persisted_at` datetime(6) NOT NULL,
  PRIMARY KEY (part_id,seq),
  CONSTRAINT ck_recording_chunk_0 CHECK (seq>0 AND sample_count>0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner C-06; planned V016: 临时/稳定字幕历史；不冒充正式转写

CREATE TABLE subtitle_revision (
  `session_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `segment_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `part_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `body` longtext NOT NULL,
  `start_ms` bigint unsigned NOT NULL,
  `end_ms` bigint unsigned NOT NULL,
  `event_sequence` bigint unsigned NOT NULL,
  PRIMARY KEY (session_id,segment_id,revision),
  INDEX subtitle_event (session_id,event_sequence),
  CONSTRAINT ck_subtitle_revision_0 CHECK (state IN ('TEMPORARY','STABLE')),
  CONSTRAINT ck_subtitle_revision_1 CHECK (end_ms>=start_ms)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner D-01; planned V017: 会议本地/知识库材料逻辑关联

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

-- Owner D-06; planned V021: 按已保存有效版本生成三格式导出

CREATE TABLE export_job (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `minutes_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `confirmation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `format` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `task_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `output_file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `access_revision` bigint unsigned NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_export_job_0 CHECK (format IN ('md','pdf','docx')),
  CONSTRAINT ck_export_job_1 CHECK (state IN ('PENDING','READY','FAILED','INVALIDATED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner D-07; planned V022: 删除请求与清理完成事实

CREATE TABLE meeting_deletion (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `cancel_epoch` bigint unsigned NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `task_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `requested_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX deletion_meeting (meeting_id),
  CONSTRAINT ck_meeting_deletion_0 CHECK (state IN ('PENDING','FAILED','COMPLETE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner D-08; planned V023: 单层个人专题，不改变内容版本/ACL

CREATE TABLE topic (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `name` longtext NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` longtext NULL,
  `name_normalized` longtext COLLATE utf8mb4_0900_bin NOT NULL,
  `name_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (id),
  INDEX topic_owner (owner_id,id),
  INDEX topic_name_bucket (owner_id,name_hash,id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner D-08; planned V023: 本人负责且已发布会议归集

CREATE TABLE meeting_topic (
  `topic_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `added_at` datetime(6) NOT NULL,
  PRIMARY KEY (topic_id,meeting_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner E-01; planned V024: 受控反馈候选/审核；不改原确认稿

CREATE TABLE corpus_feedback (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `submitted_by` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `category` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `comment` longtext NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `reviewed_by` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `review_reason` longtext NULL,
  `created_at` datetime(6) NOT NULL,
  `reviewed_at` datetime(6) NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_corpus_feedback_0 CHECK (state IN ('PENDING','ACCEPTED','REJECTED','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner E-01; planned V024: 反馈关联稳定源片段

CREATE TABLE feedback_segment (
  `feedback_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `segment_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (feedback_id,revision_id,segment_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner E-01; planned V024: 批准后可用语料修订；全局使用另有安全门

CREATE TABLE corpus_revision (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `feedback_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `content_file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `purpose` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `approved_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX corpus_version (feedback_id,revision),
  CONSTRAINT ck_corpus_revision_0 CHECK (state IN ('APPROVED','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner E-02; planned V025: 明确用途授权及撤销记录

CREATE TABLE voiceprint_consent (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `subject_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `purpose` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `consent_reference` longtext NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `granted_at` datetime(6) NOT NULL,
  `revoked_at` datetime(6) NULL,
  `revoked_by` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `revocation_reason` longtext NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_voiceprint_consent_0 CHECK (state IN ('GRANTED','REVOKED')),
  CONSTRAINT ck_voiceprint_consent_1 CHECK (state <> 'REVOKED' OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL AND revocation_reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner E-02; planned V025: 声纹样本与审核状态；不写ACL

CREATE TABLE voiceprint_sample (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `consent_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `sample_file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `model_version` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `dimension` bigint unsigned NOT NULL,
  `encrypted_embedding_file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `key_reference` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `reviewed_by` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `reviewed_at` datetime(6) NULL,
  `review_reason` longtext NULL,
  `revoked_at` datetime(6) NULL,
  PRIMARY KEY (id),
  INDEX voice_consent (consent_id,state,id),
  CONSTRAINT ck_voiceprint_sample_0 CHECK (dimension>0),
  CONSTRAINT ck_voiceprint_sample_1 CHECK (state IN ('PENDING','ACTIVE','REJECTED','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner E-03; planned V026: 受控评测数据版本

CREATE TABLE evaluation_dataset (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `version_key` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `manifest_file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `purpose` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX dataset_version (version_key)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner E-03; planned V026: 白名单配置候选；B期配置版本也可来自受控文件

CREATE TABLE configuration_revision (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `configuration` longtext NOT NULL,
  `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_by` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `reviewed_by` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `created_at` datetime(6) NOT NULL,
  `reviewed_at` datetime(6) NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX configuration_version (kind,revision),
  CONSTRAINT ck_configuration_revision_0 CHECK (kind IN ('MODEL','PROMPT','SYSTEM')),
  CONSTRAINT ck_configuration_revision_1 CHECK (state IN ('PENDING','APPROVED','REJECTED')),
  CONSTRAINT ck_configuration_revision_2 CHECK (reviewed_by IS NULL OR reviewed_by <> created_by)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner E-03; planned V026: 固定数据/模型/提示词评测任务

CREATE TABLE evaluation_run (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `dataset_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `model_config_version` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `prompt_version` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `task_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `sample_count` bigint unsigned NOT NULL,
  `metrics` longtext NULL,
  `errors_file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_evaluation_run_0 CHECK (state IN ('PENDING','RUNNING','SUCCEEDED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner E-03; planned V026: 配置发布/回滚均新增记录

CREATE TABLE configuration_release (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` bigint unsigned NOT NULL,
  `configuration_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `evaluation_run_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `rollback_of_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `released_by` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `released_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX release_revision (kind,revision),
  CONSTRAINT ck_configuration_release_0 CHECK (kind IN ('MODEL','PROMPT','SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner E-04; planned V027: 已保存版本受控链接邮件；不含正文附件/外部邮箱

CREATE TABLE mail_job (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `minutes_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `confirmation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `sender_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `task_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `business_key` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `sent_at` datetime(6) NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX mail_key (business_key),
  CONSTRAINT ck_mail_job_0 CHECK (state IN ('PENDING','SENT','FAILED','UNKNOWN','MANUAL_REQUIRED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner E-04; planned V027: 固定企业收件人集合；发送前再验证有效名单和ACL

CREATE TABLE mail_recipient (
  `mail_job_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `enterprise_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_message_id` longtext NULL,
  `operation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `message_key` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  PRIMARY KEY (mail_job_id,enterprise_id),
  UNIQUE INDEX mail_recipient_operation (operation_id),
  UNIQUE INDEX mail_recipient_key (message_key),
  CONSTRAINT ck_mail_recipient_0 CHECK (state IN ('PENDING','SENT','FAILED','UNKNOWN','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-03/D-02; planned V008: R1 列表当前名称/编号及筛选字段投影；与根修改同事务

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

-- Owner B-03/D-02; planned V008: R1 当前已确认名单姓名投影；草稿和AI候选绝不进入

CREATE TABLE meeting_person_search (
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `roster_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `member_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `name_search` longtext COLLATE utf8mb4_0900_as_ci NOT NULL,
  PRIMARY KEY (meeting_id,member_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-07/D-04; planned V012: R2 模板逻辑根与当前个人名称；历史名称不占用命名空间

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

-- Owner B-05/B-08; planned V010: R4 稳定逻辑操作下的追加执行尝试；支持同确认零效果失败重试

CREATE TABLE external_execution (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `operation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `execution_no` bigint unsigned NOT NULL,
  `task_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `task_attempt` bigint unsigned NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_manifest` longtext NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `marked_at` datetime(6) NULL,
  `finished_at` datetime(6) NULL,
  `no_effect_basis` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `no_effect_evidence` longtext NULL,
  `no_effect_verified_at` datetime(6) NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX external_execution_number (operation_id,execution_no),
  UNIQUE INDEX external_execution_task (task_id),
  CONSTRAINT ck_external_execution_0 CHECK (execution_no > 0),
  CONSTRAINT ck_external_execution_1 CHECK (state IN ('PREPARED','MARKED','UNKNOWN','SUCCEEDED','FAILED')),
  CONSTRAINT ck_external_execution_2 CHECK (state <> 'MARKED' OR (task_attempt IS NOT NULL AND marked_at IS NOT NULL)),
  CONSTRAINT ck_external_execution_3 CHECK (no_effect_basis IS NULL OR no_effect_basis IN ('NOT_SENT','DEFINITIVE_REJECTION','PROVEN_CUTOFF_FENCED')),
  CONSTRAINT ck_external_execution_4 CHECK ((no_effect_basis IS NULL AND no_effect_evidence IS NULL AND no_effect_verified_at IS NULL) OR (state='FAILED' AND no_effect_basis IS NOT NULL AND no_effect_evidence IS NOT NULL AND no_effect_verified_at IS NOT NULL AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-06/C-02/C-03/D-01; planned V011: R5 转写导入/识别/重新增强不可变输入快照，独立于纪要generation_input

CREATE TABLE transcription_input (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `batch_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `material_snapshot_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `material_mode` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `processing_config_version` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `parser_version` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `model_config_version` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `prompt_version` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `manifest_schema_version` int unsigned NOT NULL,
  `config_manifest` longtext NOT NULL,
  `input_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `base_revision` bigint unsigned NOT NULL,
  `cancel_epoch` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX transcription_input_history (meeting_id,created_at,id),
  CONSTRAINT ck_transcription_input_0 CHECK (kind IN ('IMPORT','ASR','ENHANCE')),
  CONSTRAINT ck_transcription_input_1 CHECK (material_mode IN ('EMPTY','PINNED')),
  CONSTRAINT ck_transcription_input_2 CHECK ((material_mode='EMPTY' AND material_snapshot_id IS NULL) OR (material_mode='PINNED' AND material_snapshot_id IS NOT NULL)),
  CONSTRAINT ck_transcription_input_3 CHECK (kind <> 'ENHANCE' OR source_revision_id IS NOT NULL),
  CONSTRAINT ck_transcription_input_4 CHECK (kind <> 'IMPORT' OR parser_version IS NOT NULL),
  CONSTRAINT ck_transcription_input_5 CHECK (kind='IMPORT' OR model_config_version IS NOT NULL),
  CONSTRAINT ck_transcription_input_6 CHECK (manifest_schema_version > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-06/C-03; planned V011: R6 片段多选标记随转写版本冻结；不是纪要要素

CREATE TABLE transcript_segment_mark (
  `revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `segment_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `mark` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (revision_id,segment_id,mark),
  CONSTRAINT ck_transcript_segment_mark_0 CHECK (mark IN ('KEY','CONCLUSION','DISPUTE','GENERATION_FOCUS'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-07/D-03; planned V012: 未选择也保留的敏感检测目录；不属于七类主体要素

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

-- Owner B-07/D-03; planned V012: 敏感检测的完整无序来源集合

CREATE TABLE sensitive_detection_source (
  `detection_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `locator_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (detection_id,locator_id),
  INDEX detection_source_reverse (locator_id,detection_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-07/D-03; planned V012: 生成背景补充正文；不冒充会议发言/决定/行动

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

-- Owner B-07/D-03; planned V012: 背景补充的完整来源集合

CREATE TABLE background_note_source (
  `note_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `locator_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (note_id,locator_id),
  INDEX background_source_reverse (locator_id,note_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-07/D-03; planned V012: 未核实的模型负责人原文和日期建议；与接受字段分离

CREATE TABLE action_suggestion (
  `element_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_mention` longtext NULL,
  `due_on` date NULL,
  PRIMARY KEY (element_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-07/D-03/B-08; planned V012: 人类对精确目标内容和完整证据集合的不可变断言

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

-- Owner B-07/D-03/B-08; planned V012: 复核实际使用的完整证据集合，不只存支持项

CREATE TABLE result_review_evidence (
  `review_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `evidence_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (review_id,evidence_id),
  INDEX review_evidence_reverse (evidence_id,review_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-08; planned V013: 最终确认冻结精确复核成员，不读取以后最新复核

CREATE TABLE final_result_review (
  `confirmation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `review_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (confirmation_id,review_id),
  INDEX final_review_reverse (review_id,confirmation_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner D-01/B-07; planned UNASSIGNED-D01: 不可变完整来源选择；生成与人工选择显式分型

CREATE TABLE source_context (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `origin_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `generation_input_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `transcript_confirmation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `material_snapshot_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `catalog_version` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `catalog_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX context_generation (generation_input_id),
  INDEX context_meeting (meeting_id,id),
  CONSTRAINT ck_source_context_0 CHECK (origin_kind IN ('GENERATION_INPUT','MANUAL_SELECTION')),
  CONSTRAINT ck_source_context_1 CHECK ((origin_kind='GENERATION_INPUT' AND generation_input_id IS NOT NULL) OR (origin_kind='MANUAL_SELECTION' AND generation_input_id IS NULL)),
  CONSTRAINT ck_source_context_2 CHECK (REGEXP_LIKE(catalog_sha256, '^[0-9a-f]{64}$', 'c'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner D-01/B-07; planned UNASSIGNED-D01: 上下文内每个材料版本唯一明确选择解析文档

CREATE TABLE source_context_material (
  `context_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `material_revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `parsed_document_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (context_id,material_revision_id),
  INDEX selection_document (parsed_document_id,context_id),
  INDEX selection_material (material_revision_id,context_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner D-01; planned UNASSIGNED-D01: 解析文档的明确格式及不可变解析配置归档引用

CREATE TABLE parsed_document_profile (
  `document_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `format` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `parser_config_version` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `parser_config_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (document_id),
  INDEX profile_parser (parser_config_version,document_id),
  CONSTRAINT ck_parsed_document_profile_0 CHECK (format IN ('PDF','PPT','WORD','TXT')),
  CONSTRAINT ck_parsed_document_profile_1 CHECK (REGEXP_LIKE(parser_config_sha256, '^[0-9a-f]{64}$', 'c'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-06/runtime Task2; planned V013.1: 明确不可变输入批次指纹算法；缺失为legacy/unknown

CREATE TABLE input_batch_fingerprint_profile (
  `batch_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `profile` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (batch_id),
  CONSTRAINT ck_input_batch_fingerprint_profile_0 CHECK (OCTET_LENGTH(profile) = 18 AND profile = 'ama.input-batch.v1')
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Owner B-03/B-07/runtime Task2; planned V013.1: 持久单调编辑意图；仅新建会议原事务可写零

CREATE TABLE meeting_minutes_editor_guard (
  `meeting_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `intent_epoch` bigint NOT NULL,
  PRIMARY KEY (meeting_id),
  CONSTRAINT ck_meeting_minutes_editor_guard_0 CHECK (intent_epoch BETWEEN 0 AND 9223372036854775806)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE department ADD CONSTRAINT fk_department_0 FOREIGN KEY (parent_id) REFERENCES department (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE enterprise_identity ADD CONSTRAINT fk_enterprise_identity_0 FOREIGN KEY (department_id) REFERENCES department (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE role_assignment ADD CONSTRAINT fk_role_assignment_0 FOREIGN KEY (enterprise_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE identity_session ADD CONSTRAINT fk_identity_session_0 FOREIGN KEY (enterprise_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

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

ALTER TABLE info_suggestion ADD CONSTRAINT fk_info_suggestion_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE file_object ADD CONSTRAINT fk_file_object_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE file_object ADD CONSTRAINT fk_file_object_1 FOREIGN KEY (backend_id) REFERENCES storage_backend (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE file_reference ADD CONSTRAINT fk_file_reference_0 FOREIGN KEY (file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE upload_session ADD CONSTRAINT fk_upload_session_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE upload_session ADD CONSTRAINT fk_upload_session_1 FOREIGN KEY (backend_id) REFERENCES storage_backend (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE upload_session ADD CONSTRAINT fk_upload_session_2 FOREIGN KEY (completed_file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE upload_part ADD CONSTRAINT fk_upload_part_0 FOREIGN KEY (upload_id) REFERENCES upload_session (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE file_cleanup ADD CONSTRAINT fk_file_cleanup_0 FOREIGN KEY (file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE file_cleanup ADD CONSTRAINT fk_file_cleanup_1 FOREIGN KEY (backend_id) REFERENCES storage_backend (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE durable_task ADD CONSTRAINT fk_durable_task_0 FOREIGN KEY (subject_kind,subject_id) REFERENCES task_subject (subject_kind,subject_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE task_attempt ADD CONSTRAINT fk_task_attempt_0 FOREIGN KEY (task_id) REFERENCES durable_task (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE external_operation ADD CONSTRAINT fk_external_operation_0 FOREIGN KEY (subject_kind,subject_id) REFERENCES task_subject (subject_kind,subject_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE task_outbox ADD CONSTRAINT fk_task_outbox_0 FOREIGN KEY (task_id) REFERENCES durable_task (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE model_reservation ADD CONSTRAINT fk_model_reservation_0 FOREIGN KEY (model) REFERENCES model_capacity (model) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE model_reservation ADD CONSTRAINT fk_model_reservation_1 FOREIGN KEY (execution_id) REFERENCES external_execution (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE input_batch ADD CONSTRAINT fk_input_batch_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE input_batch_file ADD CONSTRAINT fk_input_batch_file_0 FOREIGN KEY (batch_id) REFERENCES input_batch (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE input_batch_file ADD CONSTRAINT fk_input_batch_file_1 FOREIGN KEY (file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcript_revision ADD CONSTRAINT fk_transcript_revision_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcript_revision ADD CONSTRAINT fk_transcript_revision_1 FOREIGN KEY (batch_id) REFERENCES input_batch (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcript_revision ADD CONSTRAINT fk_transcript_revision_2 FOREIGN KEY (parent_id) REFERENCES transcript_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcript_revision ADD CONSTRAINT fk_transcript_revision_3 FOREIGN KEY (processing_input_id) REFERENCES transcription_input (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcript_revision ADD CONSTRAINT fk_transcript_revision_4 FOREIGN KEY (producer_task_id) REFERENCES durable_task (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcript_segment ADD CONSTRAINT fk_transcript_segment_0 FOREIGN KEY (revision_id) REFERENCES transcript_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcript_segment ADD CONSTRAINT fk_transcript_segment_1 FOREIGN KEY (source_file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcript_segment ADD CONSTRAINT fk_transcript_segment_2 FOREIGN KEY (speaker_id) REFERENCES speaker (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcript_confirmation ADD CONSTRAINT fk_transcript_confirmation_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcript_confirmation ADD CONSTRAINT fk_transcript_confirmation_1 FOREIGN KEY (revision_id) REFERENCES transcript_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcript_confirmation ADD CONSTRAINT fk_transcript_confirmation_2 FOREIGN KEY (confirmed_by) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE template_revision ADD CONSTRAINT fk_template_revision_0 FOREIGN KEY (owner_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE template_revision ADD CONSTRAINT fk_template_revision_1 FOREIGN KEY (template_id) REFERENCES minutes_template (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_input ADD CONSTRAINT fk_generation_input_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_input ADD CONSTRAINT fk_generation_input_1 FOREIGN KEY (transcript_confirmation_id) REFERENCES transcript_confirmation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_input ADD CONSTRAINT fk_generation_input_2 FOREIGN KEY (template_revision_id) REFERENCES template_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_input ADD CONSTRAINT fk_generation_input_3 FOREIGN KEY (exclusion_revision_id) REFERENCES exclusion_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_input ADD CONSTRAINT fk_generation_input_4 FOREIGN KEY (element_set_id) REFERENCES element_set (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE generation_input ADD CONSTRAINT fk_generation_input_5 FOREIGN KEY (material_snapshot_id) REFERENCES material_snapshot (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE minutes_revision ADD CONSTRAINT fk_minutes_revision_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE minutes_revision ADD CONSTRAINT fk_minutes_revision_1 FOREIGN KEY (parent_id) REFERENCES minutes_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE minutes_revision ADD CONSTRAINT fk_minutes_revision_2 FOREIGN KEY (generation_input_id) REFERENCES generation_input (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE element_set ADD CONSTRAINT fk_element_set_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE minutes_element ADD CONSTRAINT fk_minutes_element_0 FOREIGN KEY (set_id) REFERENCES element_set (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE minutes_element ADD CONSTRAINT fk_minutes_element_1 FOREIGN KEY (source_context_id) REFERENCES source_context (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

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

ALTER TABLE source_locator ADD CONSTRAINT fk_source_locator_3 FOREIGN KEY (material_revision_id) REFERENCES material_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE evidence_link ADD CONSTRAINT fk_evidence_link_0 FOREIGN KEY (locator_id) REFERENCES source_locator (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE evidence_link ADD CONSTRAINT fk_evidence_link_1 FOREIGN KEY (element_id) REFERENCES minutes_element (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE evidence_link ADD CONSTRAINT fk_evidence_link_2 FOREIGN KEY (minutes_revision_id) REFERENCES minutes_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE evidence_link ADD CONSTRAINT fk_evidence_link_3 FOREIGN KEY (source_context_id) REFERENCES source_context (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_1 FOREIGN KEY (info_revision_id) REFERENCES meeting_info_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_2 FOREIGN KEY (roster_revision_id) REFERENCES roster_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_3 FOREIGN KEY (transcript_confirmation_id) REFERENCES transcript_confirmation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_4 FOREIGN KEY (minutes_revision_id) REFERENCES minutes_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_5 FOREIGN KEY (element_set_id) REFERENCES element_set (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_6 FOREIGN KEY (template_revision_id) REFERENCES template_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_7 FOREIGN KEY (exclusion_revision_id) REFERENCES exclusion_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_8 FOREIGN KEY (confirmed_by) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_confirmation ADD CONSTRAINT fk_final_confirmation_9 FOREIGN KEY (material_snapshot_id) REFERENCES material_snapshot (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE publication ADD CONSTRAINT fk_publication_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE publication ADD CONSTRAINT fk_publication_1 FOREIGN KEY (confirmation_id) REFERENCES final_confirmation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE publication_attempt ADD CONSTRAINT fk_publication_attempt_0 FOREIGN KEY (publication_id) REFERENCES publication (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE publication_attempt ADD CONSTRAINT fk_publication_attempt_1 FOREIGN KEY (confirmation_id) REFERENCES final_confirmation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE publication_attempt ADD CONSTRAINT fk_publication_attempt_2 FOREIGN KEY (operation_id) REFERENCES external_operation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE publication_attempt ADD CONSTRAINT fk_publication_attempt_3 FOREIGN KEY (execution_id) REFERENCES external_execution (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE external_document_mapping ADD CONSTRAINT fk_external_document_mapping_0 FOREIGN KEY (publication_id) REFERENCES publication (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE publication_governance ADD CONSTRAINT fk_publication_governance_0 FOREIGN KEY (publication_id) REFERENCES publication (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE publication_governance ADD CONSTRAINT fk_publication_governance_1 FOREIGN KEY (operation_id) REFERENCES external_operation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE media_source ADD CONSTRAINT fk_media_source_0 FOREIGN KEY (batch_id) REFERENCES input_batch (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE media_source ADD CONSTRAINT fk_media_source_1 FOREIGN KEY (normalized_file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE media_source_span ADD CONSTRAINT fk_media_source_span_0 FOREIGN KEY (media_id) REFERENCES media_source (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE media_source_span ADD CONSTRAINT fk_media_source_span_1 FOREIGN KEY (source_file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE speaker ADD CONSTRAINT fk_speaker_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE speaker ADD CONSTRAINT fk_speaker_1 FOREIGN KEY (enterprise_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE speaker ADD CONSTRAINT fk_speaker_2 FOREIGN KEY (merged_into_id) REFERENCES speaker (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE correction ADD CONSTRAINT fk_correction_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE correction ADD CONSTRAINT fk_correction_1 FOREIGN KEY (revision_id,segment_id) REFERENCES transcript_segment (revision_id,segment_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE edit_operation ADD CONSTRAINT fk_edit_operation_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE edit_operation ADD CONSTRAINT fk_edit_operation_1 FOREIGN KEY (before_revision_id) REFERENCES transcript_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE edit_operation ADD CONSTRAINT fk_edit_operation_2 FOREIGN KEY (after_revision_id) REFERENCES transcript_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE edit_operation ADD CONSTRAINT fk_edit_operation_3 FOREIGN KEY (reverts_id) REFERENCES edit_operation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE recording_session ADD CONSTRAINT fk_recording_session_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE recording_session ADD CONSTRAINT fk_recording_session_1 FOREIGN KEY (backend_id) REFERENCES storage_backend (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE recording_session ADD CONSTRAINT fk_recording_session_2 FOREIGN KEY (archive_file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE recording_part ADD CONSTRAINT fk_recording_part_0 FOREIGN KEY (session_id) REFERENCES recording_session (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE recording_chunk ADD CONSTRAINT fk_recording_chunk_0 FOREIGN KEY (part_id) REFERENCES recording_part (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE recording_chunk ADD CONSTRAINT fk_recording_chunk_1 FOREIGN KEY (file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE subtitle_revision ADD CONSTRAINT fk_subtitle_revision_0 FOREIGN KEY (session_id) REFERENCES recording_session (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE subtitle_revision ADD CONSTRAINT fk_subtitle_revision_1 FOREIGN KEY (part_id) REFERENCES recording_part (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

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

ALTER TABLE export_job ADD CONSTRAINT fk_export_job_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE export_job ADD CONSTRAINT fk_export_job_1 FOREIGN KEY (minutes_revision_id) REFERENCES minutes_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE export_job ADD CONSTRAINT fk_export_job_2 FOREIGN KEY (confirmation_id) REFERENCES final_confirmation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE export_job ADD CONSTRAINT fk_export_job_3 FOREIGN KEY (task_id) REFERENCES durable_task (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE export_job ADD CONSTRAINT fk_export_job_4 FOREIGN KEY (output_file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE meeting_deletion ADD CONSTRAINT fk_meeting_deletion_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE meeting_deletion ADD CONSTRAINT fk_meeting_deletion_1 FOREIGN KEY (task_id) REFERENCES durable_task (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE topic ADD CONSTRAINT fk_topic_0 FOREIGN KEY (owner_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE meeting_topic ADD CONSTRAINT fk_meeting_topic_0 FOREIGN KEY (topic_id) REFERENCES topic (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE meeting_topic ADD CONSTRAINT fk_meeting_topic_1 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE corpus_feedback ADD CONSTRAINT fk_corpus_feedback_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE corpus_feedback ADD CONSTRAINT fk_corpus_feedback_1 FOREIGN KEY (source_revision_id) REFERENCES transcript_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE corpus_feedback ADD CONSTRAINT fk_corpus_feedback_2 FOREIGN KEY (submitted_by) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE corpus_feedback ADD CONSTRAINT fk_corpus_feedback_3 FOREIGN KEY (reviewed_by) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE feedback_segment ADD CONSTRAINT fk_feedback_segment_0 FOREIGN KEY (feedback_id) REFERENCES corpus_feedback (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE feedback_segment ADD CONSTRAINT fk_feedback_segment_1 FOREIGN KEY (revision_id,segment_id) REFERENCES transcript_segment (revision_id,segment_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE corpus_revision ADD CONSTRAINT fk_corpus_revision_0 FOREIGN KEY (feedback_id) REFERENCES corpus_feedback (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE corpus_revision ADD CONSTRAINT fk_corpus_revision_1 FOREIGN KEY (content_file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE voiceprint_consent ADD CONSTRAINT fk_voiceprint_consent_0 FOREIGN KEY (subject_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE voiceprint_consent ADD CONSTRAINT fk_voiceprint_consent_1 FOREIGN KEY (revoked_by) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE voiceprint_sample ADD CONSTRAINT fk_voiceprint_sample_0 FOREIGN KEY (consent_id) REFERENCES voiceprint_consent (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE voiceprint_sample ADD CONSTRAINT fk_voiceprint_sample_1 FOREIGN KEY (sample_file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE voiceprint_sample ADD CONSTRAINT fk_voiceprint_sample_2 FOREIGN KEY (encrypted_embedding_file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE voiceprint_sample ADD CONSTRAINT fk_voiceprint_sample_3 FOREIGN KEY (reviewed_by) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE evaluation_dataset ADD CONSTRAINT fk_evaluation_dataset_0 FOREIGN KEY (manifest_file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE configuration_revision ADD CONSTRAINT fk_configuration_revision_0 FOREIGN KEY (created_by) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE configuration_revision ADD CONSTRAINT fk_configuration_revision_1 FOREIGN KEY (reviewed_by) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE evaluation_run ADD CONSTRAINT fk_evaluation_run_0 FOREIGN KEY (dataset_id) REFERENCES evaluation_dataset (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE evaluation_run ADD CONSTRAINT fk_evaluation_run_1 FOREIGN KEY (task_id) REFERENCES durable_task (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE evaluation_run ADD CONSTRAINT fk_evaluation_run_2 FOREIGN KEY (errors_file_id) REFERENCES file_object (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE configuration_release ADD CONSTRAINT fk_configuration_release_0 FOREIGN KEY (configuration_id) REFERENCES configuration_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE configuration_release ADD CONSTRAINT fk_configuration_release_1 FOREIGN KEY (evaluation_run_id) REFERENCES evaluation_run (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE configuration_release ADD CONSTRAINT fk_configuration_release_2 FOREIGN KEY (rollback_of_id) REFERENCES configuration_release (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE configuration_release ADD CONSTRAINT fk_configuration_release_3 FOREIGN KEY (released_by) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE mail_job ADD CONSTRAINT fk_mail_job_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE mail_job ADD CONSTRAINT fk_mail_job_1 FOREIGN KEY (minutes_revision_id) REFERENCES minutes_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE mail_job ADD CONSTRAINT fk_mail_job_2 FOREIGN KEY (confirmation_id) REFERENCES final_confirmation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE mail_job ADD CONSTRAINT fk_mail_job_3 FOREIGN KEY (sender_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE mail_job ADD CONSTRAINT fk_mail_job_4 FOREIGN KEY (task_id) REFERENCES durable_task (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE mail_recipient ADD CONSTRAINT fk_mail_recipient_0 FOREIGN KEY (mail_job_id) REFERENCES mail_job (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE mail_recipient ADD CONSTRAINT fk_mail_recipient_1 FOREIGN KEY (enterprise_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE mail_recipient ADD CONSTRAINT fk_mail_recipient_2 FOREIGN KEY (operation_id) REFERENCES external_operation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE meeting_search_projection ADD CONSTRAINT fk_meeting_search_projection_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE meeting_search_projection ADD CONSTRAINT fk_meeting_search_projection_1 FOREIGN KEY (info_revision_id) REFERENCES meeting_info_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE meeting_search_projection ADD CONSTRAINT fk_meeting_search_projection_2 FOREIGN KEY (roster_revision_id) REFERENCES roster_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE meeting_person_search ADD CONSTRAINT fk_meeting_person_search_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE meeting_person_search ADD CONSTRAINT fk_meeting_person_search_1 FOREIGN KEY (roster_id,member_id) REFERENCES roster_member (roster_id,member_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE minutes_template ADD CONSTRAINT fk_minutes_template_0 FOREIGN KEY (owner_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE external_execution ADD CONSTRAINT fk_external_execution_0 FOREIGN KEY (operation_id) REFERENCES external_operation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE external_execution ADD CONSTRAINT fk_external_execution_1 FOREIGN KEY (task_id) REFERENCES durable_task (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcription_input ADD CONSTRAINT fk_transcription_input_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcription_input ADD CONSTRAINT fk_transcription_input_1 FOREIGN KEY (batch_id) REFERENCES input_batch (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcription_input ADD CONSTRAINT fk_transcription_input_2 FOREIGN KEY (source_revision_id) REFERENCES transcript_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcription_input ADD CONSTRAINT fk_transcription_input_3 FOREIGN KEY (material_snapshot_id) REFERENCES material_snapshot (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE transcript_segment_mark ADD CONSTRAINT fk_transcript_segment_mark_0 FOREIGN KEY (revision_id,segment_id) REFERENCES transcript_segment (revision_id,segment_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

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

ALTER TABLE final_result_review ADD CONSTRAINT fk_final_result_review_0 FOREIGN KEY (confirmation_id) REFERENCES final_confirmation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE final_result_review ADD CONSTRAINT fk_final_result_review_1 FOREIGN KEY (review_id) REFERENCES result_review (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE source_context ADD CONSTRAINT fk_source_context_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE source_context ADD CONSTRAINT fk_source_context_1 FOREIGN KEY (generation_input_id) REFERENCES generation_input (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE source_context ADD CONSTRAINT fk_source_context_2 FOREIGN KEY (transcript_confirmation_id) REFERENCES transcript_confirmation (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE source_context ADD CONSTRAINT fk_source_context_3 FOREIGN KEY (material_snapshot_id) REFERENCES material_snapshot (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE source_context_material ADD CONSTRAINT fk_source_context_material_0 FOREIGN KEY (context_id) REFERENCES source_context (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE source_context_material ADD CONSTRAINT fk_source_context_material_1 FOREIGN KEY (material_revision_id) REFERENCES material_revision (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE source_context_material ADD CONSTRAINT fk_source_context_material_2 FOREIGN KEY (parsed_document_id) REFERENCES parsed_document (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE parsed_document_profile ADD CONSTRAINT fk_parsed_document_profile_0 FOREIGN KEY (document_id) REFERENCES parsed_document (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE input_batch_fingerprint_profile ADD CONSTRAINT fk_input_batch_fingerprint_profile_0 FOREIGN KEY (batch_id) REFERENCES input_batch (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE meeting_minutes_editor_guard ADD CONSTRAINT fk_meeting_minutes_editor_guard_0 FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

INSERT INTO task_outbox_sequence(id,next_id) VALUES(1,1);
