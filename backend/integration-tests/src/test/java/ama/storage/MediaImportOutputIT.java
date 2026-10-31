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
 @Test void currentMediaProfileAllowsItsActualTimedSource()throws Exception {validate(MediaTranscriptParser.PROFILE_VERSION,true,false);}
 @Test void historicalMediaProfileKeepsItsTimedSourceCompatibility()throws Exception {validate(MediaTranscriptParser.LEGACY_PROFILE_VERSION,true,false);}
 @Test void textProfileStillCannotClaimMediaTimestamps()throws Exception {validate(TextTranscriptParser.PROFILE_VERSION,false,false);}
 @Test void mediaProfileCannotAttributeATimedSegmentToAnUnissuedSource()throws Exception {validate(MediaTranscriptParser.PROFILE_VERSION,false,true);}
 void validate(String parser,boolean allowed,boolean foreignSource)throws Exception {
  try(var files=new ManagedLocalFixture(root.resolve(id()))){
   var storage=files.startServer();var catalog=new FileBackendCatalog(source,List.of(new FileBackendCatalog.Backend(files.binding.id(),"local",1,"WRITABLE",storage,files.binding)),files.binding.id());catalog.configure();
   var tx=new TransactionRunner(source);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);var outbox=new JdbcOutbox(source,new MeetingEventReferences(source).validators());
   String owner="media-output-"+id();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",owner,owner);var actor=new Actor(owner,Status.ACTIVE,Set.of(Role.USER));
   var meetings=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,outbox,new ama.audit.JdbcAuditAdapter(source),m->{}));String meeting=meetings.create(actor,id(),"media-output-policy").id();
   FilePurposePolicy purposes=(a,m,p)->{if(!p.equals("TRANSCRIPT_INPUT"))throw new FilePurposePolicy.Unavailable();};
   var components=JdbcTranscriptAuthority.create(source,tx,access,purposes,catalog,Map.of(),ama.minutes.ControlledMinutesAdmin.READ,new ama.contract.MinutesContracts.CanonicalBudget(16777216,32,1000000,4194304,250000));
   var uploads=new JdbcFileUploads(source,tx,access,purposes,catalog,new ama.audit.JdbcAuditAdapter(source),outbox,1024,components.authority().publicationWriteGuard());
   byte[] bytes={1,2,3,4};String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
   String file=upload(uploads,actor,meeting,bytes,hash),attributed=foreignSource?upload(uploads,actor,meeting,bytes,hash):file;
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
