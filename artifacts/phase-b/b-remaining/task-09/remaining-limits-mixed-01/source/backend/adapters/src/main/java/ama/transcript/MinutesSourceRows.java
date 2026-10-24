package ama.transcript;
import java.util.*;
import static ama.transcript.SavedTranscriptRows.*;
/** Exact finite original-source/task projections. Content is transferred only by protected capture. */
final class MinutesSourceRows {
 static final Map<String,Shape> SHAPES=Map.of(
  "upload_session",SavedTranscriptGraph.SESSION,
  "file_object",SavedTranscriptGraph.FILE,
  "file_reference",metadata("file_reference","file_id,owner_kind,owner_id,purpose","file_id owner_kind owner_id purpose"),
  "durable_task",new Shape("durable_task",List.of("id"),List.of("id","task_type","pool","subject_kind","subject_id","producer_key","input_kind","input_id","input_fingerprint","business_version","cancel_epoch","priority","state","attempt","max_attempts","lease_owner","expires_at","available_at","created_at","output_kind","output_id","last_error"),Set.of("business_version","cancel_epoch","priority","attempt","max_attempts"),Set.of("expires_at","available_at","created_at")),
  "task_attempt",new Shape("task_attempt",List.of("task_id","attempt"),List.of("task_id","attempt","owner","started_at","ended_at","outcome","error_code"),Set.of("attempt"),Set.of("started_at","ended_at")),
  "external_operation",new Shape("external_operation",List.of("id"),List.of("id","operation_kind","business_key","request_hash","state","marked_at","next_check_at","reconcile_epoch","reconcile_owner","reconcile_expires_at","external_result_id","last_error","subject_kind","subject_id","current_execution_no","reconcile_scan_seq"),Set.of("reconcile_epoch","current_execution_no","reconcile_scan_seq"),Set.of("marked_at","next_check_at","reconcile_expires_at")),
  "external_execution",new Shape("external_execution",List.of("id"),List.of("id","operation_id","execution_no","task_id","task_attempt","state","request_hash","request_manifest","created_at","marked_at","finished_at","no_effect_basis","no_effect_evidence","no_effect_verified_at"),Set.of("execution_no","task_attempt"),Set.of("created_at","marked_at","finished_at","no_effect_verified_at")));
 static final Set<String> CONTENT=Set.of("request_manifest","no_effect_evidence","external_result_id");
 static final List<String> RANK=List.of("upload_session","file_object","file_reference","durable_task","task_attempt","external_operation","external_execution");
}
