ALTER TABLE material_adjustment
 MODIFY source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD original_task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD recording_session_id CHAR(36) NULL,
 ADD original_run_id CHAR(36) NULL,
 ADD CONSTRAINT chk_adjustment_source CHECK (
   (source_id IS NOT NULL AND original_task_id IS NULL AND recording_session_id IS NULL AND original_run_id IS NULL)
   OR (source_id IS NULL AND original_task_id IS NOT NULL AND recording_session_id IS NULL AND original_run_id IS NULL)
   OR (source_id IS NULL AND original_task_id IS NULL AND recording_session_id IS NOT NULL AND original_run_id IS NOT NULL)
 );
ALTER TABLE recording_processing_history ADD successor_id CHAR(36) NULL;
