CREATE TABLE transcript_speaker_department (
 speaker_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 department VARCHAR(255) NOT NULL,
 FOREIGN KEY (speaker_id) REFERENCES speaker(id) ON DELETE CASCADE
) ENGINE=InnoDB;
