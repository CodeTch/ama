-- The user request is immutable. A definitely failed speech task gets a separately
-- identified intake attempt, retained across lost responses and process restarts.
ALTER TABLE recording_processing
 ADD COLUMN import_request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD COLUMN import_revision BIGINT UNSIGNED NULL;
UPDATE recording_processing SET import_request_key=request_key,import_revision=expected_revision;
ALTER TABLE recording_processing
 MODIFY import_request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 MODIFY import_revision BIGINT UNSIGNED NOT NULL;
