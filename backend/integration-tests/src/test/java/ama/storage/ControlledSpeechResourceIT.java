package ama.storage;

import ama.contract.SpeechContracts.WavClip;
import ama.model.*;
import ama.task.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static ama.storage.ControlledSpeechImportIT.*;

/** Real resource evidence, independent from content completion and task lease lifetime. */
class ControlledSpeechResourceIT {
 @TempDir Path root;
 static final URI ORIGIN=URI.create("http://127.0.0.1:8792");static final ObjectMapper JSON=new ObjectMapper();
 static final HttpClient CLIENT=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).proxy(new ProxySelector(){public List<Proxy> select(URI uri){return List.of(Proxy.NO_PROXY);}public void connectFailed(URI uri,SocketAddress address,IOException failure){}}).build();
 @BeforeAll static void init(){ControlledSpeechImportIT.schema();}
 ControlledSpeechImportIT harness(){var test=new ControlledSpeechImportIT();test.root=root;return test;}
 HttpResponse<String> call(String path,Object body)throws Exception {
  assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());var request=HttpRequest.newBuilder(ORIGIN.resolve(path)).timeout(Duration.ofSeconds(3));if(body==null)request.GET();else request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)));return CLIENT.send(request.build(),HttpResponse.BodyHandlers.ofString());
 }
 void waitState(String id,String state,long seconds)throws Exception {long end=System.nanoTime()+Duration.ofSeconds(seconds).toNanos();do{if(state.equals(j.queryForObject("SELECT state FROM speech_execution WHERE id=?",String.class,id)))return;Thread.sleep(100);}while(System.nanoTime()<end);assertEquals(state,j.queryForObject("SELECT state FROM speech_execution WHERE id=?",String.class,id));}
 void unknown(ControlledSpeechImportIT.Fixture f,ama.contract.TaskContracts.TaskLease lease,String execution,String path,String hash,String state)throws Exception {
  j.update("INSERT INTO speech_execution(id,service_key,scope_sha256,lane,path,request_sha256,state,lease_task_id,lease_attempt,lease_owner_sha256,created_at) VALUES (?,? ,?,'OFFLINE',?,?, ?,?,?,?,UTC_TIMESTAMP(6))",execution,ControlledSpeechProfile.SERVICE,sha(f.input.getBytes(java.nio.charset.StandardCharsets.UTF_8)),path,hash,state,lease.taskId(),lease.attempt(),sha(lease.leaseOwner().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
 }
 @Test void unknown404NeedsAnActualProviderFenceBeforeTheSlotIsReleased()throws Exception {
  try(var f=harness().new Fixture()){
   var lease=f.claim(f.queue);String execution=id(),path="/inference/asr-nano/modelserving/asr",hash=sha(f.wav);
   try(var maintenance=TranscriptImportRuntime.startSpeechMaintenance(f.queue)){
    assertEquals(404,call("/v1/harness/speech-executions/"+execution,null).statusCode());unknown(f,lease,execution,path,hash,"UNKNOWN");waitState(execution,"FENCED",5);
    var proof=call("/v1/harness/speech-executions/"+execution,null);assertEquals(200,proof.statusCode());assertEquals("FENCED",JSON.readTree(proof.body()).path("state").asText());assertEquals(hash,JSON.readTree(proof.body()).path("requestSha256").asText());
    var late=CLIENT.send(HttpRequest.newBuilder(ORIGIN.resolve(path)).timeout(Duration.ofSeconds(3)).header("Content-Type","application/octet-stream").header("X-Ama-Speech-Execution",execution).POST(HttpRequest.BodyPublishers.ofByteArray(f.wav)).build(),HttpResponse.BodyHandlers.discarding());assertEquals(409,late.statusCode());
    var carrier=TranscriptImportRuntime.prepare(f.queue,lease,f.settings,NEVER);assertTrue(f.queue.completeWithEffects(lease,carrier));assertEquals(5,f.checkpoints());
   }
  }
 }
 @Test void twoActuallyRunningUnknownCallsKeepTheOfflineQuotaAcrossQueuesAndDeletion()throws Exception {
  try(var f=harness().new Fixture()){
   var lease=f.claim(f.queue);String path="/inference/asr-nano/modelserving/asr";var ids=List.of(id(),id());
   try(var maintenance=TranscriptImportRuntime.startSpeechMaintenance(f.queue)){
    for(String execution:ids){
     var scenario=Map.ofEntries(Map.entry("module","asr"),Map.entry("name","ama-owned-global-quota-"+execution),Map.entry("enabled",true),Map.entry("priority",1000),Map.entry("method","POST"),Map.entry("path",path),Map.entry("match",Map.of("header_equals",Map.of("x-ama-speech-execution",execution))),Map.entry("status_code",200),Map.entry("delay_ms",30000),Map.entry("response_type","json"),Map.entry("response_body",Map.of("text","controlled quota completion")),Map.entry("response_headers",Map.of()));
     assertEquals(201,call("/admin/api/scenarios",scenario).statusCode());
     var observer=new HttpSpeechModelPort.ExecutionObserver(){
      public String beforeSend(HttpSpeechModelPort.Stage stage,String hash){try{ControlledSpeechResourceIT.this.unknown(f,lease,execution,path,hash,"RESERVED");}catch(Exception failure){throw new IllegalStateException(failure);}return execution;}
      public void unknown(String id){j.update("UPDATE speech_execution SET state='UNKNOWN' WHERE id=? AND settled_at IS NULL",id);}public void notSent(String id){fail("The controlled request must actually reach the provider");}public void completed(String id,int status,String sha){fail("Client timeout must not claim provider completion");}
     };
     try(var port=new HttpSpeechModelPort(HttpSpeechModelPort.Config.at(ORIGIN,"inference.dev.zsfund.com",null,"harness-fixture-embedding-v1",256,Duration.ofMillis(200)),clip->new ByteArrayInputStream(f.wav),observer)){
      assertThrows(CompanySpeechAdapter.TimedOut.class,()->port.recognize(new WavClip(f.file,0,128000)));
     }
     var proof=call("/v1/harness/speech-executions/"+execution,null);assertEquals(200,proof.statusCode());assertEquals("STARTED",JSON.readTree(proof.body()).path("state").asText());
    }
    var restart=TranscriptImportRuntime.install(source,f.fences,f.outbox,f.components);var heartbeats=Executors.newSingleThreadScheduledExecutor();
    try{
     heartbeats.scheduleWithFixedDelay(()->f.queue.renew(lease),1,1,TimeUnit.SECONDS);
     long start=System.nanoTime();boolean blocked=false;
     try{var carrier=TranscriptImportRuntime.prepare(restart,lease,f.settings,NEVER);assertTrue(restart.completeWithEffects(lease,carrier));fail("Unexpected import completion: "+j.queryForObject("SELECT CONCAT(state,':',COALESCE(last_error,'NONE')) FROM durable_task WHERE id=?",String.class,f.task));}
     catch(IllegalStateException quota){blocked=true;}
     assertTrue(blocked);assertTrue(System.nanoTime()-start>=Duration.ofSeconds(9).toNanos());
    }finally{heartbeats.shutdownNow();assertTrue(heartbeats.awaitTermination(5,TimeUnit.SECONDS));}
    assertEquals(0,f.checkpoints());assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM speech_execution WHERE lease_task_id=? AND settled_at IS NULL",Integer.class,f.task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE producer_task_id=?",Integer.class,f.task));
    // Deleting content does not manufacture remote completion or free its resource debt.
    long revision=j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,f.meeting);f.meetings.delete(f.actor,f.meeting,id(),new ama.contract.MeetingContracts.DeleteMeeting(revision),"SPEECH_UNKNOWN_DELETE");
    assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM speech_execution WHERE lease_task_id=? AND settled_at IS NULL",Integer.class,f.task));
    try(var deletion=new ama.meeting.JdbcMeetingDeletion(source,f.tx,f.access,f.catalog)){
     String job=deletion.forMeeting(f.actor,f.meeting).deletionTaskId();for(int n=0;n<180&&!deletion.status(f.actor,job).state().equals("COMPLETE");n++)assertTrue(deletion.runOnce(job));assertEquals("COMPLETE",deletion.status(f.actor,job).state());
    }
    for(String execution:ids){waitState(execution,"COMPLETED",40);var proof=call("/v1/harness/speech-executions/"+execution,null);assertEquals("COMPLETED",JSON.readTree(proof.body()).path("state").asText());assertEquals(JSON.readTree(proof.body()).path("responseSha256").asText(),j.queryForObject("SELECT response_sha256 FROM speech_execution WHERE id=?",String.class,execution));}
    assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM speech_execution WHERE lease_task_id=? AND settled_at IS NULL",Integer.class,f.task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcription_input WHERE id=?",Integer.class,f.input));
   }
  }
 }
}
