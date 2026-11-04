-- Publication settings are a meeting-scoped draft. Frozen copies live in the
-- existing publication submission manifest and the hashed provider document.
CREATE TABLE publication_configuration (
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 version BIGINT NOT NULL,
 configuration_json MEDIUMTEXT NOT NULL,
 updated_by VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 CONSTRAINT publication_configuration_meeting FOREIGN KEY(meeting_id) REFERENCES meeting(id),
 CONSTRAINT publication_configuration_version CHECK(version > 0)
);
