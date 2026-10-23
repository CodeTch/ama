SELECT installed_rank,version,description,type,script,checksum,installed_on,execution_time,success FROM flyway_schema_history ORDER BY installed_rank;
SELECT @@version,@@global.innodb_use_native_aio,DATABASE();
SELECT id,meeting_id,SHA2(config_manifest,256) AS manifest_sha,input_fingerprint,base_revision,cancel_epoch FROM transcription_input ORDER BY id;
SELECT id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,max_attempts,state FROM durable_task ORDER BY id;
SELECT sequence_id,event_type,task_id,subject_kind,subject_id,subject_revision,reference_kind,reference_id FROM task_outbox ORDER BY sequence_id;
