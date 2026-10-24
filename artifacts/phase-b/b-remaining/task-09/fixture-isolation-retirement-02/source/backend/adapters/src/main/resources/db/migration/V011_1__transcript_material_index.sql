-- Root-registered V011.1 correction; requires successful frozen V011.
-- Add only the existing B00-1.2 material-snapshot backing index omitted by V011.
-- The material foreign key remains deferred to V017; no column or row changes.
-- Immutable after formal sharing; root central SHA ACK required before apply.
CREATE INDEX fk_transcription_input_3 ON transcription_input (material_snapshot_id);
