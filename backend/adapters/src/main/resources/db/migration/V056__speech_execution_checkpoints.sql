-- The service key is stable across parser/engine revisions: a profile change cannot
-- manufacture another pool while earlier requests remain in flight.
CREATE TABLE speech_capacity (
 service_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 total_slots INT UNSIGNED NOT NULL,
 realtime_slots INT UNSIGNED NOT NULL,
 offline_slots INT UNSIGNED NOT NULL,
 config_revision BIGINT UNSIGNED NOT NULL,
 CONSTRAINT ck_speech_capacity_bounds CHECK (total_slots BETWEEN 1 AND 100 AND realtime_slots<=total_slots AND offline_slots<=total_slots AND realtime_slots+offline_slots<=total_slots)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- No source text, audio or embedding belongs in this resource-accounting ledger.
-- It deliberately has no source FK: deleting content must not falsely settle an
-- unknown external request. Provider evidence remains necessary to free a slot.
CREATE TABLE speech_execution (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 service_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 scope_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 lane VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 path VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 response_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 response_status INT UNSIGNED NULL,
 lease_task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 lease_attempt BIGINT UNSIGNED NOT NULL,
 lease_owner_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(6) NOT NULL,
 settled_at DATETIME(6) NULL,
 CONSTRAINT fk_speech_execution_capacity FOREIGN KEY(service_key) REFERENCES speech_capacity(service_key),
 CONSTRAINT ck_speech_execution_lane CHECK (lane IN ('OFFLINE','REALTIME')),
 CONSTRAINT ck_speech_execution_state CHECK (state IN ('RESERVED','UNKNOWN','COMPLETED','NOT_SENT','FENCED','TERMINATED')),
 INDEX speech_inflight(service_key,settled_at,lane,created_at),
 INDEX speech_recovery(service_key,settled_at,id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE speech_checkpoint (
 input_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 clip_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 stage VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 file_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 pcm_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 engine_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 start_sample BIGINT UNSIGNED NOT NULL,
 sample_count BIGINT UNSIGNED NOT NULL,
 task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_attempt BIGINT UNSIGNED NOT NULL,
 payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload MEDIUMBLOB NOT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(input_id,clip_key,stage),
 CONSTRAINT fk_speech_checkpoint_input FOREIGN KEY(input_id) REFERENCES transcription_input(id),
 CONSTRAINT fk_speech_checkpoint_task FOREIGN KEY(task_id) REFERENCES durable_task(id),
 CONSTRAINT ck_speech_checkpoint_stage CHECK (stage IN ('VAD','DIARIZATION','ASR','ENHANCEMENT','EMBEDDING')),
 CONSTRAINT ck_speech_checkpoint_clip CHECK (sample_count BETWEEN 1 AND 1920000),
 CONSTRAINT ck_speech_checkpoint_payload CHECK (OCTET_LENGTH(payload) BETWEEN 1 AND 1048576)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Commit the exact exclusive key before physical I/O. Meeting cleanup inventories
-- RESERVED keys too, then permanently retires them before removing metadata; a
-- late put cannot recreate deleted normalized audio.
CREATE TABLE speech_pcm_artifact (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 input_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 file_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 clip_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 pcm_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 engine_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_samples BIGINT UNSIGNED NOT NULL,
 start_sample BIGINT UNSIGNED NOT NULL,
 sample_count BIGINT UNSIGNED NOT NULL,
 backend_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 namespace VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 object_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 wav_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 byte_count BIGINT UNSIGNED NOT NULL,
 state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(6) NOT NULL,
 UNIQUE KEY speech_pcm_clip(input_id,clip_key),
 CONSTRAINT fk_speech_pcm_meeting FOREIGN KEY(meeting_id) REFERENCES meeting(id),
 CONSTRAINT fk_speech_pcm_input FOREIGN KEY(input_id) REFERENCES transcription_input(id),
 CONSTRAINT fk_speech_pcm_task FOREIGN KEY(task_id) REFERENCES durable_task(id),
 CONSTRAINT fk_speech_pcm_file FOREIGN KEY(file_id) REFERENCES file_object(id),
 CONSTRAINT fk_speech_pcm_backend FOREIGN KEY(backend_id) REFERENCES storage_backend(id),
 CONSTRAINT ck_speech_pcm_state CHECK (state IN ('RESERVED','READY')),
 CONSTRAINT ck_speech_pcm_namespace CHECK (namespace='speech-pcm'),
 CONSTRAINT ck_speech_pcm_samples CHECK (source_samples BETWEEN 1 AND 115200000 AND sample_count BETWEEN 1 AND 1920000 AND start_sample+sample_count<=source_samples),
 CONSTRAINT ck_speech_pcm_size CHECK (byte_count=44+sample_count*2)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE meeting_deletion_item DROP CHECK ck_deletion_item_kind;
ALTER TABLE meeting_deletion_item ADD CONSTRAINT ck_deletion_item_kind CHECK(kind IN ('FILE','UPLOAD','EXPORT','RECORDING','PCM'));
