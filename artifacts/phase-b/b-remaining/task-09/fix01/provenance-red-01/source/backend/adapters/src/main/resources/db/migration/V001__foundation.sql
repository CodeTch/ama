-- MySQL 8.4 / InnoDB; UTC wall-clock values are written explicitly by the audit adapter.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE audit_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_id VARCHAR(255) NOT NULL,
    action VARCHAR(255) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    revision BIGINT NOT NULL,
    result VARCHAR(64) NOT NULL,
    trace_id VARCHAR(255) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_audit_resource_time (resource_id, occurred_at),
    INDEX idx_audit_trace (trace_id),
    CONSTRAINT chk_audit_revision CHECK (revision >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
