package ama.transcript;
import java.util.*;
/** Schema upper bounds for the closed task/publication metadata projections (V010/V013).
 * Unknown columns retain the conservative 512-scalar bound; no LONGTEXT is admitted. */
final class MinutesMetadataBounds {
 record Row(long bytes,long points,long units) {}
 static Row row(List<String> columns){long bytes=0,points=0,units=0;for(String column:columns){long maximum=scalars(column);bytes+=maximum*4+64;points+=maximum+32;units+=maximum*2+32;}return new Row(bytes,points,units);}
 static int scalars(String expression){String column=expression.contains(" AS ")?expression.substring(expression.lastIndexOf(" AS ")+4):expression;
  if(column.endsWith("_at"))return 32;
  if(column.endsWith("_bytes")||column.endsWith("_scalars")||column.endsWith("_utf16")||column.endsWith("_present"))return 20;
  return switch(column){
   case "id","task_id","input_id","output_id","operation_id","publication_id","confirmation_id","execution_id","local_execution_id","proof_store"->36;
   case "subject_id","business_key","library_id","folder_id","requester_id","local_decided_by","external_document_id"->255;
   case "business_version","cancel_epoch","priority","attempt","max_attempts","reconcile_epoch","current_execution_no","reconcile_scan_seq","execution_no","task_attempt","submission_no","local_attempt_no","provider_proof_reference_shape"->20;
   case "proof_receipt"->40;
   case "task_type","pool","subject_kind","producer_key","input_kind","input_fingerprint","state","lease_owner","output_kind","last_error","owner","outcome","error_code","operation_kind","request_hash","reconcile_owner","no_effect_basis","provider_id","logical_request_hash","execution_request_hash","local_state","local_reason","authority_state","kind","proof_digest"->64;
   default->512;
  };
 }
}
