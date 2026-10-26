package ama.storage;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.function.*;import static org.junit.jupiter.api.Assertions.*;
/** Remaining no2 work on its last genuine attempt. No broad provenance retry. */
class Task11Fix02CompositionWorkerFinalAttemptIT extends Task11Fix02CompositionWorkerIT {
 final Task11Fix02LeaseMetadataTrace metadata=new Task11Fix02LeaseMetadataTrace(trace);
 @Override javax.sql.DataSource createSource(){return metadata.wrap(super.createSource());}
 @Override String startingTaskState(){return "RUNNING";}
 @Override int startingAttempt(){return 2;}
 @Override String workerOwner(){return "FIX02_COMPOSITION_FINAL_ATTEMPT";}
 @Override Map<String,String> workerVariants(String original)throws Exception{return Map.of("transition-generation",variants(original).get("transition-generation"));}
 @Override void save()throws Exception{super.save();Files.writeString(receipt.resolve("lease-metadata.json"),json.writeValueAsString(Map.of("reads",List.copyOf(metadata.reads),"successfulUpdateResults",List.copyOf(metadata.renewals),"executions",List.copyOf(metadata.statements))));}
 @Override ama.transcript.JdbcPublicationService.Request prepareRequest(ama.transcript.JdbcPublicationService service,ama.worker.ExternalExecutionSession session,BooleanSupplier targetUntouched)throws Exception{
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);var lease=session.lease();
  for(int retry=0;retry<3;retry++){
   live(session);var before=stablePreimage();long checkpoint=metadata.beginCall();
   try{var result=service.prepare(lease);assertTrue(System.nanoTime()<deadline,"Fresh preparation exceeded15s window");return result;}
   catch(ama.contract.MinutesContracts.Failure failure){
    boolean untouched=targetUntouched.getAsBoolean();var proof=untouched&&failure.code()==ama.contract.MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE?metadata.onlyCurrentExpiry(checkpoint,trace.phase.get(),lease.taskId(),lease.attempt(),lease.leaseOwner()):null;
    if(proof==null||retry==2||System.nanoTime()>=deadline){cases.add(Map.of("case","prepare-not-retried","phase",trace.phase.get(),"ordinal",retry+1,"code",failure.code().name(),"targetUntouched",untouched,"guard",!untouched?"TARGET_ALREADY_REACHED":proof==null?metadata.rejection.get():"RETRY_BOUND"));save();throw failure;}
    assertEquals(before,stablePreimage(),"All original facts must match before retry");live(session);cases.add(Map.of("case","expiry-only-fresh-prepare","phase",trace.phase.get(),"ordinal",retry+1,"proof",proof,"originalPreimageUnchanged",true,"liveLease",true,"publicationPosts",0));save();
   }finally{metadata.endCall();}
  }throw new AssertionError("No unbounded preparation retry");
 }
 Map<String,Object> stablePreimage(){
  String pub="d629b199-2b36-4ca8-aa4d-7fee86ecdf95",task="af4da8fa-589e-4274-b12a-71d6fab60e7d";
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=? AND state='PREPARED' AND marked_at IS NULL AND task_attempt IS NULL",Integer.class,task));
  return Map.of("meeting",j.queryForList("SELECT id,revision,cancel_epoch,final_confirmation_id,stage,ever_published,lifecycle,governance_state FROM meeting WHERE id=?",meeting),"publication",j.queryForList("SELECT id,state,revision,confirmation_id,business_key,library_id,folder_id FROM publication WHERE id=?",pub),"submissions",j.queryForList("SELECT task_id,submission_no,local_state,SHA2(request_manifest,256) manifest_hash FROM publication_submission WHERE publication_id=? ORDER BY submission_no",pub),"executions",j.queryForList("SELECT e.id,e.state,e.marked_at,e.task_attempt,e.no_effect_basis,SHA2(e.request_manifest,256) manifest_hash,SHA2(e.no_effect_evidence,256) proof_hash FROM external_execution e JOIN publication_submission s ON s.task_id=e.task_id WHERE s.publication_id=? ORDER BY e.id",pub),"operations",j.queryForList("SELECT DISTINCT o.id,o.state,o.current_execution_no,o.request_hash FROM external_operation o JOIN external_execution e ON e.operation_id=o.id JOIN publication_submission s ON s.task_id=e.task_id WHERE s.publication_id=? ORDER BY o.id",pub),"uploads",j.queryForList("SELECT id,purpose,state,completed_file_id FROM upload_session WHERE meeting_id=? ORDER BY id",meeting),"files",j.queryForList("SELECT id,state,sha256,size_bytes,location_revision,reference_epoch FROM file_object WHERE meeting_id=? ORDER BY id",meeting),"mappings",j.queryForList("SELECT publication_id,provider_id,external_document_id,authority_state FROM external_document_mapping WHERE publication_id=?",pub));
 }
}
