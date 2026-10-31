package ama.transcript;

import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Detached immutable-history validation, never a write capability. All parsing runs before roots. */
final class PublicationWriteHistory {
 record ExecutionOriginal(String manifest,String noEffectEvidence) {}
 private static final Set<String> LOCAL_REASONS=Set.of("EXPIRED_UNMARKED_ABANDONED","PREPARATION_EXHAUSTED","TERMINAL_UNMARKED_FAILURE");
 private static final Map<String,String> BINDINGS=Map.of("publicationId","publication_id","confirmationId","confirmation_id","providerId","provider_id","requesterId","requester_id","businessKey","business_key","libraryId","library_id","folderId","folder_id","logicalRequestHash","logical_request_hash","executionRequestHash","execution_request_hash");

 static void verify(Map<String,Object> root,List<Map<String,Object>> publications,List<Map<String,Object>> finals,
                    PublicationGraph.Plan plan,Map<String,String> originals,Map<String,ExecutionOriginal> executionOriginals) {
  try{verifyHistory(root,publications,finals,plan,originals,executionOriginals);}
  catch(ama.contract.MinutesContracts.Failure failure){throw failure;}
  catch(RuntimeException malformed){throw fail(PROVENANCE_UNAVAILABLE);}
 }

 private static void verifyHistory(Map<String,Object> root,List<Map<String,Object>> publications,List<Map<String,Object>> finals,
                                   PublicationGraph.Plan plan,Map<String,String> originals,Map<String,ExecutionOriginal> executionOriginals) {
  var budget=plan.budget();budget.check();uuid(plan.meeting());
  require(plan.meeting().equals(root.get("id")),PROVENANCE_UNAVAILABLE);
  require(n(root,"ever_published")==0&&!"PUBLISHED".equals(root.get("stage"))&&"ACTIVE".equals(root.get("lifecycle"))&&"ACTIVE".equals(root.get("governance_state")),CAPABILITY_UNAVAILABLE);
  require(publications.size()<=1,PROVENANCE_UNAVAILABLE);
  var rows=plan.rows();
  for(String table:List.of("publication_submission","external_document_mapping","publication_attempt","durable_task","task_attempt","external_operation","external_execution"))require(rows.get(table)!=null,PROVENANCE_UNAVAILABLE);
  // The full task/operation union also catches a publication family omitted from its header.
  var publicationTasks=new TreeMap<String,Map<String,Object>>();
  for(var task:rows.get("durable_task")){
   require("MEETING".equals(task.get("subject_kind"))&&plan.meeting().equals(task.get("subject_id")),PROVENANCE_UNAVAILABLE);
   String kind=s(task,"task_type"),input=s(task,"input_kind");
   String expected=switch(kind){case "TRANSCRIPT_IMPORT_V1"->"TRANSCRIPTION_INPUT";case "MINUTES_GENERATE_V1"->"GENERATION_INPUT";case "KNOWLEDGE_PUBLISH_V1"->"PUBLICATION";case "RECORDING_ARCHIVE_V1"->"RECORDING_ARCHIVE_INPUT";default->throw fail(CAPABILITY_UNAVAILABLE);};
   require(expected.equals(input),PROVENANCE_UNAVAILABLE);
   if("PUBLICATION".equals(input))require(publicationTasks.put(s(task,"id"),task)==null,PROVENANCE_UNAVAILABLE);
  }
  var operations=new TreeMap<String,Map<String,Object>>();
  for(var operation:rows.get("external_operation")){
   require("MEETING".equals(operation.get("subject_kind"))&&plan.meeting().equals(operation.get("subject_id")),PROVENANCE_UNAVAILABLE);
   require(Set.of("MINUTES_GENERATE","KNOWLEDGE_PUBLISH").contains(operation.get("operation_kind")),CAPABILITY_UNAVAILABLE);
   if("KNOWLEDGE_PUBLISH".equals(operation.get("operation_kind")))require(operations.put(s(operation,"id"),operation)==null,PROVENANCE_UNAVAILABLE);
  }
  var allTasks=new HashMap<String,Map<String,Object>>();for(var task:rows.get("durable_task"))require(allTasks.put(s(task,"id"),task)==null,PROVENANCE_UNAVAILABLE);
  var allOperations=new HashMap<String,Map<String,Object>>();for(var operation:rows.get("external_operation"))require(allOperations.put(s(operation,"id"),operation)==null,PROVENANCE_UNAVAILABLE);
  for(var attempt:rows.get("task_attempt"))require(allTasks.containsKey(attempt.get("task_id")),PROVENANCE_UNAVAILABLE);
  for(var execution:rows.get("external_execution")){var task=allTasks.get(execution.get("task_id"));var operation=allOperations.get(execution.get("operation_id"));require(task!=null&&operation!=null,PROVENANCE_UNAVAILABLE);String expected="KNOWLEDGE_PUBLISH".equals(operation.get("operation_kind"))?"KNOWLEDGE_PUBLISH_V1":"MINUTES_GENERATE_V1";require(expected.equals(task.get("task_type")),PROVENANCE_UNAVAILABLE);}
  require(rows.get("external_document_mapping").isEmpty(),CAPABILITY_UNAVAILABLE);
  if(publications.isEmpty()){
   require(publicationTasks.isEmpty()&&operations.isEmpty()&&rows.get("publication_submission").isEmpty()&&rows.get("publication_attempt").isEmpty()&&originals.isEmpty()&&executionOriginals.isEmpty(),PROVENANCE_UNAVAILABLE);
   return;
  }
  var header=publications.getFirst();String publication=s(header,"id");uuid(publication);
  require(plan.meeting().equals(header.get("meeting_id")),PROVENANCE_UNAVAILABLE);
  require("FAILED".equals(header.get("state")),CAPABILITY_UNAVAILABLE);
  PublicationLocalDecision.requireClosed(plan,publication);
  var submissions=rows.get("publication_submission").stream().sorted(Comparator.comparingLong(r->n(r,"submission_no"))).toList();
  require(!submissions.isEmpty()&&submissions.size()==publicationTasks.size(),PROVENANCE_UNAVAILABLE);
  require(originals.keySet().equals(publicationTasks.keySet()),PROVENANCE_UNAVAILABLE);
  var usedExecutions=new HashSet<String>();var usedOperations=new HashSet<String>();var usedTasks=new HashSet<String>();long ordinal=0;
  for(var submission:submissions){
   budget.check();budget.row(submission.size());String taskId=s(submission,"task_id");
   require(usedTasks.add(taskId)&&publication.equals(submission.get("publication_id"))&&n(submission,"submission_no")==++ordinal,PROVENANCE_UNAVAILABLE);
   var task=publicationTasks.get(taskId);require(task!=null&&publication.equals(task.get("input_id")),PROVENANCE_UNAVAILABLE);
   String original=originals.get(taskId);var manifest=PublicationManifest.read(original,budget);PublicationManifest.history(manifest,plan);
   for(var pair:BINDINGS.entrySet())require(Objects.equals(manifest.get(pair.getKey()),submission.get(pair.getValue())),PROVENANCE_UNAVAILABLE);
   require(n(manifest,"submissionNo")==ordinal&&plan.meeting().equals(manifest.get("meetingId"))&&JdbcPublicationService.spec(manifest,budget).equals(TranscriptReadGraph.spec(task)),PROVENANCE_UNAVAILABLE);
   var confirmation=one(finals,r->submission.get("confirmation_id").equals(r.get("id")));
   require(plan.meeting().equals(confirmation.get("meeting_id"))&&n(confirmation,"meeting_revision")==n(task,"business_version")&&n(confirmation,"meeting_revision")<=n(root,"revision"),PROVENANCE_UNAVAILABLE);
   PublicationManifest.logical(manifest,s(confirmation,"source_manifest_hash"),s(confirmation,"input_fingerprint"),budget);
   var executions=rows.get("external_execution").stream().filter(r->taskId.equals(r.get("task_id"))).toList();require(executions.size()<=1,PROVENANCE_UNAVAILABLE);
   var execution=executions.isEmpty()?null:executions.getFirst();
   if("NEVER_MARKED".equals(submission.get("local_state"))){
    require(LOCAL_REASONS.contains(submission.get("local_reason"))&&submission.get("local_decided_at")!=null&&submission.get("local_decided_by") instanceof String actor&&!actor.isBlank()&&n(submission,"local_attempt_no")==n(task,"attempt"),PROVENANCE_UNAVAILABLE);
    if("PREPARATION_EXHAUSTED".equals(submission.get("local_reason")))require(n(task,"attempt")>=n(task,"max_attempts"),PROVENANCE_UNAVAILABLE);
   }else{
    require("UNDECIDED".equals(submission.get("local_state"))&&execution!=null,PROVENANCE_UNAVAILABLE);
    for(String field:List.of("local_decided_at","local_decided_by","local_reason","local_attempt_no","local_execution_id"))require(submission.get(field)==null,PROVENANCE_UNAVAILABLE);
   }
   if(execution!=null){
    String id=s(execution,"id"),operationId=s(execution,"operation_id");uuid(id);uuid(operationId);
    require(usedExecutions.add(id)&&executionOriginals.get(id)!=null&&original.equals(executionOriginals.get(id).manifest())&&n(manifest,"executionNo")==n(execution,"execution_no")&&execution.get("finished_at")!=null,PROVENANCE_UNAVAILABLE);
    var operation=operations.get(operationId);require(operation!=null&&manifest.get("businessKey").equals(operation.get("business_key")),PROVENANCE_UNAVAILABLE);usedOperations.add(operationId);
    String evidence=executionOriginals.get(id).noEffectEvidence();
    if(execution.get("marked_at")==null){
     require(("publication-local:"+taskId).equals(evidence)||"LOCAL_PREPARED_ATTEMPT_LIMIT".equals(evidence),PROVENANCE_UNAVAILABLE);
    }else{
     require(("knowledge:"+manifest.get("storeInstanceId")+":"+execution.get("proof_receipt")+":"+execution.get("proof_digest")).equals(evidence),PROVENANCE_UNAVAILABLE);
     require(manifest.get("storeInstanceId").equals(execution.get("proof_store"))&&s(execution,"proof_receipt").matches("receipt-[0-9a-f]{32}")&&s(execution,"proof_digest").matches("[0-9a-f]{64}"),PROVENANCE_UNAVAILABLE);
     var attempt=one(rows.get("task_attempt"),r->taskId.equals(r.get("task_id"))&&n(r,"attempt")==n(execution,"task_attempt"));
     require("FAILED".equals(attempt.get("outcome"))&&attempt.get("ended_at")!=null,PROVENANCE_UNAVAILABLE);
     var published=one(rows.get("publication_attempt"),r->id.equals(r.get("execution_id")));
     require(Set.of("PUBLISH","RECONCILE").contains(published.get("kind")),PROVENANCE_UNAVAILABLE);
    }
   }
  }
  require(usedTasks.equals(publicationTasks.keySet())&&usedExecutions.equals(executionOriginals.keySet())&&usedOperations.equals(operations.keySet()),PROVENANCE_UNAVAILABLE);
  for(var execution:rows.get("external_execution"))if(operations.containsKey(execution.get("operation_id")))require(usedExecutions.contains(execution.get("id")),PROVENANCE_UNAVAILABLE);
  for(var published:rows.get("publication_attempt"))require(publication.equals(published.get("publication_id"))&&usedExecutions.contains(published.get("execution_id")),PROVENANCE_UNAVAILABLE);
  for(var operation:operations.values()){
   var complete=rows.get("external_execution").stream().filter(e->operation.get("id").equals(e.get("operation_id"))).sorted(Comparator.comparingLong(e->n(e,"execution_no"))).toList();
   require(!complete.isEmpty()&&n(operation,"current_execution_no")==complete.size(),PROVENANCE_UNAVAILABLE);long executionNo=0;
   for(var execution:complete)require(n(execution,"execution_no")==++executionNo,PROVENANCE_UNAVAILABLE);
  }
  var latest=submissions.getLast();
  for(String field:List.of("confirmation_id","business_key","library_id","folder_id"))require(Objects.equals(header.get(field),latest.get(field)),PROVENANCE_UNAVAILABLE);
 }
 private static Map<String,Object> one(List<Map<String,Object>> rows,java.util.function.Predicate<Map<String,Object>> predicate){var matches=rows.stream().filter(predicate).toList();require(matches.size()==1,PROVENANCE_UNAVAILABLE);return matches.getFirst();}
 private static String s(Map<String,Object> row,String field){Object value=row.get(field);require(value instanceof String&&!((String)value).isBlank(),PROVENANCE_UNAVAILABLE);return (String)value;}
 private static long n(Map<String,Object> row,String field){require(row!=null&&row.get(field) instanceof Number,PROVENANCE_UNAVAILABLE);return ((Number)row.get(field)).longValue();}
}
