SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE enterprise_identity (
    enterprise_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    department VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    platform_admin BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (enterprise_id),
    CONSTRAINT chk_identity_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE identity_session (
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    enterprise_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    csrf_token CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (token_hash),
    INDEX idx_session_identity (enterprise_id),
    CONSTRAINT fk_session_identity FOREIGN KEY (enterprise_id) REFERENCES enterprise_identity(enterprise_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- B03 owns the roster workflow. Proposals may be stored here but never authorize before confirmation.
-- No platform role, external name, voice match or action owner is consulted by this ACL boundary.
CREATE TABLE meeting_acl (
    meeting_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    enterprise_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    action VARCHAR(16) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    PRIMARY KEY (meeting_id, enterprise_id, action),
    CONSTRAINT fk_acl_identity FOREIGN KEY (enterprise_id) REFERENCES enterprise_identity(enterprise_id),
    CONSTRAINT chk_acl_action CHECK (action IN ('READ', 'EDIT', 'MANAGE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
