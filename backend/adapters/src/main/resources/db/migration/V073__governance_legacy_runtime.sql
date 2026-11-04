-- Forward-only continuation: V072 is already applied in the local runtime.
-- Existing task inputs predate governance consumption. Preserve an empty historical
-- scope; never apply today's consent or corpus retroactively to those inputs.
INSERT INTO governance_runtime_snapshot(input_id,configuration_revision,configuration,corpus_json,snapshot_sha)
 SELECT t.id,0,c.config_json,JSON_ARRAY(),SHA2(CONCAT('legacy-empty:',t.id),256)
 FROM transcription_input t JOIN governance_configuration c ON c.revision=0;

-- Correct the installed model identity with matching disabled. Legacy vectors retain
-- their original identity and must be re-enrolled; they are never relabelled.
INSERT INTO governance_configuration(revision,config_json,reason)
 SELECT revision+1,JSON_OBJECT('modelVersion','harness-fixture-embedding-v1','promptVersion','summary-v1','voiceThreshold',NULL),
 'Installed speech identity corrected; legacy vectors excluded and matching disabled'
 FROM (SELECT revision,config_json FROM governance_configuration ORDER BY revision DESC LIMIT 1) previous
 WHERE JSON_UNQUOTE(JSON_EXTRACT(config_json,'$.modelVersion'))='harness-embed-v1';
