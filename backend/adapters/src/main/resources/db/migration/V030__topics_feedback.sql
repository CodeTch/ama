SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE topic (
  id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  name varchar(255) COLLATE utf8mb4_0900_as_ci NOT NULL,
  description longtext NULL,
  revision bigint unsigned NOT NULL DEFAULT 0,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX topic_owner_name (owner_id,name),
  CONSTRAINT fk_topic_owner FOREIGN KEY (owner_id) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE meeting_topic (
  topic_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  meeting_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  added_at datetime(6) NOT NULL,
  PRIMARY KEY (topic_id,meeting_id),
  INDEX meeting_topic_meeting (meeting_id,topic_id),
  CONSTRAINT fk_meeting_topic_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_meeting_topic_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE feedback (
  id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  meeting_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  submitted_by varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  category varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  comment longtext NOT NULL,
  state varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
  revision bigint unsigned NOT NULL DEFAULT 0,
  review_reason longtext NULL,
  reviewed_by varchar(255) COLLATE utf8mb4_0900_bin NULL,
  created_at datetime(6) NOT NULL,
  reviewed_at datetime(6) NULL,
  PRIMARY KEY (id),
  INDEX feedback_submitter (submitted_by,created_at,id),
  INDEX feedback_meeting (meeting_id,created_at,id),
  CONSTRAINT fk_feedback_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_feedback_submitter FOREIGN KEY (submitted_by) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_feedback_reviewer FOREIGN KEY (reviewed_by) REFERENCES enterprise_identity (enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_feedback_state CHECK (state IN ('PENDING','ACCEPTED','REJECTED','NEEDS_MORE'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
