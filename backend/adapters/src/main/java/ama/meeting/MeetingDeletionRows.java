package ama.meeting;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reviewed canonical ownership paths. Never follows an arbitrary foreign content reference.
 * Children precede parents; bounded pages commit under the deleted root and job lease.
 * New meeting-owned schema requires an explicit policy, otherwise completion fails closed. */
final class MeetingDeletionRows {
 record Owned(String table,String where) {}
 static final List<Owned> ORDER=List.of(
  new Owned("meeting_info_suggestion","meeting_id=?"),
  new Owned("generation_region_request","meeting_id=?"),
  new Owned("material_batch_receipt","meeting_id=?"),
  new Owned("material_authority_cache","meeting_id=?"),
  new Owned("material_knowledge_location","reference_id IN (SELECT id FROM material_reference WHERE meeting_id=?)"),
  new Owned("publication_governance_command","meeting_id=?"),
  new Owned("publication_governance_authority","publication_id IN (SELECT id FROM publication WHERE meeting_id=?)"),
  new Owned("publication_governance_event","publication_id IN (SELECT id FROM publication WHERE meeting_id=?)"),
  new Owned("publication_governance_poll","publication_id IN (SELECT id FROM publication WHERE meeting_id=?)"),
  new Owned("action_item","element_id IN (SELECT id FROM minutes_element WHERE set_id IN (SELECT id FROM element_set WHERE meeting_id=?))"),
  new Owned("action_suggestion","element_id IN (SELECT id FROM minutes_element WHERE set_id IN (SELECT id FROM element_set WHERE meeting_id=?))"),
  new Owned("background_note_source","note_id IN (SELECT id FROM background_note WHERE set_id IN (SELECT id FROM element_set WHERE meeting_id=?))"),
  new Owned("external_document_mapping","publication_id IN (SELECT id FROM publication WHERE meeting_id=?)"),
  new Owned("feedback_history","feedback_id IN (SELECT id FROM feedback WHERE meeting_id=?)"),
  new Owned("file_cleanup","(backend_id,namespace,object_key) IN (SELECT i.backend_id,i.namespace,i.object_key FROM meeting_deletion_item i JOIN meeting_deletion d ON d.id=i.deletion_id WHERE d.meeting_id=?)"),
  new Owned("file_reference","file_id IN (SELECT id FROM file_object WHERE meeting_id=?)"),
  new Owned("final_result_review","confirmation_id IN (SELECT id FROM final_confirmation WHERE meeting_id=?)"),
  new Owned("generated_file_origin","meeting_id=?"),
  new Owned("generation_candidate","meeting_id=?"),
  new Owned("input_batch_file","batch_id IN (SELECT id FROM input_batch WHERE meeting_id=?)"),
  new Owned("input_batch_fingerprint_profile","batch_id IN (SELECT id FROM input_batch WHERE meeting_id=?)"),
  new Owned("material_command_receipt","meeting_id=?"),
  new Owned("material_snapshot_item","snapshot_id IN (SELECT id FROM material_snapshot WHERE meeting_id=?)"),
  new Owned("media_source","batch_id IN (SELECT id FROM input_batch WHERE meeting_id=?)"),
  new Owned("media_source_map","batch_id IN (SELECT id FROM input_batch WHERE meeting_id=?)"),
  new Owned("meeting_access_grant","meeting_id=?"),
  new Owned("meeting_acl","meeting_id=?"),
  new Owned("meeting_import_command","meeting_id=?"),
  new Owned("meeting_import_draft_file","meeting_id=?"),
  new Owned("meeting_minutes_editor_guard","meeting_id=?"),
  new Owned("meeting_person_search","meeting_id=?"),
  new Owned("meeting_search_projection","meeting_id=?"),
  new Owned("meeting_topic","meeting_id=?"),
  new Owned("minutes_export_artifact","input_id IN (SELECT id FROM minutes_export_input WHERE meeting_id=?)"),
  new Owned("model_reservation","execution_id IN (SELECT id FROM external_execution WHERE operation_id IN (SELECT id FROM external_operation WHERE subject_kind='MEETING' AND subject_id=?))"),
  new Owned("parsed_document_profile","document_id IN (SELECT id FROM parsed_document WHERE material_revision_id IN (SELECT id FROM material_revision WHERE reference_id IN (SELECT id FROM material_reference WHERE meeting_id=?)))"),
  new Owned("parsed_unit","document_id IN (SELECT id FROM parsed_document WHERE material_revision_id IN (SELECT id FROM material_revision WHERE reference_id IN (SELECT id FROM material_reference WHERE meeting_id=?)))"),
  new Owned("publication_attempt","publication_id IN (SELECT id FROM publication WHERE meeting_id=?)"),
  new Owned("publication_submission","publication_id IN (SELECT id FROM publication WHERE meeting_id=?)"),
  new Owned("recording_transcript_source","meeting_id=?"),
  new Owned("recording_archive","meeting_id=?"),
  new Owned("recording_archive_current","input_id IN (SELECT id FROM recording_archive_input WHERE meeting_id=?)"),
  new Owned("recording_chunk","session_id IN (SELECT session_id FROM recording_session WHERE meeting_id=?)"),
  new Owned("recording_meeting_capture","meeting_id=?"),
  new Owned("recording_part","session_id IN (SELECT session_id FROM recording_session WHERE meeting_id=?)"),
  new Owned("result_review_evidence","review_id IN (SELECT id FROM result_review WHERE meeting_id=?)"),
  new Owned("sensitive_detection_source","detection_id IN (SELECT id FROM sensitive_detection WHERE set_id IN (SELECT id FROM element_set WHERE meeting_id=?))"),
  new Owned("sensitive_exclusion","revision_id IN (SELECT id FROM exclusion_revision WHERE meeting_id=?)"),
  new Owned("source_context_material","context_id IN (SELECT id FROM source_context WHERE meeting_id=?)"),
  new Owned("recording_subtitle_event","meeting_id=?"),
  new Owned("recording_subtitle_job","meeting_id=?"),
  new Owned("speech_review_candidate","meeting_id=?"),
  new Owned("speech_review_batch","meeting_id=?"),
  new Owned("speech_checkpoint","input_id IN (SELECT id FROM transcription_input WHERE meeting_id=?)"),
  new Owned("speech_pcm_artifact","meeting_id=?"),
  new Owned("task_attempt","task_id IN (SELECT id FROM durable_task WHERE subject_kind='MEETING' AND subject_id=?)"),
  new Owned("task_outbox","subject_kind='MEETING' AND subject_id=?"),
  new Owned("transcript_correction_operation","meeting_id=?"),
  new Owned("transcript_speaker_change","revision_id IN (SELECT id FROM transcript_revision WHERE meeting_id=?)"),
  new Owned("transcript_speaker_snapshot","meeting_id=?"),
  new Owned("transcript_segment_mark","revision_id IN (SELECT id FROM transcript_revision WHERE meeting_id=?)"),
  new Owned("transcript_speaker_access","meeting_id=?"),
  new Owned("upload_part","upload_id IN (SELECT id FROM upload_session WHERE meeting_id=?)"),
  new Owned("background_note","set_id IN (SELECT id FROM element_set WHERE meeting_id=?)"),
  new Owned("evidence_link","locator_id IN (SELECT id FROM source_locator WHERE meeting_id=?)"),
  new Owned("external_execution","operation_id IN (SELECT id FROM external_operation WHERE subject_kind='MEETING' AND subject_id=?)"),
  new Owned("feedback","meeting_id=?"),
  new Owned("meeting_import_draft","meeting_id=?"),
  new Owned("minutes_export_input","meeting_id=?"),
  new Owned("parsed_document","material_revision_id IN (SELECT id FROM material_revision WHERE reference_id IN (SELECT id FROM material_reference WHERE meeting_id=?))"),
  new Owned("publication","meeting_id=?"),
  new Owned("recording_archive_input","meeting_id=?"),
  new Owned("result_review","meeting_id=?"),
  new Owned("roster_member","roster_id IN (SELECT id FROM roster_revision WHERE meeting_id=?)"),
  new Owned("sensitive_detection","set_id IN (SELECT id FROM element_set WHERE meeting_id=?)"),
  new Owned("upload_session","meeting_id=?"),
  new Owned("external_operation","subject_kind='MEETING' AND subject_id=?"),
  new Owned("final_confirmation","meeting_id=?"),
  new Owned("minutes_element","set_id IN (SELECT id FROM element_set WHERE meeting_id=?)"),
  new Owned("recording_session","meeting_id=?"),
  new Owned("source_locator","meeting_id=?"),
  new Owned("material_revision","reference_id IN (SELECT id FROM material_reference WHERE meeting_id=?)"),
  new Owned("meeting_info_revision","meeting_id=?"),
  new Owned("minutes_revision","meeting_id=?"),
  new Owned("roster_revision","meeting_id=?"),
  new Owned("source_context","meeting_id=?"),
  new Owned("transcript_segment","revision_id IN (SELECT id FROM transcript_revision WHERE meeting_id=?)"),
  new Owned("generation_input","meeting_id=?"),
  new Owned("material_reference","meeting_id=?"),
  new Owned("speaker","meeting_id=?"),
  new Owned("element_set","meeting_id=?"),
  new Owned("exclusion_revision","meeting_id=?"),
  new Owned("file_object","meeting_id=?"),
  new Owned("transcript_confirmation","meeting_id=?"),
  new Owned("transcript_revision","meeting_id=?"),
  new Owned("durable_task","subject_kind='MEETING' AND subject_id=?"),
  new Owned("transcription_input","meeting_id=?"),
  new Owned("input_batch","meeting_id=?"),
  new Owned("material_snapshot","meeting_id=?")
 );
 static final Set<String> KEEP=Set.of("meeting_deletion","meeting_deletion_item","meeting","task_subject","idempotency_record","audit_event");
 static void verifyCoverage(JdbcTemplate j){
  var known=new HashSet<>(KEEP);ORDER.forEach(p->known.add(p.table));
  var direct=j.queryForList("SELECT c.table_name FROM information_schema.columns c JOIN information_schema.tables t ON t.table_schema=c.table_schema AND t.table_name=c.table_name WHERE c.table_schema=DATABASE() AND t.table_type='BASE TABLE' AND c.column_name='meeting_id'",String.class);
  if(!known.containsAll(direct))throw new IllegalStateException("Unreviewed meeting deletion schema");
  var edges=j.queryForList("SELECT table_name,referenced_table_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND referenced_table_name IS NOT NULL");
  for(var edge:edges)if(known.contains(edge.get("REFERENCED_TABLE_NAME"))&&!known.contains(edge.get("TABLE_NAME")))throw new IllegalStateException("Unreviewed dependent deletion schema");
 }
 static boolean detachPage(JdbcTemplate j,String meeting){
  for(String pair:List.of("transcription_input:source_revision_id","transcript_revision:parent_id","minutes_revision:parent_id","speaker:merged_into_id")){
   var p=pair.split(":");if(j.update("UPDATE "+p[0]+" SET "+p[1]+"=NULL WHERE meeting_id=? AND "+p[1]+" IS NOT NULL LIMIT 500",meeting)>0)return true;
  }return false;
 }
 static int purgePage(JdbcTemplate j,String meeting,int cursor){
  while(cursor<ORDER.size()){
   var p=ORDER.get(cursor);
   if(p.table.equals("recording_chunk")) {
    // Its generation trigger updates recording_session. A DELETE whose subquery
    // also reads that parent is rejected by MySQL 1442. Capture the owned session
    // under the deleted-root transaction, then delete by the bound session ID.
    var sessions=j.queryForList("SELECT s.session_id FROM recording_session s WHERE s.meeting_id=? AND EXISTS(SELECT 1 FROM recording_chunk c WHERE c.session_id=s.session_id) ORDER BY s.session_id LIMIT 1 FOR UPDATE",String.class,meeting);
    if(!sessions.isEmpty()) {
     if(j.update("DELETE FROM recording_chunk WHERE session_id=? LIMIT 500",sessions.getFirst())>0)return cursor;
     throw new IllegalStateException("Recording cleanup source changed");
    }
   } else if(j.update("DELETE FROM "+p.table+" WHERE "+p.where+" LIMIT 500",meeting)>0)return cursor;
   cursor++;
  }return cursor;
 }
 static void finish(JdbcTemplate j,String meeting){
  for(var p:ORDER)if(!j.queryForList("SELECT 1 FROM "+p.table+" WHERE "+p.where+" LIMIT 1",Integer.class,meeting).isEmpty())throw new IllegalStateException("Online meeting data remains");
  j.update("DELETE FROM idempotency_record WHERE resource_id=? AND operation<>'DELETE_MEETING'",meeting);
  j.update("UPDATE meeting SET info_revision_id=NULL,roster_revision_id=NULL,transcript_confirmation_id=NULL,minutes_revision_id=NULL,final_confirmation_id=NULL,actual_meeting_date=NULL,transcript_revision_id=NULL,material_snapshot_id=NULL,template_revision_id=NULL,element_set_id=NULL,exclusion_revision_id=NULL,generation_fingerprint=NULL,needs_generation=0,materials_pending=0 WHERE id=? AND lifecycle='DELETED'",meeting);
 }
}
