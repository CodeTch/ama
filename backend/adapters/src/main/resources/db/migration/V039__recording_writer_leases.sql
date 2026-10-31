ALTER TABLE recording_session
 ADD COLUMN writer_collector_id CHAR(36) NULL,
 ADD COLUMN writer_claim_id CHAR(36) NULL,
 ADD COLUMN writer_claim_base_epoch BIGINT NULL,
 ADD COLUMN writer_claim_epoch BIGINT NULL,
 ADD COLUMN writer_lease_expires_at DATETIME(6) NULL,
 ADD COLUMN writer_connection_id CHAR(36) NULL;
