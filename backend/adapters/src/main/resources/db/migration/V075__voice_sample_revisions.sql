SET NAMES utf8mb4;
SET time_zone = '+00:00';
ALTER TABLE governed_voiceprint DROP CHECK ck_voice_state;
ALTER TABLE governed_voiceprint ADD CONSTRAINT ck_voice_state CHECK (state IN ('PENDING','APPROVED','REJECTED','NEEDS_MORE','REVOKED'));
CREATE TABLE governance_voice_sample_revision (
 voiceprint_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 revision bigint unsigned NOT NULL,
 origin varchar(16) CHARACTER SET ascii NOT NULL,
 sample_sha char(64) CHARACTER SET ascii NOT NULL,
 sample_count int NOT NULL,
 model_version varchar(128) CHARACTER SET ascii NOT NULL,
 dimension int NOT NULL,
 consent_reference varchar(1024) NOT NULL,
 expires_at datetime(6) NOT NULL,
 actor_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(voiceprint_id,revision),
 CONSTRAINT ck_voice_revision_origin CHECK(origin IN ('SELF','MEETING'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
