package ama.storage;

import ama.api.ApiApplication;
import ama.config.*;
import ama.config.file.*;
import ama.meeting.*;
import ama.model.*;
import ama.task.*;
import ama.transcript.*;
import ama.worker.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Explicit main only: no inherited integration test is dispatched. Fresh owned schemas and real modules. */
public final class Bl5LiveHarness extends Task11FinalPublicationIT {
 static final Path BATCH=Path.of(System.getenv("AMA_BL5_ROOT"));
 final com.fasterxml.jackson.databind.JsonNode config;
 final String mode;
 final java.util.List<Bl5HttpMeter> meters=new java.util.ArrayList<>();Bl5HttpMeter knowledgeMeter;
 final AtomicBoolean closed=new AtomicBoolean();
 WorkerLoop liveWorker;ControlledMinutesProvider liveModel;ControlledMinutesClient liveModelClient;
 ama.storage.s3.S3FileStorage s3Storage;software.amazon.awssdk.services.s3.S3Client s3Admin;
 Bl5LiveHarness(Path selected)throws Exception {
  if(!BATCH.getFileName().toString().equals("bl5-5d9729e1-a748-4c50-a1f2-9e1d76eae33a")||!BATCH.toRealPath().equals(BATCH)||!selected.toRealPath().startsWith(BATCH))throw new IllegalArgumentException("Exact B-L5 custody required");
  config=json.readTree(Files.readAllBytes(selected));mode=config.path("mode").asText();if(!Set.of("local","s3").contains(mode)||!ROOT.equals(BATCH.resolve(mode.equals("s3")?"service-s3-r2":mode)))throw new IllegalArgumentException("Fresh mode scope required");origin=config.path("browserOrigin").asText();URI browser=URI.create(origin);if(!browser.getScheme().equals("https")||!browser.getHost().equals("127.0.0.1")||browser.getPort()<1)throw new IllegalArgumentException("HTTPS loopback required");
 }
 @Override javax.sql.DataSource createSource(){
  String schema=config.path("schema").asText();if(!schema.equals(mode.equals("s3")?"ama_bl5_5d9729e1a748_s3r2":Bl5SchemaPrepare.schema(mode)))throw new IllegalArgumentException("Foreign schema");
  int port=config.path("mysqlPort").asInt();if(port<1||port>65535)throw new IllegalArgumentException("Invalid owned MySQL port");
  return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+port+"/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true","ama_bl5",config.path("mysqlPassword").asText());
 }
 @Override boolean installedTranscript(){return true;}
 @Override void beforeApplication()throws Exception {
  register(nativeFixture.process.pid(),runLabel()+"-native");
  bounds.put("transcript.candidates.installation","AUTHORITY_CONTROLLED");bounds.put("transcript.publication.installation","AUTHORITY_CONTROLLED");bounds.put("transcript.transitions.installation-version","2");bounds.put("management.endpoint.health.group.ready.include","ping");bounds.put("processing.bounds.archive-total-bytes",2097152L);
  bounds.put("server.tomcat.threads.max",16);bounds.put("server.tomcat.threads.min-spare",2);bounds.put("server.tomcat.max-connections",64);bounds.put("server.tomcat.accept-count",16);
  if(mode.equals("local")){
   var meter=new Bl5HttpMeter(BATCH,"native",nativeFixture.uri,Task09NativeFixture.tls("server"),Task09NativeFixture.tls("writer"),true);meters.add(meter);var backend=definitions.backends().getFirst();var storage=new LocalFileClient(meter.origin(),Task09NativeFixture.tls("writer"));actualStoragePort=storage;definitions=new FileBackendDefinitions(List.of(new FileBackendCatalog.Backend(backend.id(),backend.type(),backend.revision(),backend.status(),storage,backend.definition())),backend.id());
  }
  if(mode.equals("s3")){
   String endpoint=config.path("s3Endpoint").asText();URI uri=URI.create(endpoint);if(!uri.getScheme().equals("http")||!uri.getHost().equals("127.0.0.1")||uri.getPort()<1)throw new IllegalArgumentException("Owned S3 loopback required");
   var meter=new Bl5HttpMeter(BATCH,"s3",uri,null,null,false);meters.add(meter);uri=meter.origin();endpoint=uri.toString();
   String data="bl5-5d9729e1-r2-data",control="bl5-5d9729e1-r2-control";var binding=new ManagedBackendId.S3(endpoint,"us-east-1",data,control,true,1024);
   var credentials=software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(config.path("s3Access").asText(),config.path("s3Secret").asText()));
   var client=software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3)).socketTimeout(Duration.ofSeconds(20)).proxyConfiguration(software.amazon.awssdk.http.urlconnection.ProxyConfiguration.builder().useEnvironmentVariablesValues(false).useSystemPropertyValues(false).build());
   s3Admin=software.amazon.awssdk.services.s3.S3Client.builder().endpointOverride(uri).region(software.amazon.awssdk.regions.Region.US_EAST_1).credentialsProvider(credentials).forcePathStyle(true).httpClientBuilder(client).overrideConfiguration(software.amazon.awssdk.core.client.config.ClientOverrideConfiguration.builder().retryStrategy(software.amazon.awssdk.retries.StandardRetryStrategy.builder().maxAttempts(1).build()).build()).build();
   s3Admin.createBucket(b->b.bucket(data));s3Admin.createBucket(b->b.bucket(control));
   s3Storage=new ama.storage.s3.S3FileStorage(new ama.storage.s3.S3Config(binding.id(),uri,"us-east-1",data,control,credentials,true,1024,8*1024*1024,5L*1024*1024,5L*1024*1024*1024,10000,256L*1024*1024,8,Duration.ofSeconds(20)));
   definitions=new FileBackendDefinitions(List.of(new FileBackendCatalog.Backend(binding.id(),"s3",1,"WRITABLE",new Bl5BorrowedStorage(s3Storage),binding)),binding.id());actualStoragePort=s3Storage;
  }
 }
 String runLabel(){return mode.equals("s3")?"s3-r2":mode;}
 void combined()throws Exception {
  app.close();var configured=new HashMap<>(bounds);minutesBounds(configured);configured.put("minutes.enabled",true);configured.put("transcript.enabled",true);configured.put("publication.enabled",true);configured.put("publication.mode","CONTROLLED_TLS");configured.put("publication.client-identities",knowledge.applicationIdentities.toString());configured.put("publication.origin",knowledgeMeter.origin().toString());configured.put("minutes.mode","CONTROLLED_INSTALLATION");configured.put("minutes.archive-root",archive.toString());configured.put("minutes.index-sha",pin);configured.put("minutes.prompt-version",prompt.version());configured.put("minutes.prompt-sha",prompt.sha256());configured.put("minutes.model-version",model.version());configured.put("minutes.model-sha",model.sha256());
  Path categories=BATCH.resolve("categories.bin");var category=CategoryDictionary.load(categories,config.path("categorySha256").asText(),new WorkspaceReadBudget(WorkspaceReadBudget.DEFAULT,()->false));
  app=new SpringApplicationBuilder(ApiApplication.class).properties(configured).initializers(context->{var c=(GenericApplicationContext)context;c.registerBean("bl5Source",javax.sql.DataSource.class,()->source);c.registerBean("bl5Backends",FileBackendDefinitions.class,()->definitions);c.registerBean("bl5Categories",CategoryDictionary.class,()->category,b->b.setPrimary(true));}).run("--server.address=127.0.0.1","--server.port=0","--management.server.port=0","--logging.level.root=ERROR","--identity.allowed-origin="+origin,"--processing.enabled=true","--processing.archive-root="+archive,"--processing.index-sha="+pin,"--processing.config-version="+processing.version(),"--processing.config-sha="+processing.sha256());
 }
 void workers()throws Exception {
  var queue=app.getBean(JdbcTaskQueue.class);var authority=ama.api.Task09RuntimeProbe.components(app).authority();TranscriptImportRuntime.requireCombined(queue,authority);
  var operations=new JdbcExternalOperations(queue);var permits=new JdbcModelPermitPool(operations,Map.of(ControlledMinutesProtocol.MODEL,new ModelConfiguration(ControlledMinutesProtocol.MODEL,1,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(t,m,d)->{throw new IllegalStateException("No inferred cutoff proof");});permits.configure();
  Path store=Files.createDirectory(ROOT.resolve("model-store"));String auth=UUID.randomUUID().toString()+UUID.randomUUID();liveModel=new ControlledMinutesProvider(store,new InetSocketAddress("127.0.0.1",0),auth,"CONTROLLED_INSTALLATION");liveModel.start();var modelMeter=new Bl5HttpMeter(BATCH,"model",URI.create("http://127.0.0.1:"+liveModel.port()),null,null,false);meters.add(modelMeter);liveModelClient=new ControlledMinutesClient(new ControlledMinutesHttpTransport(modelMeter.origin(),auth,"CONTROLLED_INSTALLATION",Duration.ofSeconds(2),Duration.ofSeconds(30),Duration.ofSeconds(5)));
  var preparation=new JdbcMinutesGenerationPreparation(authority,this::snapshot,pin,prompt,model,new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,8388608,128,256,256));var generation=new MinutesGenerationHandler(new MinutesExternalPreparation(authority,preparation,liveModelClient),liveModelClient,operations,permits);var publication=new KnowledgePublicationHandler(ama.api.Task11RuntimeProbe.publication(app),operations,permits);
  liveWorker=WorkerLoop.mixed(queue,operations,permits,Map.of("TRANSCRIPT_IMPORT",1,"MINUTES_GENERATE",1,"KNOWLEDGE_PUBLISH",1),Map.of(),Map.of(),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->TranscriptImportRuntime.prepare(queue,lease,settings(),cancel)),Map.of("MINUTES_GENERATE_V1",generation,"KNOWLEDGE_PUBLISH_V1",publication),Map.of("MINUTES_GENERATE_V1",ControlledMinutesProtocol.MODEL),"BL5_"+mode);liveWorker.start();
 }
 static void consume(String name,int maximum)throws Exception {Bl5Budget.consume(BATCH,name,1,maximum);}
 @Override java.net.http.HttpResponse<String> call(String method,String path,String payload,String key)throws Exception {Bl5Budget.consume(BATCH,"http",1,11000);return super.call(method,path,payload,key);}
 static void register(long pid,String label)throws Exception {
  if(Files.exists(BATCH.resolve("stopping.json")))throw new IllegalStateException("Owned batch stopping");
  String ticks=Files.readString(Path.of("/proc/"+pid+"/stat"));ticks=ticks.substring(ticks.lastIndexOf(')')+1).trim().split(" +")[19];
  Path registry=BATCH.resolve("processes");if(!Files.exists(registry))Files.createDirectory(registry);privateJson(registry.resolve(label+".json"),Map.of("owner",BATCH.getFileName().toString(),"pid",pid,"startTicks",ticks));
 }
 static void privateJson(Path path,Object value)throws Exception {
  Files.createFile(path,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));Files.writeString(path,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value));
 }
 void finish(){if(!closed.compareAndSet(false,true))return;var errors=new ArrayList<String>();
  for(AutoCloseable resource:new AutoCloseable[]{liveWorker,liveModelClient,liveModel,app,knowledge,s3Storage,s3Admin,nativeFixture})if(resource!=null)try{resource.close();}catch(Exception failure){errors.add(failure.getClass().getName());}
  for(var meter:meters)try{meter.close();}catch(Exception failure){errors.add(failure.getClass().getName());}
  try{privateJson(ROOT.resolve("harness-stopped.json"),Map.of("pid",ProcessHandle.current().pid(),"errors",errors,"dataRetained",true));}catch(Exception ignored){}
 }
 public static void main(String[] args)throws Exception {
  if(args.length!=1)throw new IllegalArgumentException("Owned config required");Bl5LiveHarness h=new Bl5LiveHarness(Path.of(args[0]));register(ProcessHandle.current().pid(),h.runLabel()+"-harness");Runtime.getRuntime().addShutdownHook(new Thread(h::finish));
  try{
   consume("pipelines",12);h.start();h.install();Path kb=BATCH.resolve("knowledge-"+h.mode+(h.mode.equals("s3")?"-r2":""));h.knowledge=Task11KnowledgeFixture.createFreshTls(h.receipt,h.owner,kb,new Task11KnowledgeFixture.TlsOptions(BATCH.resolve("pki/server.pem"),BATCH.resolve("pki/server.key"),kb.resolve("application/publicidentities.json")));register(h.knowledge.pid(),h.runLabel()+"-knowledge");h.knowledgeMeter=new Bl5HttpMeter(BATCH,"knowledge",URI.create("https://127.0.0.1:"+h.knowledge.port),Task09NativeFixture.tls("server"),Task09NativeFixture.tls("writer"),false);h.meters.add(h.knowledgeMeter);
   Map<String,Object> identities=new LinkedHashMap<>();identities.put("owner",Map.of("id",h.owner,"token",h.token));for(String role:List.of("member","outsider","admin")){String id="bl5-"+role+"-"+UUID.randomUUID(),token=h.identity(id);if(role.equals("admin"))h.j.update("INSERT INTO role_assignment(enterprise_id,role,assigned_at) VALUES (?,'PLATFORM_ADMIN',UTC_TIMESTAMP(6))",id);identities.put(role,Map.of("id",id,"token",token));}
   h.combined();h.workers();privateJson(ROOT.resolve("ready.json"),Map.of("owner",BATCH.getFileName().toString(),"mode",h.mode,"apiPort",h.app.getEnvironment().getProperty("local.server.port"),"bootstrapMeeting",h.meeting,"templateRevision",h.revision,"identities",identities,"csrf",h.csrf,"workerSameJvm",true,"knowledgeTls",true));
   long remaining=h.config.path("remainingSeconds").asLong();if(remaining<1||remaining>10600)throw new IllegalArgumentException("Remaining runtime deadline required");long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(remaining);while(System.nanoTime()<end&&!Files.exists(ROOT.resolve("stop"))&&!Files.exists(BATCH.resolve("stopping.json")))Thread.sleep(250);
  }finally{h.finish();}
 }
}
