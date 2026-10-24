package ama.transcript;

import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;
import ama.contract.MinutesContracts.CanonicalBudget;
import ama.contract.TaskContracts.*;
import ama.storage.FileRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.function.Function;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static ama.transcript.BoundedTranscriptMetadata.*;

/** Detached bounded observations only. Construction cannot mint an authorization scope. */
final class TranscriptReadGraph {
 final Map<String,Object> root,input,batch,profile;
 final SortedMap<String,Map<String,Object>> revisions,files,tasks;
 final List<Map<String,Object>> members;
 final String raw,requested;
 final TaskSpec expected;
 private TranscriptReadGraph(Map<String,Object> root,Map<String,Object> input,Map<String,Object> batch,Map<String,Object> profile,
   SortedMap<String,Map<String,Object>> revisions,SortedMap<String,Map<String,Object>> files,SortedMap<String,Map<String,Object>> tasks,
   List<Map<String,Object>> members,String raw,String requested,TaskSpec expected){
  this.root=root;this.input=input;this.batch=batch;this.profile=profile;this.revisions=Collections.unmodifiableSortedMap(revisions);
  this.files=Collections.unmodifiableSortedMap(files);this.tasks=Collections.unmodifiableSortedMap(tasks);this.members=members;this.raw=raw;this.requested=requested;this.expected=expected;
 }
 static TranscriptReadGraph discover(JdbcTemplate j,ArchiveReadBudget budget,CanonicalBudget canonical,String meeting,
   String revision,String workerInput,String workerTask,String requested,Function<Map<String,Object>,String> producer){
  var read=new BoundedTranscriptMetadata(j,budget);var root=read.one("meeting",ROOT,meeting);
  var revisions=new TreeMap<String,Map<String,Object>>();String raw=null,inputId=workerInput;
  if(workerInput==null){
   String next=revision;int depth=0;
   while(next!=null){
    read.check();uuid(next);require(++depth<=budget.maxGraphDepth(),BUDGET_EXCEEDED);require(!revisions.containsKey(next),PROVENANCE_UNAVAILABLE);
    var row=read.one("transcript_revision",REVISION,next);revisions.put(next,row);
    require(meeting.equals(row.get("meeting_id")),PROVENANCE_UNAVAILABLE);
    if("RAW".equals(row.get("kind"))){require(row.get("parent_id")==null&&row.get("producer_task_id")!=null,PROVENANCE_UNAVAILABLE);raw=next;inputId=text(row,"processing_input_id");break;}
    require("WORKING".equals(row.get("kind"))&&row.get("producer_task_id")==null&&row.get("parent_id")!=null,PROVENANCE_UNAVAILABLE);
    next=text(row,"parent_id");
   }
   require(raw!=null,PROVENANCE_UNAVAILABLE);
  }
  uuid(inputId);var input=read.one("transcription_input",INPUT,inputId);
  require(meeting.equals(input.get("meeting_id"))&&"IMPORT".equals(input.get("kind"))&&"EMPTY".equals(input.get("material_mode"))
    &&input.get("source_revision_id")==null&&input.get("material_snapshot_id")==null&&input.get("model_config_version")==null&&input.get("prompt_version")==null
    &&number(input,"manifest_schema_version")==1&&input.get("processing_config_version")!=null&&input.get("parser_version")!=null,PROVENANCE_UNAVAILABLE);
  var batch=read.one("input_batch",BATCH,text(input,"batch_id"));String batchId=text(batch,"id");
  require(meeting.equals(batch.get("meeting_id"))&&"TRANSCRIPT".equals(batch.get("kind"))&&number(batch,"base_revision")==number(input,"base_revision"),PROVENANCE_UNAVAILABLE);
  var profiles=read.select("input_batch_fingerprint_profile","batch_id profile","batch_id=?","batch_id",2,batchId);
  require(profiles.size()==1&&BatchFingerprint.PROFILE.equals(profiles.getFirst().get("profile")),PROFILE_UNAVAILABLE);var profile=profiles.getFirst();
  var members=read.select("input_batch_file",MEMBER,"batch_id=?","ordinal",budget.maxBatchFiles(),batchId);require(!members.isEmpty(),PROVENANCE_UNAVAILABLE);
  var files=new TreeMap<String,Map<String,Object>>();var fingerprintMembers=new ArrayList<BatchFingerprint.Member>();long ordinal=1;
  for(var m:members){
   read.check();require(number(m,"ordinal")==ordinal++&&batchId.equals(m.get("batch_id")),PROVENANCE_UNAVAILABLE);String fileId=text(m,"file_id");uuid(fileId);
   require(!files.containsKey(fileId),PROVENANCE_UNAVAILABLE);var file=read.one("file_object",FILE,fileId);files.put(fileId,file);
   require(meeting.equals(file.get("meeting_id"))&&Objects.equals(m.get("sha256"),file.get("sha256")),PROVENANCE_UNAVAILABLE);
   fingerprintMembers.add(new BatchFingerprint.Member(number(m,"ordinal"),fileId,text(m,"sha256"),number(file,"size_bytes")));
  }
  require(files.containsKey(requested),SOURCE_NOT_IN_INPUT);
  String digest=new BatchFingerprint().compute(meeting,batchId,"TRANSCRIPT",number(batch,"base_revision"),fingerprintMembers,canonical,()->false);
  require(digest.equals(batch.get("input_fingerprint")),FINGERPRINT_MISMATCH);
  for(var row:revisions.values())require(batchId.equals(row.get("batch_id"))&&inputId.equals(row.get("processing_input_id"))&&Objects.equals(input.get("input_fingerprint"),row.get("input_fingerprint")),PROVENANCE_UNAVAILABLE);
  var expected=new TaskSpec("TRANSCRIPT_IMPORT_V1","TRANSCRIPT_IMPORT",new SubjectRef("MEETING",meeting),producer.apply(input),new TypedRef("TRANSCRIPTION_INPUT",inputId),text(input,"input_fingerprint"),number(input,"base_revision"),0,number(input,"cancel_epoch"),3);
  String taskId=workerTask==null?text(revisions.get(raw),"producer_task_id"):workerTask;uuid(taskId);
  var tasks=new TreeMap<String,Map<String,Object>>();
  var declared=read.select("durable_task",TASK,"(input_kind='TRANSCRIPTION_INPUT' AND input_id=?) OR id=?","id",(int)Math.min(Integer.MAX_VALUE-1L,budget.maxFactRows()),inputId,taskId);
  require(declared.size()==1&&taskId.equals(declared.getFirst().get("id")),PROVENANCE_UNAVAILABLE);
  var task=declared.getFirst();tasks.put(taskId,task);require(spec(task).equals(expected),PROVENANCE_UNAVAILABLE);
  return new TranscriptReadGraph(root,input,batch,profile,revisions,files,tasks,members,raw,requested,expected);
 }
 static TaskSpec spec(Map<String,Object> row){
  return new TaskSpec(text(row,"task_type"),text(row,"pool"),new SubjectRef(text(row,"subject_kind"),text(row,"subject_id")),text(row,"producer_key"),new TypedRef(text(row,"input_kind"),text(row,"input_id")),text(row,"input_fingerprint"),number(row,"business_version"),Math.toIntExact(number(row,"priority")),number(row,"cancel_epoch"),Math.toIntExact(number(row,"max_attempts")));
 }
 static String text(Map<String,Object> row,String name){Object value=row.get(name);require(value instanceof String,PROVENANCE_UNAVAILABLE);return (String)value;}
 static long number(Map<String,Object> row,String name){Object value=row.get(name);require(value instanceof Number,PROVENANCE_UNAVAILABLE);return ((Number)value).longValue();}
}
