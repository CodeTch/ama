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
 private final TranscriptImportRuntime.Settings settings;private final String worker;private final int capacity;private final ConfiguredMinutes minutes;
 public TranscriptWorkerRuntimeProvider(DataSource source,FileBackendDefinitions definitions,ArchiveReadBudget budget,CanonicalBudget canonical,TranscriptImportRuntime.Settings settings,String worker,int capacity){
  this(source,definitions,budget,canonical,settings,worker,capacity,null);
 }
 public TranscriptWorkerRuntimeProvider(DataSource source,FileBackendDefinitions definitions,ArchiveReadBudget budget,CanonicalBudget canonical,TranscriptImportRuntime.Settings settings,String worker,int capacity,ConfiguredMinutes minutes){
  this.minutes=minutes;
  this.source=Objects.requireNonNull(source);this.definitions=Objects.requireNonNull(definitions);this.budget=Objects.requireNonNull(budget);this.canonical=Objects.requireNonNull(canonical);this.settings=Objects.requireNonNull(settings);this.worker=worker;this.capacity=capacity;
  if(capacity<1||capacity>64)throw new IllegalArgumentException("Bounded worker capacity required");
 }
 @Override public WorkerRuntime create(){
  ama.model.ControlledMinutesClient minutesClient=null;
  try{
   // Validate retained archive index before any poll, independent of the current processing default.
   settings.archives().get();
   var tx=new TransactionRunner(source,minutes==null?-1:30);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);
   var catalog=new FileBackendCatalog(source,definitions.backends(),definitions.defaultId());catalog.configure();
   var validators=new HashMap<String,OutboxReferenceValidator>(new MeetingEventReferences(source).validators());
   new TranscriptEventReferences(source).validators().forEach((k,v)->{if(validators.putIfAbsent(k,v)!=null)throw new IllegalArgumentException("Duplicate outbox validator");});
   new MinutesEventReferences(source).validators().forEach((k,v)->{if(validators.putIfAbsent(k,v)!=null)throw new IllegalArgumentException("Duplicate outbox validator");});
   var outbox=new JdbcOutbox(source,validators);
   var components=JdbcTranscriptAuthority.create(source,tx,access,new TranscriptFilePurposePolicy(),catalog,Map.of(),budget,canonical);
   var queue=TranscriptImportRuntime.install(source,fences,outbox,components);
   var loop=WorkerLoop.contextual(queue,Map.of("TRANSCRIPT_IMPORT",capacity),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->TranscriptImportRuntime.prepare(queue,lease,settings,cancel)),worker);
   ReconciliationLoop reconciliation=null;
   if(minutes!=null){
    minutes.archives().get();minutesClient=minutes.client();var operations=new JdbcExternalOperations(queue);var permits=new JdbcModelPermitPool(operations,Map.of(ama.model.ControlledMinutesProtocol.MODEL,minutes.modelConfiguration()),(t,m,d)->{throw new IllegalStateException("No controlled cutoff proof");});
    var preparation=new MinutesExternalPreparation(components.authority(),minutes.preparation(components.authority()),minutesClient);
    reconciliation=ReconciliationLoop.prepared(operations,permits,minutes.reconciliationCapacity(),new MinutesReconciliationHandler(preparation,minutesClient,operations,permits),worker);
   }
   return new Runtime(loop,reconciliation,minutesClient,definitions);
  }catch(RuntimeException failure){if(minutesClient!=null)minutesClient.close();try{definitions.close();}catch(Exception ignored){}throw failure;}
 }
 public static final class Runtime implements WorkerRuntime {
  private final WorkerLoop loop;private final ReconciliationLoop reconciliation;private final ama.model.ControlledMinutesClient client;private final FileBackendDefinitions definitions;private boolean closed;
  private Runtime(WorkerLoop loop,ReconciliationLoop reconciliation,ama.model.ControlledMinutesClient client,FileBackendDefinitions definitions){this.loop=loop;this.reconciliation=reconciliation;this.client=client;this.definitions=definitions;}
  public void start(){loop.start();if(reconciliation!=null)reconciliation.start();}
  public WorkerLoop.Observation observation(){return loop.observation();}
  public synchronized void close(){
   if(closed)return;closed=true;
   Throwable failure=null;
   try{loop.close();}catch(RuntimeException|Error error){failure=error;}
   try{if(reconciliation!=null)reconciliation.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else failure.addSuppressed(error);}
   try{if(client!=null)client.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else failure.addSuppressed(error);}
   try{definitions.close();}catch(Exception|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
   if(failure instanceof RuntimeException error)throw error;
   if(failure instanceof Error error)throw error;
   if(failure!=null)throw new IllegalStateException("Worker backend close failed",failure);
  }
 }
}
