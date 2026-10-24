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
  var response=call("POST","/meetings/"+meeting+"/final-confirmations",json.writeValueAsString(Map.of("expectedRevisionToken",Long.toString(revision()),"optionalReviewIds",List.of())),"task11-actual-final");
  Files.writeString(receipt.resolve("task11-final-response.json"),json.writeValueAsString(Map.of("status",response.statusCode(),"body",response.body())));
  assertEquals(201,response.statusCode(),response.body());
 }
 @Override void afterExternal(Map<String,Object> candidate,MinutesExternalPreparation preparation,ControlledMinutesClient client,JdbcExternalOperations operations){}
}
