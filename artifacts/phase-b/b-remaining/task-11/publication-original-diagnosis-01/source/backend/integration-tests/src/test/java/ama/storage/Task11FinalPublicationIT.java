package ama.storage;

import ama.transcript.*;
import ama.model.*;
import ama.task.*;
import ama.contract.MeetingContracts.*;
import java.util.*;
import java.nio.file.Files;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/** Real Task11 fixture: no bootstrap, fake final rows, or inherited old-schema connection. */
class Task11FinalPublicationIT extends Task10AdoptIT {
 Task11KnowledgeFixture knowledge;
 @org.junit.jupiter.api.AfterEach void stopKnowledge()throws Exception{if(knowledge!=null){long unresolved=j.queryForObject("SELECT COUNT(*) FROM external_execution e JOIN durable_task t ON t.id=e.task_id WHERE t.task_type='KNOWLEDGE_PUBLISH_V1' AND e.state IN ('MARKED','UNKNOWN')",Long.class);if(unresolved==0)knowledge.close();else Files.writeString(receipt.resolve("knowledge-retained-unresolved.json"),json.writeValueAsString(Map.of("pid",knowledge.process.pid(),"port",knowledge.port,"store",knowledge.store.toString(),"unresolved",unresolved)));}}
 @Override javax.sql.DataSource createSource(){return ama.publication.Task11Source.open();}
 @Override String instruction(){return "controlled:rich-no-sensitive";}
 @Override void preflightBeforeBusiness()throws Exception {
  var queue=app.getBean(JdbcTaskQueue.class);
  new JdbcModelPermitPool(new JdbcExternalOperations(queue),Map.of(ControlledMinutesProtocol.MODEL,new ModelConfiguration(ControlledMinutesProtocol.MODEL,1,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(t,m,d)->{throw new IllegalStateException("No cutoff proof");}).configure();
  super.preflightBeforeBusiness();
 }
 @Override void beforeWorker(String task) {
  try {
  super.beforeWorker(task);
  Files.writeString(receipt.resolve("task11-generation-pre-post-custody.json"),json.writeValueAsString(Map.ofEntries(
   Map.entry("task",task),Map.entry("meeting",meeting),Map.entry("requester",owner),
   Map.entry("providerPort",providerPort),Map.entry("clientPort",relay.port()),Map.entry("store",providerStore.toString()),
   Map.entry("clientClass",ControlledMinutesClient.class.getName()),Map.entry("transport","ControlledMinutesHttpTransport via Task09ControlledRelay"),
   Map.entry("authorizationIdentity","task09-owned-provider-test-authorization-value"),Map.entry("mode","CONTROLLED_INSTALLATION"),
   Map.entry("signingKey","NOT_APPLICABLE_GENERATION_PROTOCOL"),Map.entry("lifecycle","fresh store; same-process provider; close in parent fixture try-with-resources"),
   Map.entry("observedUtc",Instant.now().toString()),Map.entry("beforeFirstPost",true))));
  } catch(Exception failure){throw new IllegalStateException(failure);}
 }
 long revision(){return j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,meeting);}
 @Override void afterCandidate(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot facts)throws Exception {
  super.afterCandidate(candidate,facts);
  var authority=ama.api.Task09RuntimeProbe.components(app).authority();
  var limits=new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,8388608,128,256,256);
  var runtime=MinutesEditorRuntime.compose(authority,this::snapshot,pin,prompt,model,limits,isolated());
  var repository=new ama.meeting.JdbcMeetingRepository(source,app.getBean(ama.db.TransactionRunner.class),app.getBean(ama.meeting.JdbcMeetingAccess.class),app.getBean(JdbcSubjectFences.class),app.getBean(JdbcOutbox.class),new ama.audit.JdbcAuditAdapter(source),id->{if(!id.equals("task11-controlled-category"))throw new CategoryUnavailable();},authority);
  repository.save(actor(),meeting,"final-info",new SaveInfo(revision(),"Task11 actual reviewed meeting","task11-controlled-category",null,null,List.of(),List.of()),UUID.randomUUID().toString());
  repository.confirm(actor(),meeting,"final-roster",new ConfirmRoster(revision()),UUID.randomUUID().toString());
  String set=j.queryForObject("SELECT element_set_id FROM meeting WHERE id=?",String.class,meeting);
  var elements=j.queryForList("SELECT id,category FROM minutes_element WHERE set_id=? AND category IN ('CONCLUSION','ACTION') ORDER BY category,ordinal,id",set);
  assertEquals(3,elements.size());
  for(var element:elements){
   runtime.reviews().review(actor(),meeting,UUID.randomUUID().toString(),revision(),"ELEMENT",(String)element.get("id"),"SOURCE_SUPPORT","WHOLE","SUPPORTED",null,null,UUID.randomUUID().toString());
   if("ACTION".equals(element.get("category")))runtime.reviews().review(actor(),meeting,UUID.randomUUID().toString(),revision(),"ELEMENT",(String)element.get("id"),"ACTION_FIELDS",null,"CONFIRMED",null,null,UUID.randomUUID().toString());
  }
  var readiness=runtime.queries().readiness(actor(),meeting);
  assertTrue((Boolean)readiness.get("businessReady"));assertEquals(5,((List<?>)readiness.get("requiredReviewIds")).size());
  Files.writeString(receipt.resolve("task11-final-precondition.json"),json.writeValueAsString(Map.of("meeting",meeting,"revision",revision(),"readiness",readiness,"actualGenerationTask",candidate.get("task_id"))));
  knowledge=new Task11KnowledgeFixture(receipt,owner);
  restartForFinal();
  var response=call("POST","/meetings/"+meeting+"/final-confirmations",json.writeValueAsString(Map.of("expectedRevisionToken",Long.toString(revision()),"optionalReviewIds",List.of())),"task11-actual-final");
  Files.writeString(receipt.resolve("task11-final-response.json"),json.writeValueAsString(Map.of("status",response.statusCode(),"body",response.body())));
  assertEquals(201,response.statusCode(),response.body());
  String confirmation=body(response).get("resultId").asText();var frozen=call("GET","/meetings/"+meeting+"/final-confirmations/"+confirmation,"",null);assertEquals(200,frozen.statusCode(),frozen.body());assertTrue(body(frozen).get("provenanceVerified").asBoolean());assertEquals(5,body(frozen).get("reviewIds").size());assertEquals("READY_TO_PUBLISH",j.queryForObject("SELECT stage FROM meeting WHERE id=?",String.class,meeting));
  Files.writeString(receipt.resolve("task11-final-frozen-readback.json"),frozen.body());
  var submitted=call("POST","/meetings/"+meeting+"/publications",json.writeValueAsString(Map.of("expectedRevisionToken",Long.toString(revision()),"confirmationId",confirmation,"providerId","CONTROLLED_KNOWLEDGE_V1","libraryId","controlled-library","folderId","controlled-root")),"task11-publish");
  Files.writeString(receipt.resolve("task11-publication-response.json"),json.writeValueAsString(Map.of("status",submitted.statusCode(),"body",submitted.body())));assertEquals(202,submitted.statusCode(),submitted.body());
  String publicationTask=body(submitted).get("taskId").asText();var service=ama.api.Task11RuntimeProbe.publication(app);assertNotNull(service);var publicationQueue=app.getBean(JdbcTaskQueue.class);var operations=new JdbcExternalOperations(publicationQueue);var permits=new JdbcModelPermitPool(operations,Map.of(),(t,m,d)->{throw new IllegalStateException("No publication model reservation");});var handler=new ama.worker.KnowledgePublicationHandler(service,operations,permits);
  Files.writeString(receipt.resolve("knowledge-before-first-post.json"),json.writeValueAsString(Map.of("task",publicationTask,"publication",body(submitted).get("publicationId").asText(),"confirmation",confirmation,"requester",owner,"port",knowledge.port,"store",knowledge.store.toString(),"clientIdentities",knowledge.applicationIdentities.toString())));
  try(var loop=ama.worker.WorkerLoop.external(publicationQueue,operations,permits,Map.of("KNOWLEDGE_PUBLISH",1),Map.of("KNOWLEDGE_PUBLISH_V1",handler),Map.of(),"TASK11_KNOWLEDGE")){loop.pollOnce("KNOWLEDGE_PUBLISH");long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);String state="";while(System.nanoTime()<end){state=j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,publicationTask);if(Set.of("SUCCEEDED","FAILED","STALE","RECONCILING").contains(state))break;Thread.sleep(50);}Files.writeString(receipt.resolve("knowledge-task-result.json"),json.writeValueAsString(Map.of("task",publicationTask,"state",state,"lastError",Objects.toString(j.queryForObject("SELECT last_error FROM durable_task WHERE id=?",String.class,publicationTask),""))));Files.writeString(receipt.resolve("knowledge-durable-state.json"),json.writeValueAsString(Map.of("task",j.queryForList("SELECT id,state,last_error FROM durable_task WHERE id=?",publicationTask),"submission",j.queryForList("SELECT task_id,publication_id,submission_no,confirmation_id,provider_id,local_state FROM publication_submission WHERE task_id=?",publicationTask),"executions",j.queryForList("SELECT id,operation_id,execution_no,state FROM external_execution WHERE task_id=?",publicationTask))));assertEquals("SUCCEEDED",state);}


 }
 void minutesBounds(Map<String,Object> values){String[] names={"utf8","scalars","utf16","copy-bytes","copy-utf16","response-bytes","revision-depth","revision-nodes","tasks"};long[] bounds={16777216,16777216,16777216,268435456,134217728,33554432,128,256,256};for(int n=0;n<names.length;n++)values.put("minutes.bounds."+names[n],bounds[n]);}
 void restartForFinal(){if(app!=null)app.close();var configured=new HashMap<>(bounds);minutesBounds(configured);configured.put("minutes.enabled",true);configured.put("transcript.enabled",true);configured.put("publication.enabled",true);configured.put("publication.mode","ISOLATED_LOOPBACK_TEST");configured.put("publication.client-identities",knowledge.applicationIdentities.toString());configured.put("publication.origin","http://127.0.0.1:"+knowledge.port);configured.put("minutes.mode","CONTROLLED_INSTALLATION");configured.put("minutes.archive-root",archive.toString());configured.put("minutes.index-sha",pin);configured.put("minutes.prompt-version",prompt.version());configured.put("minutes.prompt-sha",prompt.sha256());configured.put("minutes.model-version",model.version());configured.put("minutes.model-sha",model.sha256());app=new org.springframework.boot.builder.SpringApplicationBuilder(ama.api.ApiApplication.class).properties(configured).initializers(context->{var c=(org.springframework.context.support.GenericApplicationContext)context;c.registerBean("task09Source",javax.sql.DataSource.class,()->source);c.registerBean("task09Backends",ama.storage.FileBackendDefinitions.class,()->definitions);}).run("--server.address=127.0.0.1","--server.port=0","--management.server.port=0","--logging.level.root=ERROR","--identity.allowed-origin="+origin,"--processing.enabled=true","--processing.archive-root="+archive,"--processing.index-sha="+pin,"--processing.config-version="+processing.version(),"--processing.config-sha="+processing.sha256());source=app.getBean(javax.sql.DataSource.class);j=new org.springframework.jdbc.core.JdbcTemplate(source);}
 @Override void afterExternal(Map<String,Object> candidate,MinutesExternalPreparation preparation,ControlledMinutesClient client,JdbcExternalOperations operations){}
}
