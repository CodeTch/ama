-- Preserve historical punctuation checkpoints and append independently resumable LLM proposals.
ALTER TABLE speech_checkpoint DROP CHECK ck_speech_checkpoint_stage;
ALTER TABLE speech_checkpoint ADD CONSTRAINT ck_speech_checkpoint_stage
 CHECK (stage IN ('VAD','DIARIZATION','ASR','ENHANCEMENT','EMBEDDING','CORRECTION'));
