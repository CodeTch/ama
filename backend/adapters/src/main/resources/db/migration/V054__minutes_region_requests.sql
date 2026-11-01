-- Immutable selected-region instructions are distinct from legacy FULL input rows.
ALTER TABLE generation_input DROP CHECK ck_generation_input_0, DROP CHECK ck_generation_input_1,
 ADD CONSTRAINT ck_generation_input_0 CHECK(scope IN ('FULL','ELEMENT','REGION')),
 ADD CONSTRAINT ck_generation_input_1 CHECK((scope IN ('FULL','REGION') AND target_element_id IS NULL) OR (scope='ELEMENT' AND target_element_id IS NOT NULL));
CREATE TABLE generation_region_request (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 base_element_set_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 category VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 instructions TEXT NOT NULL,
 KEY region_request_meeting(meeting_id,id),
 CONSTRAINT fk_region_request_input FOREIGN KEY(id) REFERENCES generation_input(id),
 CONSTRAINT fk_region_request_meeting FOREIGN KEY(meeting_id) REFERENCES meeting(id),
 CONSTRAINT fk_region_request_set FOREIGN KEY(base_element_set_id) REFERENCES element_set(id),
 CONSTRAINT ck_region_request_category CHECK(category IN ('OVERVIEW','DISCUSSION','CONCLUSION','ACTION','RISK','OPEN_QUESTION','DISAGREEMENT')),
 CONSTRAINT ck_region_request_instructions CHECK(CHAR_LENGTH(instructions) BETWEEN 1 AND 4096 AND CHAR_LENGTH(TRIM(instructions))>0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
UPDATE meeting_deletion SET purge_cursor=0 WHERE state<>'COMPLETE' AND phase='PURGE';
