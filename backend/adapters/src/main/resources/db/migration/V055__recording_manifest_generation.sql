-- A verified prefix is reusable only while this database-maintained generation is unchanged.
-- Include out-of-band repairs, earlier-part changes, moves and deletion in invalidation.
ALTER TABLE recording_session ADD COLUMN manifest_generation BIGINT UNSIGNED NOT NULL DEFAULT 0;

CREATE TRIGGER recording_chunk_manifest_insert AFTER INSERT ON recording_chunk
FOR EACH ROW UPDATE recording_session SET manifest_generation=manifest_generation+1 WHERE session_id=NEW.session_id;

CREATE TRIGGER recording_chunk_manifest_update AFTER UPDATE ON recording_chunk
FOR EACH ROW UPDATE recording_session SET manifest_generation=manifest_generation+1 WHERE session_id IN (OLD.session_id,NEW.session_id);

CREATE TRIGGER recording_chunk_manifest_delete AFTER DELETE ON recording_chunk
FOR EACH ROW UPDATE recording_session SET manifest_generation=manifest_generation+1 WHERE session_id=OLD.session_id;
