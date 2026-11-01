package ama.storage;

import ama.contract.IdentityContracts.*;
import ama.contract.TaskContracts.*;
import ama.db.*;
import ama.meeting.*;
import ama.task.*;
import ama.transcript.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL output-policy regression. Explicit task/output fixture rows do not prove model execution. */
class MediaImportOutputIT {
 @TempDir Path root;
 static javax.sql.DataSource source;static JdbcTemplate j;
 @BeforeAll static void schema(){
  String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");
  if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Owned local C schema required");
  source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");j=new JdbcTemplate(source);
  Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
 }
 static String id(){return UUID.randomUUID().toString();}
 @Test void currentMediaProfileIsActuallyAdmittedWithAnArchivedMediaStep()throws Exception {validate(MediaTranscriptParser.PROFILE_VERSION,true,false,true);}
 @Test void controlledSpeechProfileIsActuallyAdmittedWithItsOwnArchivedStep()throws Exception {validate(ControlledSpeechProfile.PARSER,true,false,true);}
 @Test void controlledSpeechProfileAllowsOriginalTimedOutput()throws Exception {validate(ControlledSpeechProfile.PARSER,true,false);}
 @Test void historicalMediaProfileIsStillActuallyAdmitted()throws Exception {validate(MediaTranscriptParser.LEGACY_PROFILE_VERSION,true,false,true);}
 @Test void currentMediaProfileAllowsItsActualTimedSource()throws Exception {validate(MediaTranscriptParser.PROFILE_VERSION,true,false);}
 @Test void historicalMediaProfileKeepsItsTimedSourceCompatibility()throws Exception {validate(MediaTranscriptParser.LEGACY_PROFILE_VERSION,true,false);}
 @Test void textProfileStillCannotClaimMediaTimestamps()throws Exception {validate(TextTranscriptParser.PROFILE_VERSION,false,false);}
 @Test void mediaProfileCannotAttributeATimedSegmentToAnUnissuedSource()throws Exception {validate(MediaTranscriptParser.PROFILE_VERSION,false,true);}
 void validate(String parser,boolean allowed,boolean foreignSource)throws Exception {validate(parser,allowed,foreignSource,false);}
 void validate(String parser,boolean allowed,boolean foreignSource,boolean admit)throws Exception {
  try(var files=new ManagedLocalFixture(root.resolve(id()))){
   var storage=files.startServer();var catalog=new FileBackendCatalog(source,List.of(new FileBackendCatalog.Backend(files.binding.id(),"local",1,"WRITABLE",storage,files.binding)),files.binding.id());catalog.configure();
   var tx=new TransactionRunner(source);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);var validators=new HashMap<String,ama.task.OutboxReferenceValidator>(new MeetingEventReferences(source).validators());validators.putAll(new TranscriptEventReferences(source).validators());var outbox=new JdbcOutbox(source,validators);
   String owner="media-output-"+id();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",owner,owner);var actor=new Actor(owner,Status.ACTIVE,Set.of(Role.USER));
   var meetings=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,outbox,new ama.audit.JdbcAuditAdapter(source),m->{}));String meeting=meetings.create(actor,id(),"media-output-policy").id();
   FilePurposePolicy purposes=(a,m,p)->{if(!p.equals("TRANSCRIPT_INPUT"))throw new FilePurposePolicy.Unavailable();};
   var components=JdbcTranscriptAuthority.create(source,tx,access,purposes,catalog,Map.of(),ama.minutes.ControlledMinutesAdmin.READ,new ama.contract.MinutesContracts.CanonicalBudget(16777216,32,1000000,4194304,250000));
   var uploads=new JdbcFileUploads(source,tx,access,purposes,catalog,new ama.audit.JdbcAuditAdapter(source),outbox,1024,components.authority().publicationWriteGuard());
   byte[] bytes={1,2,3,4};String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
   String file=upload(uploads,actor,meeting,bytes,hash),attributed=foreignSource?upload(uploads,actor,meeting,bytes,hash):file;
   if(admit){
    String version=parser.equals(ControlledSpeechProfile.PARSER)?"v3":parser.equals(MediaTranscriptParser.PROFILE_VERSION)?"v2":"v1";
    var archives=new ArrayList<ama.contract.ConfigArchiveContracts.RawArchive>();
    for(String[] resource:new String[][]{{"restart/codec","CANONICAL_CODEC","ama.canonical-value.v1"},{"restart/confirmation","CONFIRMATION_HASH","ama.transcript-confirmation.v1"},{"media-parser-"+version,"PARSER_CONFIG",parser},{"media-processing-"+version,"PROCESSING_CONFIG","ama.import-media-documents."+version}}){
     try(var in=getClass().getResourceAsStream("/ama/transcript/"+resource[0]+".json")){assertNotNull(in);archives.add(new ama.contract.ConfigArchiveContracts.RawArchive(ama.contract.ConfigArchiveContracts.ArtifactType.valueOf(resource[1]),resource[2],in.readAllBytes()));}
    }
    Path directory=java.nio.file.Files.createDirectory(root.resolve("archives-"+id()));
    var pin=new ama.config.file.ControlledArchiveReleaseWriter().publish(directory,null,archives,ama.minutes.ControlledMinutesAdmin.INDEX,ama.minutes.ControlledMinutesAdmin.READ,()->false);
    var snapshot=new ama.config.file.ControlledConfigArchiveSource().open(directory,pin,ama.minutes.ControlledMinutesAdmin.INDEX,ama.minutes.ControlledMinutesAdmin.READ,()->false);
    var processing=snapshot.index().entries().stream().map(ama.config.ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==ama.contract.ConfigArchiveContracts.ArtifactType.PROCESSING_CONFIG).findFirst().orElseThrow();
    var queue=TranscriptImportRuntime.install(source,fences,outbox,components);
    var intake=new JdbcProcessingIntake(source,tx,access,components,fences,queue,outbox,new ama.audit.JdbcAuditAdapter(source),(a,m,r)->components.authority().prepare(components.intake().readDraft(a,m,r.orderedFileIds(),()->false),id(),id(),snapshot,processing,()->false));
    var request=new ama.contract.ProcessingContracts.ImportRequest(0,List.of(file));String key=id();
    var accepted=intake.accept(actor,meeting,key,request,"MEDIA_PROFILE_REGRESSION");
    assertEquals(accepted,intake.accept(actor,meeting,key,request,"MEDIA_PROFILE_REPLAY"));
    assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,accepted.taskId()));
    var manifest=new com.fasterxml.jackson.databind.ObjectMapper().readTree(j.queryForObject("SELECT config_manifest FROM transcription_input WHERE id=?",String.class,accepted.inputId()));
    assertEquals("IMPORT_MEDIA_DOCUMENT",manifest.path("steps").get(0).path("kind").asText());
    assertEquals(parser,manifest.path("configs").path("parser").path("version").asText());
    assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_id=?",Integer.class,meeting));
    return;
   }
   String batch=id(),input=id(),task=id(),revision=id(),producer=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(task.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   j.update("INSERT INTO input_batch(id,meeting_id,kind,input_fingerprint,base_revision,created_at) VALUES (?,?,'TRANSCRIPT',?,1,UTC_TIMESTAMP(6))",batch,meeting,hash);
   j.update("INSERT INTO input_batch_fingerprint_profile(batch_id,profile) VALUES (?,'ama.input-batch.v1')",batch);
   j.update("INSERT INTO input_batch_file(batch_id,ordinal,file_id,sha256) VALUES (?,1,?,?)",batch,file,hash);
   j.update("INSERT INTO transcription_input(id,meeting_id,batch_id,kind,material_mode,processing_config_version,parser_version,manifest_schema_version,config_manifest,input_fingerprint,base_revision,cancel_epoch,created_at) VALUES (?,?,?,'IMPORT','EMPTY','output-policy-fixture',?,1,'{}',?,1,0,UTC_TIMESTAMP(6))",input,meeting,batch,parser,hash);
   j.update("INSERT INTO durable_task(id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,state,attempt,max_attempts,lease_owner,expires_at,available_at,created_at) VALUES (?,'TRANSCRIPT_IMPORT_V1','TRANSCRIPT_IMPORT','MEETING',?,?,'TRANSCRIPTION_INPUT',?,?,1,0,0,'RUNNING',1,3,'MEDIA_OUTPUT_FIXTURE',UTC_TIMESTAMP(6)+INTERVAL 1 MINUTE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",task,meeting,producer,input,hash);
   j.update("INSERT INTO transcript_revision(id,meeting_id,revision,kind,batch_id,input_fingerprint,created_by,created_at,processing_input_id,producer_task_id) VALUES (?,?,1,'RAW',?,?,?,UTC_TIMESTAMP(6),?,?)",revision,meeting,batch,hash,owner,input,task);
   j.update("INSERT INTO transcript_segment(revision_id,segment_id,ordinal,body,source_file_id,source_paragraph,start_ms,end_ms) VALUES (?,?,1,'controlled source text',?,1,0,100)",revision,id(),attributed);
   var spec=new TaskSpec("TRANSCRIPT_IMPORT_V1","TRANSCRIPT_IMPORT",new SubjectRef("MEETING",meeting),producer,new TypedRef("TRANSCRIPTION_INPUT",input),hash,1,0,0,3);
   var result=new TaskResult(Outcome.SUCCEEDED,new TypedRef("TRANSCRIPT_REVISION",revision),null);
   if(allowed)assertDoesNotThrow(()->components.authority().taskPolicy().validateOutput(spec,result));
   else assertThrows(ama.contract.MinutesContracts.Failure.class,()->components.authority().taskPolicy().validateOutput(spec,result));
  }
 }
 String upload(FileUploads uploads,Actor actor,String meeting,byte[] bytes,String hash){
  var accepted=uploads.begin(actor,meeting,id(),new ama.contract.FileContracts.Begin("TRANSCRIPT_INPUT","原文件.wav",bytes.length,hash),"MEDIA_OUTPUT");
  uploads.part(actor,accepted.uploadId(),1,new ByteArrayInputStream(bytes),bytes.length,hash,"MEDIA_OUTPUT");
  return uploads.complete(actor,accepted.uploadId(),new ama.contract.FileContracts.Completion(List.of(new ama.contract.FileContracts.ClientPart(1,hash)),hash),"MEDIA_OUTPUT").fileId();
 }
}
