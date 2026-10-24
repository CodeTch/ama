ALTER TABLE external_operation
    ADD COLUMN reconcile_scan_seq BIGINT NOT NULL AUTO_INCREMENT,
    ADD UNIQUE INDEX external_reconcile_scan (reconcile_scan_seq);
