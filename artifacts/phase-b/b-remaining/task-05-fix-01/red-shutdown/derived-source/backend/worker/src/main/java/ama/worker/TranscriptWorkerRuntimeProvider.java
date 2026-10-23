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
 private final TranscriptImportRuntime.Settings settings;private final String worker;private final int capacity;
 public TranscriptWorkerRuntimeProvider(DataSource source,FileBackendDefinitions definitions,ArchiveReadBudget budget,CanonicalBudget canonical,TranscriptImportRuntime.Settings settings,String worker,int capacity){
  this.source=Objects.requireNonNull(source);this.definitions=Objects.requireNonNull(definitions);this.budget=Objects.requireNonNull(budget);this.canonical=Objects.requireNonNull(canonical);this.settings=Objects.requireNonNull(settings);this.worker=worker;this.capacity=capacity;
  if(capacity<1||capacity>64)throw new IllegalArgumentException("Bounded worker capacity required");
 }
 @Override public WorkerRuntime create(){
  try{
   // Validate retained archive index before any poll, independent of the current processing default.
   settings.archives().get();
   var tx=new TransactionRunner(source);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);
   var catalog=new FileBackendCatalog(source,definitions.backends(),definitions.defaultId());catalog.configure();
   var validators=new HashMap<String,OutboxReferenceValidator>(new MeetingEventReferences(source).validators());
   new TranscriptEventReferences(source).validators().forEach((k,v)->{if(validators.putIfAbsent(k,v)!=null)throw new IllegalArgumentException("Duplicate outbox validator");});
   var outbox=new JdbcOutbox(source,validators);
   var components=JdbcTranscriptAuthority.create(source,tx,access,new TranscriptFilePurposePolicy(),catalog,Map.of(),budget,canonical);
   var queue=TranscriptImportRuntime.install(source,fences,outbox,components);
   var loop=WorkerLoop.contextual(queue,Map.of("TRANSCRIPT_IMPORT",capacity),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->TranscriptImportRuntime.prepare(queue,lease,settings,cancel)),worker);
   return new Runtime(loop,definitions);
  }catch(RuntimeException failure){try{definitions.close();}catch(Exception ignored){}throw failure;}
 }
 public static final class Runtime implements WorkerRuntime {
  private final WorkerLoop loop;private final FileBackendDefinitions definitions;private boolean closed;
  private Runtime(WorkerLoop loop,FileBackendDefinitions definitions){this.loop=loop;this.definitions=definitions;}
  public void start(){loop.start();}
  public WorkerLoop.Observation observation(){return loop.observation();}
  public synchronized void close(){if(closed)return;closed=true;loop.close();try{definitions.close();}catch(Exception failure){throw new IllegalStateException("Worker backend close failed");}}
 }
}
