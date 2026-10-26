package ama.storage;

import ama.api.Task11B2ReceiptApi;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import javax.sql.DataSource;
import java.lang.reflect.*;import java.net.URI;import java.net.http.*;import java.nio.file.*;import java.nio.file.attribute.PosixFilePermissions;import java.security.SecureRandom;import java.sql.*;import java.time.*;import java.util.*;import java.util.concurrent.TimeUnit;
import org.springframework.context.ConfigurableApplicationContext;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.AbstractDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ama.storage.Task11B2ExactRetryOracle.*;

/** Separate JVM/window: complete existing successor custody, then seven receipt-only calls. */
class Task11B2ExactRetryReadbackIT {
 static final String MEETING="36db643e-5fbd-4f9a-9a85-a7e6b7d76e38",PATH="/meetings/"+MEETING+"/publications/"+PUBLICATION+"/zero-effect-retries";
 static final String NUMERIC=Task11B2ExactRetryIT.NUMERIC,TOKENS=Task11B2ExactRetryIT.TOKENS,DUAL=Task11B2ExactRetryIT.DUAL;
 static final String INSERT_SESSION="INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) SELECT ?,enterprise_id,session_generation,?,UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR FROM enterprise_identity WHERE enterprise_id=? AND status='ACTIVE'",DELETE_SESSION="DELETE FROM identity_session WHERE token_hash=?";
 final Map<String,Object> result=new LinkedHashMap<>();final List<Map<String,Object>> events=new ArrayList<>(),responses=new ArrayList<>();final List<Session> sessions=new ArrayList<>();final Map<String,List<String>> primaryKeys=new TreeMap<>();
 final ThreadLocal<Permit> sessionPermit=new ThreadLocal<>();
 Task11B2RuntimeFixture.Inputs inputs;JdbcTemplate j;ConfigurableApplicationContext app;HttpClient client;JsonNode origin,failed,provider,relay;String task,execution,api;int calls,sqliteAttempts;boolean appCloseAttempted,clientCloseAttempted,complete;
 record Permit(String sql,String hash,String actor) {}
 static final class Session {final String role,actor,token,hash;boolean insertAttempted,inserted,deleteAttempted;int deleted;Session(String role,String actor,String token,String hash){this.role=role;this.actor=actor;this.token=token;this.hash=hash;}Map<String,Object> custody(){return Map.of("role",role,"actor",actor,"tokenHash",hash,"insertAttempted",insertAttempted,"inserted",inserted,"deleteAttempted",deleteAttempted,"deleted",deleted);}}
 @Test void readExistingSuccessorThenSevenIndependentReceiptCalls()throws Exception {
  inputs=Task11B2RuntimeFixture.inspectExactRetryReadbackInputs();Path w=inputs.root().resolve(".superpowers/sdd/2026-10-23-ama-phase-b-remaining");
  origin=pin(w,"rejectionAttainedFacts");failed=Task11B2RuntimeFixture.publicReadbackResult(inputs.root().resolve(inputs.configuration().path("failedWindowResult").asText()),inputs.configuration().path("failedWindowResultSha256").asText());provider=origin.at("/retainedEndpoints/provider");relay=origin.at("/retainedEndpoints/relay");task=inputs.configuration().path("existingTaskId").asText();execution=inputs.configuration().path("existingExecutionId").asText();
  result.put("label",Task11B2RuntimeFixture.READBACK_LABEL);result.put("configurationAckSha256",Task11B2RuntimeFixture.READBACK_ACK_SHA);result.put("failedWindowResultSha256",inputs.configuration().path("failedWindowResultSha256").asText());result.put("existingTaskId",task);result.put("existingExecutionId",execution);result.put("status","PARTIAL_UNQUALIFIED");Throwable primary=null;
  try{
   record("STARTED_NO_MUTATIONS",Map.of("grantAttempts",0,"nativeReopens",0));
   DataSource guarded=guard(ama.publication.Task11Source.open());j=new JdbcTemplate(guarded);j.setQueryTimeout(5);j.setMaxRows(10001);
   assertEquals("ama_t11b2_d9e2fdcc611e_behavior",j.queryForObject("SELECT DATABASE()",String.class));assertEquals("t11b2_d9e2fdcc611e@%",j.queryForObject("SELECT CURRENT_USER()",String.class));
   var baselineEvent=event("RETAINED_BASELINE_VERIFIED");var originals=json.convertValue(baselineEvent.path("originalRows"),new TypeReference<Map<String,List<Map<String,Object>>>>(){});
   var current=rows();captureRows("accepted-rows-before.json",current);record("CURRENT_SEVENTEEN_TABLES_CAPTURED_BEFORE_ORACLE",Map.of("artifact","accepted-rows-before.json","sessions",0,"contexts",0));
   var verified=verify(originals,current,task,execution,origin);record("COMPLETE_EXISTING_SUCCESSOR_ORACLE_PASSED",verified);
   identities();counters("BEFORE_REPLAY");assertEquals(inputs.allocation().at("/actors/owner").asText(),j.queryForObject("SELECT owner_id FROM meeting WHERE id=?",String.class,MEETING));
   record("BEFORE_RECEIPT_ONLY_CONTEXT",Map.of("contextsMax",1));app=Task11B2ReceiptApi.start(guarded);Task11B2ReceiptApi.requireIndependent(app);app.getBean(org.springframework.context.support.DefaultLifecycleProcessor.class).setTimeoutPerShutdownPhase(5000L);assertSame(guarded,app.getBean(DataSource.class));api="http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port")+"/api/v1";
   client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
   for(String role:List.of("owner","member","outsider","admin"))createSession(role);
   for(var entry:List.of(Map.entry("numeric",NUMERIC),Map.entry("tokens",TOKENS),Map.entry("equal-dual",DUAL)))association(call("owner-"+entry.getKey(),"owner",entry.getValue(),202));
   call("changed-body-same-key","owner",NUMERIC.replace(":13",":12"),409);
   for(String role:List.of("member","outsider","admin"))call("fresh-edit-denied-"+role,role,NUMERIC,403);
   assertEquals(7,calls);var after=rows();captureRows("accepted-rows-after.json",after);equal(current,after);verify(originals,after,task,execution,origin);record("BUSINESS_UNCHANGED_AFTER_SEVEN_CALLS",Map.of("businessBefore",hashes(current),"businessAfter",hashes(after)));counters("AFTER_REPLAY");
   cleanup();record("RECEIPT_RESOURCES_CLOSED_BEFORE_FINAL_OBSERVATION",Map.of("sessions",sessionCustody()));
   JsonNode providerAfter=providerState();record("FINAL_PROVIDER_CAPTURED",Map.of("providerAfter",providerAfter));equal(event("GRANT_OBSERVED").path("providerAfterGrant"),providerAfter);identities();counters("FINAL");
   assertEquals(1,sqliteAttempts);complete=true;record("COMPLETE_HELD_SUCCESSOR_NO_CLAIM_SEND",Map.of("fullOracle",true,"receiptOnlyCalls",7,"finalProviderInvariant",true));
  }catch(Throwable failure){primary=failure;result.put("safeFailureType",failure.getClass().getName());try{record("FAILED_NO_RETRY_OR_COMPENSATION",Map.of("callsAttempted",calls,"sqliteAttempts",sqliteAttempts));}catch(Throwable capture){failure.addSuppressed(capture);}throw failure;}
  finally{
   Throwable cleanupFailure=null;try{cleanup();}catch(Throwable failure){cleanupFailure=failure;}
   try{result.put("status",complete&&primary==null&&cleanupFailure==null?"PASS":"PARTIAL_UNQUALIFIED");result.put("servletRequestsAttempted",calls);result.put("sqliteObservationsAttempted",sqliteAttempts);result.put("grantAttempts",0);result.put("nativeReopens",0);result.put("sessions",sessionCustody());if(cleanupFailure!=null)result.put("cleanupFailureType",cleanupFailure.getClass().getName());record("FINAL_CUSTODY",Map.of("noClaimOrSend",true,"originalWindowPreserved",true));}catch(Throwable failure){if(cleanupFailure==null)cleanupFailure=failure;else cleanupFailure.addSuppressed(failure);}
   if(cleanupFailure!=null){if(primary!=null)primary.addSuppressed(cleanupFailure);else rethrow(cleanupFailure);}
  }
 }
 JsonNode pin(Path w,String key)throws Exception{return Task11B2RuntimeFixture.publicJson(w.resolve(inputs.configuration().path(key).asText()),inputs.configuration().path(key+"Sha256").asText());}
 JsonNode event(String phase){var matches=new ArrayList<JsonNode>();failed.path("events").forEach(e->{if(phase.equals(e.path("phase").asText()))matches.add(e.path("facts"));});assertEquals(1,matches.size());return matches.getFirst();}
 void record(String phase,Map<String,?> facts)throws Exception {events.add(Map.of("sequence",events.size()+1,"utc",Instant.now().toString(),"phase",phase,"facts",canonical(facts)));result.put("phase",phase);result.put("events",events);result.put("responses",responses);result.put("sessions",sessionCustody());write("exact-retry-readback-result.json",result,false);}
 void write(String name,Object value,boolean exclusive)throws Exception {Path file=inputs.evidence().resolve(name);assertFalse(Files.isSymbolicLink(file));String text=json.writerWithDefaultPrettyPrinter().writeValueAsString(value);Files.writeString(file,text,exclusive?new OpenOption[]{StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE}:new OpenOption[]{StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE});}
 List<Map<String,Object>> sessionCustody(){return sessions.stream().map(Session::custody).toList();}
 Map<String,List<Map<String,Object>>> rows(){var all=new TreeMap<String,List<Map<String,Object>>>();for(String table:TABLES){var keys=primaryKeys.computeIfAbsent(table,t->j.queryForList("SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME='PRIMARY' ORDER BY ORDINAL_POSITION",String.class,t));assertFalse(keys.isEmpty());for(String key:keys)assertTrue(key.matches("[a-z_]+"));var found=j.queryForList("SELECT * FROM "+table+" ORDER BY "+String.join(",",keys));assertTrue(found.size()<=10000,"Bounded business rows");all.put(table,found);}return all;}
 Map<String,String> hashes(Map<String,List<Map<String,Object>>> rows)throws Exception {var hashes=new TreeMap<String,String>();for(var entry:rows.entrySet())hashes.put(entry.getKey(),Task11B2RuntimeFixture.digest(json.writeValueAsBytes(canonical(entry.getValue()))));return hashes;}
 void captureRows(String name,Map<String,List<Map<String,Object>>> rows)throws Exception {var classes=new TreeMap<String,List<Map<String,String>>>();rows.forEach((table,list)->classes.put(table,list.stream().map(row->{var types=new TreeMap<String,String>();row.forEach((k,v)->{if(v instanceof Number)types.put(k,v.getClass().getName());});return (Map<String,String>)types;}).toList()));write(name,Map.of("rows",Task11B2SameGraphFixture.normalize(rows),"numericJavaClasses",classes),true);}
 static String tokenHash(String value)throws Exception{return Task11B2RuntimeFixture.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 void createSession(String role)throws Exception {
  assertTrue(sessions.size()<4);String actor=inputs.allocation().at("/actors/"+role).asText();assertFalse(actor.isBlank());String token=Base64.getUrlEncoder().withoutPadding().encodeToString(SecureRandom.getSeed(32));var s=new Session(role,actor,token,tokenHash(token));sessions.add(s);record("SESSION_PREPARED_BEFORE_INSERT",Map.of("session",s.custody()));
  sessionPermit.set(new Permit(INSERT_SESSION,s.hash,s.actor));try{s.insertAttempted=true;record("SESSION_INSERT_INTENT",Map.of("session",s.custody()));assertEquals(1,j.update(INSERT_SESSION,s.hash,"T".repeat(43),s.actor));s.inserted=true;record("SESSION_INSERT_RETURNED",Map.of("session",s.custody()));}finally{sessionPermit.remove();}
 }
 JsonNode call(String name,String role,String body,int expected)throws Exception {
  assertTrue(calls<7);calls++;record("BEFORE_RECEIPT_REQUEST",Map.of("number",calls,"case",name,"role",role,"body",body,"key",KEY));var session=sessions.stream().filter(s->s.role.equals(role)).findFirst().orElseThrow();
  var request=HttpRequest.newBuilder(URI.create(api+PATH)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").header("Origin","https://task11-b2.example.invalid").header("X-CSRF-Token","T".repeat(43)).header("Idempotency-Key",KEY).header("Cookie","__Host-AMA_SESSION="+session.token).POST(HttpRequest.BodyPublishers.ofString(body)).build();
  var response=client.send(request,HttpResponse.BodyHandlers.ofString());responses.add(Map.of("number",calls,"case",name,"status",response.statusCode(),"body",response.body()));record("RECEIPT_RESPONSE_CAPTURED",Map.of("number",calls,"status",response.statusCode()));assertEquals(expected,response.statusCode());assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElseThrow());return json.readTree(response.body());
 }
 void association(JsonNode body){assertTrue(body.path("replayed").isBoolean()&&body.get("replayed").asBoolean());for(var entry:Map.of("taskId",task,"resultId",task,"executionId",execution,"publicationId",PUBLICATION,"confirmationId","1431080e-9b05-4913-9355-db3fa5b0c39a","submissionNoToken","3","executionNoToken","2","operation","PUBLICATION_ZERO_EFFECT_RETRY").entrySet())assertEquals(entry.getValue(),body.path(entry.getKey()).asText());assertEquals(java.math.BigInteger.valueOf(202),integer(body.get("httpStatus")));assertTrue(body.has("observation"));}
 void cleanup()throws Exception {
  boolean interrupted=Thread.interrupted();Throwable failure=null;
  try{
   if(client!=null&&!clientCloseAttempted){clientCloseAttempted=true;try{Task11B2SameGraphFixture.joinExactOwned(client::shutdownNow,nanos->client.awaitTermination(Duration.ofNanos(nanos)));}catch(Throwable f){failure=add(failure,f);}finally{interrupted|=Thread.interrupted();}}
   if(app!=null&&!appCloseAttempted){appCloseAttempted=true;try{app.close();result.put("appCloseReturned",true);}catch(Throwable f){failure=add(failure,f);}finally{interrupted|=Thread.interrupted();}}
   if(j!=null)for(var s:sessions)if(s.insertAttempted&&!s.deleteAttempted){s.deleteAttempted=true;try{record("SESSION_DELETE_INTENT",Map.of("session",s.custody()));}catch(Throwable f){failure=add(failure,f);}sessionPermit.set(new Permit(DELETE_SESSION,s.hash,s.actor));try{s.deleted=j.update(DELETE_SESSION,s.hash);assertTrue(s.deleted==1||!s.inserted&&s.deleted==0);assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM identity_session WHERE token_hash=?",Integer.class,s.hash));record("SESSION_DELETE_RETURNED",Map.of("session",s.custody()));}catch(Throwable f){failure=add(failure,f);}finally{sessionPermit.remove();interrupted|=Thread.interrupted();}}
  }finally{if(interrupted)Thread.currentThread().interrupt();}
  if(failure!=null)rethrow(failure);
 }
 static Throwable add(Throwable first,Throwable next){if(first==null)return next;first.addSuppressed(next);return first;}
 static void rethrow(Throwable failure)throws Exception {if(failure instanceof Exception e)throw e;throw (Error)failure;}
 static Object invoke(Object target,Method method,Object[] args)throws Throwable {try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
 DataSource guard(DataSource actual){return new AbstractDataSource(){public Connection getConnection()throws SQLException{return connection(actual.getConnection());}public Connection getConnection(String u,String p)throws SQLException{throw new SQLException("Alternate credentials forbidden");}};}
 Connection connection(Connection actual){return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
  if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "equals"->proxy==args[0];case "hashCode"->System.identityHashCode(proxy);default->"ReadbackGuardConnection";};
  if(method.getName().equals("unwrap")){assertEquals(Connection.class,args[0]);return proxy;}if(method.getName().equals("isWrapperFor"))return args[0]==Connection.class;
  if(method.getName().equals("prepareCall"))throw new AssertionError("Callable SQL forbidden");
  Object value=invoke(actual,method,args);if(!(value instanceof Statement statement))return value;String prepared=args!=null&&args.length>0&&args[0] instanceof String text?text:null;var parameters=new HashMap<Integer,Object>();
  return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{statement instanceof PreparedStatement?PreparedStatement.class:Statement.class},(sp,sm,sa)->{
   if(sm.getName().equals("getConnection"))return proxy;if(sm.getName().equals("unwrap"))throw new AssertionError("Statement unwrap forbidden");
   if(sm.getName().startsWith("set")&&sa!=null&&sa.length>=2&&sa[0] instanceof Integer index)parameters.put(index,sa[1]);if(sm.getName().equals("clearParameters"))parameters.clear();
   if(sm.getName().contains("Batch"))throw new AssertionError("Batch SQL forbidden");
   if(sm.getName().startsWith("execute")){String sql=prepared!=null?prepared:sa!=null&&sa.length>0&&sa[0] instanceof String text?text:null;assertNotNull(sql);if(!sql.equals("SET NAMES utf8mb4")&&!sql.equals("SET time_zone = '+00:00'")&&!sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT ")){Permit permit=sessionPermit.get();assertNotNull(permit,"Business write forbidden");assertEquals(permit.sql(),sql);assertEquals(permit.hash(),parameters.get(1));if(sql.equals(INSERT_SESSION)){assertEquals(permit.actor(),parameters.get(3));assertEquals("T".repeat(43),parameters.get(2));}else assertEquals(DELETE_SESSION,sql);}}
   return invoke(statement,sm,sa);
  });
 });}
 JsonNode privateJson(Path path)throws Exception {privatePath(path);assertTrue(Files.size(path)<=262144);return json.readTree(Files.readAllBytes(path));}
 void privatePath(Path path)throws Exception {assertTrue(path.isAbsolute()&&path.startsWith(inputs.owned())&&path.normalize().equals(path));Path current=inputs.owned();for(Path part:inputs.owned().relativize(path)){current=current.resolve(part);assertFalse(Files.isSymbolicLink(current));}assertTrue(Files.isRegularFile(path));assertEquals(path,path.toRealPath());}
 void identities()throws Exception {for(JsonNode endpoint:List.of(provider,relay)){long pid=endpoint.path("pid").asLong();assertTrue(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));String stat=Files.readString(Path.of("/proc/"+pid+"/stat"));assertEquals(endpoint.path("startTicks").asText(),stat.substring(stat.lastIndexOf(')')+2).split(" ")[19]);Path cfg=Path.of(endpoint.path("config").asText());assertTrue(Files.readString(Path.of("/proc/"+pid+"/cmdline")).replace('\0',' ').contains(cfg.toString()));assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(cfg));assertEquals(endpoint.path("port"),privateJson(cfg).path("port"));}record("RETAINED_IDENTITIES_OBSERVED",Map.of("providerPid",provider.path("pid").asLong(),"relayPid",relay.path("pid").asLong()));}
 void counters(String phase)throws Exception {JsonNode actual=privateJson(Path.of(relay.path("state").asText()));record("RELAY_COUNTERS_CAPTURED",Map.of("purpose",phase,"relay",actual));for(String key:List.of("get","postAuthorization","postPublication","failures","blockedPublicationAttempts","withheld"))equal(inputs.configuration().path("retainedRelayBaseline").get(key),actual.get(key));}
 JsonNode providerState()throws Exception {
  assertEquals(0,sqliteAttempts);sqliteAttempts++;record("BEFORE_ONLY_REMAINING_SQLITE_OBSERVATION",Map.of("windowObservation",1,"combinedObservation",3));Path store=Path.of(provider.path("store").asText());privatePath(store);
  String script="import os,resource; os.sched_setaffinity(0,sorted(os.sched_getaffinity(0))[:2]); resource.setrlimit(resource.RLIMIT_AS,(67108864,67108864)); import sqlite3,json,sys; c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True); c.row_factory=sqlite3.Row; executions=[dict(r) for r in c.execute('SELECT id,publication,key,number,client,actor,logical_hash,request_hash,payload_hash,receipt FROM execution ORDER BY id LIMIT 2')]; [(r.update(receipt=json.loads(r['receipt']))) for r in executions]; print(json.dumps({'documents':c.execute('SELECT COUNT(*) FROM document').fetchone()[0], 'publications':c.execute('SELECT COUNT(*) FROM publication_slot').fetchone()[0], 'executionCount':c.execute('SELECT COUNT(*) FROM execution').fetchone()[0], 'grants':[list(r) for r in c.execute('SELECT actor,library,folder,can_read,can_publish,version FROM grant_record ORDER BY actor,library,folder')], 'slots':[dict(r) for r in c.execute('SELECT * FROM publication_slot ORDER BY publication LIMIT 2')], 'executions':executions}))";
  Path output=inputs.evidence().resolve("exact-retry-readback-sqlite-final.log");Files.createFile(output);Process child=new ProcessBuilder("/usr/bin/python3","-c",script,store.toString()).directory(inputs.root().toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();Throwable primary=null;boolean[] interrupted={Thread.interrupted()};var custody=new LinkedHashMap<String,Object>();custody.put("pid",child.pid());custody.put("startInstant",child.info().startInstant().map(Object::toString).orElse("unavailable"));
  try{record("SQLITE_CHILD_OWNED",custody);if(interrupted[0])throw new InterruptedException("Interrupted before observation wait");assertTrue(child.waitFor(5,TimeUnit.SECONDS));assertEquals(0,child.exitValue());assertTrue(Files.size(output)<=262144);return json.readTree(Files.readAllBytes(output));}
  catch(Throwable failure){primary=failure;if(failure instanceof InterruptedException)interrupted[0]=true;throw failure;}
  finally{Throwable cleanupFailure=null;try{if(child.isAlive())Task11ResponseLossFixture.stopOwned(child,interrupted);}catch(Throwable failure){cleanupFailure=failure;}finally{custody.put("alive",child.isAlive());custody.put("exitCode",child.isAlive()?-999:child.exitValue());result.put("sqliteChild",custody);}
   try{write("exact-retry-readback-sqlite-exit.json",custody,true);record("SQLITE_CHILD_EXIT",custody);assertFalse(child.isAlive());}catch(Throwable failure){cleanupFailure=add(cleanupFailure,failure);}finally{if(interrupted[0])Thread.currentThread().interrupt();}
   if(cleanupFailure!=null){if(primary!=null)primary.addSuppressed(cleanupFailure);else rethrow(cleanupFailure);}
  }
 }
}
