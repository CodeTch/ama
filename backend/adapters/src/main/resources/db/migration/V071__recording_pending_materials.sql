-- Material edits made after processing starts remain separate from the fixed
-- task input. They become available for an explicit later enhancement.
ALTER TABLE recording_processing
 ADD COLUMN pending_material_snapshot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD COLUMN pending_materials_changed BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN pending_materials_failed BOOLEAN NOT NULL DEFAULT FALSE,
 ADD CONSTRAINT recording_pending_material_snapshot_fk FOREIGN KEY(pending_material_snapshot_id) REFERENCES material_snapshot(id);
