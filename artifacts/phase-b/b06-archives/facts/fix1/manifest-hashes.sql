SELECT id,SHA2(config_manifest,256) AS sha256 FROM transcription_input ORDER BY id;
