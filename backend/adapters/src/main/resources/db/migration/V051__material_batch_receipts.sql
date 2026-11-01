SET NAMES utf8mb4;
SET time_zone = '+00:00';
CREATE TABLE material_batch_receipt (
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 scope_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 response_body LONGTEXT COLLATE utf8mb4_0900_bin NOT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(meeting_id,scope_hash),
 CONSTRAINT fk_material_batch_meeting FOREIGN KEY(meeting_id) REFERENCES meeting(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
UPDATE meeting_deletion SET purge_cursor=0 WHERE state<>'COMPLETE' AND phase='PURGE';
