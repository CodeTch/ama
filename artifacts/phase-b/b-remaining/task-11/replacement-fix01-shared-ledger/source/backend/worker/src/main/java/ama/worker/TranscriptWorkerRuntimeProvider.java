package ama.worker;
import ama.config.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.MinutesContracts.CanonicalBudget;
import ama.db.TransactionRunner;
import ama.meeting.*;
import ama.storage.*;
import ama.task.*;
import ama.transcript.*;
import javax.sql.DataSource;
import java.util.*;
/** Explicit TXT-only provider. Every graph collaborator in this process shares the one assembly. */
public final class TranscriptWorkerRuntimeProvider implements WorkerRuntimeProvider {
 private final DataSource source;private final FileBackendDefinitions definitions;private final ArchiveReadBudget budget;private final CanonicalBudget canonical;
 private final TranscriptImportRuntime.Settings settings;private final String worker;private final int capacity;private final ConfiguredMinutes minutes;private final ConfiguredPublication publication;
 public TranscriptWorkerRuntimeProvider(DataSource source,FileBackendDefinitions definitions,ArchiveReadBudget budget,CanonicalBudget canonical,TranscriptImportRuntime.Settings settings,String worker,int capacity){
  this(source,definitions,budget,canonical,settings,worker,capacity,null);
 }
 public TranscriptWorkerRuntimeProvider(DataSource source,FileBackendDefinitions definitions,ArchiveReadBudget budget,CanonicalBudget canonical,TranscriptImportRuntime.Settings settings,String worker,int capacity,ConfiguredMinutes minutes){
  this(source,definitions,budget,canonical,settings,worker,capacity,minutes,null);
 }
 public TranscriptWorkerRuntimeProvider(DataSource source,FileBackendDefinitions definitions,ArchiveReadBudget budget,CanonicalBudget canonical,TranscriptImportRuntime.Settings settings,String worker,int capacity,ConfiguredMinutes minutes,ConfiguredPublication publication){
  this.minutes=minutes;this.publication=publication;
  this.source=Objects.requireNonNull(source);this.definitions=Objects.requireNonNull(definitions);this.budget=Objects.requireNonNull(budget);this.canonical=Objects.requireNonNull(canonical);this.settings=Objects.requireNonNull(settings);this.worker=worker;this.capacity=capacity;
  if(capacity<1||capacity>64)throw new IllegalArgumentException("Bounded worker capacity required");
 }
 @Override public WorkerRuntime create(){
  ama.model.ControlledMinutesClient minutesClient=null;ControlledKnowledgeClient knowledgeClient=null;WorkerLoop loop=null,external=null;ReconciliationLoop reconciliation=null;
  try{
   // Validate retained archive index before any poll, independent of the current processing default.
   settings.archives().get();
   var tx=new TransactionRunner(source,minutes==null&&publication==null?-1:30);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);
   var catalog=new FileBackendCatalog(source,definitions.backends(),definitions.defaultId());catalog.configure();
   var validators=new HashMap<String,OutboxReferenceValidator>(new MeetingEventReferences(source).validators());
   new TranscriptEventReferences(source).validators().forEach((k,v)->{if(validators.putIfAbsent(k,v)!=null)throw new IllegalArgumentException("Duplicate outbox validator");});
   new MinutesEventReferences(source).validators().forEach((k,v)->{if(validators.putIfAbsent(k,v)!=null)throw new IllegalArgumentException("Duplicate outbox validator");});
   var outbox=new JdbcOutbox(source,validators);
   var components=JdbcTranscriptAuthority.create(source,tx,access,new TranscriptFilePurposePolicy(),catalog,Map.of(),budget,canonical);
   var queue=TranscriptImportRuntime.install(source,fences,outbox,components);
   loop=WorkerLoop.contextual(queue,Map.of("TRANSCRIPT_IMPORT",capacity),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->TranscriptImportRuntime.prepare(queue,lease,settings,cancel)),worker);
   if(minutes!=null||publication!=null){
    var operations=new JdbcExternalOperations(queue);var permits=new JdbcModelPermitPool(operations,minutes==null?Map.of():Map.of(ama.model.ControlledMinutesProtocol.MODEL,minutes.modelConfiguration()),(t,m,d)->{throw new IllegalStateException("No controlled cutoff proof");});
    var handlers=new HashMap<String,WorkerLoop.ExternalHandler>();var pools=new HashMap<String,Integer>();var models=new HashMap<String,String>();MinutesReconciliationHandler minutesRecovery=null;KnowledgeReconciliationHandler knowledgeRecovery=null;
    if(minutes!=null){minutes.archives().get();minutesClient=minutes.client();var preparation=new MinutesExternalPreparation(components.authority(),minutes.preparation(components.authority()),minutesClient);minutesRecovery=new MinutesReconciliationHandler(preparation,minutesClient,operations,permits);if(publication!=null){handlers.put("MINUTES_GENERATE_V1",new MinutesGenerationHandler(preparation,minutesClient,operations,permits));pools.put("MINUTES_GENERATE",capacity);models.put("MINUTES_GENERATE_V1",ama.model.ControlledMinutesProtocol.MODEL);}}
    if(publication!=null){publication.archives().get();knowledgeClient=publication.client();var service=new JdbcPublicationService(components.authority(),publication.archives(),publication.pin(),publication.bounds(),null,queue,outbox,knowledgeClient);handlers.put("KNOWLEDGE_PUBLISH_V1",new KnowledgePublicationHandler(service,operations,permits));pools.put("KNOWLEDGE_PUBLISH",publication.capacity());knowledgeRecovery=new KnowledgeReconciliationHandler(service,operations,permits);}
    if(!handlers.isEmpty())external=WorkerLoop.external(queue,operations,permits,pools,handlers,models,worker);
    final var minutesHandler=minutesRecovery;final var knowledgeHandler=knowledgeRecovery;var jdbc=new org.springframework.jdbc.core.JdbcTemplate(source);jdbc.setQueryTimeout(2);
    ReconciliationLoop.PreparedHandler dispatcher=session->{var actual=(ExternalExecutionSession)session;var token=actual.token();String kind=jdbc.queryForObject("SELECT o.operation_kind FROM external_operation o JOIN external_execution e ON e.operation_id=o.id WHERE o.id=? AND e.id=? AND e.execution_no=?",String.class,token.operationId(),token.executionId(),token.executionNo());if("KNOWLEDGE_PUBLISH".equals(kind)&&knowledgeHandler!=null)knowledgeHandler.reconcile(session);else if("MINUTES_GENERATE".equals(kind)&&minutesHandler!=null)minutesHandler.reconcile(session);else throw new IllegalStateException("No installed recovery handler for recorded operation");};
    int recoveryCapacity=publication==null?minutes.reconciliationCapacity():publication.reconciliationCapacity();reconciliation=publication==null?ReconciliationLoop.prepared(operations,permits,recoveryCapacity,dispatcher,worker):ReconciliationLoop.preparedWithPublicationMaintenance(operations,permits,recoveryCapacity,dispatcher,worker,new JdbcPublicationLocalRecovery(components.authority(),publication.bounds(),queue,outbox));
   }
   return new Runtime(loop,external,reconciliation,minutesClient,knowledgeClient,definitions);
  }catch(RuntimeException|Error failure){for(AutoCloseable resource:Arrays.asList(external,reconciliation,loop)){if(resource!=null)try{resource.close();}catch(Exception|Error close){if(close!=failure)failure.addSuppressed(close);}}if(minutesClient!=null)minutesClient.close();if(knowledgeClient!=null)knowledgeClient.close();try{definitions.close();}catch(Exception ignored){}throw failure;}
 }
 public static final class Runtime implements WorkerRuntime {
  private final WorkerLoop loop,external;private final ControlledKnowledgeClient knowledgeClient;private final ReconciliationLoop reconciliation;private final ama.model.ControlledMinutesClient client;private final FileBackendDefinitions definitions;private boolean closed;
  private Runtime(WorkerLoop loop,WorkerLoop external,ReconciliationLoop reconciliation,ama.model.ControlledMinutesClient client,ControlledKnowledgeClient knowledgeClient,FileBackendDefinitions definitions){this.loop=loop;this.external=external;this.reconciliation=reconciliation;this.client=client;this.knowledgeClient=knowledgeClient;this.definitions=definitions;}
  public void start(){loop.start();if(external!=null)external.start();if(reconciliation!=null)reconciliation.start();}
  public WorkerLoop.Observation observation(){return loop.observation();}
  public synchronized void close(){
   if(closed)return;closed=true;
   Throwable failure=null;
   try{if(external!=null)external.close();}catch(RuntimeException|Error error){failure=error;}
   try{loop.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else failure.addSuppressed(error);}
   try{if(reconciliation!=null)reconciliation.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else failure.addSuppressed(error);}
   try{if(client!=null)client.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else failure.addSuppressed(error);}
   try{if(knowledgeClient!=null)knowledgeClient.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else failure.addSuppressed(error);}
   try{definitions.close();}catch(Exception|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
   if(failure instanceof RuntimeException error)throw error;
   if(failure instanceof Error error)throw error;
   if(failure!=null)throw new IllegalStateException("Worker backend close failed",failure);
  }
 }
}
