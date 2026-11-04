-- Immutable association of saved minutes, elements and generation settings (MIN-11).
-- Existing independent histories cannot be paired by timestamps; only current
-- pointers are backfilled. Every subsequent relevant transition records a pair.
CREATE TABLE minutes_workspace_snapshot (
 id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 meeting_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 revision bigint unsigned NOT NULL,
 minutes_revision_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 element_set_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 template_revision_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 exclusion_revision_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 transcript_confirmation_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 material_snapshot_id char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 needs_generation tinyint NOT NULL,
 generation_fingerprint char(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 created_by varchar(255) COLLATE utf8mb4_bin NOT NULL,
 created_at datetime(6) NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY minutes_workspace_history(meeting_id,revision),
 CHECK(needs_generation IN (0,1))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
INSERT INTO minutes_workspace_snapshot
 SELECT UUID(),id,revision,minutes_revision_id,element_set_id,template_revision_id,exclusion_revision_id,
 transcript_confirmation_id,material_snapshot_id,needs_generation,generation_fingerprint,owner_id,updated_at
 FROM meeting WHERE minutes_revision_id IS NOT NULL AND element_set_id IS NOT NULL
 AND template_revision_id IS NOT NULL AND exclusion_revision_id IS NOT NULL AND transcript_confirmation_id IS NOT NULL;
