package ama.storage;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class Task11Fix01TransferFaultIT extends Task11Fix01HistoryValidationIT {
 final Task11Fix01RollbackFault fault=new Task11Fix01RollbackFault();
 @Override javax.sql.DataSource createSource(){return fault.wrap(super.createSource());}
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
  var history=json.readTree(Files.readAllBytes(previousPipeline().resolve("fix01-isolated-history.json")));String pub=history.get("original").get("publicationId").asText(),old=history.get("original").get("taskId").asText();String execution=j.queryForObject("SELECT id FROM external_execution WHERE task_id=?",String.class,old);var service=ama.api.Task11RuntimeProbe.publication(app);var reports=new ArrayList<Map<String,Object>>();
  for(var kind:List.of(Task11Fix01RollbackFault.Kind.MANIFEST,Task11Fix01RollbackFault.Kind.EVIDENCE)){
   var before=j.queryForMap("SELECT request_manifest,no_effect_evidence FROM external_execution WHERE id=?",execution);var traffic=service.traffic();fault.arm(kind,execution);
   try{var denied=assertThrows(ama.contract.MinutesContracts.Failure.class,()->service.recoverOriginalReceipt(actor(),meeting,pub,old));assertEquals(ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED,denied.code());assertTrue(fault.injected);assertFalse(fault.after.stream().anyMatch(sql->sql.startsWith("SELECT e.request_manifest execution_manifest")||sql.startsWith("SELECT request_manifest,no_effect_evidence")));}
   finally{fault.disarm();}
   assertEquals(before,j.queryForMap("SELECT request_manifest,no_effect_evidence FROM external_execution WHERE id=?",execution));var after=service.traffic();assertEquals(traffic.get("postWriteAttempts"),after.get("postWriteAttempts"));assertEquals(traffic.get("getWriteAttempts")+(kind==Task11Fix01RollbackFault.Kind.EVIDENCE?1:0),after.get("getWriteAttempts"));reports.add(Map.of("kind",kind.name(),"execution",execution,"rolledBackExactPreimage",true,"sqlAfterInjection",List.copyOf(fault.after)));
  }
  var source=j.queryForMap("SELECT id,purpose FROM upload_session WHERE meeting_id=? AND state='COMPLETED' ORDER BY id LIMIT 1",meeting);assertEquals(false,ama.transcript.Task11Fix01FinalProbe.readiness(service,actor(),meeting).get("ready"));fault.arm(Task11Fix01RollbackFault.Kind.SOURCE,(String)source.get("id"));
  try{var denied=assertThrows(ama.contract.MinutesContracts.Failure.class,()->ama.transcript.Task11Fix01FinalProbe.readiness(service,actor(),meeting));assertEquals(ama.contract.MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE,denied.code());assertTrue(fault.injected);}
  finally{fault.disarm();}
  assertEquals(source,j.queryForMap("SELECT id,purpose FROM upload_session WHERE id=?",source.get("id")));reports.add(Map.of("kind","SOURCE","upload",source.get("id"),"rolledBackExactPreimage",true,"sqlAfterInjection",List.copyOf(fault.after),"qualification","source validation actually reached before existing closed-history readiness decision; manual history is not the failure cause"));
  Files.writeString(receipt.resolve("rollback-transfer-and-current-source-faults.json"),json.writeValueAsString(reports));
 }
}
