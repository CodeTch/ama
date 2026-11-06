-- Preserve the highest observed DB time without contending with meeting edits on the enterprise identity.
CREATE TABLE identity_session_read_clock (
 enterprise_id VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL PRIMARY KEY,
 observed_at DATETIME(6) NOT NULL,
 FOREIGN KEY (enterprise_id) REFERENCES enterprise_identity(enterprise_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

INSERT INTO identity_session_read_clock(enterprise_id,observed_at)
SELECT enterprise_id,COALESCE(session_time_floor,'1000-01-01 00:00:00') FROM enterprise_identity;
