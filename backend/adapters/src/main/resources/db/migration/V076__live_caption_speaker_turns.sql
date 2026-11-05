ALTER TABLE recording_subtitle_event
 ADD COLUMN speaker_turns JSON NULL,
 ADD COLUMN diarization_execution_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD CONSTRAINT fk_subtitle_event_diarization FOREIGN KEY(diarization_execution_id) REFERENCES speech_execution(id),
 ADD CONSTRAINT ck_subtitle_speaker_origin CHECK ((speaker_turns IS NULL) = (diarization_execution_id IS NULL));
