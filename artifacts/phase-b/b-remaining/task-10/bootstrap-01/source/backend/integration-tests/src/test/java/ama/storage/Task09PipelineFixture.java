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

class Task09PipelineFixture {
 static final ArchiveReadBudget B=new ArchiveReadBudget(65536,1048576,64,128,16,200000,32,255,65536,262144,1048576,65536,32,250000,500000);
 ConfigurableApplicationContext app;JdbcTemplate j;javax.sql.DataSource source;
 final ObjectMapper json=new ObjectMapper();String owner,token,meeting;final String csrf="T".repeat(43);String origin="https://task09.example.invalid";
 FileStoragePort actualStoragePort;final java.util.Set<Thread> actualReadThreads=java.util.concurrent.ConcurrentHashMap.newKeySet();Path receipt;Task09NativeFixture nativeFixture;FileBackendDefinitions definitions;Path archive;String pin;Ref processing;Map<String,Object> bounds;
 static final Path ROOT=Path.of(System.getenv("AMA_TASK09_RUN_ROOT"));
 static void directory(Path path)throws Exception{if(!Files.exists(path))Files.createDirectory(path,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));if(Files.isSymbolicLink(path)||!path.toRealPath().startsWith(ROOT.toRealPath()))throw new IllegalStateException("Task9 owned runtime path");}
 javax.sql.DataSource createSource(){return ama.minutes.Task09Source.open();}
 @BeforeEach void start()throws Exception {
  source=createSource();j=new JdbcTemplate(source);receipt=Files.createDirectory(Path.of(System.getenv("AMA_TASK09_EVIDENCE_WINDOW")).resolve("pipeline-"+UUID.randomUUID()));
  directory(ROOT.resolve("native"));directory(ROOT.resolve("native/data"));directory(ROOT.resolve("runtime"));Path durable=ROOT.resolve("native/data");
  nativeFixture=new Task09NativeFixture(durable.resolve("case-"+UUID.randomUUID()));var storage=nativeFixture.startProcess();
  definitions=new FileBackendDefinitions(List.of(new FileBackendCatalog.Backend(nativeFixture.binding.id(),"local",1,"WRITABLE",storage,nativeFixture.binding)),nativeFixture.binding.id());
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
  long[] values={65536,1048576,64,128,16,200000,32,255,65536,262144,1048576,65536,32,250000,500000,16777216,32,1000000,2097152,250000,4194304,4096,67108864,128,1048576};
  for(int n=0;n<names.length;n++)bounds.put("processing.bounds."+names[n],values[n]);
  String[] savedNames={"utf8","scalars","utf16","copy-bytes","copy-utf16","response-bytes","revision-depth","revision-nodes","tasks"};long[] savedValues={16777216,16777216,16777216,67108864,67108864,33554432,128,256,64};for(int i=0;i<savedNames.length;i++)bounds.put("transcript.bounds."+savedNames[i],savedValues[i]);
  bounds.put("transcript.enabled",installedTranscript());
  bounds.put("transcript.correction.mode","DISABLED_NOT_INSTALLED");bounds.put("transcript.correction.installation","DISABLED_NOT_INSTALLED");bounds.put("transcript.correction.installation-version","1");bounds.put("transcript.candidates.installation","DISABLED_NOT_INSTALLED");bounds.put("transcript.publication.installation","DISABLED_NOT_INSTALLED");bounds.put("transcript.transitions.installation-version","1");
  beforeApplication();
  app=new SpringApplicationBuilder(ApiApplication.class).properties(bounds).initializers(context->{var c=(GenericApplicationContext)context;c.registerBean("task09Source",javax.sql.DataSource.class,()->source);c.registerBean("task09Backends",FileBackendDefinitions.class,()->definitions);}).run("--server.address=127.0.0.1","--server.port="+serverPort(),"--management.server.port=0","--logging.level.root=ERROR","--identity.allowed-origin="+origin,"--processing.enabled=true","--processing.archive-root="+archive,"--processing.index-sha="+pin,"--processing.config-version="+processing.version(),"--processing.config-sha="+processing.sha256());
  owner="task09-"+UUID.randomUUID();token=identity(owner);var created=call("POST","/meetings","{}","create");assertEquals(201,created.statusCode(),created.body());meeting=body(created).get("id").asText();
  Files.writeString(receipt.resolve("resource.json"),json.writeValueAsString(Map.of("nativePid",nativeFixture.process.pid(),"nativePort",nativeFixture.port,"managedId",definitions.backends().getFirst().id(),"nativePath",nativeFixture.base.root.toString(),"archive",archive.toString(),"pin",pin,"databaseNow",j.queryForObject("SELECT UTC_TIMESTAMP(6)",String.class),"jvmNow",java.time.Instant.now().toString(),"maxMemory",Runtime.getRuntime().maxMemory(),"owner",owner)));
 }
 ama.task.TranscriptImportRuntime.Settings settings(){return new ama.task.TranscriptImportRuntime.Settings(()->new ControlledConfigArchiveSource().open(archive,pin,new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128),B,()->false),B,new ama.transcript.TranscriptDocumentParser.ParseBudget(1048576,1048576,4096),java.time.Duration.ofSeconds(60));}
 static final ama.task.PreparationCancellation NEVER=new ama.task.PreparationCancellation(){public boolean cancelled(){return false;}public AutoCloseable register(Runnable abort){return ()->{};}};
 void newMeeting()throws Exception {var created=call("POST","/meetings","{}",UUID.randomUUID().toString());assertEquals(201,created.statusCode(),created.body());meeting=body(created).get("id").asText();}
 String accept(List<String> files)throws Exception {var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",files)),UUID.randomUUID().toString());assertEquals(202,accepted.statusCode(),accepted.body());String acceptedTask=body(accepted).get("taskId").asText();Files.writeString(receipt.resolve("accepted-"+acceptedTask+".json"),json.writeValueAsString(Map.of("task",acceptedTask,"owner",owner,"meeting",meeting,"files",files)));return acceptedTask;}
 ama.contract.TaskContracts.TaskLease claim(String task){var lease=app.getBean(ama.task.JdbcTaskQueue.class).claim("TRANSCRIPT_IMPORT","TASK09_BOUNDARY").orElseThrow();assertEquals(task,lease.taskId());return lease;}
 @AfterEach void close()throws Exception {try{if(app!=null)app.close();}finally{if(nativeFixture!=null)nativeFixture.close();}}
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
  try(var loop=ama.worker.WorkerLoop.contextual(q,Map.of("TRANSCRIPT_IMPORT",1),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->ama.task.TranscriptImportRuntime.prepare(q,lease,settings(),cancel)),"TASK09_PIPELINE")){
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
 JdbcSavedTranscripts isolated(){return ama.api.Task09RuntimeProbe.compose(app).service();}
 Actor actor(){return new Actor(owner,Status.ACTIVE,Set.of(Role.USER));}
}
