-- Additive durable controlled governance. Original publication/content provenance remains intact.
ALTER TABLE meeting DROP CHECK ck_meeting_2,
 ADD CONSTRAINT ck_meeting_2 CHECK (governance_state IN ('ACTIVE','UNKNOWN','WITHDRAW_PENDING','WITHDRAWN','OBSOLETE_PENDING','OBSOLETE'));

CREATE TABLE publication_governance_command (
 id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 meeting_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 publication_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 scope_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 wire_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 wire_body longtext NOT NULL,
 action varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reason varchar(2048) COLLATE utf8mb4_0900_bin NOT NULL,
 state varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 attempts bigint unsigned NOT NULL DEFAULT 0,
 lease_owner char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until datetime(6) NULL,
 next_attempt_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 last_error varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 finished_at datetime(6) NULL,
 UNIQUE KEY governance_scope (scope_hash),
 KEY governance_command_due (state,next_attempt_at,lease_until,id),
 KEY governance_command_history (publication_id,created_at,id),
 CONSTRAINT fk_governance_command_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id),
 CONSTRAINT fk_governance_command_publication FOREIGN KEY (publication_id) REFERENCES publication(id),
 CONSTRAINT ck_governance_action CHECK (action IN ('WITHDRAW','OBSOLETE')),
 CONSTRAINT ck_governance_command_state CHECK (state IN ('PENDING','RECONCILING','SUCCEEDED','CONFLICT')),
 CONSTRAINT ck_governance_wire CHECK (OCTET_LENGTH(wire_body)<=32768 AND OCTET_LENGTH(reason)<=8192)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE publication_governance_authority (
 publication_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 document_id varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 remote_revision bigint NOT NULL,
 event_id char(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 proof_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 verified_until datetime(6) NOT NULL,
 next_check_at datetime(6) NOT NULL,
 lease_owner char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until datetime(6) NULL,
 KEY governance_authority_due (next_check_at,lease_until,publication_id),
 CONSTRAINT fk_governance_authority_publication FOREIGN KEY (publication_id) REFERENCES publication(id),
 CONSTRAINT ck_governance_authority_state CHECK (state IN ('ACTIVE','WITHDRAWN','OBSOLETE')),
 CONSTRAINT ck_governance_authority_revision CHECK (remote_revision>=0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE publication_governance_event (
 publication_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_id char(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 remote_revision bigint NOT NULL,
 proof_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 observed_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY (publication_id,event_id),
 CONSTRAINT fk_governance_event_publication FOREIGN KEY (publication_id) REFERENCES publication(id),
 CONSTRAINT ck_governance_event_state CHECK (state IN ('WITHDRAWN','OBSOLETE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE publication_governance_poll (
 publication_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 next_check_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 lease_owner char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until datetime(6) NULL,
 last_error varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 KEY governance_poll_due (next_check_at,lease_until,publication_id),
 CONSTRAINT fk_governance_poll_publication FOREIGN KEY (publication_id) REFERENCES publication(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
