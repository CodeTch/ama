CREATE TABLE feedback_history (
  id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  feedback_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  actor_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  action varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  revision_before bigint unsigned NOT NULL,
  revision_after bigint unsigned NOT NULL,
  state_after varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  text varchar(4096) NOT NULL,
  created_at datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY feedback_history_revision (feedback_id,revision_after),
  CONSTRAINT fk_feedback_history_feedback FOREIGN KEY (feedback_id) REFERENCES feedback(id),
  CONSTRAINT fk_feedback_history_actor FOREIGN KEY (actor_id) REFERENCES enterprise_identity(enterprise_id),
  CONSTRAINT ck_feedback_history_action CHECK (action IN ('SUPPLEMENT','REVIEW','UNDO_REVIEW')),
  CONSTRAINT ck_feedback_history_revision CHECK (revision_after=revision_before+1),
  CONSTRAINT ck_feedback_history_state CHECK (state_after IN ('PENDING','ACCEPTED','REJECTED','NEEDS_MORE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
