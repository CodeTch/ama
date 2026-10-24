CREATE TABLE task_subject (
    subject_kind VARCHAR(64) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    business_version BIGINT NOT NULL,
    cancel_epoch BIGINT NOT NULL,
    PRIMARY KEY(subject_kind, subject_id),
    CHECK (business_version >= 0 AND cancel_epoch >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE durable_task (
    id CHAR(36) NOT NULL PRIMARY KEY,
    task_type VARCHAR(64) NOT NULL,
    pool VARCHAR(64) NOT NULL,
    subject_kind VARCHAR(64) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    input_reference VARCHAR(512) NOT NULL,
    input_fingerprint VARCHAR(128) NOT NULL,
    business_version BIGINT NOT NULL,
    cancel_epoch BIGINT NOT NULL,
    priority INT NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'READY',
    attempt BIGINT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(128),
    expires_at DATETIME(6),
    available_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    output_reference VARCHAR(512),
    INDEX task_claim(pool, state, available_at, priority),
    FOREIGN KEY(subject_kind, subject_id) REFERENCES task_subject(subject_kind, subject_id),
    CHECK (business_version >= 0 AND cancel_epoch >= 0 AND attempt >= 0),
    CHECK (state IN ('READY','RUNNING','RECONCILING','SUCCEEDED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- Serial allocation stays locked until COMMIT: cursor readers never skip an earlier uncommitted event.
CREATE TABLE task_outbox_sequence (id INT NOT NULL PRIMARY KEY, next_id BIGINT NOT NULL) ENGINE=InnoDB;
INSERT INTO task_outbox_sequence VALUES (1,1);
CREATE TABLE task_outbox (
    sequence_id BIGINT NOT NULL PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    task_id CHAR(36) NOT NULL,
    reference VARCHAR(512) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    FOREIGN KEY(task_id) REFERENCES durable_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE model_capacity (
    model VARCHAR(128) NOT NULL PRIMARY KEY,
    capacity INT NOT NULL,
    CHECK (capacity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE model_reservation (
    id CHAR(36) NOT NULL PRIMARY KEY,
    model VARCHAR(128) NOT NULL,
    safe_deadline DATETIME(6) NOT NULL,
    FOREIGN KEY(model) REFERENCES model_capacity(model),
    INDEX model_deadline(model,safe_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
