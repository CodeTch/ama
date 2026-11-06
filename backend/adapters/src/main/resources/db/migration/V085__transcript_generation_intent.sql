CREATE TABLE transcript_generation_intent (
 confirmation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 actor_id VARCHAR(255) NOT NULL,
 state ENUM('WAITING','SUBMITTED','SUPERSEDED') NOT NULL DEFAULT 'WAITING',
 next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 INDEX generation_intent_dispatch (state,next_attempt_at),
 FOREIGN KEY (confirmation_id) REFERENCES transcript_confirmation(id) ON DELETE CASCADE
) ENGINE=InnoDB;
