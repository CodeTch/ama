-- Keep list sorting independent of potentially multi-megabyte audio JSON.
-- STORED metadata preserves old immutable dataset bytes and computes future counts atomically.
ALTER TABLE governance_dataset
 ADD COLUMN sample_count INT UNSIGNED GENERATED ALWAYS AS (JSON_LENGTH(samples_json)) STORED,
 ADD INDEX operations_dataset_history (created_at,id),
 ADD CONSTRAINT ck_operations_dataset_count CHECK (sample_count BETWEEN 1 AND 50);
