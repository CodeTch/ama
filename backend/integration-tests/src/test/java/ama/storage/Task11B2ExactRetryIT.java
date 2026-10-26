package ama.storage;

import ama.api.Task11B2ReceiptApi;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;import java.net.http.*;import java.nio.file.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;import org.springframework.jdbc.core.JdbcTemplate;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;

/** One genuine unchanged acceptance, held unclaimed. The inert ACK alone cannot run this test. */
class Task11B2ExactRetryIT {
 static final String MEETING="36db643e-5fbd-4f9a-9a85-a7e6b7d76e38",PUBLICATION="142a236c-bcd6-4608-8e93-2dd5446c5fa9",OPERATION="896456c1-cc73-4a8c-803d-8b5bb058d4c7",OLD_EXECUTION="0ef15c54-cc24-49f5-b5f8-6b01933118db",OLD_TASK="f0f17320-130e-46b9-908c-c143a02e10f8";
 static final String PATH="/meetings/"+MEETING+"/publications/"+PUBLICATION+"/zero-effect-retries",KEY="task11-b2-exact-retry-winner-01";
 static final String NUMERIC="{\"expectedRevision\":13,\"expectedPublicationRevision\":5,\"expectedExecutionNo\":1}",TOKENS="{\"expectedRevisionToken\":\"13\",\"expectedPublicationRevisionToken\":\"5\",\"expectedExecutionNoToken\":\"1\"}",DUAL="{\"expectedRevision\":13,\"expectedRevisionToken\":\"13\",\"expectedPublicationRevision\":5,\"expectedPublicationRevisionToken\":\"5\",\"expectedExecutionNo\":1,\"expectedExecutionNoToken\":\"1\"}";
 final ObjectMapper json=Task11B2RuntimeFixture.JSON;
 final Map<String,Object> result=new LinkedHashMap<>();final List<Map<String,Object>> events=new ArrayList<>(),requests=new ArrayList<>();final AtomicInteger requestCount=new AtomicInteger();
 Task11B2RuntimeFixture.Inputs inputs;Task11B2SameGraphFixture g;Task11B2ExactRetryFault fault;Task11B2RejectedTransportIT retained=new Task11B2RejectedTransportIT();
 JsonNode origin,host,provider,relay,providerBefore;Map<String,String> before;Map<String,List<Map<String,Object>>> originalRows;JdbcTemplate j;int sqliteCount,grantCount;boolean complete;String newTask,newExecution;
 @Test void genuineExactUnchangedRetryThenIndependentReplay()throws Exception {
  inputs=Task11B2RuntimeFixture.inspectExactRetryInputs();retained.inputs=inputs;Path w=inputs.root().resolve(".superpowers/sdd/2026-10-23-ama-phase-b-remaining");
  origin=pin(w,"rejectionAttainedFacts");host=pin(w,"hostRuntimeFacts");provider=origin.at("/retainedEndpoints/provider");relay=origin.at("/retainedEndpoints/relay");
  result.put("label",Task11B2RuntimeFixture.EXACT_RETRY_LABEL);result.put("configurationAckSha256",Task11B2RuntimeFixture.EXACT_RETRY_ACK_SHA);result.put("hostRuntimeFactsSha256",inputs.configuration().get("hostRuntimeFactsSha256").asText());result.put("hostRuntimeAdoptionSha256",inputs.configuration().get("hostRuntimeAdoptionSha256").asText());result.put("qualification","One held successor; no claim/send. Serialized commit admission after overlapping preparation; precommit rollback is not lost commit ACK.");
  var source=ama.publication.Task11Source.open();j=new JdbcTemplate(source);j.setQueryTimeout(5);assertEquals("ama_t11b2_d9e2fdcc611e_behavior",j.queryForObject("SELECT DATABASE()",String.class));assertEquals("t11b2_d9e2fdcc611e@%",j.queryForObject("SELECT CURRENT_USER()",String.class));
  Throwable primary=null;
  try{
   ownedRetained();before=Task11B2ReceiptApi.business(j);Task11B2RuntimeFixture.verifyHostBusinessPreimages(inputs,before);assertEquals(host.at("/business/after"),json.valueToTree(before));assertFalse(ProcessHandle.of(4141477).map(ProcessHandle::isAlive).orElse(false));assertFalse(ProcessHandle.of(2655411).map(ProcessHandle::isAlive).orElse(false));
   providerBefore=providerState("BEFORE_GRANT");assertEquals(origin.at("/actual/providerAfter"),providerBefore);counters(25,3);originalRows=rows();record("RETAINED_BASELINE_VERIFIED",Map.of("businessBefore",before,"providerBefore",providerBefore,"originalRows",originalRows));
   fault=new Task11B2ExactRetryFault(MEETING,PUBLICATION,OPERATION,this::record);DataSource guarded=fault.wrap(source);g=new Task11B2SameGraphFixture(inputs,guarded);
   g.retainedApplicationIdentities=Path.of(provider.get("applicationIdentities").asText());g.privateFile(g.retainedApplicationIdentities,65536);g.knowledgeOriginPort=45519;g.retainProvider=true;
   assertEquals(g.f.owner,inputs.configuration().at("/grantRestorationIntent/actor").asText());for(String role:List.of("owner","member","outsider","admin"))g.session(g.actor(role));g.reopenNative();g.initialPublicationApp();preNetworkCases();assertEquals(22,requestCount.get());assertEquals(before,g.business());counters(25,3);
   restoreGrant();int cut=0;
   for(var point:Task11B2ExactRetryFault.Point.values()){
    String key="task11-b2-exact-retry-rollback-"+point.name().toLowerCase(Locale.ROOT);record("BEFORE_ROLLBACK_CASE",Map.of("point",point.name(),"case",cut+1));fault.arm(point,key);boolean verified=false;
    try{call("rollback-"+point,"owner",PATH,NUMERIC,List.of(key),"NORMAL",503);assertTrue(fault.hit,"Real write/commit cut must be reached");assertTrue(fault.rollbackObserved,"Actual physical rollback must return");assertEquals(before,g.business());record("ROLLBACK_CASE_VERIFIED",Map.of("point",point.name(),"fault",fault.observation(),"businessAfter",g.business()));verified=true;}finally{if(verified)fault.disarm();else fault.abort();}
    cut++;counters(25+cut,3+cut);
   }
   concurrentAcceptance();assertEquals(36,requestCount.get());counters(39,17);verifyAccepted();var acceptedImage=g.business();record("HELD_SUCCESSOR_VERIFIED",Map.of("businessAfterAcceptance",acceptedImage,"newTaskId",newTask,"newExecutionId",newExecution));
   g.f.app.close();g.f.app=null;g.stopNative();record("FULL_RUNTIME_CLOSED_BEFORE_REPLAY",Map.of("nativeStopped",true));
   g.f.app=Task11B2ReceiptApi.start(source);Task11B2ReceiptApi.requireIndependent(g.f.app);
   for(var entry:List.of(Map.entry("numeric",NUMERIC),Map.entry("tokens",TOKENS),Map.entry("equal-dual",DUAL))){var response=call("independent-replay-"+entry.getKey(),"owner",PATH,entry.getValue(),List.of(KEY),"NORMAL",202);assertAssociation(response,true);}
   call("changed-body-same-key","owner",PATH,NUMERIC.replace(":13",":12"),List.of(KEY),"NORMAL",409);
   for(String role:List.of("member","outsider","admin"))call("independent-replay-denied-"+role,role,PATH,NUMERIC,List.of(KEY),"NORMAL",403);
   assertEquals(43,requestCount.get());assertEquals(acceptedImage,g.business());verifyAccepted();counters(39,17);var providerAfter=providerState("FINAL");assertProviderRestored(providerAfter);ownedRetained();
   assertEquals(3,sqliteCount);assertEquals(1,grantCount);record("COMPLETE_HELD_SUCCESSOR_BEFORE_ANY_CLAIM",Map.of("providerAfter",providerAfter,"businessAfter",g.business(),"rowsAfter",rows(),"receiptIndependentRuntimeAbsent",true));complete=true;
  }catch(Throwable failure){primary=failure;result.put("safeFailureType",failure.getClass().getName());try{record("FAILED_NO_RETRY_OR_COMPENSATION",Map.of("successorMayExist",fault!=null&&fault.commits.get()>0,"grantAttempts",grantCount));}catch(Throwable capture){failure.addSuppressed(capture);}throw failure;}
  finally{
   Throwable cleanup=null;if(fault!=null)fault.abort();
   try{if(g!=null)g.close();}catch(Throwable failure){cleanup=failure;}
   try{result.put("status",complete&&cleanup==null?"PASS":"PARTIAL_UNQUALIFIED");result.put("servletRequestsAttempted",requestCount.get());result.put("sqliteObservationsAttempted",sqliteCount);result.put("grantAttempts",grantCount);result.put("retainedProviderAlive",ProcessHandle.of(2655441).map(ProcessHandle::isAlive).orElse(false));result.put("retainedRelayAlive",ProcessHandle.of(2655444).map(ProcessHandle::isAlive).orElse(false));if(cleanup!=null)result.put("cleanupFailureType",cleanup.getClass().getName());record("FINAL_CUSTODY",Map.of("noClaimOrSend",true,"noSelectorRerun",true));}catch(Throwable failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
   if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Exception e)throw e;else throw (Error)cleanup;}
  }
 }
 JsonNode pin(Path w,String name)throws Exception{return Task11B2RuntimeFixture.publicJson(w.resolve(inputs.configuration().get(name).asText()),inputs.configuration().get(name+"Sha256").asText());}
 synchronized void record(String phase,Map<String,Object> facts)throws Exception {events.add(Map.of("sequence",events.size()+1,"utc",Instant.now().toString(),"phase",phase,"facts",Task11B2SameGraphFixture.normalize(facts)));result.put("phase",phase);result.put("events",events);result.put("requests",requests);Files.writeString(inputs.evidence().resolve("exact-retry-result.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(result));}
 void ownedRetained()throws Exception {retained.ownedIdentity(provider,2655441,45709,"21557905");retained.ownedIdentity(relay,2655444,45519,"21557924");}
 void counters(long gets,long destinations)throws Exception {var actual=retained.read(Path.of(relay.get("state").asText()));assertEquals(gets,actual.path("get").asLong());assertEquals(destinations,actual.path("postAuthorization").asLong());assertEquals(1,actual.path("postPublication").asInt());assertEquals(0,actual.path("failures").asInt());assertEquals(0,actual.path("blockedPublicationAttempts").asInt());assertFalse(actual.path("withheld").asBoolean());record("RELAY_COUNTERS_VERIFIED",Map.of("relay",actual));}
 JsonNode call(String name,String role,String path,String body,List<String> keys,String security,int status)throws Exception {
  int number=requestCount.incrementAndGet();assertTrue(number<=43,"No extra servlet request allowed");record("BEFORE_SERVLET_REQUEST",Map.of("number",number,"case",name,"role",role,"path",path,"body",body,"keyCount",keys.size()));
  var request=HttpRequest.newBuilder(URI.create(g.f.apiUrl()+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
  if(!security.equals("NO_SESSION"))request.header("Cookie","__Host-AMA_SESSION="+g.sessions.get(g.actor(role)));
  request.header("Origin",security.equals("BAD_ORIGIN")?"https://unrelated.example.invalid":g.f.origin);if(!security.equals("NO_CSRF"))request.header("X-CSRF-Token",g.f.csrf);for(String key:keys)request.header("Idempotency-Key",key);
  var response=g.apiClient.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
  synchronized(this){requests.add(Map.of("number",number,"case",name,"status",response.statusCode(),"body",response.body()));record("SERVLET_RESPONSE_CAPTURED",Map.of("number",number,"case",name,"status",response.statusCode()));}
  assertEquals(status,response.statusCode(),response.body());assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElseThrow());return json.readTree(response.body());
 }
 void preNetworkCases()throws Exception {
  var bad=List.of("{",NUMERIC.replace("\"expectedRevision\":13","\"expectedRevision\":13,\"expectedRevision\":13"),NUMERIC+" {}",NUMERIC.replace("}",",\"finalConfirmationId\":\"1431080e-9b05-4913-9355-db3fa5b0c39a\"}"),"{\"expectedRevision\":13,\"expectedPublicationRevision\":5}",NUMERIC.replace("\"expectedExecutionNo\":1","\"expectedExecutionNo\":0"),TOKENS.replace("\"13\"","\"013\""),DUAL.replace("\"expectedRevisionToken\":\"13\"","\"expectedRevisionToken\":\"12\"")," ".repeat(65537));
  int i=0;for(String body:bad){i++;call("strict-body-"+i,"owner",PATH,body,List.of("task11-b2-exact-retry-invalid-"+i),"NORMAL",i==9?413:400);}
  call("missing-key","owner",PATH,NUMERIC,List.of(),"NORMAL",400);call("duplicate-key","owner",PATH,NUMERIC,List.of("task11-exact-key-a","task11-exact-key-b"),"NORMAL",400);call("empty-key","owner",PATH,NUMERIC,List.of(""),"NORMAL",400);
  call("stale-meeting","owner",PATH,NUMERIC.replace(":13",":12"),List.of("task11-exact-stale-meeting"),"NORMAL",409);call("stale-publication","owner",PATH,NUMERIC.replace(":5",":4"),List.of("task11-exact-stale-publication"),"NORMAL",409);call("stale-execution","owner",PATH,NUMERIC.replace("\"expectedExecutionNo\":1","\"expectedExecutionNo\":2"),List.of("task11-exact-stale-execution"),"NORMAL",409);
  for(String role:List.of("member","outsider","admin"))call("edit-denied-"+role,role,PATH,NUMERIC,List.of("task11-exact-edit-"+role),"NORMAL",403);
  call("cross-meeting","owner",PATH.replace(MEETING,"00000000-0000-4000-8000-000000000001"),NUMERIC,List.of("task11-exact-cross-meeting"),"NORMAL",403);
  call("missing-session","owner",PATH,NUMERIC,List.of("task11-exact-no-session"),"NO_SESSION",401);call("bad-origin","owner",PATH,NUMERIC,List.of("task11-exact-bad-origin"),"BAD_ORIGIN",403);call("missing-csrf","owner",PATH,NUMERIC,List.of("task11-exact-no-csrf"),"NO_CSRF",403);
 }
 void restoreGrant()throws Exception {
  assertEquals(0,grantCount);grantCount++;record("BEFORE_ONE_ADMIN_GRANT",Map.of("grantAttempt",grantCount,"before",List.of(1,0,2),"intendedAfter",List.of(1,1,3)));
  String script="import os,resource,runpy,sys; os.sched_setaffinity(0,sorted(os.sched_getaffinity(0))[:2]); resource.setrlimit(resource.RLIMIT_AS,(67108864,67108864)); path=sys.argv[1];sys.argv=sys.argv[1:];runpy.run_path(path,run_name='__main__')";
  child("grant",List.of("/usr/bin/python3","-c",script,inputs.root().resolve("tools/providers/controlled_knowledge.py").toString(),"--config",provider.get("config").asText(),"--grant-actor",g.f.owner,"--grant-read","--grant-publish"));
  var after=providerState("AFTER_GRANT");record("GRANT_OBSERVED",Map.of("providerAfterGrant",after));assertProviderRestored(after);assertEquals(before,g.business());counters(25,3);
 }
 void assertProviderRestored(JsonNode after){var expected=providerBefore.deepCopy();((ObjectNode)expected).set("grants",json.valueToTree(List.of(List.of(inputs.allocation().at("/actors/owner").asText(),"controlled-library","controlled-root",1,1,3))));assertEquals(expected,after,"Only exact grant fields may change in provider store");}
 JsonNode providerState(String phase)throws Exception {
  assertTrue(sqliteCount<3,"No fourth provider observation");sqliteCount++;record("BEFORE_SQLITE_OBSERVATION",Map.of("number",sqliteCount,"purpose",phase));
String script="import os,resource; os.sched_setaffinity(0,sorted(os.sched_getaffinity(0))[:2]); resource.setrlimit(resource.RLIMIT_AS,(67108864,67108864)); import sqlite3,json,sys; c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True); c.row_factory=sqlite3.Row; executions=[dict(r) for r in c.execute('SELECT id,publication,key,number,client,actor,logical_hash,request_hash,payload_hash,receipt FROM execution ORDER BY id LIMIT 2')]; [(r.update(receipt=json.loads(r['receipt']))) for r in executions]; print(json.dumps({'documents':c.execute('SELECT COUNT(*) FROM document').fetchone()[0], 'publications':c.execute('SELECT COUNT(*) FROM publication_slot').fetchone()[0], 'executionCount':c.execute('SELECT COUNT(*) FROM execution').fetchone()[0], 'grants':[list(r) for r in c.execute('SELECT actor,library,folder,can_read,can_publish,version FROM grant_record ORDER BY actor,library,folder')], 'slots':[dict(r) for r in c.execute('SELECT * FROM publication_slot ORDER BY publication LIMIT 2')], 'executions':executions}))";
  Path output=child("sqlite-"+sqliteCount,List.of("/usr/bin/python3","-c",script,provider.get("store").asText()));assertTrue(Files.size(output)<=262144);return json.readTree(Files.readAllBytes(output));
 }
 Path child(String label,List<String> command)throws Exception {
  Path output=inputs.evidence().resolve("exact-retry-"+label+".log");Files.createFile(output);Process child=new ProcessBuilder(command).directory(inputs.root().toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();Throwable primary=null;boolean[] interrupted={Thread.interrupted()};
  try{record("CHILD_OWNED_BEFORE_WAIT",Map.of("kind",label,"pid",child.pid(),"startInstant",child.info().startInstant().map(Object::toString).orElse("unavailable")));if(interrupted[0])throw new InterruptedException("Interrupted before child wait");assertTrue(child.waitFor(5,TimeUnit.SECONDS),"Bounded child wait");assertEquals(0,child.exitValue(),"Controlled child failed; custody retained");assertTrue(Files.size(output)<=262144);return output;}
  catch(Throwable failure){primary=failure;if(failure instanceof InterruptedException)interrupted[0]=true;throw failure;}
  finally{try{if(child.isAlive())Task11ResponseLossFixture.stopOwned(child,interrupted);record("CHILD_EXIT_CUSTODY",Map.of("kind",label,"pid",child.pid(),"alive",child.isAlive(),"exitCode",child.isAlive()?-999:child.exitValue()));assertFalse(child.isAlive());}catch(Throwable cleanup){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Exception e)throw e;else throw (Error)cleanup;}finally{if(interrupted[0])Thread.currentThread().interrupt();}}
 }
 void concurrentAcceptance()throws Exception {
  fault.race(KEY);var pool=Executors.newFixedThreadPool(2);Future<JsonNode> a=null,b=null;Throwable primary=null;
  try{record("BEFORE_TWO_SAME_KEY_REQUESTS",Map.of("maxRequests",2));a=pool.submit(()->call("concurrent-numeric","owner",PATH,NUMERIC,List.of(KEY),"NORMAL",202));b=pool.submit(()->call("concurrent-tokens","owner",PATH,TOKENS,List.of(KEY),"NORMAL",202));assertTrue(fault.arrivals.await(5,TimeUnit.SECONDS),"Both authorized preparations must reach pre-insert barrier");counters(39,17);fault.release(0);assertTrue(fault.firstCommit.await(5,TimeUnit.SECONDS),"First physical commit must complete before second admission");fault.release(1);var first=a.get(30,TimeUnit.SECONDS);var second=b.get(30,TimeUnit.SECONDS);assertNotEquals(first.path("replayed").asBoolean(),second.path("replayed").asBoolean());newTask=first.path("taskId").asText();newExecution=first.path("executionId").asText();assertAssociation(first,first.get("replayed").asBoolean());assertAssociation(second,second.get("replayed").asBoolean());assertEquals(2,fault.admitted.get());assertEquals(2,fault.commits.get());record("CONCURRENT_ACCEPTANCE_RESPONSES",Map.of("first",first,"second",second,"observation",fault.observation()));}
  catch(Throwable failure){primary=failure;throw failure;}
  finally{fault.abort();if(a!=null&&!a.isDone())a.cancel(true);if(b!=null&&!b.isDone())b.cancel(true);try{Task11B2SameGraphFixture.joinExactOwned(pool::shutdownNow,nanos->pool.awaitTermination(nanos,TimeUnit.NANOSECONDS));}catch(Throwable failure){if(primary!=null)primary.addSuppressed(failure);else if(failure instanceof Exception e)throw e;else throw (Error)failure;}finally{if(primary==null)fault.disarm();}}
 }
 void assertAssociation(JsonNode body,boolean replayed){assertTrue(body.path("replayed").isBoolean());assertEquals(replayed,body.get("replayed").asBoolean());assertEquals(newTask,body.path("taskId").asText());assertEquals(newTask,body.path("resultId").asText());assertEquals(newExecution,body.path("executionId").asText());assertEquals(PUBLICATION,body.path("publicationId").asText());assertEquals("1431080e-9b05-4913-9355-db3fa5b0c39a",body.path("confirmationId").asText());assertEquals("3",body.path("submissionNoToken").asText());assertEquals("2",body.path("executionNoToken").asText());assertEquals("PUBLICATION_ZERO_EFFECT_RETRY",body.path("operation").asText());assertEquals(202,body.path("httpStatus").asInt());assertTrue(body.has("observation"));}
 Map<String,List<Map<String,Object>>> rows(){var all=new TreeMap<String,List<Map<String,Object>>>();for(String table:before.keySet()){var keys=j.queryForList("SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME='PRIMARY' ORDER BY ORDINAL_POSITION",String.class,table);assertFalse(keys.isEmpty());all.put(table,j.queryForList("SELECT * FROM "+table+" ORDER BY "+String.join(",",keys)));}return all;}
 static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 static Map<String,Object> byId(List<Map<String,Object>> rows,String field,String id){return rows.stream().filter(row->id.equals(row.get(field))).findFirst().orElseThrow();}
 void verifyAccepted()throws Exception {
  record("ACTUAL_SUCCESSOR_ROWS_VERIFIED",Task11B2ExactRetryOracle.verify(originalRows,rows(),newTask,newExecution,origin));
 }
}
