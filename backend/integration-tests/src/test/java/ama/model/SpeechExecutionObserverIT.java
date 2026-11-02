package ama.model;

import ama.contract.SpeechContracts;
import ama.worker.TranscriptionHandler;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual fixed sibling endpoints, including provider execution evidence; no model-quality assertion. */
class SpeechExecutionObserverIT {
 private static final URI ORIGIN=URI.create("http://127.0.0.1:8792");
 private static final String ASR="/inference/asr-nano/modelserving/asr";
 private static final ObjectMapper JSON=new ObjectMapper();
 private static final HttpClient CLIENT=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).proxy(new ProxySelector(){
  public List<Proxy> select(URI uri){return List.of(Proxy.NO_PROXY);}public void connectFailed(URI uri,SocketAddress address,IOException failure){}
 }).build();
 static class Trace implements HttpSpeechModelPort.ExecutionObserver {
  final List<String> events=new ArrayList<>();final Map<String,String> requests=new LinkedHashMap<>(),responses=new HashMap<>();String fixed;
  public String beforeSend(HttpSpeechModelPort.Stage stage,String sha){String id=fixed==null?UUID.randomUUID().toString():fixed;events.add("RESERVE");requests.put(id,sha);return id;}
  public void completed(String id,int status,String sha){events.add("COMPLETED:"+status);responses.put(id,sha);}
  public void notSent(String id){events.add("NOT_SENT");}
  public void unknown(String id){events.add("UNKNOWN");}
 }
 private HttpSpeechModelPort.Config config(long timeout){assertEquals(ORIGIN.toString(),System.getenv("AMA_SPEECH_HARNESS_ORIGIN"));return HttpSpeechModelPort.Config.at(ORIGIN,"inference.dev.zsfund.com",null,"harness-fixture-embedding-v1",256,Duration.ofMillis(timeout));}
 private static JsonNode request(String method,String path,Object value)throws Exception{
  var builder=HttpRequest.newBuilder(ORIGIN.resolve(path)).timeout(Duration.ofSeconds(3));
  if(value==null)builder.GET();else builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(value)));
  var response=CLIENT.send(builder.build(),HttpResponse.BodyHandlers.ofString());assertEquals(method.equals("POST")?201:200,response.statusCode());return JSON.readTree(response.body());
 }
 private static JsonNode proof(String id)throws Exception{return request("GET","/v1/harness/speech-executions/"+id,null);}
 private static void scenario(String id,int status,int delay)throws Exception{
  scenario(id,status,delay,ASR,"asr");
 }
 private static void scenario(String id,int status,int delay,String path,String module)throws Exception{
  request("POST","/admin/api/scenarios",Map.ofEntries(Map.entry("module",module),Map.entry("name","ama-owned-execution-"+id),Map.entry("enabled",true),Map.entry("priority",1000),Map.entry("method","POST"),Map.entry("path",path),Map.entry("match",Map.of("header_equals",Map.of("x-ama-speech-execution",id))),Map.entry("status_code",status),Map.entry("delay_ms",delay),Map.entry("response_type","json"),Map.entry("response_body",status==200?Map.of("text","controlled completion"):Map.of("error","controlled limit")),Map.entry("response_headers",Map.of())));
 }
 @Test void llmTimeoutRetainsUnknownUntilHarnessProvidesCompletionEvidence()throws Exception{
  timeoutAfterObservedStart("/v1/chat/completions","llm");
 }
 private void timeoutAfterObservedStart(String path,String module)throws Exception{
  String id=UUID.randomUUID().toString();scenario(id,200,5000,path,module);var trace=new Trace();trace.fixed=id;
  try(var tasks=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();var port=new HttpSpeechModelPort(config(3000),c->new ByteArrayInputStream(SpeechPipelineIT.wav(32000)),trace)){
   var call=tasks.submit(()->{if(module.equals("llm"))port.correct("本次核对模型超时。");else port.recognize(new SpeechContracts.WavClip("original",0,32000));});
   long admissionDeadline=System.nanoTime()+Duration.ofSeconds(2).toNanos();boolean started=false;
   do{
    var response=CLIENT.send(HttpRequest.newBuilder(ORIGIN.resolve("/v1/harness/speech-executions/"+id)).timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.ofString());
    if(response.statusCode()==200){assertEquals("STARTED",JSON.readTree(response.body()).path("state").asText());started=true;break;}
    assertEquals(404,response.statusCode());Thread.sleep(25);
   }while(System.nanoTime()<admissionDeadline);
   assertTrue(started,"The delayed request must reach the harness before testing its timeout");
   var failed=assertThrows(java.util.concurrent.ExecutionException.class,()->call.get(4,java.util.concurrent.TimeUnit.SECONDS));assertInstanceOf(CompanySpeechAdapter.TimedOut.class,failed.getCause());
  }
  assertEquals(List.of("RESERVE","UNKNOWN"),trace.events);assertTrue(trace.responses.isEmpty());assertEquals("STARTED",proof(id).path("state").asText());
  long deadline=System.nanoTime()+Duration.ofSeconds(6).toNanos();JsonNode observed;
  do{observed=proof(id);if(observed.path("state").asText().equals("COMPLETED"))break;Thread.sleep(25);}while(System.nanoTime()<deadline);
  assertEquals("COMPLETED",observed.path("state").asText());assertEquals(path,observed.path("path").asText());assertEquals(trace.requests.get(id),observed.path("requestSha256").asText());
 }
 @Test void everyRealStageBindsTheExactRequestAndCompleteResponseDigests()throws Exception{
  var trace=new Trace();var clip=new SpeechContracts.WavClip("original-file",0,32000);
  try(var port=new HttpSpeechModelPort(config(3000),c->new ByteArrayInputStream(SpeechPipelineIT.wav(32000)),trace)){
   new TranscriptionHandler(port).transcribe(clip);
  }
  assertEquals(6,trace.requests.size());assertEquals(6,trace.responses.size());assertEquals(6,trace.events.stream().filter("COMPLETED:200"::equals).count());
  for(var entry:trace.requests.entrySet()){var actual=proof(entry.getKey());assertEquals("COMPLETED",actual.path("state").asText());assertEquals(entry.getValue(),actual.path("requestSha256").asText());assertEquals(trace.responses.get(entry.getKey()),actual.path("responseSha256").asText());}
 }
 @Test void timeoutKeepsUnknownUntilTheProviderActuallyFinishes()throws Exception{
  timeoutAfterObservedStart(ASR,"asr");
 }
 @Test void fullyRead429CanSettleWithoutPretendingTheStageSucceeded()throws Exception{
  String id=UUID.randomUUID().toString();scenario(id,429,0);var trace=new Trace();trace.fixed=id;
  try(var port=new HttpSpeechModelPort(config(3000),c->new ByteArrayInputStream(SpeechPipelineIT.wav(32000)),trace)){
   assertThrows(CompanySpeechAdapter.RateLimited.class,()->port.recognize(new SpeechContracts.WavClip("original",0,32000)));
  }
  assertEquals(List.of("RESERVE","COMPLETED:429"),trace.events);assertEquals(429,proof(id).path("status").asInt());
 }
 @Test void closingBeforeCallReportsNoSendAndNeverCreatesAProviderExecution()throws Exception{
  var reference=new AtomicReference<HttpSpeechModelPort>();var trace=new Trace(){@Override public String beforeSend(HttpSpeechModelPort.Stage stage,String sha){String id=super.beforeSend(stage,sha);reference.get().close();return id;}};
  try(var port=new HttpSpeechModelPort(config(3000),c->new ByteArrayInputStream(SpeechPipelineIT.wav(32000)),trace)){
   reference.set(port);assertThrows(RuntimeException.class,()->port.recognize(new SpeechContracts.WavClip("original",0,32000)));
  }
  assertEquals(List.of("RESERVE","NOT_SENT"),trace.events);
  var response=CLIENT.send(HttpRequest.newBuilder(ORIGIN.resolve("/v1/harness/speech-executions/"+trace.requests.keySet().iterator().next())).GET().build(),HttpResponse.BodyHandlers.discarding());assertEquals(404,response.statusCode());
 }
}
