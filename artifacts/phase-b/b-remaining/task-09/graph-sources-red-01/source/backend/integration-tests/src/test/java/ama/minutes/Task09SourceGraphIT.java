package ama.minutes;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Adversarial row-closure fixtures, deliberately not an attested import or generation pipeline. */
class Task09SourceGraphIT extends Task09GraphDiscoveryIT {
 final class SourceFixture {
  final DataSource source=Task09Source.open();final JdbcTemplate j=new JdbcTemplate(source);
  final String m=meeting(j),owner=j.queryForObject("SELECT owner_id FROM meeting WHERE id=?",String.class,m),file=id(),session=id(),batch=id(),input=id(),raw=id(),confirmation=id(),task=id(),operation=id(),execution=id(),backend="t09-source-"+id();
  SourceFixture(){
   j.update("INSERT INTO task_subject(subject_kind,subject_id,business_version,cancel_epoch,deleted) VALUES ('MEETING',?,0,0,0)",m);
   j.update("INSERT INTO storage_backend(id,storage_type,status,config_revision) VALUES (?,'local','DISABLED',1)",backend);
   j.update("INSERT INTO file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,display_name,size_bytes,sha256,state,created_at) VALUES (?,?,?,'local','task09',?,'metadata fixture',1,?,'AVAILABLE',UTC_TIMESTAMP(6))",file,m,backend,file,"a".repeat(64));
   j.update("INSERT INTO upload_session(id,meeting_id,backend_id,namespace,object_key,cancel_epoch,expected_size,expected_sha256,state,completed_file_id,created_at,updated_at,display_name,purpose) VALUES (?,?,?,'task09',?,0,1,?,'COMPLETED',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'metadata fixture','TRANSCRIPT_INPUT')",session,m,backend,file,"a".repeat(64),file);
   j.update("INSERT INTO input_batch(id,meeting_id,kind,input_fingerprint,base_revision,created_at) VALUES (?,?,'TRANSCRIPT',?,0,UTC_TIMESTAMP(6))",batch,m,"a".repeat(64));
   j.update("INSERT INTO input_batch_file(batch_id,ordinal,file_id,sha256) VALUES (?,1,?,?)",batch,file,"a".repeat(64));
   j.update("INSERT INTO input_batch_fingerprint_profile(batch_id,profile) VALUES (?,'ama.input-batch.v1')",batch);
   j.update("INSERT INTO file_reference(file_id,owner_kind,owner_id,purpose) VALUES (?,'INPUT_BATCH_FILE',?,'TRANSCRIPT_INPUT')",file,batch);
   j.update("INSERT INTO transcription_input(id,meeting_id,batch_id,kind,material_mode,processing_config_version,parser_version,manifest_schema_version,config_manifest,input_fingerprint,base_revision,cancel_epoch,created_at) VALUES (?,?,?,'IMPORT','EMPTY','fixture-processing','fixture-parser',1,'{}',?,0,0,UTC_TIMESTAMP(6))",input,m,batch,"a".repeat(64));
   task(task,"TRANSCRIPTION_INPUT",input);
   j.update("INSERT INTO task_attempt(task_id,attempt,owner,started_at,ended_at,outcome) VALUES (?,1,'task09',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'SUCCEEDED')",task);
   j.update("INSERT INTO transcript_revision(id,meeting_id,revision,kind,batch_id,input_fingerprint,processing_input_id,producer_task_id,created_by,created_at) VALUES (?,?,1,'RAW',?,?,?,?,?,UTC_TIMESTAMP(6))",raw,m,batch,"a".repeat(64),input,task,owner);
   j.update("INSERT INTO transcript_confirmation(id,meeting_id,revision_id,content_hash,hash_profile,confirmed_by,confirmed_at) VALUES (?,?,?,?,'ama.confirmation.v1',?,UTC_TIMESTAMP(6))",confirmation,m,raw,"a".repeat(64),owner);
   j.update("UPDATE meeting SET transcript_confirmation_id=?,transcript_revision_id=? WHERE id=?",confirmation,raw,m);
  }
  void task(String id,String kind,String inputId){j.update("INSERT INTO durable_task(id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,state,max_attempts,available_at,created_at) VALUES (?,'TRANSCRIPT_IMPORT_V1','TRANSCRIPT_IMPORT','MEETING',?,?,?,?,?,0,0,0,'READY',3,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id,m,id.replace("-","")+id.replace("-",""),kind,inputId,"a".repeat(64));}
  Object graph()throws Exception{return discover(source,m,"CURRENT_GENERATION",null,1000);}
 }
 @Test void enumeratesOriginalSessionFileReferenceAndEveryTypedInputTaskAttempt()throws Exception {
  var f=new SourceFixture();String extra=id(),wrongKind=id();f.task(extra,"TRANSCRIPTION_INPUT",f.input);f.task(wrongKind,"GENERATION_INPUT",f.input);
  Object graph=f.graph();Map<String,List<Map<String,Object>>> tables=field(graph,"tables");
  assertEquals(1,tables.getOrDefault("upload_session",List.of()).size());assertEquals(1,tables.get("file_object").size());assertEquals(1,tables.get("file_reference").size());
  assertEquals(Set.of(f.task,extra),new HashSet<>(tables.get("durable_task").stream().map(r->(String)r.get("id")).toList()));assertEquals(1,tables.get("task_attempt").size());
 }
 @Test void discoversWholeExternalHistoryWithoutLongtextBeforeAnyCapture()throws Exception {
  var f=new SourceFixture();f.j.update("INSERT INTO external_operation(id,operation_kind,business_key,request_hash,state,marked_at,next_check_at,subject_kind,subject_id,current_execution_no) VALUES (?,'MINUTES_GENERATE',?,?,'UNKNOWN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'MEETING',?,1)",f.operation,f.input,"a".repeat(64),f.m);
  f.j.update("INSERT INTO external_execution(id,operation_id,execution_no,task_id,state,request_hash,request_manifest,created_at) VALUES (?,?,1,?,'PREPARED',?,'private manifest',UTC_TIMESTAMP(6))",f.execution,f.operation,f.task,"a".repeat(64));
  Object graph=f.graph();Map<String,List<Map<String,Object>>> tables=field(graph,"tables");assertEquals(1,tables.getOrDefault("external_operation",List.of()).size());assertEquals(1,tables.get("external_execution").size());assertFalse(tables.get("external_execution").getFirst().containsKey("request_manifest"));
  Map<String,List<List<Object>>> sets=field(graph,"memberships");assertEquals(List.of(List.of(f.execution)),sets.get("external_execution|operation_id|"+f.operation));
 }
}
