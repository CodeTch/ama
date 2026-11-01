-- Exact library metadata and short-lived, actor-bound proof cache. Neither grants local ACLs.
SET NAMES utf8mb4;
SET time_zone = '+00:00';
CREATE TABLE material_knowledge_location (
 reference_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 library_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 CONSTRAINT fk_material_library_reference FOREIGN KEY(reference_id) REFERENCES material_reference(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
INSERT INTO material_knowledge_location(reference_id,library_id)
 SELECT r.id,MIN(p.library_id) FROM material_reference r
 JOIN external_document_mapping d ON d.provider_id=r.provider_id AND d.external_document_id=r.external_id
 JOIN publication p ON p.id=d.publication_id AND p.state='SUCCEEDED'
 JOIN meeting m ON m.id=p.meeting_id AND m.final_confirmation_id=p.confirmation_id
 WHERE r.kind='KNOWLEDGE' AND REGEXP_LIKE(p.library_id,'^[A-Za-z0-9_-]{1,128}$','c')
 GROUP BY r.id HAVING COUNT(*)=1;
CREATE TABLE material_authority_cache (
 meeting_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
 dependency_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 pins_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 proof_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 verified_until DATETIME(6) NULL,
 demand_until DATETIME(6) NOT NULL,
 next_check_at DATETIME(6) NOT NULL,
 lease_owner CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until DATETIME(6) NULL,
 last_error VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 PRIMARY KEY(meeting_id,actor_id),
 KEY material_authority_due(next_check_at,demand_until,lease_until),
 CONSTRAINT fk_material_authority_root FOREIGN KEY(meeting_id) REFERENCES meeting(id),
 CONSTRAINT fk_material_authority_actor FOREIGN KEY(actor_id) REFERENCES enterprise_identity(enterprise_id),
 CONSTRAINT ck_material_authority_state CHECK(state IN ('ACTIVE','DENIED','UNKNOWN')),
 CONSTRAINT ck_material_authority_proof CHECK(state<>'ACTIVE' OR (proof_hash IS NOT NULL AND verified_until IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- Cleanup is idempotent; reset unfinished cursors because the new ownership steps precede old rows.
UPDATE meeting_deletion SET purge_cursor=0 WHERE state<>'COMPLETE' AND phase='PURGE';
