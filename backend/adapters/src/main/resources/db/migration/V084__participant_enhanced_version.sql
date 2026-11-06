ALTER TABLE material_enhancement_candidate ADD COLUMN adopted_revision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD INDEX enhancement_participant_version(meeting_id,adopted_revision_id);
UPDATE material_enhancement_candidate SET adopted_revision_id=JSON_UNQUOTE(JSON_EXTRACT(receipt_body,'$.result.revisionId'))
 WHERE receipt_body IS NOT NULL AND JSON_VALID(receipt_body);
