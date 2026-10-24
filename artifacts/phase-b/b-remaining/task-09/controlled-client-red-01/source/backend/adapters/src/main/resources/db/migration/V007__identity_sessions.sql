SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- STOP all legacy business writers before this expand/switch migration.
-- Legacy display text/admin booleans have no authoritative department/status verification provenance.
-- Refuse BEFORE permanent DDL; an existing deployment needs a separately reviewed authoritative
-- mapping/backfill plan. Never fabricate verified_at/issued_at or convert legacy ACL to a grant.
CREATE TEMPORARY TABLE b02_identity_upgrade_preflight (
    safe TINYINT NOT NULL,
    CONSTRAINT identity_upgrade_requires_authoritative_mapping CHECK (safe = 1)
);
INSERT INTO b02_identity_upgrade_preflight
SELECT IF((SELECT COUNT(*) FROM enterprise_identity) = 0
       AND (SELECT COUNT(*) FROM identity_session) = 0
       AND (SELECT COUNT(*) FROM meeting_acl) = 0, 1, 0);
DROP TEMPORARY TABLE b02_identity_upgrade_preflight;

CREATE TABLE department (
    enterprise_id VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
    display_name LONGTEXT NOT NULL,
    parent_id VARCHAR(255) COLLATE utf8mb4_0900_bin NULL,
    verified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (enterprise_id),
    CONSTRAINT fk_department_0 FOREIGN KEY (parent_id) REFERENCES department(enterprise_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE identity_session DROP FOREIGN KEY fk_session_identity;
ALTER TABLE meeting_acl DROP FOREIGN KEY fk_acl_identity;
ALTER TABLE enterprise_identity
    MODIFY enterprise_id VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
    MODIFY display_name LONGTEXT NOT NULL,
    MODIFY department VARCHAR(255) NOT NULL DEFAULT '',
    MODIFY status VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD department_id VARCHAR(255) COLLATE utf8mb4_0900_bin NULL,
    ADD session_generation BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD verified_at DATETIME(6) NOT NULL,
    DROP CHECK chk_identity_status,
    ADD CONSTRAINT ck_enterprise_identity_0 CHECK (status IN ('ACTIVE','DISABLED')),
    ADD CONSTRAINT fk_enterprise_identity_0 FOREIGN KEY (department_id) REFERENCES department(enterprise_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

CREATE TABLE role_assignment (
    enterprise_id VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
    role VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    PRIMARY KEY (enterprise_id, role),
    CONSTRAINT ck_role_assignment_0 CHECK (role IN ('USER','PLATFORM_ADMIN')),
    CONSTRAINT fk_role_assignment_0 FOREIGN KEY (enterprise_id) REFERENCES enterprise_identity(enterprise_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE identity_session
    MODIFY enterprise_id VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
    ADD session_generation BIGINT UNSIGNED NOT NULL,
    ADD issued_at DATETIME(6) NOT NULL,
    DROP INDEX idx_session_identity,
    ADD INDEX session_identity (enterprise_id, expires_at),
    ADD CONSTRAINT ck_identity_session_0 CHECK (expires_at > issued_at),
    ADD CONSTRAINT fk_identity_session_0 FOREIGN KEY (enterprise_id) REFERENCES enterprise_identity(enterprise_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

-- Preserve legacy ACL metadata/integrity only. No new application authorization may read this table.
ALTER TABLE meeting_acl
    MODIFY enterprise_id VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
    ADD CONSTRAINT fk_acl_identity FOREIGN KEY (enterprise_id) REFERENCES enterprise_identity(enterprise_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;
-- department/platform_admin legacy columns remain expand-window metadata, never authority.
-- Owner/effective READ-grant switch and meeting/roster tables belong exclusively to V008.
