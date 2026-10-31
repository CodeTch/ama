ALTER TABLE recording_archive_input
 ADD COLUMN generation BIGINT NOT NULL DEFAULT 0,
 DROP INDEX uq_recording_archive_admission,
 ADD UNIQUE KEY uq_recording_archive_admission(session_id,base_revision,cancel_epoch,generation);
