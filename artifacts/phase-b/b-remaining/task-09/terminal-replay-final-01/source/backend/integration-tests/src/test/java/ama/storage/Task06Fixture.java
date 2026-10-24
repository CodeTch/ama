package ama.storage;
import ama.transcript.*;
import ama.contract.IdentityContracts.*;
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
class Task06Fixture {
 ama.worker.WorkerHealthServer health;final java.util.concurrent.atomic.AtomicReference<ama.worker.WorkerLoop> healthLoop=new java.util.concurrent.atomic.AtomicReference<>();
 Task06ProbeSource probe;ConfigurableApplicationContext app;JdbcTemplate j;javax.sql.DataSource source;
 final ObjectMapper json=new ObjectMapper();String owner,token,meeting;final String csrf="T".repeat(43);String origin="https://task05.example.invalid";
 FileStoragePort actualStoragePort;final java.util.Set<Thread> actualReadThreads=java.util.concurrent.ConcurrentHashMap.newKeySet();ama.storage.s3.Task05Fix02Proxy s3Proxy;ama.storage.s3.S3Fixture s3Fixture;boolean s3Mode;Path receipt;Task06NativeFixture nativeFixture;FileBackendDefinitions definitions;Path archive;String pin;Ref processing;Map<String,Object> bounds;
 static final Path ROOT=Path.of(System.getenv("AMA_TASK05_ROOT"));
 @BeforeEach void start(TestInfo info)throws Exception {
  probe=new Task06ProbeSource(MysqlTestDatabase.dataSource());source=probe;j=new JdbcTemplate(source);
  assertEquals(14,j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1",Integer.class));
  Path publicRoot=Path.of("artifacts/phase-b/b-remaining/task-06/fix-01").toAbsolutePath();
  while(!Files.exists(publicRoot.getParent()))publicRoot=Path.of("../../artifacts/phase-b/b-remaining/task-06/fix-01").toAbsolutePath().normalize();
  receipt=Files.createDirectory(publicRoot.resolve("pipeline-"+UUID.randomUUID()));
  Path durable=ROOT.resolve("native/data");if(!Files.exists(durable))Files.createDirectory(durable);
  nativeFixture=new Task06NativeFixture(durable.resolve("case-"+UUID.randomUUID()));
  var storage=nativeFixture.startProcess();
  s3Mode=info.getTestMethod().orElseThrow().getName().contains("S3");
  if(s3Mode){
   s3Fixture=new ama.storage.s3.S3Fixture("task06-infrastructure");var c=s3Fixture.config;
   var binding=new ManagedBackendId.S3(c.endpoint().toString(),c.region(),c.dataBucket(),c.controlBucket(),c.pathStyle(),c.keyBudget());
   definitions=new FileBackendDefinitions(List.of(ManagedBackendAdapters.s3(admissionTwo(ama.storage.s3.S3Fixture.config(binding.id(),c.endpoint(),c.dataBucket(),c.controlBucket())),1,"WRITABLE")),binding.id());
  }else definitions=new FileBackendDefinitions(List.of(new FileBackendCatalog.Backend(nativeFixture.binding.id(),"local",1,"WRITABLE",storage,nativeFixture.binding)),nativeFixture.binding.id());
  var actualBackend=definitions.backends().getFirst();var actualStorage=actualBackend.storage();actualStoragePort=actualStorage;
  FileStoragePort observed=(FileStoragePort)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),actualStorage instanceof AutoCloseable?new Class[]{FileStoragePort.class,AutoCloseable.class}:new Class[]{FileStoragePort.class},(proxy,method,args)->{
   if(Set.of("read","stat").contains(method.getName()))assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),"Actual source I/O under transaction");
   if(method.getName().equals("read"))actualReadThreads.add(Thread.currentThread());
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
  String[] savedNames={"utf8","scalars","utf16","copy-bytes","copy-utf16","response-bytes","revision-depth","revision-nodes","tasks"};long[] savedValues={16777216,16777216,16777216,67108864,67108864,33554432,128,256,64};for(int i=0;i<savedNames.length;i++)bounds.put("transcript.bounds."+savedNames[i],savedValues[i]);
  bounds.put("transcript.enabled",installedTranscript());
  bounds.put("transcript.correction.mode","DISABLED_NOT_INSTALLED");bounds.put("transcript.correction.installation","DISABLED_NOT_INSTALLED");bounds.put("transcript.correction.installation-version","1");bounds.put("transcript.candidates.installation","DISABLED_NOT_INSTALLED");bounds.put("transcript.publication.installation","DISABLED_NOT_INSTALLED");bounds.put("transcript.transitions.installation-version","1");
  if(info.getTestMethod().orElseThrow().getName().contains("Health")){
   health=new ama.worker.WorkerHealthServer(new InetSocketAddress("127.0.0.1",0),Task05TransportIT.tls("server"),"spiffe://ama/writer",()->{var loop=healthLoop.get();return loop==null?new ama.worker.WorkerLoop.Observation("STARTING",Long.MAX_VALUE,0):loop.observation();});health.start();
   String password=Files.readString(ROOT.resolve("pki/keystore-password"));java.security.cert.Certificate cert;try(var in=Files.newInputStream(ROOT.resolve("pki/server.crt"))){cert=java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(in);}
   bounds.put("processing.worker.health-url","https://localhost:"+health.port());bounds.put("processing.worker.key-store",ROOT.resolve("pki/writer.p12").toString());bounds.put("processing.worker.trust-store",ROOT.resolve("pki/trust.p12").toString());bounds.put("processing.worker.key-store-password",password);bounds.put("processing.worker.trust-store-password",password);bounds.put("processing.worker.server-cert-sha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(cert.getEncoded())));bounds.put("processing.worker.max-age-ms",10000);
  }
  beforeApplication();
  app=new SpringApplicationBuilder(ApiApplication.class).properties(bounds).initializers(context->{var c=(GenericApplicationContext)context;c.registerBean("task06Source",javax.sql.DataSource.class,()->source);c.registerBean("task06Backends",FileBackendDefinitions.class,()->definitions);}).run("--server.address=127.0.0.1","--server.port="+serverPort(),"--management.server.port=0","--logging.level.root=ERROR","--identity.allowed-origin="+origin,"--processing.enabled=true","--processing.archive-root="+archive,"--processing.index-sha="+pin,"--processing.config-version="+processing.version(),"--processing.config-sha="+processing.sha256());
  owner="task06-"+UUID.randomUUID();token=identity(owner);var created=call("POST","/meetings","{}","create");assertEquals(201,created.statusCode(),created.body());meeting=body(created).get("id").asText();
  Files.writeString(receipt.resolve("resource.json"),json.writeValueAsString(Map.of("nativePid",nativeFixture.process.pid(),"nativePort",nativeFixture.port,"managedId",definitions.backends().getFirst().id(),"nativePath",nativeFixture.base.root.toString(),"archive",archive.toString(),"pin",pin,"databaseNow",j.queryForObject("SELECT UTC_TIMESTAMP(6)",String.class),"jvmNow",java.time.Instant.now().toString(),"maxMemory",Runtime.getRuntime().maxMemory(),"owner",owner)));
 }
 static ama.storage.s3.S3Config admissionTwo(ama.storage.s3.S3Config c){return new ama.storage.s3.S3Config(c.backendId(),c.endpoint(),c.region(),c.dataBucket(),c.controlBucket(),c.credentials(),c.pathStyle(),c.keyBudget(),c.chunkBytes(),c.nativeMinimum(),c.nativeMaximum(),c.nativeMaxParts(),c.maximumObject(),2,c.timeout());}
 ama.task.TranscriptImportRuntime.Settings settings(){return new ama.task.TranscriptImportRuntime.Settings(()->new ControlledConfigArchiveSource().open(archive,pin,new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128),B,()->false),B,new ama.transcript.TranscriptDocumentParser.ParseBudget(1048576,1048576,4096),java.time.Duration.ofSeconds(60));}
 static final ama.task.PreparationCancellation NEVER=new ama.task.PreparationCancellation(){public boolean cancelled(){return false;}public AutoCloseable register(Runnable abort){return ()->{};}};
 void newMeeting()throws Exception {var created=call("POST","/meetings","{}",UUID.randomUUID().toString());assertEquals(201,created.statusCode(),created.body());meeting=body(created).get("id").asText();}
 String accept(List<String> files)throws Exception {var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",files)),UUID.randomUUID().toString());assertEquals(202,accepted.statusCode(),accepted.body());String acceptedTask=body(accepted).get("taskId").asText();Files.writeString(receipt.resolve("accepted-"+acceptedTask+".json"),json.writeValueAsString(Map.of("task",acceptedTask,"owner",owner,"meeting",meeting,"files",files)));return acceptedTask;}
 ama.contract.TaskContracts.TaskLease claim(String task){var lease=app.getBean(ama.task.JdbcTaskQueue.class).claim("TRANSCRIPT_IMPORT","TASK05_BOUNDARY").orElseThrow();assertEquals(task,lease.taskId());return lease;}
 @AfterEach void close()throws Exception {if(app!=null)app.close();if(health!=null)health.close();if(nativeFixture!=null)nativeFixture.close();if(s3Fixture!=null)s3Fixture.close();if(s3Proxy!=null)s3Proxy.close();}
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
        var request=authenticated(HttpRequest.newBuilder(URI.create(apiUrl()+path))).header("Origin",origin).header("X-CSRF-Token",csrf).header("Content-Type","application/json");
        if(key!=null)request.header("Idempotency-Key",key);
        var built=request.method(method,HttpRequest.BodyPublishers.ofString(payload)).build();
        var response=client().send(built,HttpResponse.BodyHandlers.ofString());
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
        var req=authenticated(HttpRequest.newBuilder(URI.create(apiUrl()+"/uploads/"+upload+"/parts/1"))).header("Origin",origin).header("X-CSRF-Token",csrf).header("Content-Type","application/octet-stream").header("X-Checksum-SHA256",hash(value)).PUT(HttpRequest.BodyPublishers.ofString(value)).build();
        var response=client().send(req,HttpResponse.BodyHandlers.ofString());
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
 String raw()throws Exception {
  String task=accept(List.of(upload("Alpha😀\nBeta")));var q=app.getBean(ama.task.JdbcTaskQueue.class);
  try(var loop=ama.worker.WorkerLoop.contextual(q,Map.of("TRANSCRIPT_IMPORT",1),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->ama.task.TranscriptImportRuntime.prepare(q,lease,settings(),cancel)),"TASK06_PROBE")){
   loop.start();long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
   while(System.nanoTime()<end&&List.of("READY","RUNNING").contains(j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task)))Thread.sleep(50);
  }
  assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
  return j.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,task);
 }
 void beforeApplication()throws Exception {}
 int serverPort(){return 0;}
 String apiUrl(){return "http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port")+"/api/v1";}
 HttpClient client()throws Exception{return HttpClient.newHttpClient();}
 HttpRequest.Builder authenticated(HttpRequest.Builder request)throws Exception{return request.header("Cookie","__Host-AMA_SESSION="+token);}
 boolean installedTranscript(){return false;}
 JdbcSavedTranscripts isolated(){return ama.api.Task06RuntimeProbe.compose(app).service();}
 Actor actor(){return new Actor(owner,Status.ACTIVE,Set.of(Role.USER));}
}
