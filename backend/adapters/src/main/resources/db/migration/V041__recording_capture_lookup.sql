-- Bounded discovery under the admission-row lock; no cross-meeting locking scan.
ALTER TABLE recording_session ADD KEY ix_recording_session_meeting_state(meeting_id,state,session_id);
