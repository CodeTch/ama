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
 Task11KnowledgeFixture knowledge;boolean preserveKnowledge;
 @org.junit.jupiter.api.AfterEach void stopKnowledge()throws Exception{if(knowledge!=null){long unresolved=j.queryForObject("SELECT COUNT(*) FROM external_execution e JOIN durable_task t ON t.id=e.task_id WHERE t.task_type='KNOWLEDGE_PUBLISH_V1' AND e.state IN ('MARKED','UNKNOWN')",Long.class);if(unresolved==0&&!preserveKnowledge)knowledge.close();else Files.writeString(receipt.resolve("knowledge-retained-unresolved.json"),json.writeValueAsString(Map.of("pid",knowledge.pid(),"port",knowledge.port,"store",knowledge.store.toString(),"unresolved",unresolved)));}}
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
  knowledge=knowledgeFixture();
  restartForFinal();
  var response=call("POST","/meetings/"+meeting+"/final-confirmations",json.writeValueAsString(Map.of("expectedRevisionToken",Long.toString(revision()),"optionalReviewIds",List.of())),"task11-actual-final");
  Files.writeString(receipt.resolve("task11-final-response.json"),json.writeValueAsString(Map.of("status",response.statusCode(),"body",response.body())));
  assertEquals(201,response.statusCode(),response.body());
  String confirmation=body(response).get("resultId").asText();var frozen=call("GET","/meetings/"+meeting+"/final-confirmations/"+confirmation,"",null);assertEquals(200,frozen.statusCode(),frozen.body());assertTrue(body(frozen).get("provenanceVerified").asBoolean());assertEquals(5,body(frozen).get("reviewIds").size());assertEquals("READY_TO_PUBLISH",j.queryForObject("SELECT stage FROM meeting WHERE id=?",String.class,meeting));
  Files.writeString(receipt.resolve("task11-final-frozen-readback.json"),frozen.body());
  var submitted=call("POST","/meetings/"+meeting+"/publications",json.writeValueAsString(Map.of("expectedRevisionToken",Long.toString(revision()),"confirmationId",confirmation,"providerId","CONTROLLED_KNOWLEDGE_V1","libraryId","controlled-library","folderId","controlled-root")),"task11-publish");
  Files.writeString(receipt.resolve("task11-publication-response.json"),json.writeValueAsString(Map.of("status",submitted.statusCode(),"body",submitted.body())));assertEquals(202,submitted.statusCode(),submitted.body());
  runPublication(json.convertValue(body(submitted),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));


 }
 Task11PublicationWaiter.State publicationState(String task,boolean done,boolean failed){var t=j.queryForMap("SELECT state FROM durable_task WHERE id=?",task);var e=j.queryForList("SELECT e.state execution_state,o.state operation_state FROM external_execution e JOIN external_operation o ON o.id=e.operation_id WHERE e.task_id=? ORDER BY e.execution_no DESC LIMIT 1",task);return new Task11PublicationWaiter.State((String)t.get("state"),e.isEmpty()?null:(String)e.getFirst().get("operation_state"),e.isEmpty()?null:(String)e.getFirst().get("execution_state"),done,failed);}
 void runPublication(Map<String,Object> accepted)throws Exception {
  String task=(String)accepted.get("taskId");var queue=app.getBean(JdbcTaskQueue.class);var operations=new JdbcExternalOperations(queue);var permits=new JdbcModelPermitPool(operations,Map.of(),(t,m,d)->{throw new IllegalStateException("No publication model reservation");});var service=ama.api.Task11RuntimeProbe.publication(app);var handler=new ama.worker.KnowledgePublicationHandler(service,operations,permits);var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();var done=new java.util.concurrent.atomic.AtomicBoolean();
  Files.writeString(receipt.resolve("knowledge-before-first-post.json"),json.writeValueAsString(Map.of("task",task,"publication",accepted.get("publicationId"),"confirmation",accepted.get("confirmationId"),"requester",owner,"port",knowledgeClientPort(),"providerPort",knowledge.port,"store",knowledge.store.toString(),"clientIdentities",knowledge.applicationIdentities.toString(),"trafficBefore",service.traffic())));
  ama.worker.WorkerLoop.ExternalHandler guarded=execution->{try{handler.handle(execution);}catch(Throwable e){failure.set(e);var safe=new ArrayList<String>();for(Throwable x=e;x!=null;x=x.getCause()){safe.add(x.getClass().getName());if(x instanceof ama.contract.MinutesContracts.Failure f)safe.add(f.code().name());for(var frame:x.getStackTrace())safe.add(frame.toString());}Files.writeString(receipt.resolve("safe-publication-worker-failure.json"),json.writeValueAsString(safe));throw e;}finally{done.set(true);}};
  var loop=ama.worker.WorkerLoop.external(queue,operations,permits,Map.of("KNOWLEDGE_PUBLISH",1),Map.of("KNOWLEDGE_PUBLISH_V1",guarded),Map.of(),"TASK11_KNOWLEDGE");Task11PublicationWaiter.Decision decision=Task11PublicationWaiter.Decision.WAIT;Task11PublicationWaiter.State before=null,after=null;boolean workerClosed=false;long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(45);
  try{loop.pollOnce("KNOWLEDGE_PUBLISH");while(decision==Task11PublicationWaiter.Decision.WAIT){before=publicationState(task,done.get(),failure.get()!=null);decision=Task11PublicationWaiter.decide(before,System.nanoTime()>=end);if(decision==Task11PublicationWaiter.Decision.WAIT)Thread.sleep(50);}Files.writeString(receipt.resolve("knowledge-before-worker-close.json"),json.writeValueAsString(Map.of("decision",decision.name(),"state",before,"traffic",service.traffic())));}
  finally{try{loop.close();workerClosed=true;}finally{try{after=publicationState(task,done.get(),failure.get()!=null);long other=j.queryForObject("SELECT COUNT(*) FROM external_execution e JOIN durable_task t ON t.id=e.task_id WHERE t.task_type='KNOWLEDGE_PUBLISH_V1' AND e.state IN ('MARKED','UNKNOWN')",Long.class);preserveKnowledge=before==null||Task11PublicationWaiter.retainProvider(before,after,workerClosed,other>0);Files.writeString(receipt.resolve("knowledge-after-worker-close.json"),json.writeValueAsString(Map.of("workerClosed",workerClosed,"state",after,"retainProvider",preserveKnowledge,"unresolvedAcrossFixture",other,"traffic",service.traffic())));Files.writeString(receipt.resolve("knowledge-durable-state.json"),json.writeValueAsString(Map.of("task",j.queryForList("SELECT id,state,last_error FROM durable_task WHERE id=?",task),"submission",j.queryForList("SELECT task_id,publication_id,submission_no,confirmation_id,provider_id,local_state FROM publication_submission WHERE task_id=?",task),"executions",j.queryForList("SELECT id,operation_id,execution_no,state FROM external_execution WHERE task_id=?",task))));}catch(Throwable diagnostic){preserveKnowledge=true;throw diagnostic;}}}
  assertEquals(expectedPublicationDecision(),decision,failure.get()==null?"See captured complete owner states":failure.get().getClass().getName());assertNotNull(after);assertTrue(after.both(expectedPublicationDecision()==Task11PublicationWaiter.Decision.SUCCESS?"SUCCEEDED":expectedPublicationDecision()==Task11PublicationWaiter.Decision.TERMINAL_FAILURE?"FAILED":"UNKNOWN"));
 }
 Task11PublicationWaiter.Decision expectedPublicationDecision(){return Task11PublicationWaiter.Decision.SUCCESS;}
 Task11KnowledgeFixture knowledgeFixture()throws Exception{return new Task11KnowledgeFixture(receipt,owner);}
 int knowledgeClientPort(){return knowledge.port;}
 void minutesBounds(Map<String,Object> values){String[] names={"utf8","scalars","utf16","copy-bytes","copy-utf16","response-bytes","revision-depth","revision-nodes","tasks"};long[] bounds={16777216,16777216,16777216,268435456,134217728,33554432,128,256,256};for(int n=0;n<names.length;n++)values.put("minutes.bounds."+names[n],bounds[n]);}
 void restartForFinal(){if(app!=null)app.close();var configured=new HashMap<>(bounds);minutesBounds(configured);configured.put("minutes.enabled",true);configured.put("transcript.enabled",true);configured.put("publication.enabled",true);configured.put("publication.mode","ISOLATED_LOOPBACK_TEST");configured.put("publication.client-identities",knowledge.applicationIdentities.toString());configured.put("publication.origin","http://127.0.0.1:"+knowledgeClientPort());configured.put("minutes.mode","CONTROLLED_INSTALLATION");configured.put("minutes.archive-root",archive.toString());configured.put("minutes.index-sha",pin);configured.put("minutes.prompt-version",prompt.version());configured.put("minutes.prompt-sha",prompt.sha256());configured.put("minutes.model-version",model.version());configured.put("minutes.model-sha",model.sha256());app=new org.springframework.boot.builder.SpringApplicationBuilder(ama.api.ApiApplication.class).properties(configured).initializers(context->{var c=(org.springframework.context.support.GenericApplicationContext)context;c.registerBean("task09Source",javax.sql.DataSource.class,()->source);c.registerBean("task09Backends",ama.storage.FileBackendDefinitions.class,()->definitions);}).run("--server.address=127.0.0.1","--server.port=0","--management.server.port=0","--logging.level.root=ERROR","--identity.allowed-origin="+origin,"--processing.enabled=true","--processing.archive-root="+archive,"--processing.index-sha="+pin,"--processing.config-version="+processing.version(),"--processing.config-sha="+processing.sha256());source=app.getBean(javax.sql.DataSource.class);j=new org.springframework.jdbc.core.JdbcTemplate(source);}
 @Override void afterExternal(Map<String,Object> candidate,MinutesExternalPreparation preparation,ControlledMinutesClient client,JdbcExternalOperations operations){}
}
