package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Viewer READ capture only. It never borrows the original requester's receipt capability. */
final class PublicationDocumentRead {
 record Snapshot(Map<String,Object> root,Map<String,Object> publication,Map<String,Object> confirmation,
                 Map<String,Object> mapping,Map<String,Object> submission,Map<String,Object> task,
                 Map<String,Object> operation,Map<String,Object> execution,Map<String,Object> attempt,
                 String manifest,String result) {}
 static Snapshot capture(JdbcPublicationService service,Actor viewer,String meeting,String publication,PublicationGraph.Plan plan) {
  var budget=plan.budget();var authority=service.authority;var j=service.j;
  return authority.transactionRunner().required(()->{
   budget.check();authority.savedAccess().require(authority.savedAccess().lock(viewer,meeting),MeetingAccess.Action.READ);
   var root=one(service,"SELECT id,owner_id,revision,access_revision,cancel_epoch,lifecycle,governance_state,stage,ever_published,final_confirmation_id,info_revision_id,roster_revision_id,transcript_confirmation_id,minutes_revision_id,material_snapshot_id,element_set_id,template_revision_id,exclusion_revision_id FROM meeting WHERE id=?",budget,meeting);
   var pub=one(service,"SELECT id,meeting_id,confirmation_id,business_key,library_id,folder_id,state,revision,published_at FROM publication WHERE id=? AND meeting_id=? FOR UPDATE",budget,publication,meeting);
   var confirmation=one(service,"SELECT id,meeting_id,meeting_revision,info_revision_id,roster_revision_id,transcript_confirmation_id,minutes_revision_id,material_snapshot_id,element_set_id,template_revision_id,exclusion_revision_id,source_manifest_hash,input_fingerprint,confirmed_by,confirmed_at FROM final_confirmation WHERE id=? AND meeting_id=? FOR UPDATE",budget,pub.get("confirmation_id"),meeting);
   PublicationGraph.lock(authority.dataSource(),plan,true);PublicationGraph.lock(authority.dataSource(),plan,false);
   var selected=select(root,pub,confirmation,plan.rows());
   var submission=selected.submission();var execution=selected.execution();var operation=selected.operation();
   long bytes=n(submission,"request_manifest_bytes"),points=n(submission,"request_manifest_scalars"),units=n(submission,"request_manifest_utf16");
   require(bytes>0&&bytes<=Math.min(65536,authority.readBudget().maxManifestBytes())&&points>0&&points<=bytes&&units>=points&&units<=bytes,BUDGET_EXCEEDED);budget.text(bytes,points,units,2,false);
   var originals=j.queryForList("SELECT request_manifest FROM publication_submission WHERE task_id=? AND publication_id=? AND OCTET_LENGTH(request_manifest)=? AND CHAR_LENGTH(request_manifest)=? AND OCTET_LENGTH(CONVERT(request_manifest USING utf16))/2=?",submission.get("task_id"),publication,bytes,points,units);require(originals.size()==1,PROVENANCE_UNAVAILABLE);String original=(String)originals.getFirst().get("request_manifest");
   var stored=PublicationExecutionRead.read(j,budget,authority.readBudget().maxManifestBytes(),(String)submission.get("task_id"),(String)operation.get("id"),(String)execution.get("id"),n(execution,"execution_no"));
   require(original!=null&&original.equals(stored.manifest()),PROVENANCE_UNAVAILABLE);
   var sizes=one(service,"SELECT OCTET_LENGTH(external_result_id) bytes,CHAR_LENGTH(external_result_id) points,OCTET_LENGTH(CONVERT(external_result_id USING utf16))/2 units FROM external_operation WHERE id=?",budget,operation.get("id"));
   long rb=n(sizes,"bytes"),rp=n(sizes,"points"),ru=n(sizes,"units");require(rb>0&&rb<=2048&&rp>0&&rp<=255&&ru>=rp&&ru<=rb,BUDGET_EXCEEDED);budget.text(rb,rp,ru,2,false);
   var results=j.queryForList("SELECT external_result_id FROM external_operation WHERE id=? AND OCTET_LENGTH(external_result_id)=? AND CHAR_LENGTH(external_result_id)=? AND OCTET_LENGTH(CONVERT(external_result_id USING utf16))/2=?",operation.get("id"),rb,rp,ru);require(results.size()==1,PROVENANCE_UNAVAILABLE);String result=PublicationDestinationPage.id(results.getFirst().get("external_result_id"));require(result.matches("result-[0-9a-f]{32}"),PROVENANCE_UNAVAILABLE);
   return new Snapshot(root,pub,confirmation,selected.mapping(),submission,selected.task(),operation,execution,selected.attempt(),original,result);
  });
 }
 /** Complete persisted success association, also used by the DB-only route projection. */
 static Snapshot select(Map<String,Object> root,Map<String,Object> pub,Map<String,Object> confirmation,Map<String,List<Map<String,Object>>> rows) {
  String publication=(String)pub.get("id"),meeting=(String)pub.get("meeting_id"),finalId=(String)pub.get("confirmation_id");
  require("SUCCEEDED".equals(pub.get("state"))&&"PUBLISHED".equals(root.get("stage"))&&n(root,"ever_published")==1&&"ACTIVE".equals(root.get("lifecycle"))&&"ACTIVE".equals(root.get("governance_state"))&&meeting.equals(root.get("id"))&&finalId.equals(root.get("final_confirmation_id"))&&finalId.equals(confirmation.get("id"))&&meeting.equals(confirmation.get("meeting_id"))&&n(root,"revision")==n(confirmation,"meeting_revision"),PROVENANCE_UNAVAILABLE);
  JdbcMinutesFactsReader.requireEffectiveFinal(root,confirmation);
  var mapping=unique(rows.get("external_document_mapping"),r->publication.equals(r.get("publication_id")));
  require("ACTIVE".equals(mapping.get("authority_state")),PROVENANCE_UNAVAILABLE);require(PublicationDestinationPage.id(mapping.get("external_document_id")).matches("doc-[0-9a-f]{32}"),PROVENANCE_UNAVAILABLE);
  var attempt=unique(rows.get("publication_attempt"),r->publication.equals(r.get("publication_id"))&&"SUCCESS".equals(r.get("outcome")));
  require(finalId.equals(attempt.get("confirmation_id"))&&Set.of("PUBLISH","RECONCILE").contains(attempt.get("kind"))&&attempt.get("finished_at")!=null,PROVENANCE_UNAVAILABLE);
  var execution=unique(rows.get("external_execution"),r->attempt.get("execution_id").equals(r.get("id")));
  var operation=unique(rows.get("external_operation"),r->attempt.get("operation_id").equals(r.get("id")));
  var submission=unique(rows.get("publication_submission"),r->execution.get("task_id").equals(r.get("task_id")));
  var task=unique(rows.get("durable_task"),r->execution.get("task_id").equals(r.get("id")));
  require("SUCCEEDED".equals(execution.get("state"))&&"SUCCEEDED".equals(operation.get("state"))&&"SUCCEEDED".equals(task.get("state"))&&execution.get("marked_at")!=null&&execution.get("finished_at")!=null&&execution.get("task_attempt")!=null&&operation.get("id").equals(execution.get("operation_id"))&&n(operation,"current_execution_no")==n(execution,"execution_no"),PROVENANCE_UNAVAILABLE);
  require(publication.equals(submission.get("publication_id"))&&finalId.equals(submission.get("confirmation_id"))&&mapping.get("provider_id").equals(submission.get("provider_id"))&&"UNDECIDED".equals(submission.get("local_state")),PROVENANCE_UNAVAILABLE);
  for(String key:List.of("business_key","library_id","folder_id"))require(Objects.equals(pub.get(key),submission.get(key)),PROVENANCE_UNAVAILABLE);
  require("KNOWLEDGE_PUBLISH_V1".equals(task.get("task_type"))&&"KNOWLEDGE_PUBLISH".equals(task.get("pool"))&&"MEETING".equals(task.get("subject_kind"))&&meeting.equals(task.get("subject_id"))&&"PUBLICATION".equals(task.get("input_kind"))&&publication.equals(task.get("input_id"))&&"PUBLICATION".equals(task.get("output_kind"))&&publication.equals(task.get("output_id"))&&n(task,"business_version")==n(root,"revision")&&n(task,"cancel_epoch")==n(root,"cancel_epoch"),PROVENANCE_UNAVAILABLE);
  require("KNOWLEDGE_PUBLISH".equals(operation.get("operation_kind"))&&"MEETING".equals(operation.get("subject_kind"))&&meeting.equals(operation.get("subject_id"))&&pub.get("business_key").equals(operation.get("business_key"))&&submission.get("logical_request_hash").equals(operation.get("request_hash"))&&submission.get("logical_request_hash").equals(task.get("input_fingerprint"))&&submission.get("execution_request_hash").equals(execution.get("request_hash")),PROVENANCE_UNAVAILABLE);
  var attempts=rows.get("task_attempt").stream().filter(r->task.get("id").equals(r.get("task_id"))).toList();require(attempts.size()==n(task,"attempt")&&attempts.stream().allMatch(r->r.get("ended_at")!=null)&&attempts.stream().anyMatch(r->n(r,"attempt")==n(execution,"task_attempt")&&"SUCCEEDED".equals(r.get("outcome"))),PROVENANCE_UNAVAILABLE);
  return new Snapshot(root,pub,confirmation,mapping,submission,task,operation,execution,attempt,null,null);
 }
 static Map<String,Object> verify(JdbcPublicationService service,Snapshot snapshot,PublicationGraph.Plan plan) {
  JdbcMinutesFactsReader.independent();var budget=plan.budget();var manifest=PublicationManifest.read(snapshot.manifest(),budget);PublicationManifest.history(manifest,plan);
  for(var identity:service.client.identities().entrySet())require(identity.getValue().equals(manifest.get(identity.getKey())),PROVENANCE_UNAVAILABLE);
  for(var pair:Map.of("publicationId","publication_id","confirmationId","confirmation_id","providerId","provider_id","requesterId","requester_id","businessKey","business_key","libraryId","library_id","folderId","folder_id","logicalRequestHash","logical_request_hash","executionRequestHash","execution_request_hash").entrySet())require(Objects.equals(manifest.get(pair.getKey()),snapshot.submission().get(pair.getValue())),PROVENANCE_UNAVAILABLE);
  require(snapshot.root().get("id").equals(manifest.get("meetingId"))&&n(manifest,"executionNo")==n(snapshot.execution(),"execution_no")&&n(manifest,"submissionNo")==n(snapshot.submission(),"submission_no")&&JdbcPublicationService.spec(manifest,budget).equals(TranscriptReadGraph.spec(snapshot.task())),PROVENANCE_UNAVAILABLE);
  PublicationManifest.logical(manifest,(String)snapshot.confirmation().get("source_manifest_hash"),(String)snapshot.confirmation().get("input_fingerprint"),budget);return manifest;
 }
 static boolean available(Map<String,Object> root,Map<String,Object> publication,Map<String,List<Map<String,Object>>> rows) {
  try{uuid((String)root.get("id"));uuid((String)publication.get("id"));var confirmations=rows.getOrDefault("final_confirmation",List.of());if(confirmations.size()!=1)return false;select(root,publication,confirmations.getFirst(),rows);return true;}catch(RuntimeException unavailable){return false;}
 }
 private static Map<String,Object> unique(List<Map<String,Object>> rows,java.util.function.Predicate<Map<String,Object>> predicate){var found=rows.stream().filter(predicate).toList();require(found.size()==1,PROVENANCE_UNAVAILABLE);return found.getFirst();}
 private static Map<String,Object> one(JdbcPublicationService service,String sql,TranscriptAcquisitionBudget.Ledger budget,Object...args){budget.check();budget.admitCapture(65536,16384,32768,2);var rows=service.j.queryForList(sql,args);if(rows.isEmpty())throw new ama.contract.TranscriptContracts.Failure(404,"OFFICIAL_DOCUMENT_UNAVAILABLE");require(rows.size()==1,PROVENANCE_UNAVAILABLE);budget.row(rows.getFirst().size());var copy=new LinkedHashMap<String,Object>();for(var entry:rows.getFirst().entrySet()){Object value=JdbcPublicationQueries.normalizeMetadata(entry.getValue());if(value instanceof String text){scalars(text);require(text.length()<=512,BUDGET_EXCEEDED);budget.capture(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,text.codePointCount(0,text.length()),text.length(),3);}copy.put(entry.getKey(),value);}return Collections.unmodifiableMap(copy);}
 private static long n(Map<String,Object> row,String field){require(row.get(field) instanceof Number,PROVENANCE_UNAVAILABLE);return ((Number)row.get(field)).longValue();}
}
