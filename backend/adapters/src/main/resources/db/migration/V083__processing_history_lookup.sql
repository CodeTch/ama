CREATE INDEX task_subject_type_history ON durable_task(subject_kind,subject_id,task_type,created_at,id);
