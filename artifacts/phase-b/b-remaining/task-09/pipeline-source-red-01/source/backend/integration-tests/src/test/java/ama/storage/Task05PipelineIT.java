package ama.storage;
import ama.api.ApiApplication;
import ama.config.*;
import ama.config.file.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.support.MysqlTestDatabase;
import ama.transcript.TranscriptFilePurposePolicy;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ama.transcript.ArchiveProcessingFixture.B;
class Task05PipelineIT {
 ama.worker.WorkerHealthServer health;final java.util.concurrent.atomic.AtomicReference<ama.worker.WorkerLoop> healthLoop=new java.util.concurrent.atomic.AtomicReference<>();
 Task04ProbeSource probe;ConfigurableApplicationContext app;JdbcTemplate j;javax.sql.DataSource source;
 final ObjectMapper json=new ObjectMapper();String owner,token,meeting;final String csrf="T".repeat(43),origin="https://task05.example.invalid";
 ama.storage.s3.S3Fixture s3Fixture;boolean s3Mode;Path receipt;Task05NativeFixture nativeFixture;FileBackendDefinitions definitions;Path archive;String pin;Ref processing;Map<String,Object> bounds;
 static final Path ROOT=Path.of(System.getenv("AMA_TASK05_ROOT"));
 @BeforeEach void start(TestInfo info)throws Exception {
  probe=new Task04ProbeSource(MysqlTestDatabase.dataSource());source=probe;j=new JdbcTemplate(source);
  assertEquals(13,j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1",Integer.class));
  Path publicRoot=Path.of("artifacts/phase-b/b-remaining/task-05").toAbsolutePath();
  while(!Files.exists(publicRoot.getParent()))publicRoot=Path.of("../../artifacts/phase-b/b-remaining/task-05").toAbsolutePath().normalize();
  receipt=Files.createDirectory(publicRoot.resolve("pipeline-"+UUID.randomUUID()));
  Path durable=ROOT.resolve("native/data");if(!Files.exists(durable))Files.createDirectory(durable);
  nativeFixture=new Task05NativeFixture(durable.resolve("case-"+UUID.randomUUID()));
  var storage=nativeFixture.startProcess();
  s3Mode=info.getTestMethod().orElseThrow().getName().contains("S3");
  if(s3Mode){
   s3Fixture=new ama.storage.s3.S3Fixture("task05-infrastructure");var c=s3Fixture.config;
   var binding=new ManagedBackendId.S3(c.endpoint().toString(),c.region(),c.dataBucket(),c.controlBucket(),c.pathStyle(),c.keyBudget());
   definitions=new FileBackendDefinitions(List.of(ManagedBackendAdapters.s3(ama.storage.s3.S3Fixture.config(binding.id(),c.endpoint(),c.dataBucket(),c.controlBucket()),1,"WRITABLE")),binding.id());
  }else definitions=new FileBackendDefinitions(List.of(new FileBackendCatalog.Backend(nativeFixture.binding.id(),"local",1,"WRITABLE",storage,nativeFixture.binding)),nativeFixture.binding.id());
  var actualBackend=definitions.backends().getFirst();var actualStorage=actualBackend.storage();
  FileStoragePort observed=(FileStoragePort)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),actualStorage instanceof AutoCloseable?new Class[]{FileStoragePort.class,AutoCloseable.class}:new Class[]{FileStoragePort.class},(proxy,method,args)->{
   if(Set.of("read","stat").contains(method.getName()))assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),"Actual source I/O under transaction");
   Object result;try{result=method.invoke(actualStorage,args);}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
   if(result instanceof java.io.InputStream stream)return new java.io.FilterInputStream(stream){public int read()throws java.io.IOException{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return super.read();}public int read(byte[] b,int o,int n)throws java.io.IOException{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return in.read(b,o,n);}};
   return result;
  });
  definitions=new FileBackendDefinitions(List.of(new FileBackendCatalog.Backend(actualBackend.id(),actualBackend.type(),actualBackend.revision(),actualBackend.status(),observed,actualBackend.definition())),actualBackend.id());
  archive=Files.createDirectory(ROOT.resolve("runtime/archive-"+UUID.randomUUID()));
  LocalFixture.privateMode(archive,true);LocalFixture.privateMode(durable,true);
  var raw=new ArrayList<RawArchive>();
  for(String name:List.of("codec","confirmation","parser","processing1")){
   var type=switch(name){case "codec"->ArtifactType.CANONICAL_CODEC;case "confirmation"->ArtifactType.CONFIRMATION_HASH;case "parser"->ArtifactType.PARSER_CONFIG;default->ArtifactType.PROCESSING_CONFIG;};
   String version=switch(name){case "codec"->"ama.canonical-value.v1";case "confirmation"->"ama.transcript-confirmation.v1";case "parser"->"ama.strict-utf8-lines.v1";default->"b06.controlled.import-txt.1";};
   try(var in=getClass().getResourceAsStream("/ama/transcript/restart/"+name+".json")){raw.add(new RawArchive(type,version,in.readAllBytes()));}
  }
  var ib=new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128);pin=new ControlledArchiveReleaseWriter().publish(archive,null,raw,ib,B,()->false);
  processing=new ControlledConfigArchiveSource().open(archive,pin,ib,B,()->false).index().entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(x->x.artifactType()==ArtifactType.PROCESSING_CONFIG).findFirst().orElseThrow();
  bounds=new HashMap<>();String[] names={"artifact-bytes","archive-total-bytes","artifacts","dependencies","graph-depth","json-tokens","json-depth","name-utf16","string-utf16","string-utf8","aggregate-scalars","manifest-bytes","batch-files","fact-rows","fact-edges","canonical-bytes","canonical-depth","canonical-nodes","canonical-string-utf8","canonical-scalars","index-bytes","index-entries","release-bytes","index-generations","total-source-bytes"};
  long[] values={65536,1048576,16,32,8,200000,32,255,65536,262144,1048576,65536,32,250000,500000,16777216,32,1000000,2097152,250000,4194304,4096,67108864,128,1048576};
  for(int n=0;n<names.length;n++)bounds.put("processing.bounds."+names[n],values[n]);
  if(info.getTestMethod().orElseThrow().getName().contains("Health")){
   health=new ama.worker.WorkerHealthServer(new InetSocketAddress("127.0.0.1",0),Task05TransportIT.tls("server"),"spiffe://ama/writer",()->{var loop=healthLoop.get();return loop==null?new ama.worker.WorkerLoop.Observation("STARTING",Long.MAX_VALUE,0):loop.observation();});health.start();
   String password=Files.readString(ROOT.resolve("pki/keystore-password"));java.security.cert.Certificate cert;try(var in=Files.newInputStream(ROOT.resolve("pki/server.crt"))){cert=java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(in);}
   bounds.put("processing.worker.health-url","https://localhost:"+health.port());bounds.put("processing.worker.key-store",ROOT.resolve("pki/writer.p12").toString());bounds.put("processing.worker.trust-store",ROOT.resolve("pki/trust.p12").toString());bounds.put("processing.worker.key-store-password",password);bounds.put("processing.worker.trust-store-password",password);bounds.put("processing.worker.server-cert-sha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(cert.getEncoded())));bounds.put("processing.worker.max-age-ms",10000);
  }
  app=new SpringApplicationBuilder(ApiApplication.class).properties(bounds).initializers(context->{var c=(GenericApplicationContext)context;c.registerBean("task05Source",javax.sql.DataSource.class,()->source);c.registerBean("task05Backends",FileBackendDefinitions.class,()->definitions);}).run("--server.address=127.0.0.1","--server.port=0","--management.server.port=0","--logging.level.root=ERROR","--identity.allowed-origin="+origin,"--processing.enabled=true","--processing.archive-root="+archive,"--processing.index-sha="+pin,"--processing.config-version="+processing.version(),"--processing.config-sha="+processing.sha256());
  owner="task05-"+UUID.randomUUID();token=identity(owner);var created=call("POST","/meetings","{}","create");assertEquals(201,created.statusCode(),created.body());meeting=body(created).get("id").asText();
  Files.writeString(receipt.resolve("resource.json"),json.writeValueAsString(Map.of("nativePid",nativeFixture.process.pid(),"nativePort",nativeFixture.port,"managedId",definitions.backends().getFirst().id(),"nativePath",nativeFixture.base.root.toString(),"archive",archive.toString(),"pin",pin,"databaseNow",j.queryForObject("SELECT UTC_TIMESTAMP(6)",String.class),"jvmNow",java.time.Instant.now().toString(),"maxMemory",Runtime.getRuntime().maxMemory(),"owner",owner)));
 }
 ama.task.TranscriptImportRuntime.Settings settings(){return new ama.task.TranscriptImportRuntime.Settings(()->new ControlledConfigArchiveSource().open(archive,pin,new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128),B,()->false),B,new ama.transcript.TranscriptDocumentParser.ParseBudget(1048576,1048576,4096),java.time.Duration.ofSeconds(60));}
 @Test void actualNativeHttpIntakeWorkerAndRaw()throws Exception {
  var first=upload("\ufeffAlpha😀\r\n \r\nBeta");var second=upload("Gamma\nDelta");
  var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(first,second))),"import");
  assertEquals(202,accepted.statusCode(),accepted.body());String task=body(accepted).get("taskId").asText();Files.writeString(receipt.resolve("accepted-"+task+".json"),json.writeValueAsString(Map.of("task",task,"owner",owner,"meeting",meeting,"httpStatus",accepted.statusCode())));
  assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
  var queue=app.getBean(ama.task.JdbcTaskQueue.class);
  try(var loop=ama.worker.WorkerLoop.contextual(queue,Map.of("TRANSCRIPT_IMPORT",1),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->ama.task.TranscriptImportRuntime.prepare(queue,lease,settings(),cancel)),"TASK05_ACTUAL")){
   loop.start();long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
   while(System.nanoTime()<end&&List.of("READY","RUNNING").contains(j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task)))Thread.sleep(50);
  }
  var actual=j.queryForMap("SELECT state,output_kind,output_id,last_error,business_version FROM durable_task WHERE id=?",task);Files.writeString(receipt.resolve("task.json"),json.writeValueAsString(actual));
  assertEquals("SUCCEEDED",actual.get("state"),actual.toString());String raw=(String)actual.get("output_id");
  assertEquals("TRANSCRIPT_REVISION",actual.get("output_kind"));assertEquals(List.of("Alpha😀","Beta","Gamma","Delta"),j.queryForList("SELECT body FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw));
  assertEquals(List.of(1L,3L,1L,2L),j.queryForList("SELECT source_paragraph FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",Long.class,raw));
  assertEquals(((Number)actual.get("business_version")).longValue(),j.queryForObject("SELECT revision FROM transcript_revision WHERE id=?",Long.class,raw));
  assertNull(j.queryForObject("SELECT transcript_revision_id FROM meeting WHERE id=?",String.class,meeting));
 }
 @Test void actualS3HttpIntakeWorkerAndRaw()throws Exception {actualNativeHttpIntakeWorkerAndRaw();}
 @Test void freshJvmConsumesOriginalReadyAfterDefaultsChange()throws Exception {restartProof();}
 @Test void freshJvmS3ConsumesOriginalReadyAfterDefaultsChange()throws Exception {restartProof();}
 static final ama.task.PreparationCancellation NEVER=new ama.task.PreparationCancellation(){public boolean cancelled(){return false;}public AutoCloseable register(Runnable abort){return ()->{};}};
 void newMeeting()throws Exception {var created=call("POST","/meetings","{}",UUID.randomUUID().toString());assertEquals(201,created.statusCode(),created.body());meeting=body(created).get("id").asText();}
 String accept(List<String> files)throws Exception {var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",files)),UUID.randomUUID().toString());assertEquals(202,accepted.statusCode(),accepted.body());String acceptedTask=body(accepted).get("taskId").asText();Files.writeString(receipt.resolve("accepted-"+acceptedTask+".json"),json.writeValueAsString(Map.of("task",acceptedTask,"owner",owner,"meeting",meeting,"files",files)));return acceptedTask;}
 ama.contract.TaskContracts.TaskLease claim(String task){var lease=app.getBean(ama.task.JdbcTaskQueue.class).claim("TRANSCRIPT_IMPORT","TASK05_BOUNDARY").orElseThrow();assertEquals(task,lease.taskId());return lease;}
 @Test void actualCarrierRejectsDirectForeignAndOrdinaryCompletionThenWritesOnce()throws Exception {
  String task=accept(List.of(upload("First\nSecond")));var queue=app.getBean(ama.task.JdbcTaskQueue.class);var lease=claim(task);
  Task04HashObserver.held=()->probe.rootsHeld.contains(Thread.currentThread());Task04HashObserver.inside.set(0);Task04HashObserver.outside.set(0);String observer="Task05Hash"+UUID.randomUUID();java.security.Security.insertProviderAt(new Task04HashObserver(observer),1);
  var carrier=ama.task.TranscriptImportRuntime.prepare(queue,lease,settings(),NEVER);assertThrows(IllegalStateException.class,carrier::get);
  app.getBean(ama.db.TransactionRunner.class).required(()->{assertThrows(IllegalStateException.class,carrier::get);return null;});
  var success=new ama.contract.TaskContracts.TaskResult(ama.contract.TaskContracts.Outcome.SUCCEEDED,new ama.contract.TaskContracts.TypedRef("TRANSCRIPT_REVISION",UUID.randomUUID().toString()),null);
  assertThrows(RuntimeException.class,()->queue.complete(lease,success));assertThrows(RuntimeException.class,()->queue.completeWithEffects(lease,()->success));
  var failed=new ama.contract.TaskContracts.TaskResult(ama.contract.TaskContracts.Outcome.FAILED,null,"CONTROLLED_FAILURE");var legacyCalls=new java.util.concurrent.atomic.AtomicInteger();
  assertThrows(RuntimeException.class,()->queue.complete(lease,failed));assertThrows(RuntimeException.class,()->queue.completeWithEffects(lease,failed,legacyCalls::incrementAndGet));assertEquals(0,legacyCalls.get());
  var components=ama.api.Task05RuntimeProbe.components(app);var foreign=new Task04ProbeSource(source);
  assertThrows(IllegalArgumentException.class,()->ama.task.TranscriptImportRuntime.install(foreign,new ama.task.JdbcSubjectFences(foreign,Set.of("MEETING")),new ama.task.JdbcOutbox(foreign,Map.of()),components));
  var oldLease=new ama.contract.TaskContracts.TaskLease(lease.taskId(),lease.attempt(),"FOREIGN",lease.expiresAt(),lease.spec());assertThrows(RuntimeException.class,()->components.authority().readWorkerFacts(oldLease,()->false));
  assertTrue(queue.completeWithEffects(lease,carrier));assertFalse(queue.completeWithEffects(lease,carrier));assertThrows(IllegalStateException.class,carrier::get);
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE producer_task_id=?",Integer.class,task));
  java.security.Security.removeProvider(observer);assertEquals(0,Task04HashObserver.inside.get());assertTrue(Task04HashObserver.outside.get()>0);Files.writeString(receipt.resolve("hash-boundary.json"),json.writeValueAsString(Map.of("insideRoots",Task04HashObserver.inside.get(),"outsideRoots",Task04HashObserver.outside.get())));Task04HashObserver.held=()->false;
 }
 @Test void everyAtomicFailureRollsBackRawSegmentsTaskAttemptAuditAndOutbox()throws Exception {
  for(String point:List.of("RAW","SEGMENT","VALIDATOR","AUDIT","ATTEMPT","OUTBOX","COMMIT")){
   newMeeting();String task=accept(List.of(upload("First\nSecond")));var queue=app.getBean(ama.task.JdbcTaskQueue.class);var lease=claim(task);var carrier=ama.task.TranscriptImportRuntime.prepare(queue,lease,settings(),NEVER);
   int events=j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE task_id=?",Integer.class,task);var counter=new java.util.concurrent.atomic.AtomicInteger();
   if(point.equals("SEGMENT"))probe.before=sql->{if(sql.startsWith("INSERT INTO transcript_segment(")&&counter.incrementAndGet()==2)throw new IllegalStateException("CONTROLLED_MID_SEGMENT");};
   else if(point.equals("COMMIT"))probe.rollbackOn.set("INSERT INTO transcript_revision");
   else probe.failStatement.set(switch(point){case "RAW"->"INSERT INTO transcript_revision";case "VALIDATOR"->"SELECT segment_id,ordinal,OCTET_LENGTH";case "AUDIT"->"INSERT INTO audit_event";case "ATTEMPT"->"UPDATE task_attempt SET ended_at";default->"INSERT INTO task_outbox";});
   assertThrows(RuntimeException.class,()->queue.completeWithEffects(lease,carrier),point);probe.before=sql->{};probe.failStatement.set(null);
   assertEquals("RUNNING",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task),point);assertNull(j.queryForObject("SELECT ended_at FROM task_attempt WHERE task_id=? AND attempt=?",Object.class,task,lease.attempt()));
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE producer_task_id=?",Integer.class,task),point);
   assertEquals(events,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE task_id=?",Integer.class,task),point);
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='TRANSCRIPT_IMPORTED'",Integer.class,meeting),point);
   assertTrue(queue.completeWithEffects(lease,carrier),point);Files.writeString(receipt.resolve("rollback-"+point+".txt"),"task="+task+" attempt="+lease.attempt()+" actual fault rollback verified then same original lease completed\n");
  }
 }
 @Test void aggregateCountsAllDecodedUnitsAcrossOrderedFilesWithoutPartialRaw()throws Exception {
  String task=accept(List.of(upload("A\n\n"),upload("B\r\n")));var queue=app.getBean(ama.task.JdbcTaskQueue.class);var lease=claim(task);var normal=settings();
  var tight=new ama.task.TranscriptImportRuntime.Settings(normal.archives(),normal.archiveBudget(),new ama.transcript.TranscriptDocumentParser.ParseBudget(1048576,5,4096),normal.batchDeadline());
  var carrier=ama.task.TranscriptImportRuntime.prepare(queue,lease,tight,NEVER);assertTrue(queue.completeWithEffects(lease,carrier));
  assertEquals("FAILED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE producer_task_id=?",Integer.class,task));
 }
 @Test void adoptedAndStaleEmptyPreparationsCannotAuthorizeCompletion()throws Exception {
  String file=upload("Adopted");var c=ama.api.Task05RuntimeProbe.components(app);var queue=app.getBean(ama.task.JdbcTaskQueue.class);var tx=app.getBean(ama.db.TransactionRunner.class);var fences=app.getBean(ama.task.JdbcSubjectFences.class);
  var adopted=new java.util.concurrent.atomic.AtomicReference<ama.task.TaskDomainPolicy.PreparedDomain>();
  ama.task.TaskQueue intercept=new ama.task.TaskQueue(){
   public String enqueue(ama.contract.TaskContracts.TaskSpec spec,ama.task.TaskDomainPolicy.PreparedDomain held){adopted.set(held);assertThrows(RuntimeException.class,()->c.authority().requireImportCompletion(List.of(spec),held));return queue.enqueue(spec,held);}
   public String enqueue(ama.contract.TaskContracts.TaskSpec spec){throw new AssertionError();}
   public Optional<ama.contract.TaskContracts.TaskLease> claim(String a,String b){throw new AssertionError();}public boolean renew(ama.contract.TaskContracts.TaskLease l){throw new AssertionError();}
   public boolean complete(ama.contract.TaskContracts.TaskLease l,ama.contract.TaskContracts.TaskResult x){throw new AssertionError();}public boolean completeWithEffects(ama.contract.TaskContracts.TaskLease l,ama.contract.TaskContracts.TaskResult x,Runnable effects){throw new AssertionError();}
  };
  var intake=new ama.transcript.JdbcProcessingIntake(source,tx,app.getBean(ama.access.MeetingAccess.class),c,fences,intercept,app.getBean(ama.task.JdbcOutbox.class),new ama.audit.JdbcAuditAdapter(source),app.getBean(ama.transcript.JdbcProcessingIntake.NewIntake.class));
  var actor=new ama.contract.IdentityContracts.Actor(owner,ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
  var accepted=intake.accept(actor,meeting,UUID.randomUUID().toString(),new ama.contract.ProcessingContracts.ImportRequest(0,List.of(file)),UUID.randomUUID().toString());
  Files.writeString(receipt.resolve("accepted-"+accepted.taskId()+".json"),json.writeValueAsString(Map.of("task",accepted.taskId(),"owner",owner,"meeting",meeting,"source","actual production intake decorator")));
  var lease=claim(accepted.taskId());var specs=List.of(lease.spec());var policy=c.authority().taskPolicy();var retained=new java.util.concurrent.atomic.AtomicReference<ama.task.TaskDomainPolicy.PreparedDomain>();
  tx.required(()->{var prepared=policy.prepare(specs,ama.task.TaskDomainPolicy.Acquisition.TASK_MUTATION,false).orElseThrow();fences.lock(lease.spec().subject(),false).orElseThrow();assertTrue(policy.lockRoots(specs,false,prepared));retained.set(prepared);int count=probe.statements.size();c.authority().requireImportCompletion(specs,prepared);assertEquals(count,probe.statements.size(),"Held verifier must acquire no later lock/read");
   var nested=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));nested.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);nested.execute(status->{assertThrows(RuntimeException.class,()->c.authority().requireImportCompletion(specs,prepared));return null;});return null;});
  assertThrows(RuntimeException.class,()->c.authority().requireImportCompletion(specs,retained.get()));tx.required(()->{assertThrows(RuntimeException.class,()->c.authority().requireImportCompletion(specs,retained.get()));assertThrows(RuntimeException.class,()->c.authority().requireImportCompletion(specs,adopted.get()));return null;});
  tx.required(()->{var subject=fences.lock(lease.spec().subject(),false).orElseThrow();j.update("UPDATE meeting SET revision=revision+1 WHERE id=?",meeting);fences.advance(subject,lease.spec().businessVersion()+1,lease.spec().cancelEpoch(),false);return null;});
  tx.required(()->{var prepared=policy.prepare(specs,ama.task.TaskDomainPolicy.Acquisition.TASK_MUTATION,false).orElseThrow();fences.lock(lease.spec().subject(),false).orElseThrow();assertTrue(policy.lockRoots(specs,false,prepared));assertThrows(RuntimeException.class,()->c.authority().requireImportCompletion(specs,prepared));return null;});
  j.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",lease.taskId());assertTrue(queue.claim("TRANSCRIPT_IMPORT","TASK05_STALE").isEmpty());assertEquals("STALE",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,lease.taskId()));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE producer_task_id=?",Integer.class,lease.taskId()));
 }
 @Test void actualHealthProjectionTracksInstalledImportLoop()throws Exception {
  String endpoint="/meetings/"+meeting+"/processing-capabilities";var initial=body(call("GET",endpoint,"",null));assertEquals("UNAVAILABLE",initial.get("execution").asText());assertFalse(initial.get("businessReady").asBoolean());
  var queue=app.getBean(ama.task.JdbcTaskQueue.class);
  try(var loop=ama.worker.WorkerLoop.contextual(queue,Map.of("TRANSCRIPT_IMPORT",1),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->ama.task.TranscriptImportRuntime.prepare(queue,lease,settings(),cancel)),"TASK05_HEALTH_ACTUAL")){
   healthLoop.set(loop);loop.start();long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);while(loop.observation().state().equals("STARTING")&&System.nanoTime()<end)Thread.sleep(10);
   var live=body(call("GET",endpoint,"",null));assertEquals("INSTALLED",live.get("execution").asText());assertTrue(live.get("businessReady").asBoolean());loop.close();var stopped=body(call("GET",endpoint,"",null));assertEquals("UNAVAILABLE",stopped.get("execution").asText());assertFalse(stopped.get("businessReady").asBoolean());
   Files.writeString(receipt.resolve("health.json"),json.writeValueAsString(Map.of("before",initial,"live",live,"stopped",stopped,"managementPort",health.port())));
  }
 }
 @Test void realRenewalTransactionDrainsBeforeAnyCompletionAcquisition()throws Exception {
  String task=accept(List.of(upload("Renewal drain")));var queue=app.getBean(ama.task.JdbcTaskQueue.class);var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var prepared=new java.util.concurrent.CountDownLatch(1);var afterPrepared=new java.util.concurrent.atomic.AtomicInteger();var trace=new java.util.concurrent.CopyOnWriteArrayList<String>();
  probe.before=sql->{if(sql.startsWith("UPDATE durable_task SET expires_at=UTC_TIMESTAMP")){trace.add("renew-db-enter");entered.countDown();try{if(!release.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("barrier");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}trace.add("renew-db-exit");}else if(prepared.getCount()==0&&Thread.currentThread().getName().startsWith("ama-work-")){afterPrepared.incrementAndGet();trace.add("completion-acquisition");}};
  try(var loop=ama.worker.WorkerLoop.contextual(queue,Map.of("TRANSCRIPT_IMPORT",1),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->{var carrier=ama.task.TranscriptImportRuntime.prepare(queue,lease,settings(),cancel);assertTrue(entered.await(7,java.util.concurrent.TimeUnit.SECONDS));prepared.countDown();trace.add("prepared-return");return carrier;}),"TASK05_DRAIN")){
   loop.start();assertTrue(prepared.await(8,java.util.concurrent.TimeUnit.SECONDS));Thread.sleep(200);assertEquals(0,afterPrepared.get(),"No preparation/root/SQL completion entry while renewal owns its DB transaction");release.countDown();awaitTerminal(task);assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
  }finally{release.countDown();probe.before=sql->{};Files.writeString(receipt.resolve("renewal-trace.json"),json.writeValueAsString(trace));}
 }
 void awaitTerminal(String task)throws Exception{long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end&&List.of("READY","RUNNING").contains(j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task)))Thread.sleep(40);}
 @Test void lostCommitAcknowledgementNeverCausesSecondCompletion()throws Exception {
  String task=accept(List.of(upload("Lost ACK")));var queue=app.getBean(ama.task.JdbcTaskQueue.class);probe.lostAckOn.set("INSERT INTO transcript_revision");var calls=new java.util.concurrent.atomic.AtomicInteger();
  try(var loop=ama.worker.WorkerLoop.contextual(queue,Map.of("TRANSCRIPT_IMPORT",1),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->{calls.incrementAndGet();return ama.task.TranscriptImportRuntime.prepare(queue,lease,settings(),cancel);}),"TASK05_ACK")){loop.start();awaitTerminal(task);Thread.sleep(150);}
  assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));assertEquals(1,calls.get());assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE producer_task_id=?",Integer.class,task));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE task_id=? AND event_type='TASK_SUCCEEDED'",Integer.class,task));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=? AND ended_at IS NOT NULL",Integer.class,task));
 }
 void restartProof()throws Exception {
  String file=upload("Original😀\r\nsecond");
  var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file))),"restart");
  assertEquals(202,accepted.statusCode(),accepted.body());String task=body(accepted).get("taskId").asText();Files.writeString(receipt.resolve("accepted-"+task+".json"),json.writeValueAsString(Map.of("task",task,"owner",owner,"meeting",meeting,"httpStatus",accepted.statusCode())));
  var original=j.queryForMap("SELECT input_id,input_fingerprint,producer_key,business_version,cancel_epoch FROM durable_task WHERE id=?",task);
  assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
  byte[] next;try(var in=getClass().getResourceAsStream("/ama/transcript/restart/processing2.json")){next=in.readAllBytes();}
  pin=new ControlledArchiveReleaseWriter().publish(archive,pin,List.of(new RawArchive(ArtifactType.PROCESSING_CONFIG,"b06.controlled.import-txt.2",next)),new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128),B,()->false);
  var nextRef=new ControlledConfigArchiveSource().open(archive,pin,new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128),B,()->false).index().entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(x->x.version().equals("b06.controlled.import-txt.2")).findFirst().orElseThrow();
  ((org.springframework.core.env.ConfigurableEnvironment)app.getEnvironment()).getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("task05-default-change",Map.of("processing.config-version",nextRef.version(),"processing.config-sha",nextRef.sha256(),"processing.index-sha",pin)));
  String originalMeeting=meeting;newMeeting();String nextFile=upload("New default");String nextTask=accept(List.of(nextFile));
  assertEquals(nextRef.version(),j.queryForObject("SELECT processing_config_version FROM transcription_input WHERE id=(SELECT input_id FROM durable_task WHERE id=?)",String.class,nextTask));meeting=originalMeeting;
  app.close();assertFalse(app.isActive());

  var props=new Properties();props.setProperty("archive",archive.toString());props.setProperty("pin",pin);props.setProperty("default-processing-version","b06.controlled.import-txt.2");props.setProperty("mode",s3Mode?"s3":"local");
  var backend=definitions.backends().getFirst();props.setProperty("id",backend.id());
  if(s3Mode){var c=s3Fixture.config;props.setProperty("endpoint",c.endpoint().toString());props.setProperty("data",c.dataBucket());props.setProperty("control",c.controlBucket());}
  else{var b=nativeFixture.binding;props.setProperty("origin",b.serviceOrigin());props.setProperty("volume",b.volumeId());props.setProperty("root",b.relativeRoot());props.setProperty("filesystem",b.filesystem());}
  if(!s3Mode){
   for(var entry:bounds.entrySet())props.setProperty(entry.getKey(),entry.getValue().toString());
   props.setProperty("processing.bounds.total-utf16","1048576");props.setProperty("processing.bounds.total-segments","4096");props.setProperty("processing.bounds.batch-deadline-ms","60000");
   props.setProperty("processing.archive-root",archive.toString());props.setProperty("processing.index-sha",pin);
   props.setProperty("database.url",System.getenv("AMA_TEST_DATABASE_URL"));props.setProperty("database.user",System.getenv("AMA_TEST_DATABASE_USERNAME"));props.setProperty("database.password",System.getenv("AMA_TEST_DATABASE_PASSWORD"));
   props.setProperty("worker.id","TASK05_PRODUCTION_MAIN");props.setProperty("worker.capacity","1");props.setProperty("worker.lease-seconds","20");props.setProperty("worker.heartbeat-seconds","5");props.setProperty("worker.poll-seconds","5");
   String password=Files.readString(ROOT.resolve("pki/keystore-password"));props.setProperty("worker.health.port","0");props.setProperty("worker.health.peer","spiffe://ama/writer");
   props.setProperty("worker.health.key-store",ROOT.resolve("pki/server.p12").toString());props.setProperty("worker.health.key-store-password",password);props.setProperty("worker.health.trust-store",ROOT.resolve("pki/trust.p12").toString());props.setProperty("worker.health.trust-store-password",password);
   props.setProperty("files.backends","historical");String prefix="files.historical.";props.setProperty(prefix+"id",backend.id());props.setProperty(prefix+"type","local");props.setProperty(prefix+"revision","2");props.setProperty(prefix+"status","READ_ONLY");
   props.setProperty(prefix+"service-origin",nativeFixture.binding.serviceOrigin());props.setProperty(prefix+"volume-id",nativeFixture.binding.volumeId());props.setProperty(prefix+"relative-root",nativeFixture.binding.relativeRoot());props.setProperty(prefix+"filesystem",nativeFixture.binding.filesystem());props.setProperty(prefix+"key-store",ROOT.resolve("pki/writer.p12").toString());props.setProperty(prefix+"trust-store",ROOT.resolve("pki/trust.p12").toString());props.setProperty(prefix+"key-store-password",password);props.setProperty(prefix+"trust-store-password",password);
  }
  Path cfg=ROOT.resolve("runtime/worker-"+UUID.randomUUID()+".properties");Files.createFile(cfg,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));try(var out=Files.newOutputStream(cfg,StandardOpenOption.WRITE)){props.store(out,"owned Task5 current defaults changed, original task retained");}LocalFixture.privateMode(cfg,false);
  String classpath=System.getProperty("java.class.path");Path log=receipt.resolve("worker-process.log");
  var command=List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx512m","-XX:ActiveProcessorCount=2","-cp",classpath,s3Mode?"ama.storage.Task05WorkerProcess":"ama.worker.TranscriptWorkerMain",cfg.toString());
  var child=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
  try{
   long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(25);
   while(child.isAlive()&&System.nanoTime()<end&&j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE id IN (?,?) AND state IN ('READY','RUNNING')",Integer.class,task,nextTask)>0)Thread.sleep(50);
   assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,nextTask));
   var state=j.queryForMap("SELECT state,output_id,last_error FROM durable_task WHERE id=?",task);
   Files.writeString(receipt.resolve("restart.json"),json.writeValueAsString(Map.of("task",task,"original",original,"after",state,"parentPid",ProcessHandle.current().pid(),"workerPid",child.pid(),"command",command,"currentDefault","b06.controlled.import-txt.2","pin",pin,"apiClosedBeforeWorker",!app.isActive())));
   assertEquals("SUCCEEDED",state.get("state"),state+" log="+log);
   assertEquals(original,j.queryForMap("SELECT input_id,input_fingerprint,producer_key,business_version,cancel_epoch FROM durable_task WHERE id=?",task));
   assertEquals(List.of("Original😀","second"),j.queryForList("SELECT body FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,state.get("output_id")));
   assertEquals("b06.controlled.import-txt.1",j.queryForObject("SELECT processing_config_version FROM transcription_input WHERE id=?",String.class,original.get("input_id")));
  }finally{child.destroy();if(!child.waitFor(8,java.util.concurrent.TimeUnit.SECONDS)){child.destroyForcibly();assertTrue(child.waitFor(3,java.util.concurrent.TimeUnit.SECONDS));}Files.writeString(receipt.resolve("worker-exit.txt"),"pid="+child.pid()+" exit="+child.exitValue()+" alive="+child.isAlive()+"\n");}
 }
 @AfterEach void close()throws Exception {if(app!=null)app.close();if(health!=null)health.close();if(nativeFixture!=null)nativeFixture.close();if(s3Fixture!=null)s3Fixture.close();}
    String identity(String id) throws Exception {
        return identity(id,true);
    }
    String identity(String id,boolean alreadyIssued) throws Exception {
        String value=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.SecureRandom.getSeed(32));
        j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",id,id);
        // Only the new positive integrity fixtures use a recent already-issued session after the observed backward clock step.
        String issued=alreadyIssued?"UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE":"UTC_TIMESTAMP(6)";
        j.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) VALUES (?,?,0,?,"+issued+",UTC_TIMESTAMP(6)+INTERVAL 1 HOUR)",hash(value),id,csrf);
        return value;
    }
    static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    JsonNode body(HttpResponse<String> response) throws Exception {
        return json.readTree(response.body());
    }
    HttpResponse<String> call(String method,String path,String payload,String key) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port")+"/api/v1"+path)).header("Cookie","__Host-AMA_SESSION="+token).header("Origin",origin).header("X-CSRF-Token",csrf).header("Content-Type","application/json");
        if(key!=null)request.header("Idempotency-Key",key);
        var built=request.method(method,HttpRequest.BodyPublishers.ofString(payload)).build();
        var response=HttpClient.newHttpClient().send(built,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()==401)authDiagnostic(method+" "+path,built);
        return response;
    }
    void authDiagnostic(String operation,HttpRequest request)throws Exception {
        Files.writeString(receipt.resolve("authentication-failure.txt"),"operation="+operation+" databaseNow="+j.queryForObject("SELECT UTC_TIMESTAMP(6)",String.class)+" JVM="+java.time.Instant.now()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    String open(String value) throws Exception {
        String checksum=hash(value);
        var begin=call("POST","/meetings/"+meeting+"/uploads",json.writeValueAsString(Map.of("purpose","TRANSCRIPT_INPUT","name","original😀.txt","size",value.getBytes(StandardCharsets.UTF_8).length,"sha256",checksum)),UUID.randomUUID().toString());
        assertEquals(201,begin.statusCode(),begin.body());
        String upload=body(begin).get("uploadId").asText();
        var part=part(upload,value);
        assertEquals(204,part.statusCode(),part.body());
        return upload;
    }
    HttpResponse<String> part(String upload,String value) throws Exception {
        var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port")+"/api/v1/uploads/"+upload+"/parts/1")).header("Cookie","__Host-AMA_SESSION="+token).header("Origin",origin).header("X-CSRF-Token",csrf).header("Content-Type","application/octet-stream").header("X-Checksum-SHA256",hash(value)).PUT(HttpRequest.BodyPublishers.ofString(value)).build();
        var response=HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()==401)authDiagnostic("PART "+upload,req);
        return response;
    }
    HttpResponse<String> complete(String upload,String value) throws Exception {
        return call("POST","/uploads/"+upload+"/completion",json.writeValueAsString(Map.of("parts",List.of(Map.of("part",1,"sha256",hash(value))),"sha256",hash(value))),null);
    }
    String upload(String value) throws Exception {
        var completed=complete(open(value),value);
        assertEquals(200,completed.statusCode(),completed.body());
        return body(completed).get("fileId").asText();
    }
}
