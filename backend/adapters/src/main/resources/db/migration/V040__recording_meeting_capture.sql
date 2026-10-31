-- One admission row serializes active capture per meeting. Historical sessions remain intact.
-- Existing active sessions are adopted at runtime only when their identity is unambiguous.
CREATE TABLE recording_meeting_capture (
 meeting_id VARCHAR(255) NOT NULL PRIMARY KEY,
 session_id CHAR(36) NULL,
 UNIQUE KEY uq_recording_meeting_capture_session(session_id),
 FOREIGN KEY(session_id) REFERENCES recording_session(session_id)
);
