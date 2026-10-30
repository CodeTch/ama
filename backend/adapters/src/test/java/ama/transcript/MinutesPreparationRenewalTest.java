package ama.transcript;

import ama.contract.MinutesContracts;
import ama.contract.TaskContracts.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MinutesPreparationRenewalTest {
    final TaskLease lease=new TaskLease("00000000-0000-0000-0000-000000000001",1,"WORKER",Instant.parse("2026-10-29T00:00:00Z"),new TaskSpec("MINUTES_GENERATE_V1","MINUTES_GENERATE",new SubjectRef("MEETING","meeting"),"a".repeat(64),new TypedRef("GENERATION_INPUT","00000000-0000-0000-0000-000000000002"),"b".repeat(64),1,0,0,3));
    Map<String,Object> row(){return Map.of("id",lease.taskId(),"state","RUNNING","attempt",1L,"lease_owner","WORKER","expires_at",LocalDateTime.parse("2026-10-29T00:00:20"),"input_id",lease.spec().input().id(),"input_fingerprint",lease.spec().inputFingerprint());}
    Map<String,List<Map<String,Object>>> original(){return Map.of("durable_task",List.of(row()),"task_attempt",List.of(Map.of("attempt",1L)),"external_operation",List.of(),"external_execution",List.of());}
    Map<String,List<Map<String,Object>>> renewed(){var result=new HashMap<>(original());var task=new HashMap<>(row());task.put("expires_at",LocalDateTime.parse("2026-10-29T00:00:25"));result.put("durable_task",List.of(task));return result;}
    void rejects(Map<String,List<Map<String,Object>>> before,Map<String,List<Map<String,Object>>> after){var failure=assertThrows(MinutesContracts.Failure.class,()->PublicationGraph.requirePreparationUnion(lease,before,after));assertEquals(MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE,failure.code());}
    @Test void unchangedCompleteUnionRequiresNoRecapture(){assertDoesNotThrow(()->PublicationGraph.requirePreparationUnion(lease,original(),original()));}
    @Test void onlyForwardOwnExpiryProducesSignalBoundToExactLeaseReference(){var signal=assertThrows(PublicationGraph.PreparationRenewed.class,()->PublicationGraph.requirePreparationUnion(lease,original(),renewed()));assertTrue(signal.issuedFor(lease));assertFalse(signal.issuedFor(new TaskLease(lease.taskId(),lease.attempt(),lease.leaseOwner(),lease.expiresAt(),lease.spec())));}
    @Test void nullLeaseCannotEnterThisPreparationProtocol(){assertThrows(MinutesContracts.Failure.class,()->PublicationGraph.requirePreparationUnion(null,original(),original()));assertThrows(MinutesContracts.Failure.class,()->PublicationGraph.requirePreparationUnion(null,original(),renewed()));}
    @Test void evenEqualIncompleteOrAdditionalUnionDoesNotAuthorizeThisPath(){rejects(Map.of(),Map.of());var missing=new HashMap<>(original());missing.remove("external_execution");rejects(missing,missing);var extra=new HashMap<>(original());extra.put("other",List.of());rejects(extra,extra);}
    @Test void otherOwnTaskChangesCannotBeHiddenByForwardExpiry(){for(var change:Map.<String,Object>of("id","other","state","RECONCILING","attempt",2L,"lease_owner","other","input_id","other-input","input_fingerprint","different").entrySet()){var next=renewed();var task=new HashMap<>(next.get("durable_task").getFirst());task.put(change.getKey(),change.getValue());next.put("durable_task",List.of(task));rejects(original(),next);}}
    @Test void everyOtherUnionRowMustRemainExact(){for(String table:List.of("task_attempt","external_operation","external_execution")){var next=renewed();next.put(table,List.of(Map.of("other",true)));rejects(original(),next);}}
    @Test void missingOrAdditionalTasksCannotBecomeRecapture(){var next=renewed();next.put("durable_task",List.of());rejects(original(),next);next=renewed();next.put("durable_task",List.of(next.get("durable_task").getFirst(),Map.of("id","other")));rejects(original(),next);}
    @Test void expiryMustBePresentWellFormedAndStrictlyForward(){for(Object expiry:List.of(LocalDateTime.parse("2026-10-29T00:00:19"),"broken",5L)){var next=renewed();var task=new HashMap<>(next.get("durable_task").getFirst());task.put("expires_at",expiry);next.put("durable_task",List.of(task));rejects(original(),next);}var next=renewed();var task=new HashMap<>(next.get("durable_task").getFirst());task.remove("expires_at");next.put("durable_task",List.of(task));rejects(original(),next);}
}
