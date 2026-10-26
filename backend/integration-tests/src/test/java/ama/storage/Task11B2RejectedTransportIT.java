package ama.storage;

import ama.transcript.*;import ama.api.Task11B2ReceiptApi;import java.nio.file.*;import java.nio.file.attribute.PosixFilePermissions;import java.net.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import org.springframework.jdbc.core.JdbcTemplate;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;

/** Read-only socket delivery faults over the retained genuine rejected execution. No app or worker. */
class Task11B2RejectedTransportIT {
 static final String MEETING="36db643e-5fbd-4f9a-9a85-a7e6b7d76e38",PUBLICATION="142a236c-bcd6-4608-8e93-2dd5446c5fa9",EXECUTION="0ef15c54-cc24-49f5-b5f8-6b01933118db";
 static final List<String> FULL=List.of("NORMAL","HEADER_STALL","BODY_STALL","ERROR_STALL","SLOW_PROGRESS","DECLARED_OVERSIZE","ABSENT_LENGTH","DUP_LENGTH","MALFORMED_LENGTH","DUP_HEADER","TRANSFER_ENCODING","COMPRESSION","REDIRECT","DUP_JSON","TRAILING_JSON","INVALID_UTF8","LONE_SURROGATE","NAME_BOUNDS","IDENTITY_MISMATCH","PROOF_MISMATCH","TRUNCATED");
 static final List<String> CONTROLS=List.of("CANCEL","CLOSE_BLOCKED","CLOSE_BEFORE","ADMISSION_CLOSE","INTERRUPT");
 Task11B2RuntimeFixture.Inputs inputs;Path state,mode;Process front;int port;final List<Map<String,Object>> cases=new ArrayList<>();final Map<String,Object> result=new LinkedHashMap<>();
 @Test void genuineRejectedReceiptTransportIsBoundedAndReadOnly()throws Exception {runMatrix(Task11B2RuntimeFixture.inspectRejectedTransportInputs());}
 void runMatrix(Task11B2RuntimeFixture.Inputs selected)throws Exception {
  inputs=selected;var w=inputs.root().resolve(".superpowers/sdd/2026-10-23-ama-phase-b-remaining");var origin=Task11B2RuntimeFixture.publicJson(w.resolve("task-11-b2-rejection-attained-facts-20261026.json"),"fd2ac24399e9443af4dd9fe7078c6c2b4ee0a8f7201428249bbf16551b0ef556");
  assertEquals(Task11B2RejectedTransportProbe.ARCHIVE_PIN,origin.at("/actual/archivePin").asText(),"Use genuine retained pin even though archive supplier must not run");
  var provider=origin.at("/retainedEndpoints/provider");var relay=origin.at("/retainedEndpoints/relay");ownedIdentity(provider,2655441,45709,"21557905");ownedIdentity(relay,2655444,45519,"21557924");
  assertEquals(MEETING,origin.get("meetingId").asText());assertEquals(PUBLICATION,origin.get("publicationId").asText());assertEquals(EXECUTION,origin.get("executionId").asText());
  var source=ama.publication.Task11Source.open();var j=new JdbcTemplate(source);j.setQueryTimeout(5);assertEquals("ama_t11b2_d9e2fdcc611e_behavior",j.queryForObject("SELECT DATABASE()",String.class));assertEquals("FAILED",j.queryForObject("SELECT state FROM publication WHERE id=?",String.class,PUBLICATION));assertEquals(5L,j.queryForObject("SELECT revision FROM publication WHERE id=?",Long.class,PUBLICATION));assertEquals(13L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,MEETING));
  var before=Task11B2ReceiptApi.business(j);var storeBefore=providerState(Path.of(provider.get("store").asText()));var relayBefore=read(Path.of(relay.get("state").asText()));assertEquals(3,relayBefore.get("postAuthorization").asInt());assertEquals(1,relayBefore.get("postPublication").asInt());assertEquals(hostStage()?1L:0L,relayBefore.get("get").asLong());if(hostStage())assertHostRelayCounters(relayBefore,1);
  result.put("businessBefore",before);result.put("providerBefore",storeBefore);result.put("retainedRelayBefore",relayBefore);result.put("nativeStarts",0);result.put("appHttpRequests",0);result.put("sessionsCreated",0);result.put("archiveCalls",0);result.put("qualification","Loopback HTTP service-level and test-only exchange controls; not appHTTP/TLS/connect-stall/recovery/apply");persist("RAW_ORIGIN_BEFORE_BASELINE_ASSERTIONS");if(inputs.configuration().has("transportPartialFacts"))assertContinuationPreimage(before,w);if(inputs.configuration().has("transportContinuationFacts"))assertLatestContinuationPreimage(before,w);assertProviderOrigin(storeBefore,origin.at("/actual/providerAfter"));
  if(hostStage()){
   Task11B2RuntimeFixture.verifyHostBusinessPreimages(inputs,before);
   var preflight=Task11B2RuntimeFixture.hostBusinessPreflight(inputs.root(),w,inputs.configuration());
   assertEquals(preflight.get("businessPreimage"),Task11B2RuntimeFixture.JSON.valueToTree(before),"Matrix17-table image must equal successful released preflight before GET");
   result.put("hostBusinessPreflightMatches",true);result.put("allThreeConsumedPreimagesMatch",true);result.put("allThreeConsumedFrontsAbsent",true);
   result.put("retainedRelayGetBaseline",1L);result.put("retainedRelayGetExpectedAfter",25L);result.put("incrementalGetBudget",24L);result.put("hostRetainedPreflightSha256",Task11B2RuntimeFixture.HOST_RETAINED_SHA);
   assertHostRelayCounters(read(Path.of(relay.get("state").asText())),1);
  }
  persist("ORIGIN_VERIFIED_BEFORE_FRONT");
  Throwable primary=null;boolean[] outerInterrupted={Thread.interrupted()};
  try{
   if(outerInterrupted[0])throw new InterruptedException("Harness interrupted before front startup");
   state=inputs.evidence().resolve("transport-front-state.json");mode=inputs.evidence().resolve("transport-front-mode.json");write(mode,Map.of("mode","NORMAL"));Path cfg=inputs.evidence().resolve("transport-front-config.json");write(cfg,Map.of("state",state.toString(),"mode",mode.toString(),"path","/v1/knowledge/publications/"+PUBLICATION+"/executions/"+EXECUTION,"upstreamPort",45519,"maxRequests",24));
   Path log=inputs.evidence().resolve("transport-front.log");assertFalse(Files.exists(log));front=new ProcessBuilder("/usr/bin/python3",inputs.root().resolve("tools/task11_rejected_transport.py").toString(),cfg.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();result.put("frontPid",front.pid());result.put("frontStartInstant",front.info().startInstant().map(Object::toString).orElse("unavailable"));result.put("frontStartTicks",startTicks(front.pid()));result.put("frontConfig",cfg.toString());persist("FRONT_CHILD_OWNED_BEFORE_READY");
   waitUntil(()->Files.isRegularFile(state),5000);port=read(state).get("port").asInt();result.put("frontPort",port);result.put("sinkPort",read(state).get("sinkPort").asInt());assertTrue(front.isAlive());
   Path identities=Path.of(provider.get("applicationIdentities").asText());Task11B2RuntimeFixture.privateDirectory(inputs.owned(),identities.getParent());assertFalse(Files.isSymbolicLink(identities));assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(identities));
   for(String name:FULL)runCase(source,identities,name,false);
   for(String name:CONTROLS)runCase(source,identities,name,true);
   originNegatives(identities);
   var after=Task11B2ReceiptApi.business(j);var storeAfter=providerState(Path.of(provider.get("store").asText()));var relayAfter=read(Path.of(relay.get("state").asText()));result.put("businessAfter",after);result.put("providerAfter",storeAfter);result.put("retainedRelayAfter",relayAfter);result.put("frontFinalBeforeClose",read(state));persist("RAW_FINAL_FACTS_BEFORE_ASSERTIONS");
   assertEquals(before,after);assertProviderOrigin(storeAfter,origin.at("/actual/providerAfter"));assertEquals(storeBefore,storeAfter);assertEquals(3,relayAfter.get("postAuthorization").asInt());assertEquals(1,relayAfter.get("postPublication").asInt());assertEquals(hostStage()?25L:24L,relayAfter.get("get").asLong());if(hostStage()){assertHostRelayCounters(relayAfter,25);assertEquals(24L,Math.subtractExact(relayAfter.get("get").longValue(),1L));result.put("retainedRelayGetDelta",24L);Task11B2RuntimeFixture.verifyHostBusinessPreimages(inputs,after);}assertEquals(24,read(state).get("requests").asInt());assertEquals(24,read(state).get("forwards").asInt());assertEquals(0,read(state).get("posts").asInt());assertEquals(0,read(state).get("sinkRequests").asInt());ownedIdentity(provider,2655441,45709,"21557905");ownedIdentity(relay,2655444,45519,"21557924");persist("COMPLETE_READONLY_TRANSPORT");
  }catch(Throwable failure){primary=failure;remember(failure,outerInterrupted);result.put("safeFailureType",failure.getClass().getName());try{persist("FAILED_RETAIN_ORIGINAL_ORIGIN");}catch(Throwable capture){remember(capture,outerInterrupted);failure.addSuppressed(capture);}throw failure;}
  finally{
   boolean[] interrupted=outerInterrupted;remember(primary,interrupted);Throwable cleanup=null;boolean stopped=front==null;
   try{
    try{if(front!=null){Task11ResponseLossFixture.stopOwned(front,interrupted);stopped=!front.isAlive();}}catch(Throwable close){remember(close,interrupted);cleanup=merge(cleanup,close);}
    try{assertTrue(stopped,"Owned front stopped");}catch(Throwable assertion){cleanup=merge(cleanup,assertion);}
    try{
     result.put("frontStopped",stopped);result.put("frontExitCode",front!=null&&!front.isAlive()?front.exitValue():-999);result.put("originalProviderAlive",ProcessHandle.of(2655441).map(ProcessHandle::isAlive).orElse(false));result.put("originalRelayAlive",ProcessHandle.of(2655444).map(ProcessHandle::isAlive).orElse(false));
     if(state!=null&&Files.isRegularFile(state)){var finalState=read(state);result.put("frontAfterClose",finalState);assertTrue(finalState.get("sinkJoined").asBoolean(),"Owned sink joined");}
    }catch(Throwable capture){remember(capture,interrupted);cleanup=merge(cleanup,capture);}
    if(interrupted[0]&&primary==null)cleanup=merge(cleanup,new InterruptedException("Harness interrupted during outer cleanup"));
    if(cleanup!=null)result.put("cleanupFailureType",cleanup.getClass().getName());
    try{persist("CLEANUP_RETAIN_ORIGINAL_ENDPOINTS");}catch(Throwable capture){remember(capture,interrupted);cleanup=merge(cleanup,capture);}
    if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else failWith(cleanup);}
   }finally{if(interrupted[0])Thread.currentThread().interrupt();}
  }
 }

 boolean hostStage(){return inputs.configuration().has("hostRetainedPreflight");}
 void assertHostRelayCounters(com.fasterxml.jackson.databind.JsonNode actual,long expectedGet){
  var baseline=inputs.configuration().get("retainedRelayBaseline");
  assertTrue(actual.path("get").isIntegralNumber()&&actual.get("get").canConvertToLong());assertEquals(expectedGet,actual.get("get").longValue());
  for(String key:List.of("postAuthorization","postPublication","failures","blockedPublicationAttempts","withheld"))assertEquals(baseline.get(key),actual.get(key),"Retained relay counters must stay at observed baseline");
 }
 void assertContinuationPreimage(Map<String,String> current,Path w)throws Exception {
  var prior=Task11B2RuntimeFixture.publicJson(w.resolve(inputs.configuration().get("transportPartialFacts").asText()),"43e580194ac602fbabcb2f5b457ecd3b9c96b6fa0078a17806000834035f9cd5");
  assertEquals("b2-rejected-transport-01",prior.get("window").asText());assertEquals(1,prior.get("exitCode").asInt());assertEquals(1,prior.at("/totals/errors").asInt());assertEquals(0,prior.at("/totals/failures").asInt());assertEquals("INVALID_ID",prior.at("/error/message").asText());assertFalse(prior.get("receiptInvocationReached").asBoolean());
  assertEquals(2659369L,prior.at("/frontCleanup/frontPid").asLong());assertEquals("21768352",prior.at("/frontCleanup/frontStartTicks").asText());assertTrue(prior.at("/frontCleanup/frontStopped").asBoolean());assertEquals(0,prior.at("/frontCleanup/frontExitCode").asInt());assertTrue(prior.at("/front/sinkJoined").asBoolean());assertEquals(0,prior.at("/front/requests").asInt());assertEquals(0,prior.at("/front/forwards").asInt());assertEquals(0,prior.at("/front/posts").asInt());assertFalse(ProcessHandle.of(2659369L).map(ProcessHandle::isAlive).orElse(false),"Consumed original front must be absent; never kill/reuse a PID");
  assertEquals(prior.get("businessPreimage"),Task11B2RuntimeFixture.JSON.valueToTree(current),"Current17-table image must equal retained partial preimage before GET");result.put("partialBusinessPreimageMatches",true);result.put("priorFailedFrontAbsent",true);result.put("transportPartialFactsSha256","43e580194ac602fbabcb2f5b457ecd3b9c96b6fa0078a17806000834035f9cd5");persist("CONTINUATION_PARTIAL_PREIMAGE_VERIFIED_BEFORE_FRONT");
 }
 void assertLatestContinuationPreimage(Map<String,String> current,Path w)throws Exception {
  var prior=Task11B2RuntimeFixture.publicJson(w.resolve(inputs.configuration().get("transportContinuationFacts").asText()),"a70911410827f27aa748fec8ab63ae7bf501ea1298edc6666d86793dc828271f");
  assertEquals("b2-rejected-transport-continuation-01",prior.get("window").asText());assertEquals(1,prior.get("exitCode").asInt());assertEquals(1,prior.at("/totals/failures").asInt());assertEquals(0,prior.at("/totals/errors").asInt());assertEquals("org.opentest4j.AssertionFailedError",prior.at("/failure/type").asText());assertFalse(prior.get("receiptWireRequestReached").asBoolean());
  assertEquals(2660337L,prior.at("/frontCleanup/frontPid").asLong());assertEquals("21862512",prior.at("/frontCleanup/frontStartTicks").asText());assertTrue(prior.at("/frontCleanup/frontStopped").asBoolean());assertEquals(0,prior.at("/frontCleanup/frontExitCode").asInt());assertTrue(prior.at("/front/sinkJoined").asBoolean());for(String field:List.of("requests","forwards","posts"))assertEquals(0,prior.at("/front/"+field).asInt());assertEquals(0,prior.at("/actualBudgetObservations/clientGetAttempts").asInt());assertFalse(ProcessHandle.of(2660337L).map(ProcessHandle::isAlive).orElse(false),"Latest consumed front must be absent; never kill a PID");
  assertEquals(prior.get("businessPreimage"),Task11B2RuntimeFixture.JSON.valueToTree(current),"Current17-table image must equal latest continuation preimage as well as original");result.put("latestPartialBusinessPreimageMatches",true);result.put("latestFailedFrontAbsent",true);result.put("transportContinuationFactsSha256","a70911410827f27aa748fec8ab63ae7bf501ea1298edc6666d86793dc828271f");persist("BOTH_CONSUMED_PREIMAGES_VERIFIED_BEFORE_FRONT");
 }
 void runCase(javax.sql.DataSource source,Path identities,String name,boolean control)throws Exception {
  int previous=read(state).get("cases").size();write(mode,Map.of("mode",name));var row=new LinkedHashMap<String,Object>();row.put("case",name);if(List.of("INVALID_UTF8","LONE_SURROGATE","NAME_BOUNDS").contains(name))row.put("qualification","Malformed/incomplete delivery rejected; does not isolate Unicode/name parser boundary from missing or mismatched receipt identities");row.put("clientReceivedBytes","UNOBSERVABLE_THROUGH_PRODUCTION_API");row.put("deliveryByteQualification","Successfully sent front socket bytes; not inferred client consumption");row.put("driver",control?"terminal+service-authorized exchange probe":"JdbcPublicationService.readRejectedReceipt");
  long start=System.nanoTime();Throwable invocation=null,harness=null;Object value=null;Task11B2RejectedTransportProbe probe=null;ExecutorService executor=null;Future<Map<String,Object>> future=null;boolean[] interrupted={Thread.interrupted()};
  try{
   if(interrupted[0])throw new InterruptedException("Harness interrupted before case");
   probe=new Task11B2RejectedTransportProbe(source,identities,port,inputs.allocation().at("/actors/owner").asText(),MEETING,PUBLICATION);
   if(control)value=probe.controlled(name,()->{try{waitUntil(()->{var a=read(state).get("cases");return a.size()>previous&&a.get(previous).path("bytesSent").asLong()>0;},5000);}catch(Exception failure){throw new RuntimeException(failure);}});
   else{
    executor=Executors.newSingleThreadExecutor();var selected=probe;future=executor.submit(selected::fullService);
    try{value=future.get(22,TimeUnit.SECONDS);}catch(ExecutionException failure){invocation=failure.getCause();remember(invocation,interrupted);}
   }
  }catch(Throwable failure){remember(failure,interrupted);harness=merge(harness,failure);}
  finally{
   try{
    if(probe!=null)try{probe.close();if(Thread.interrupted()){interrupted[0]=true;throw new InterruptedException("Harness interrupted during client close");}}catch(Throwable close){remember(close,interrupted);harness=merge(harness,close);}
    if(future!=null)try{future.cancel(true);}catch(Throwable cancel){harness=merge(harness,cancel);}
    if(executor!=null){try{executor.shutdownNow();}catch(Throwable shutdown){harness=merge(harness,shutdown);}try{awaitExecutor(executor,interrupted);}catch(Throwable join){remember(join,interrupted);harness=merge(harness,join);}}
    if(interrupted[0]&&harness==null)harness=new InterruptedException("Harness interrupted during invocation/cleanup");
    try{
     row.put("elapsedMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));row.put("traffic",probe==null?Map.of():probe.traffic());row.put("archiveCalls",probe==null?-1:probe.archiveCalls());row.put("invocationFailureType",invocation==null?"NONE":invocation.getClass().getName());row.put("invocationFailureCode",invocation instanceof ama.contract.MinutesContracts.Failure failure?failure.code().name():"NONE");row.put("harnessFailureType",harness==null?"NONE":harness.getClass().getName());row.put("harnessFailureCode",harness instanceof ama.contract.MinutesContracts.Failure failure?failure.code().name():"NONE");row.put("callerInterrupted",interrupted[0]);if(value!=null)row.put("result",value);cases.add(row);persist("RAW_CASE_"+name+"_BEFORE_ASSERTIONS");
    }catch(Throwable capture){remember(capture,interrupted);harness=merge(harness,capture);}
   }finally{if(interrupted[0])Thread.currentThread().interrupt();}
  }
  if(harness!=null){if(invocation!=null)harness.addSuppressed(invocation);failWith(harness);}
  boolean noAdmission=name.equals("CLOSE_BEFORE")||name.equals("ADMISSION_CLOSE");assertNotNull(probe);Throwable counters=null;
  try{assertEquals(Map.of("getWriteAttempts",noAdmission?0L:1L,"postWriteAttempts",0L),probe.traffic());}catch(Throwable failure){counters=merge(counters,failure);}
  try{assertEquals(0,probe.archiveCalls());}catch(Throwable failure){counters=merge(counters,failure);}
  if(!control&&name.equals("NORMAL")&&invocation!=null){var normal=new AssertionError("NORMAL actual service invocation failed",invocation);if(counters!=null)normal.addSuppressed(counters);throw normal;}
  if(counters!=null)failWith(counters);
  if(invocation!=null&&!expectedInvocation(invocation))throw new AssertionError("Unexpected provider invocation failure",invocation);
  if(control){assertNotNull(value);}else if(name.equals("NORMAL")){assertEquals("FAILED",((Map<?,?>)value).get("state"));}else assertNotNull(invocation,"Actual expected provider invocation must fail closed: "+name);
  if(noAdmission)assertEquals(previous,read(state).get("cases").size());else{waitUntil(()->{var a=read(state).get("cases");return a.size()==previous+1&&a.get(previous).path("finished").asBoolean();},8000);var delivery=read(state).get("cases").get(previous);row.put("delivery",delivery);assertTrue(delivery.get("forwarded").asBoolean());assertTrue(delivery.get("upstream200").asBoolean());if(name.equals("DECLARED_OVERSIZE"))assertEquals(0,delivery.get("bodyBytesSent").asInt());}
  long elapsed=((Number)row.get("elapsedMillis")).longValue();assertTrue(elapsed<22000);if(name.equals("SLOW_PROGRESS"))assertTrue(elapsed>=12000&&elapsed<19000,"Whole deadline, despite per-second progress");if(List.of("HEADER_STALL","BODY_STALL","ERROR_STALL").contains(name))assertTrue(elapsed>=3500&&elapsed<8500,"Socket inactivity bounded");persist("CASE_"+name+"_VERIFIED");
 }
 static boolean expectedInvocation(Throwable failure){return failure instanceof java.io.IOException||failure instanceof ama.contract.MinutesContracts.Failure||failure instanceof IllegalArgumentException;}
 static Throwable merge(Throwable primary,Throwable next){if(primary==null)return next;if(primary!=next)primary.addSuppressed(next);return primary;}
 static void remember(Throwable failure,boolean[] interrupted){if(Thread.interrupted())interrupted[0]=true;for(var cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof InterruptedException)interrupted[0]=true;}
 static void failWith(Throwable failure)throws Exception {if(failure instanceof Exception exception)throw exception;if(failure instanceof Error error)throw error;throw new AssertionError(failure);}
 static void awaitExecutor(ExecutorService executor,boolean[] interrupted)throws Exception {
  long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);Throwable failure=null;boolean joined=executor.isTerminated();
  while(!joined&&System.nanoTime()<end)try{joined=executor.awaitTermination(Math.max(1,end-System.nanoTime()),TimeUnit.NANOSECONDS);}catch(InterruptedException stop){interrupted[0]=true;failure=merge(failure,stop);}
  if(!joined)failure=merge(failure,new AssertionError("Owned service executor failed bounded join"));if(failure!=null)failWith(failure);
 }
 void assertProviderOrigin(Map<String,Object> actual,com.fasterxml.jackson.databind.JsonNode sealed)throws Exception {
  var snapshot=Task11B2RuntimeFixture.JSON.readTree(Task11B2RuntimeFixture.JSON.writeValueAsBytes(actual));assertEquals(sealed,snapshot,"Current provider observation must equal pinned actual rejection providerAfter");
  assertEquals(0,snapshot.get("documents").asInt());assertEquals(1,snapshot.get("publications").asInt());assertEquals(1,snapshot.get("executionCount").asInt());assertEquals(1,snapshot.get("grants").size());var grant=snapshot.get("grants").get(0);assertEquals(inputs.allocation().at("/actors/owner").asText(),grant.get(0).asText());assertEquals("controlled-library",grant.get(1).asText());assertEquals("controlled-root",grant.get(2).asText());assertEquals(1,grant.get(3).asInt());assertEquals(0,grant.get(4).asInt());assertEquals(2,grant.get(5).asInt());
  assertEquals(1,snapshot.get("slots").size());var slot=snapshot.get("slots").get(0);assertEquals(PUBLICATION,slot.get("publication").asText());assertEquals(1,slot.get("generation").asInt());assertTrue(slot.get("document").isNull());
  assertEquals(1,snapshot.get("executions").size());var execution=snapshot.get("executions").get(0);assertEquals(EXECUTION,execution.get("id").asText());assertEquals(PUBLICATION,execution.get("publication").asText());assertEquals(1,execution.get("number").asInt());assertEquals(slot.get("key"),execution.get("key"));assertEquals(slot.get("logical_hash"),execution.get("logical_hash"));var receipt=execution.get("receipt");assertEquals("FAILED",receipt.get("state").asText());assertEquals("DEFINITIVE_REJECTION",receipt.get("noEffectBasis").asText());assertEquals(EXECUTION,receipt.get("executionId").asText());assertEquals(execution.get("key"),receipt.get("businessKey"));assertEquals(execution.get("request_hash"),receipt.get("executionRequestHash"));assertEquals(execution.get("payload_hash"),receipt.get("payloadSha256"));
 }
 void originNegatives(Path identities)throws Exception {
  var rejected=new ArrayList<String>();for(String uri:List.of("http://localhost:45519","http://127.0.0.1:0","http://127.0.0.1:45519/not-root","http://127.0.0.1:45519?q=1","http://127.0.0.1:45519#fragment","http://user@127.0.0.1:45519","https://127.0.0.1:45519")){assertThrows(IllegalArgumentException.class,()->{try(var client=new ControlledKnowledgeClient(identities,URI.create(uri),"ISOLATED_LOOPBACK_TEST")){} });rejected.add(uri);}
  result.put("originRejectionsNoSocket",rejected);result.put("originQualification","Only explicit isolated HTTP constructor boundaries; HTTPS unsupported, not a TLS certificate test");
 }
 interface Condition {boolean get()throws Exception;}
 static void waitUntil(Condition condition,long millis)throws Exception {long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(millis);while(!condition.get()){if(System.nanoTime()>=end)throw new AssertionError("Bounded condition timeout");Thread.sleep(20);}}
 static String startTicks(long pid)throws Exception {var stat=Files.readString(Path.of("/proc/"+pid+"/stat"));return stat.substring(stat.lastIndexOf(')')+2).split(" ")[19];}
 void ownedIdentity(com.fasterxml.jackson.databind.JsonNode identity,long pid,int expectedPort,String ticks)throws Exception {
  assertEquals(pid,identity.get("pid").asLong());assertEquals(expectedPort,identity.get("port").asInt());assertTrue(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));assertEquals(ticks,startTicks(pid));String cmd=Files.readString(Path.of("/proc/"+pid+"/cmdline")).replace('\0',' ');assertTrue(cmd.contains(identity.get("config").asText()));Path cfg=Path.of(identity.get("config").asText());assertTrue(cfg.startsWith(inputs.owned()));assertFalse(Files.isSymbolicLink(cfg));assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(cfg));var config=read(cfg);assertEquals(expectedPort,config.get("port").asInt());
 }
 com.fasterxml.jackson.databind.JsonNode read(Path path)throws Exception {assertFalse(Files.isSymbolicLink(path));assertTrue(Files.size(path)<=262144);return Task11B2RuntimeFixture.JSON.readTree(Files.readAllBytes(path));}
 void write(Path path,Object value)throws Exception {assertFalse(Files.isSymbolicLink(path));Files.writeString(path,Task11B2RuntimeFixture.JSON.writeValueAsString(value));}
 void persist(String phase)throws Exception {result.put("phase",phase);result.put("cases",cases);write(inputs.evidence().resolve("rejected-transport-result.json"),result);}
 Map<String,Object> providerState(Path store)throws Exception {
  String script="import os,resource; os.sched_setaffinity(0,sorted(os.sched_getaffinity(0))[:2]); resource.setrlimit(resource.RLIMIT_AS,(67108864,67108864)); import sqlite3,json,sys; c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True); c.row_factory=sqlite3.Row; executions=[dict(r) for r in c.execute('SELECT id,publication,key,number,client,actor,logical_hash,request_hash,payload_hash,receipt FROM execution ORDER BY id LIMIT 2')]; [(r.update(receipt=json.loads(r['receipt']))) for r in executions]; print(json.dumps({'documents':c.execute('SELECT COUNT(*) FROM document').fetchone()[0], 'publications':c.execute('SELECT COUNT(*) FROM publication_slot').fetchone()[0], 'executionCount':c.execute('SELECT COUNT(*) FROM execution').fetchone()[0], 'grants':[list(r) for r in c.execute('SELECT actor,library,folder,can_read,can_publish,version FROM grant_record ORDER BY actor,library,folder')], 'slots':[dict(r) for r in c.execute('SELECT * FROM publication_slot ORDER BY publication LIMIT 2')], 'executions':executions}))";
  var child=new ProcessBuilder("/usr/bin/python3","-c",script,store.toString()).redirectErrorStream(true).start();Throwable primary=null;boolean[] interrupted={Thread.interrupted()};
  try{
   if(interrupted[0])throw new InterruptedException("Harness interrupted before store observation wait");
   assertTrue(child.waitFor(5,TimeUnit.SECONDS),"Bounded store SELECT timeout");byte[] bytes=child.getInputStream().readNBytes(262145);assertEquals(0,child.exitValue(),"New-store SELECT failed");assertTrue(bytes.length<=262144);return Task11B2RuntimeFixture.JSON.readValue(bytes,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
  }catch(Throwable failure){primary=failure;remember(failure,interrupted);throw failure;}
  finally{
   Throwable cleanup=null;
   try{
    if(child.isAlive())try{Task11ResponseLossFixture.stopOwned(child,interrupted);}catch(Throwable close){remember(close,interrupted);cleanup=merge(cleanup,close);}
    try{assertFalse(child.isAlive(),"Exact SQLite child stopped");}catch(Throwable assertion){cleanup=merge(cleanup,assertion);}
    if(interrupted[0]&&primary==null)cleanup=merge(cleanup,new InterruptedException("Harness interrupted during store child cleanup"));
    if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else failWith(cleanup);}
   }finally{if(interrupted[0])Thread.currentThread().interrupt();}
  }

 }
}
