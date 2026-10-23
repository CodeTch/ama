SELECT installed_rank,version,description,type,script,checksum,installed_on,execution_time,success FROM flyway_schema_history ORDER BY installed_rank;
SELECT @@version,@@global.innodb_use_native_aio,@@global.time_zone,UTC_TIMESTAMP(6),DATABASE();
SELECT id,meeting_id,kind,base_revision,input_fingerprint,created_at FROM input_batch ORDER BY id;
SELECT batch_id,ordinal,file_id,sha256 FROM input_batch_file ORDER BY batch_id,ordinal;
SELECT id,meeting_id,batch_id,kind,processing_config_version,parser_version,manifest_schema_version,OCTET_LENGTH(config_manifest) AS manifest_bytes,CHAR_LENGTH(config_manifest) AS manifest_chars,SHA2(config_manifest,256) AS manifest_sha,input_fingerprint,base_revision,cancel_epoch FROM transcription_input ORDER BY id;
SELECT id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,max_attempts,state FROM durable_task ORDER BY id;
SELECT id,meeting_id,revision,batch_id,input_fingerprint,processing_input_id,producer_task_id FROM transcript_revision ORDER BY id;
SELECT id,meeting_id,sha256,size_bytes FROM file_object ORDER BY id;
