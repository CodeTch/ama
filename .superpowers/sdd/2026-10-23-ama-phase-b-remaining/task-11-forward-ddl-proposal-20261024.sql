-- Task 11 publication_submission: UNVERSIONED PROSPECTIVE DDL ONLY.
-- Do not install as a Flyway resource or apply this file. Formal allocation,
-- exact-byte freeze, independent review, and separate application ACK remain future root work.
-- Source baseline: de1611a67ba483afe21f3b29bb69c2d32028efef.

CREATE TABLE publication_submission (
  `task_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `publication_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `submission_no` bigint unsigned NOT NULL,
  `confirmation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `business_key` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `library_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `folder_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `requester_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `logical_request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `execution_request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_manifest` longtext NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `local_state` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `local_decided_at` datetime(6) NULL,
  `local_reason` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `local_decided_by` varchar(255) COLLATE utf8mb4_0900_bin NULL,
  `local_attempt_no` bigint unsigned NULL,
  `local_execution_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (task_id),
  UNIQUE INDEX publication_submission_number (publication_id, submission_no),
  INDEX publication_submission_undecided (local_state, created_at, task_id),
  INDEX publication_submission_confirmation (confirmation_id),
  INDEX publication_submission_requester (requester_id),
  INDEX publication_submission_decider (local_decided_by),
  INDEX publication_submission_execution (local_execution_id),
  CONSTRAINT ck_publication_submission_0
    CHECK (submission_no BETWEEN 1 AND 9223372036854775806),
  CONSTRAINT ck_publication_submission_1
    CHECK (local_state IN ('UNDECIDED', 'NEVER_MARKED')),
  CONSTRAINT ck_publication_submission_2
    CHECK (OCTET_LENGTH(request_manifest) <= 65536),
  CONSTRAINT ck_publication_submission_3
    CHECK (
      (local_state = 'UNDECIDED'
       AND local_decided_at IS NULL
       AND local_reason IS NULL
       AND local_decided_by IS NULL
       AND local_attempt_no IS NULL
       AND local_execution_id IS NULL)
      OR
      (local_state = 'NEVER_MARKED'
       AND local_decided_at IS NOT NULL
       AND local_reason IS NOT NULL
       AND local_reason IN (
         'PREPARATION_EXHAUSTED',
         'TERMINAL_UNMARKED_FAILURE',
         'EXPIRED_UNMARKED_ABANDONED')
       AND local_decided_by IS NOT NULL
       AND local_attempt_no IS NOT NULL)
    ),
  CONSTRAINT fk_publication_submission_task
    FOREIGN KEY (task_id) REFERENCES durable_task (id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_publication_submission_publication
    FOREIGN KEY (publication_id) REFERENCES publication (id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_publication_submission_confirmation
    FOREIGN KEY (confirmation_id) REFERENCES final_confirmation (id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_publication_submission_requester
    FOREIGN KEY (requester_id) REFERENCES enterprise_identity (enterprise_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_publication_submission_decider
    FOREIGN KEY (local_decided_by) REFERENCES enterprise_identity (enterprise_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_publication_submission_execution
    FOREIGN KEY (local_execution_id) REFERENCES external_execution (id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
