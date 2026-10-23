# Static fixture INSERT audit before assertion RED attempt 4

Author checked frozen V007 identity ALTER and V008/V009/V010/V011 CREATE/ALTER clauses, not just original V002 definitions. Earlier setup runs preserved errors: Role ambiguity (attempt1), nonexistent identity updated_at (attempt2), then omitted required V007 verified_at (attempt3). No schema changed; no reset/repair.

- enterprise_identity: enterprise_id/display_name/status/verified_at all required; department default empty, department_id nullable, session_generation0, platform_admin false. ACTIVE valid. Matching adopted B06QueueSeamsIT insert.
- task_subject: kind/id/business_version/cancel_epoch/deleted supplied. MEETING registration, unsigned20/5, deleted0. Actual source subject owner uses same meeting UUID.
- meeting: id/owner_id/stage/created_at/updated_at required all supplied; revision20/cancel_epoch5 supplied; access0/ACTIVE lifecycle+governance/ever_published0 defaults; pointers nullable, no fake current pointers. Owner FK actual inserted identity; TRANSCRIPT_PENDING valid.
- file_object: id/backend/storage/namespace/object_key/display_name/size/sha/state/created_at all supplied; meeting FK real; local/AVAILABLE valid; location_revision/reference_epoch0 defaults, provider nullable. SHA(A), size1 controlled metadata only.
- input_batch: all six fixed Table columns supplied; real meeting FK, TRANSCRIPT, base7, exact timestamp.
- input_batch_file: all four Table columns supplied, ordinal1>0, actual batch/file FKs and full SHA.
- transcription_input: all mandatory fixed columns supplied; IMPORT+parser version, EMPTY+null material/source, schema1; model/prompt optional null; complete canonical manifest generated, fingerprint from existing S1 compute, base7/epoch2 preserved. No sentinel.
- durable_task: V010 immutable spec columns id/type/pool/subject/producer/input kind+id/fingerprint/business_version/cancel_epoch/priority/max_attempts supplied; READY state, attempt0, available/created timestamps supplied, lease/output/error nullable. Real MEETING subject FK; unique SHA(taskId) producer; real input UUID, saved9/2 fence deliberately differs from current20/5. Test queue metadata type is explicitly controlled and unregistered, not production Worker.
- transcript_revision: required id/meeting/revision/kind/batch/fingerprint/creator/time/processing_input supplied, parent nullable; producer FK points to actual inserted durable_task when nonnull; RAW/revision8 valid. All fixed Table projection columns accounted for.

Config archive helper independently builds closed canonical TreeMaps with complete processing/parser/codec/confirmation raw definitions and exact raw-SHA references; full controlled budgets match Task3 plan. Historical manifest fence remains7/9/2 while current root is20/5. No production provider/API/registry authorization is implied.
