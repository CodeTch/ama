-- Import selection is durable independently of private browser recovery journals.
-- V017 owns background material provenance. This extension owns only the selected main-file order.
CREATE TABLE meeting_import_draft (
 meeting_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 revision bigint unsigned NOT NULL,
 saved_by varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 saved_at datetime(6) NOT NULL,
 CONSTRAINT fk_import_draft_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id),
 CONSTRAINT fk_import_draft_actor FOREIGN KEY (saved_by) REFERENCES enterprise_identity(enterprise_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE meeting_import_draft_file (
 meeting_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 file_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 ordinal int unsigned NOT NULL,
 PRIMARY KEY (meeting_id,file_id), UNIQUE KEY import_file_order (meeting_id,ordinal),
 CONSTRAINT ck_import_file_ordinal CHECK (ordinal BETWEEN 1 AND 16),
 CONSTRAINT fk_import_file_draft FOREIGN KEY (meeting_id) REFERENCES meeting_import_draft(meeting_id),
 CONSTRAINT fk_import_file_object FOREIGN KEY (file_id) REFERENCES file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE meeting_import_command (
 meeting_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 operation varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 command_key char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result_json text COLLATE utf8mb4_0900_bin NOT NULL,
 created_at datetime(6) NOT NULL,
 PRIMARY KEY (meeting_id,actor_id,operation,command_key),
 CONSTRAINT ck_import_command_operation CHECK (operation IN ('SAVE','DISCARD')),
 CONSTRAINT fk_import_command_meeting FOREIGN KEY (meeting_id) REFERENCES meeting(id),
 CONSTRAINT fk_import_command_actor FOREIGN KEY (actor_id) REFERENCES enterprise_identity(enterprise_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
