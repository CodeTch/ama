SET NAMES utf8mb4;
SET time_zone = '+00:00';
CREATE TABLE meeting_info_suggestion (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 candidate_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 field VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(6) NOT NULL,
 decided_at DATETIME(6) NULL,
 decided_by VARCHAR(255) COLLATE utf8mb4_0900_bin NULL,
 decided_revision BIGINT NULL,
 UNIQUE KEY info_suggestion_candidate_field(candidate_id,field),
 KEY info_suggestion_pending(meeting_id,state,created_at,id),
 CONSTRAINT fk_info_suggestion_meeting FOREIGN KEY(meeting_id) REFERENCES meeting(id),
 CONSTRAINT fk_info_suggestion_candidate FOREIGN KEY(candidate_id) REFERENCES generation_candidate(id),
 CONSTRAINT ck_info_suggestion_field CHECK(field IN ('NAME','SUMMARY')),
 CONSTRAINT ck_info_suggestion_state CHECK((state='PENDING' AND decided_at IS NULL AND decided_by IS NULL AND decided_revision IS NULL) OR (state IN ('ACCEPTED','REJECTED') AND decided_at IS NOT NULL AND decided_by IS NOT NULL AND decided_revision>=0))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- Metadata indexes are not source proofs. Every value is derived after a full candidate read.
INSERT INTO meeting_info_suggestion(id,meeting_id,candidate_id,field,state,created_at)
 SELECT UUID(),c.meeting_id,c.id,'NAME','PENDING',c.created_at FROM generation_candidate c JOIN minutes_revision b ON b.id=c.minutes_revision_id WHERE b.title_origin='AI' AND CHAR_LENGTH(TRIM(b.title))>0;
INSERT INTO meeting_info_suggestion(id,meeting_id,candidate_id,field,state,created_at)
 SELECT UUID(),c.meeting_id,c.id,'SUMMARY','PENDING',c.created_at FROM generation_candidate c WHERE EXISTS(SELECT 1 FROM minutes_element e WHERE e.set_id=c.element_set_id AND e.category='OVERVIEW' AND CHAR_LENGTH(TRIM(e.body))>0);
UPDATE meeting_deletion SET purge_cursor=0 WHERE state<>'COMPLETE' AND phase='PURGE';
