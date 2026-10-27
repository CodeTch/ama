package ama.storage;

import ama.api.*;
import ama.config.*;
import ama.config.file.*;
import ama.contract.ConfigArchiveContracts.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh test JVM/API/provider reopen of owner02 SUCCESS; no publication send, native start or schema migration. */
class Task11B4PublishedReadback03IT {
 static final Path REPO=Path.of("/home/tanren/ama/.worktrees/phase-b-completion"),W=REPO.resolve(".superpowers/sdd/2026-10-23-ama-phase-b-remaining");
 static final String INPUT_SHA="ed6029fc14768eb65e624e56ef3242411783c8c97d829093e6aba16d981a0ee2",CSRF="T".repeat(43);
 final ObjectMapper json=new ObjectMapper();Path out;JdbcTemplate j;Task11KnowledgeFixture knowledge;org.springframework.context.ConfigurableApplicationContext app;HttpClient client;String api,meeting,publication,owner;int requests,adminCommands;
 final List<Map<String,Object>> observations=new ArrayList<>();
 @Test void retainedSuccessHasActualApplicationAndProviderReadGatesOverTls()throws Exception {
  out=Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW"));assertEquals(W.resolve("task-11-b4-retained02-evidence-20261027/b4-doc03"),out);assertTrue(Files.isRegularFile(out.resolve("consumed.json")));Files.writeString(out.resolve("test-started-once.json"),"{\"started\":true}",StandardOpenOption.CREATE_NEW);
  Path inputs=W.resolve("task-11-b4-retained02-doc03-inputs-20261027.json");assertEquals(INPUT_SHA,hash(Files.readAllBytes(inputs)));var input=json.readTree(Files.readAllBytes(inputs));
  var fields=input.path("publicPins").fields();while(fields.hasNext()){var e=fields.next();Path file=Path.of(e.getKey());assertTrue(file.normalize().startsWith(W));assertEquals(e.getValue().asText(),hash(Files.readAllBytes(file)));}
  Path baseline=Path.of(input.path("baseline").asText());Path priorFile=Path.of(input.path("combinedResult").asText());assertEquals(input.path("combinedResultSha256").asText(),hash(Files.readAllBytes(priorFile)));var prior=json.readTree(Files.readAllBytes(priorFile));assertTrue(prior.path("qualified").asBoolean());
  var resource=json.readTree(Files.readAllBytes(baseline.resolve("resource.json")));var install=json.readTree(Files.readAllBytes(baseline.resolve("controlled-template-archive-installation.json")));var accepted=json.readTree(json.readTree(Files.readAllBytes(baseline.resolve("task11-publication-response.json"))).path("body").asText());
  var initial=json.readTree(Files.readAllBytes(baseline.resolve("b5-initial-intent-generation-http.json")));meeting=initial.path("meeting").asText();publication=accepted.path("publicationId").asText();owner=resource.path("owner").asText();
  Path previous=Path.of(input.path("previousWindow").asText());var previousResult=json.readTree(Files.readAllBytes(previous.resolve("result.json")));assertFalse(previousResult.path("qualified").asBoolean());assertEquals(1,previousResult.at("/testCounts/failures").asInt());var previousClose=json.readTree(Files.readAllBytes(previous.resolve("b4-close.json")));assertTrue(previousClose.path("apiClosed").asBoolean());assertEquals(0,previousClose.path("adminCommands").asInt());var closed=json.readTree(Files.readAllBytes(previous.resolve("knowledge-closed.json")));assertFalse(closed.path("alive").asBoolean());assertTrue(closed.path("storeRetained").asBoolean());
  var source=ama.publication.Task11B5Source02.open();j=new JdbcTemplate(source);j.setQueryTimeout(5);assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM publication WHERE id=? AND meeting_id=?",String.class,publication,meeting));assertEquals(1,j.queryForObject("SELECT ever_published FROM meeting WHERE id=? AND owner_id=?",Integer.class,meeting,owner));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE state IN ('MARKED','UNKNOWN')",Integer.class));
  Map<String,List<String>> before=business();
  Path root=Path.of(System.getenv("AMA_TASK09_RUN_ROOT")),archive=Path.of(resource.path("archive").asText());assertTrue(archive.toRealPath().startsWith(root.toRealPath()));String pin=install.path("indexSha").asText();
  var read=new ArchiveReadBudget(65536,2097152,64,128,16,200000,32,255,65536,262144,1048576,65536,32,250000,500000);var snapshot=new ControlledConfigArchiveSource().open(archive,pin,new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128),read,()->false);
  Ref processing=ref(snapshot,ArtifactType.PROCESSING_CONFIG,null),prompt=ref(snapshot,ArtifactType.PROMPT_CONFIG,install.path("promptVersion").asText()),model=ref(snapshot,ArtifactType.MODEL_CONFIG,install.path("modelVersion").asText());
  try {
   var previousProvider=json.readTree(Files.readAllBytes(baseline.resolve("knowledge-before-start.json")));knowledge=Task11KnowledgeFixture.reopenOnly(out,owner,root.resolve("knowledge"),previousProvider.path("port").asInt(),json.convertValue(previousProvider.path("identities"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));assertNotEquals(closed.path("pid").asLong(),knowledge.pid());var started=json.readTree(Files.readAllBytes(out.resolve("knowledge-before-start.json")));assertFalse(started.path("initialization").asBoolean());
   var settings=new Task11B3LifecycleApi.Settings(archive,pin,processing,prompt,model,knowledge.applicationIdentities,URI.create("http://127.0.0.1:"+knowledge.port),false);Path pki=root.resolve("pki");app=Task11B4ReadbackApi.start(source,settings,pki,Files.readString(pki.resolve("keystore-password")));Task11B3LifecycleApi.requireInstalled(app);
   assertTrue(app.getBeansOfType(ama.worker.WorkerLoop.class).isEmpty());assertTrue(app.getBeansOfType(ama.worker.ReconciliationLoop.class).isEmpty());api="https://localhost:"+app.getEnvironment().getProperty("local.server.port");client=HttpClient.newBuilder().sslContext(Task09NativeFixture.tls("writer")).connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
   String ownerToken=session(owner,false,false),outsider=session("b4-outsider-"+UUID.randomUUID(),true,false),admin=session("b4-admin-"+UUID.randomUUID(),true,true);String route="/api/v1/meetings/"+meeting+"/publications/"+publication+"/document";
   byte[] html=request("owner-allowed",ownerToken,route,200,null).body();assertTrue(new String(html,java.nio.charset.StandardCharsets.UTF_8).contains("Task11 actual reviewed meeting"));
   var service=Task11RuntimeProbe.publication(app);var traffic=service.traffic();request("outsider-app-denied",outsider,route,403,"MEETING_FORBIDDEN");assertEquals(traffic,service.traffic());request("admin-no-bypass",admin,route,403,"MEETING_FORBIDDEN");assertEquals(traffic,service.traffic());
   grant(owner,false,true);request("provider-read-denied-publish-still-allowed",ownerToken,route,403,"OFFICIAL_DOCUMENT_FORBIDDEN");
   grant(owner,true,false);assertArrayEquals(html,request("provider-read-only-allows",ownerToken,route,200,null).body());
   grant(owner,true,true);assertArrayEquals(html,request("restored-grant-identical-html",ownerToken,route,200,null).body());
   new ama.identity.JdbcIdentitySessions(source).revoke(ownerToken,UUID.randomUUID().toString());traffic=service.traffic();request("revoked-session",ownerToken,route,401,"UNAUTHENTICATED");assertEquals(traffic,service.traffic());
   assertEquals(before,business());assertEquals(0L,service.traffic().get("postWriteAttempts"));
   Files.writeString(out.resolve("b4-actual-readback.json"),json.writeValueAsString(Map.of("requests",observations,"htmlSha256",hash(html),"htmlBytes",html.length,"businessTablesUnchanged",before.keySet(),"providerTraffic",service.traffic(),"freshJvmPid",ProcessHandle.current().pid(),"providerReinitialized",false,"qualification","owner/outsider/admin and provider READ gate; no genuine member positive, browser, race, allB4/B6 qualification")),StandardOpenOption.CREATE_NEW);
  } finally {
   try {if(client!=null){client.shutdownNow();assertTrue(client.awaitTermination(Duration.ofSeconds(5)));}}
   finally {try {if(app!=null){try{Files.writeString(out.resolve("b4-traffic-before-close.json"),json.writeValueAsString(Task11RuntimeProbe.publication(app).traffic()),StandardOpenOption.CREATE_NEW);}finally{app.close();}}}finally {try {if(knowledge!=null)knowledge.close();}finally {
    Files.writeString(out.resolve("b4-close.json"),json.writeValueAsString(Map.of("apiClosed",app==null||!app.isActive(),"requests",requests,"adminCommands",adminCommands,"noNativeStart",true,"noWorker",true)),StandardOpenOption.CREATE_NEW);
   }}}

  }
 }
 Ref ref(ConfigArchiveSource.Snapshot snapshot,ArtifactType type,String version){var refs=snapshot.index().entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==type&&(version==null||version.equals(r.version()))).toList();assertEquals(1,refs.size());return refs.getFirst();}
 String session(String actor,boolean create,boolean admin)throws Exception {
  if(create)j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",actor,actor);
  if(admin)j.update("INSERT INTO role_assignment(enterprise_id,role,assigned_at) VALUES (?,'PLATFORM_ADMIN',UTC_TIMESTAMP(6))",actor);
  String token=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.SecureRandom.getSeed(32));assertEquals(1,j.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) SELECT ?,enterprise_id,session_generation,?,UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR FROM enterprise_identity WHERE enterprise_id=? AND status='ACTIVE'",hash(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)),CSRF,actor));var resolved=new ama.identity.JdbcIdentitySessions(j.getDataSource()).resolve(token).orElseThrow();assertEquals(actor,resolved.actor().enterpriseId());return token;
 }
 HttpResponse<byte[]> request(String name,String token,String path,int status,String code)throws Exception {
  assertTrue(++requests<=7);var response=client.send(HttpRequest.newBuilder(URI.create(api+path)).timeout(Duration.ofSeconds(35)).header("Cookie","__Host-AMA_SESSION="+token).GET().build(),HttpResponse.BodyHandlers.ofByteArray());String actualCode=response.headers().firstValue("Content-Type").orElse("").contains("json")?json.readTree(response.body()).at("/error/code").asText():"";observations.add(Map.of("case",name,"status",response.statusCode(),"errorCode",actualCode,"bytes",response.body().length,"sha256",hash(response.body()),"tls",response.sslSession().isPresent(),"providerTraffic",Task11RuntimeProbe.publication(app).traffic()));Files.writeString(out.resolve("b4-request-progress.json"),json.writeValueAsString(observations));assertEquals(status,response.statusCode(),name);assertTrue(response.body().length<=33554432);assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
  if(code!=null)assertEquals(code,json.readTree(response.body()).at("/error/code").asText());else {assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));assertEquals("nosniff",response.headers().firstValue("X-Content-Type-Options").orElse(""));assertEquals("default-src 'none'; frame-ancestors 'self'",response.headers().firstValue("Content-Security-Policy").orElse(""));assertTrue(response.sslSession().isPresent());}
  return response;
 }
 void grant(String actor,boolean read,boolean publish)throws Exception {
  assertTrue(++adminCommands<=3);var command=new ArrayList<String>(List.of("/usr/bin/python3",knowledge.providerScript.toString(),"--config",knowledge.config.toString(),"--grant-actor",actor));if(read)command.add("--grant-read");if(publish)command.add("--grant-publish");var process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(out.resolve("grant-"+adminCommands+".log").toFile()).start();if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();assertTrue(process.waitFor(5,TimeUnit.SECONDS));fail("Owned provider admin deadline");}assertEquals(0,process.exitValue());
 }
 Map<String,List<String>> business()throws Exception {
  var result=new TreeMap<String,List<String>>();for(String table:List.of("meeting","meeting_info_revision","roster_revision","roster_member","meeting_access_grant","final_confirmation","final_result_review","publication","publication_submission","external_document_mapping","publication_attempt","durable_task","task_attempt","external_operation","external_execution","idempotency_record","task_outbox","task_outbox_sequence","task_subject","meeting_search_projection","minutes_revision","element_set","result_review")){
   var rows=j.queryForList("SELECT * FROM "+table);assertTrue(rows.size()<=1024);var values=new ArrayList<String>();for(var row:rows){var canonical=new TreeMap<String,String>();row.forEach((k,v)->canonical.put(k,v==null?null:v.toString()));values.add(json.writeValueAsString(canonical));}Collections.sort(values);result.put(table,values);
  }return result;
 }
 static String hash(byte[] value)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));}
}
