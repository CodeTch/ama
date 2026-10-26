package ama.transcript;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
public final class Task11Fix01HistoryProbe {
 public static Map<String,Object> verify(JdbcPublicationService service,String meeting,String selected) {
  var a=service.authority;var budget=service.limits.ledger(a.readBudget(),a.canonicalBudget(),()->false);
  var plan=PublicationGraph.discover(a.dataSource(),meeting,budget);
  var submission=plan.rows().get("publication_submission").stream().filter(s->selected.equals(s.get("task_id"))).findFirst().orElseThrow();
  var actor=new ama.contract.IdentityContracts.Actor((String)submission.get("requester_id"),ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
  String manifest=a.transactionRunner().required(()->{a.savedAccess().require(a.savedAccess().lock(actor,meeting),ama.access.MeetingAccess.Action.EDIT);service.j.queryForList("SELECT id FROM publication WHERE meeting_id=? ORDER BY id FOR UPDATE",meeting);service.j.queryForList("SELECT id FROM final_confirmation WHERE meeting_id=? ORDER BY id FOR UPDATE",meeting);PublicationGraph.lock(a.dataSource(),plan,true);PublicationGraph.lock(a.dataSource(),plan,false);return service.j.queryForObject("SELECT request_manifest FROM publication_submission WHERE task_id=?",String.class,selected);});
  var original=ControlledKnowledgeClient.parse(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8),65536);
  assertDoesNotThrow(()->PublicationManifest.history(original,plan));
  var changed=new LinkedHashMap<>(plan.rows());var executions=new ArrayList<Map<String,Object>>();
  for(var e:plan.rows().get("external_execution")){var row=new LinkedHashMap<>(e);if(!selected.equals(e.get("task_id"))&&e.get("marked_at")!=null)row.put("marked_at",null);executions.add(Collections.unmodifiableMap(row));}
  changed.put("external_execution",List.copyOf(executions));
  var missing=new PublicationGraph.Plan(plan.meeting(),plan.controls(),plan.tasks(),Map.copyOf(changed),budget);
  var hidden=new TreeMap<String,Object>(original);hidden.put("priorProofs",List.of());hidden.put("bindingTransition",null);hidden.put("executionRequestHash",ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(Map.of("schema","ama.knowledge-publish-execution.v1","logicalRequestHash",hidden.get("logicalRequestHash"),"executionNo",hidden.get("executionNo"),"priorProofs",List.of()))));
  assertThrows(ama.contract.MinutesContracts.Failure.class,()->PublicationManifest.history(hidden,missing),"An unmarked predecessor needs an actual immutable local-never-marked seal; omission alone is no gap proof");
  return Map.of("actualStoredHistoryAccepted",true,"unsealedMissingMarkerRejected",true,"metadataOrigin","complete actual isolated publication union; detached single-field corruption only","publication",original.get("publicationId"));
 }
}
