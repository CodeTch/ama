package ama.transcript;
import java.util.*;

/** A bounded read hint from the complete captured history. Writes repeat all authority checks. */
final class PublicationRecoveryEligibility {
 static List<String> actions(Map<String,Object> root,Map<String,Object> publication,Map<String,List<Map<String,Object>>> rows,boolean owner){
  if(!owner||!"ACTIVE".equals(root.get("lifecycle"))||!"ACTIVE".equals(root.get("governance_state"))||((Number)root.get("ever_published")).longValue()!=0||"SUCCEEDED".equals(publication.get("state"))||!rows.get("external_document_mapping").isEmpty())return List.of();
  if(rows.get("external_operation").stream().anyMatch(o->Set.of("MARKED","UNKNOWN","SUCCEEDED","MANUAL_REQUIRED").contains(o.get("state")))||rows.get("external_execution").stream().anyMatch(e->Set.of("MARKED","UNKNOWN","SUCCEEDED").contains(e.get("state"))))return List.of();
  var result=new ArrayList<String>();
  // These existing selectors are metadata-only. The plan contains no call capability or ledger.
  var plan=new PublicationGraph.Plan((String)root.get("id"),List.of(),List.of(),rows,null);
  try{
   var latest=rows.get("publication_submission").stream().max(Comparator.comparingLong(x->((Number)x.get("submission_no")).longValue())).orElseThrow();
   var task=rows.get("durable_task").stream().filter(t->latest.get("task_id").equals(t.get("id"))).findFirst().orElseThrow();
   if(Set.of("FAILED","CANCELLED","STALE").contains(task.get("state"))&&"UNDECIDED".equals(latest.get("local_state"))&&PublicationLocalDecision.inspect(plan,TranscriptReadGraph.spec(task),(String)task.get("id"))!=null)result.add("RECOVER_LOCAL");
  }catch(RuntimeException unavailable){/* Incomplete or remote-marked history grants no local closure hint. */}
  if("FAILED".equals(publication.get("state"))){
   try{PublicationLocalDecision.requireClosed(plan,(String)publication.get("id"));result.add("RESUBMIT");}catch(RuntimeException unavailable){return List.copyOf(result);}
   try{
    var operation=rows.get("external_operation").stream().filter(o->publication.get("business_key").equals(o.get("business_key"))).findFirst().orElseThrow();
    var selected=JdbcPublicationReplacement.exactSelection(plan,publication,((Number)operation.get("current_execution_no")).longValue());
    if(Objects.equals(root.get("final_confirmation_id"),publication.get("confirmation_id"))&&Objects.equals(root.get("revision"),selected.task().get("business_version")))result.add("RETRY_ZERO_EFFECT");
   }catch(RuntimeException unavailable){/* Reconfiguration may remain available; exact input is not proven. */}
  }
  return List.copyOf(result);
 }
}
