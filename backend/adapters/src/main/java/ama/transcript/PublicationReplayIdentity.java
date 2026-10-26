package ama.transcript;
import java.util.*;import ama.contract.TaskContracts.TaskSpec;import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
/** Original normalized request identity. It is never reconstructed from current publication state. */
record PublicationReplayIdentity(String operation,String publication,String task,long revision,Long publicationRevision,String confirmation,String provider,String library,String folder,Long predecessorExecution) {
 static PublicationReplayIdentity parse(String operation,String... values){
  return switch(operation){
   case "PUBLICATION_SUBMIT"->{if(values.length!=5)throw new IllegalArgumentException("Submission identity required");uuid(values[1]);yield new PublicationReplayIdentity(operation,null,null,JdbcMinutesSavedQueries.counter(values[0]),null,values[1],values[2],values[3],values[4],null);}
   case "PUBLICATION_RESUBMIT"->{if(values.length!=7)throw new IllegalArgumentException("Replacement identity required");uuid(values[0]);uuid(values[3]);yield new PublicationReplayIdentity(operation,values[0],null,JdbcMinutesSavedQueries.counter(values[1]),JdbcMinutesSavedQueries.counter(values[2]),values[3],values[4],values[5],values[6],null);}
   case "PUBLICATION_LOCAL_RECOVERY"->{if(values.length!=4)throw new IllegalArgumentException("Recovery identity required");uuid(values[0]);uuid(values[1]);yield new PublicationReplayIdentity(operation,values[0],values[1],JdbcMinutesSavedQueries.counter(values[2]),JdbcMinutesSavedQueries.counter(values[3]),null,null,null,null,null);}
   case "PUBLICATION_ZERO_EFFECT_RETRY"->{if(values.length!=4)throw new IllegalArgumentException("Exact retry identity required");uuid(values[0]);long predecessor=JdbcMinutesSavedQueries.counter(values[3]);if(predecessor<1)throw new IllegalArgumentException("Positive predecessor execution required");yield new PublicationReplayIdentity(operation,values[0],null,JdbcMinutesSavedQueries.counter(values[1]),JdbcMinutesSavedQueries.counter(values[2]),null,null,null,null,predecessor);}
   default->throw new IllegalArgumentException("Unknown publication command");
  };
 }
 boolean requiresLegacyInventory(Map<String,Object> manifest){return operation.equals("PUBLICATION_RESUBMIT")&&!manifest.containsKey("acceptanceBinding");}
 void verifyCommand(String scope,String requestHash,Map<String,Object> selected,Map<String,Object> manifest,List<Map<String,Object>> compatible){
  if(operation.equals("PUBLICATION_LOCAL_RECOVERY"))return;
  if(operation.equals("PUBLICATION_ZERO_EFFECT_RETRY"))require(manifest.containsKey("acceptanceBinding"),PROVENANCE_UNAVAILABLE);
  if(manifest.containsKey("acceptanceBinding")){var binding=(Map<?,?>)manifest.get("acceptanceBinding");require(operation.equals(binding.get("operation"))&&scope.equals(binding.get("receiptScopeHash"))&&requestHash.equals(binding.get("requestHash")),PROVENANCE_UNAVAILABLE);}
  else if(operation.equals("PUBLICATION_RESUBMIT")){
   var candidates=compatible.stream().filter(candidate->{Object value=candidate.get("acceptanceBinding");if(value==null)return true;var binding=(Map<?,?>)value;return operation.equals(binding.get("operation"))&&scope.equals(binding.get("receiptScopeHash"))&&requestHash.equals(binding.get("requestHash"));}).toList();
   require(candidates.size()==1,PROVENANCE_UNAVAILABLE);var candidate=candidates.getFirst();
   // Uniqueness proves this acknowledgment only when the candidate is the selected immutable result.
   require(Objects.equals(selected.get("task_id"),candidate.get("task_id"))&&Objects.equals(selected.get("publication_id"),candidate.get("publication_id"))&&legacyOrdinal(selected.get("submission_no"))==legacyOrdinal(candidate.get("submission_no")),PROVENANCE_UNAVAILABLE);
  }
 }
 private static long legacyOrdinal(Object value){
  require(value instanceof Number,PROVENANCE_UNAVAILABLE);
  final long ordinal;
  try{ordinal=new java.math.BigDecimal(value.toString()).longValueExact();}
  catch(NumberFormatException|ArithmeticException invalid){throw new Failure(PROVENANCE_UNAVAILABLE);}
  require(ordinal>1&&ordinal<Long.MAX_VALUE,PROVENANCE_UNAVAILABLE);
  return ordinal;
 }
 boolean compatible(Map<String,Object> submission,Map<String,Object> task){return publication!=null&&publication.equals(submission.get("publication_id"))&&((Number)task.get("business_version")).longValue()==revision&&confirmation.equals(submission.get("confirmation_id"))&&provider.equals(submission.get("provider_id"))&&library.equals(submission.get("library_id"))&&folder.equals(submission.get("folder_id"))&&((Number)submission.get("submission_no")).longValue()>1;}
 void verify(Map<String,Object> submission,TaskSpec spec,Map<String,Object> manifest,List<Map<String,Object>> executions){
  if(operation.equals("PUBLICATION_SUBMIT"))require(((Number)submission.get("submission_no")).longValue()==1,PROVENANCE_UNAVAILABLE);
  if(operation.equals("PUBLICATION_RESUBMIT"))require(((Number)submission.get("submission_no")).longValue()>1,PROVENANCE_UNAVAILABLE);
  if(publication!=null)require(publication.equals(submission.get("publication_id")),PROVENANCE_UNAVAILABLE);
  if(task!=null)require(task.equals(submission.get("task_id")),PROVENANCE_UNAVAILABLE);
  if(operation.equals("PUBLICATION_ZERO_EFFECT_RETRY")){require(revision==spec.businessVersion()&&((Number)submission.get("submission_no")).longValue()>1&&predecessorExecution<Long.MAX_VALUE-1&&((Number)manifest.get("executionNo")).longValue()==predecessorExecution+1&&executions.size()==1,PROVENANCE_UNAVAILABLE);var execution=executions.getFirst();require(submission.get("task_id").equals(execution.get("task_id")),PROVENANCE_UNAVAILABLE);try{require(execution.get("operation_id") instanceof String,PROVENANCE_UNAVAILABLE);uuid((String)execution.get("operation_id"));}catch(RuntimeException invalid){throw fail(PROVENANCE_UNAVAILABLE);}}
  else if(!operation.equals("PUBLICATION_LOCAL_RECOVERY"))require(revision==spec.businessVersion()&&confirmation.equals(submission.get("confirmation_id"))&&provider.equals(submission.get("provider_id"))&&library.equals(submission.get("library_id"))&&folder.equals(submission.get("folder_id")),PROVENANCE_UNAVAILABLE);
  // Local recovery may be acknowledged after an earlier closure/current revision change;
  // its request counters are proven by the receipt digest, not a mutable current root.
  require(executions.size()<=1,PROVENANCE_UNAVAILABLE);for(var execution:executions)require(((Number)execution.get("execution_no")).longValue()==((Number)manifest.get("executionNo")).longValue(),PROVENANCE_UNAVAILABLE);
 }
}
