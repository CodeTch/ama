CREATE TABLE recording_part (
  session_id CHAR(36) NOT NULL,
  part_id CHAR(36) NOT NULL,
  part_no INT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (session_id, part_id),
  UNIQUE KEY uq_recording_part_order (session_id, part_no),
  CONSTRAINT fk_recording_part_session FOREIGN KEY (session_id) REFERENCES recording_session(session_id)
);
-- Existing issued identity is preserved; no audio or additional parts are synthesized.
INSERT INTO recording_part(session_id,part_id,part_no,created_at)
SELECT session_id,part_id,1,created_at FROM recording_session;
