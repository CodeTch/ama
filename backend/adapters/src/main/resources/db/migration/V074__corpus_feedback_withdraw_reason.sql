-- V072 was already applied before this additive field was introduced.
-- Keep its original checksum and apply the new field as a forward migration.
ALTER TABLE corpus_feedback_binding ADD COLUMN withdraw_reason varchar(2048) NULL;
