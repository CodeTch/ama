package ama.localdev;
import ama.meeting.*;import ama.storage.*;import ama.task.*;import ama.transcript.*;import ama.worker.*;
import ama.minutes.ControlledMinutesDataSource;import ama.db.TransactionRunner;import ama.contract.TaskContracts.TaskLease;
import com.fasterxml.jackson.databind.ObjectMapper;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.core.env.*;

/** Qualification around the genuine WorkerLoop/session/handler; the final marker and POST use production code. */
public final class LocalPublicationWorkerRenewalCheck {
 record Phase(String name,TaskLease lease,LocalPublicationRenewalCheck.Barrier barrier){}
 public static void main(String[] args)throws Exception{
  if(!"1".equals(System.getenv("AMA_LOCAL_DEV"))||args.length!=2)throw new IllegalArgumentException("Explicit daily-local qualification required");
  Path config=Path.of(args[0]).toRealPath();if(!config.getParent().endsWith(Path.of(".tools/local-app")))throw new IllegalArgumentException("Daily-local configuration required");
  var properties=new Properties();try(var in=Files.newInputStream(config)){properties.load(in);}
  String url=properties.getProperty("database.url");if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_local_dev\\?.+"))throw new IllegalArgumentException("Dedicated local database required");
  var env=new StandardEnvironment();env.getPropertySources().addFirst(new PropertiesPropertySource("daily-local",properties));
  var source=new LocalPublicationRenewalCheck.TracedSource(ControlledMinutesDataSource.create(url,properties.getProperty("database.user"),properties.getProperty("database.password")));
  var j=new JdbcTemplate(source);j.setQueryTimeout(5);var json=new ObjectMapper();var fixture=json.readTree(Files.readString(Path.of(args[1])));
  String task=fixture.get("taskId").asText(),meeting=fixture.get("meetingId").asText(),publication=fixture.get("publicationId").asText();for(String value:List.of(task,meeting,publication))UUID.fromString(value);
  var settings=ConfiguredProcessing.read(env);var definitions=ConfiguredBackends.read(env);var tx=new TransactionRunner(source,30);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);
  var catalog=new FileBackendCatalog(source,definitions.backends(),definitions.defaultId());catalog.configure();var validators=new HashMap<String,OutboxReferenceValidator>(new MeetingEventReferences(source).validators());validators.putAll(new TranscriptEventReferences(source).validators());validators.putAll(new MinutesEventReferences(source).validators());var outbox=new JdbcOutbox(source,validators);
  var components=JdbcTranscriptAuthority.create(source,tx,access,new TranscriptFilePurposePolicy(),catalog,Map.of(),settings.read(),settings.canonical());var queue=TranscriptImportRuntime.install(source,fences,outbox,components);ConfiguredMinutes.read(env,settings.read(),settings.index()).preparation(components.authority());var publicationConfig=ConfiguredPublication.read(env,settings.read(),settings.index());
  var pending=j.queryForList("SELECT id FROM durable_task WHERE pool='KNOWLEDGE_PUBLISH' AND state IN ('READY','RUNNING','RECONCILING') ORDER BY id",String.class);if(!pending.equals(List.of(task)))throw new IllegalStateException("Only the qualification submission may be pending");
  var phases=new LinkedBlockingQueue<Phase>();var cases=Collections.synchronizedList(new ArrayList<Map<String,Object>>());var finished=new CountDownLatch(1);var failure=new AtomicReference<Throwable>();
  try(var client=publicationConfig.client()){
   var service=new JdbcPublicationService(components.authority(),publicationConfig.archives(),publicationConfig.pin(),publicationConfig.bounds(),null,queue,outbox,client);new JdbcPublicationLocalRecovery(components.authority(),publicationConfig.bounds(),queue,outbox);
   var operations=new JdbcExternalOperations(queue);var permits=new JdbcModelPermitPool(operations,Map.of(),(t,m,d)->{throw new IllegalStateException("No model cutoff proof");});var delegate=new KnowledgePublicationHandler(service,operations,permits);
   WorkerLoop.ExternalHandler handler=execution->{String original=Thread.currentThread().getName();Thread.currentThread().setName("ama-local-prepare");try{
    var lease=execution.lease();if(!task.equals(lease.taskId()))throw new IllegalStateException("Unexpected task");var initial=LocalPublicationRenewalCheck.effects(j,meeting,publication,task);
    for(String name:List.of("AFTER_DISCOVERY","AFTER_CAPTURE_COMMIT")){
     var gate=new LocalPublicationRenewalCheck.Barrier(name);source.barrier=gate;phases.put(new Phase(name,lease,gate));
     String outcome="PREPARED",code="NONE";try{if(service.prepare(lease)==null)throw new IllegalStateException("No prepared request");}catch(ama.contract.MinutesContracts.Failure f){outcome="REJECTED";code=f.code().name();}finally{source.barrier=null;gate.resume.countDown();}
     boolean unchanged=initial.equals(LocalPublicationRenewalCheck.effects(j,meeting,publication,task));cases.add(Map.of("phase",name,"outcome",outcome,"code",code,"zeroPreMarkerEffects",unchanged,"taskAttempt",lease.attempt()));
     if(!outcome.equals("PREPARED")||!unchanged)throw new IllegalStateException("Renewal preparation must succeed without effects");
    }
    // An actual production session, not a fabricated send token, now admits the original handler.
    delegate.handle(execution);
   }catch(Exception|Error cause){failure.set(cause);throw cause;}finally{source.barrier=null;Thread.currentThread().setName(original);finished.countDown();}};
   try(var loop=WorkerLoop.external(queue,operations,permits,Map.of("KNOWLEDGE_PUBLISH",1),Map.of("KNOWLEDGE_PUBLISH_V1",handler),Map.of(),"LOCAL_RENEWAL_WORKER_QUAL")){
    loop.start();
    for(int n=0;n<2;n++){
     var phase=phases.poll(25,TimeUnit.SECONDS);if(phase==null)throw new IllegalStateException("Qualification handler did not reach next phase");
     var gate=phase.barrier();if(!gate.arrived.await(15,TimeUnit.SECONDS)){gate.resume.countDown();throw new IllegalStateException("Exact qualification barrier was not reached");}
     var before=j.queryForMap("SELECT state,attempt,lease_owner,expires_at FROM durable_task WHERE id=?",task);boolean renewed=queue.renew(phase.lease());var after=j.queryForMap("SELECT state,attempt,lease_owner,expires_at FROM durable_task WHERE id=?",task);
     var old=new LinkedHashMap<>(before);var fresh=new LinkedHashMap<>(after);Object prior=old.remove("expires_at"),next=fresh.remove("expires_at");if(!renewed||Objects.equals(prior,next)||!old.equals(fresh)){gate.resume.countDown();throw new IllegalStateException("Exact same-lease-only renewal required");}gate.resume.countDown();
    }
    if(!finished.await(60,TimeUnit.SECONDS))throw new IllegalStateException("Production handler completion timeout");if(failure.get()!=null)throw new IllegalStateException("Production qualification handler failed: "+failure.get().getClass().getSimpleName());
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);String state;do{state=j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task);if(state.equals("SUCCEEDED"))break;Thread.sleep(100);}while(System.nanoTime()<deadline);
    long executions=j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Long.class,task),mappings=j.queryForObject("SELECT COUNT(*) FROM external_document_mapping WHERE publication_id=?",Long.class,publication);boolean pass=state.equals("SUCCEEDED")&&executions==1&&mappings==1;
    System.out.println(json.writeValueAsString(Map.of("pass",pass,"taskId",task,"meetingId",meeting,"publicationId",publication,"phases",List.copyOf(cases),"state",state,"externalExecutions",executions,"documentMappings",mappings,"genuineProductionWorkerSession",true)));
    if(!pass)throw new IllegalStateException("Original publication must have exactly one execution and document");
   }
  }finally{if(source.barrier!=null)source.barrier.resume.countDown();definitions.close();}
 }
}
