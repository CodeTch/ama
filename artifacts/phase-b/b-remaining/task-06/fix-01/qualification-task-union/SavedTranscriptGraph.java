package ama.transcript;
import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.*;
import ama.storage.FileRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static ama.transcript.SavedTranscriptRows.*;
/** Complete detached S/F facts. No authority is carried by this record. */
record SavedTranscriptGraph(String meeting,String selected,Map<String,Object> root,List<FullRow> rows,
 List<Map<String,Object>> sessions,List<Map<String,Object>> files,Map<String,Object> task,
 String profile,List<Map<String,Object>> sourceBytes,long maximumHistory) {
 static final Shape ROOT=metadata("meeting","id","id owner_id revision# cancel_epoch# access_revision# stage lifecycle governance_state ever_published# transcript_revision_id transcript_confirmation_id final_confirmation_id needs_generation#");
 static final Shape FILE=metadata("file_object","id","id meeting_id backend_id storage_type namespace object_key size_bytes# sha256 state location_revision# reference_epoch#");
 static final Shape SESSION=metadata("upload_session","id","id meeting_id purpose cancel_epoch# backend_id namespace object_key expected_size# expected_sha256 state completed_file_id");
 static final Shape TASK=metadata("durable_task","id",BoundedTranscriptMetadata.TASK+" state output_kind output_id");
 record Discovery(String selected,SortedMap<String,List<String>> ancestry) {}
 static Discovery discover(JdbcTemplate j,String meeting,String selected,TranscriptAcquisitionBudget limits,TranscriptAcquisitionBudget.Ledger budget){
  if(selected==null){budget.row(1);budget.text(36,36,36,2,false);var roots=j.queryForList("SELECT transcript_revision_id FROM meeting WHERE id=?",String.class,meeting);require(roots.size()==1,PROVENANCE_UNAVAILABLE);selected=roots.getFirst();}
  var ids=new TreeMap<String,List<String>>();String next=selected;
  while(next!=null){
   uuid(next);require(ids.size()<Math.min(limits.revisionDepth(),limits.revisionNodes())&&!ids.containsKey(next),BUDGET_EXCEEDED);
   budget.row(6);budget.text(256,256,256,2,false);
   var found=j.query("SELECT id,parent_id,processing_input_id,batch_id,producer_task_id,kind FROM transcript_revision WHERE id=? AND meeting_id=?",(r,n)->Arrays.asList(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6)),next,meeting);
   if(found.isEmpty())throw new ama.contract.FileContracts.NotFound();require(found.size()==1,PROVENANCE_UNAVAILABLE);var row=found.getFirst();ids.put(next,row);
   if("RAW".equals(row.get(5))){require(row.get(1)==null&&row.get(4)!=null,PROVENANCE_UNAVAILABLE);next=null;}
   else {require("WORKING".equals(row.get(5))&&row.get(1)!=null&&row.get(4)==null,PROVENANCE_UNAVAILABLE);next=row.get(1);}
  }
  return new Discovery(selected,Collections.unmodifiableSortedMap(ids));
 }
 static SavedTranscriptGraph acquire(JdbcTranscriptAuthority authority,JdbcTemplate j,FileRegistry.LockedMeeting gate,Object scope,Actor actor,String meeting,JdbcTranscriptAuthority.SavedPurpose purpose,Discovery discovered,TranscriptAcquisitionBudget limits,TranscriptAcquisitionBudget.Ledger budget){
  authority.requireSavedScope(scope,gate,actor,meeting,purpose);var read=new SavedTranscriptRows(j,budget,authority.readBudget().maxManifestBytes());var root=read.one(ROOT,meeting);
  if(purpose==JdbcTranscriptAuthority.SavedPurpose.CURRENT)require(Objects.equals(root.get("transcript_revision_id"),discovered.selected),PROVENANCE_UNAVAILABLE);
  if(discovered.selected==null){
   if(purpose==JdbcTranscriptAuthority.SavedPurpose.CURRENT&&root.get("transcript_confirmation_id")!=null){
    read.rows(Table.TRANSCRIPT_CONFIRMATION,"id=?","id",1,true,root.get("transcript_confirmation_id"));
    throw fail(PROVENANCE_UNAVAILABLE);
   }
   return new SavedTranscriptGraph(meeting,null,root,List.of(),List.of(),List.of(),Map.of(),null,List.of(),0);
  }
  var rows=new ArrayList<FullRow>();var revisions=new TreeMap<String,FullRow>();
  for(var entry:discovered.ancestry.entrySet()){
   var actual=read.rows(Table.TRANSCRIPT_REVISION,"id=?","id",1,false,entry.getKey());require(actual.size()==1,PROVENANCE_UNAVAILABLE);var r=actual.getFirst();
   require(meeting.equals(r.string("meeting_id"))&&Arrays.asList(r.string("id"),r.nullableString("parent_id"),r.string("processing_input_id"),r.string("batch_id"),r.nullableString("producer_task_id"),r.string("kind")).equals(entry.getValue()),PROVENANCE_UNAVAILABLE);
   rows.add(r);revisions.put(entry.getKey(),r);
  }
  int maxRows=(int)Math.min(Integer.MAX_VALUE-2L,authority.readBudget().maxFactRows());
  // Every revision parent is held before any segment; every segment parent before any mark.
  var transfers=new ArrayList<java.util.function.Supplier<List<FullRow>>>();
  for(String id:revisions.keySet())transfers.add(read.deferRows(Table.TRANSCRIPT_SEGMENT,"revision_id=?","segment_id",maxRows,id.equals(discovered.selected),id));
  for(String id:revisions.keySet())transfers.add(read.deferRows(Table.TRANSCRIPT_SEGMENT_MARK,"revision_id=?","segment_id,mark",maxRows,id.equals(discovered.selected),id));
  var chosen=revisions.get(discovered.selected);
  if(purpose==JdbcTranscriptAuthority.SavedPurpose.CURRENT&&root.get("transcript_confirmation_id")!=null){
   // PK plus the unique (meeting,revision) key define at most two declared rows. Acquire their
   // complete set in PK order, including a corrupt pointed row, before rejecting any mismatch.
   var confirmations=read.rows(Table.TRANSCRIPT_CONFIRMATION,"id=? OR (meeting_id=? AND revision_id=?)","id",2,true,root.get("transcript_confirmation_id"),meeting,discovered.selected);
   require("WORKING".equals(chosen.string("kind"))&&confirmations.size()==1,PROVENANCE_UNAVAILABLE);
   var current=confirmations.getFirst();require(current.string("id").equals(root.get("transcript_confirmation_id"))&&current.string("meeting_id").equals(meeting)&&current.string("revision_id").equals(discovered.selected),PROVENANCE_UNAVAILABLE);
   rows.addAll(confirmations);
  }else rows.addAll(read.rows(Table.TRANSCRIPT_CONFIRMATION,"meeting_id=? AND revision_id=?","id",1,true,meeting,discovered.selected));
  String inputId=chosen.string("processing_input_id"),batchId=chosen.string("batch_id");
  var inputTransfer=read.deferRows(Table.TRANSCRIPTION_INPUT,"id=?","id",1,false,inputId);
  var batchRows=read.rows(Table.INPUT_BATCH,"id=?","id",1,false,batchId);require(batchRows.size()==1,PROVENANCE_UNAVAILABLE);rows.addAll(batchRows);
  var profileRows=read.read(metadata("input_batch_fingerprint_profile","batch_id","batch_id profile"),"batch_id=?","batch_id",1,false,batchId);require(profileRows.size()==1&&BatchFingerprint.PROFILE.equals(profileRows.getFirst().get("profile")),PROFILE_UNAVAILABLE);
  var members=read.rows(Table.INPUT_BATCH_FILE,"batch_id=?","ordinal",authority.readBudget().maxBatchFiles(),true,batchId);require(!members.isEmpty(),PROVENANCE_UNAVAILABLE);rows.addAll(members);
  var associations=new TreeMap<String,String>();var sortedFiles=new TreeSet<String>();for(var member:members)require(sortedFiles.add(member.string("file_id")),PROVENANCE_UNAVAILABLE);
  for(String file:sortedFiles){budget.row(1);budget.text(72,72,72,2,false);var original=j.queryForList("SELECT id FROM upload_session WHERE completed_file_id=? AND state='COMPLETED' ORDER BY id LIMIT 2",String.class,file);require(original.size()==1,PROVENANCE_UNAVAILABLE);associations.put(file,original.getFirst());}
  var sessions=new ArrayList<Map<String,Object>>();var sessionByFile=new HashMap<String,Map<String,Object>>();
  for(String id:new TreeSet<>(associations.values())){var s=read.one(SESSION,id);sessions.add(s);require("COMPLETED".equals(s.get("state"))&&"TRANSCRIPT_INPUT".equals(s.get("purpose"))&&meeting.equals(s.get("meeting_id"))&&id.equals(associations.get(s.get("completed_file_id"))),PROVENANCE_UNAVAILABLE);sessionByFile.put((String)s.get("completed_file_id"),s);}
  var files=new ArrayList<Map<String,Object>>();var byteStatus=new ArrayList<Map<String,Object>>();
  for(String id:sortedFiles){
   var f=read.one(FILE,id);files.add(f);var s=sessionByFile.get(id);require(s!=null&&meeting.equals(f.get("meeting_id")),PROVENANCE_UNAVAILABLE);
   for(String col:List.of("backend_id","namespace","object_key"))require(Objects.equals(f.get(col),s.get(col)),PROVENANCE_UNAVAILABLE);
   require(Objects.equals(f.get("sha256"),s.get("expected_sha256"))&&Objects.equals(f.get("size_bytes"),s.get("expected_size")),PROVENANCE_UNAVAILABLE);
   var originals=j.queryForList("SELECT id FROM upload_session WHERE completed_file_id=? AND state='COMPLETED' ORDER BY id LIMIT 2",String.class,id);require(originals.equals(List.of(associations.get(id))),PROVENANCE_UNAVAILABLE);
   require(j.queryForObject("SELECT COUNT(*) FROM file_reference WHERE file_id=? AND owner_kind='INPUT_BATCH_FILE' AND owner_id=? AND purpose='TRANSCRIPT_INPUT'",Long.class,id,batchId)==1,PROVENANCE_UNAVAILABLE);
   String status="ELIGIBLE",reason="AVAILABLE";
   if(!Objects.equals(s.get("cancel_epoch"),root.get("cancel_epoch"))){status="INELIGIBLE";reason="SOURCE_EPOCH_CHANGED";}
   else if(!"AVAILABLE".equals(f.get("state"))){status="INELIGIBLE";reason="FILE_UNAVAILABLE";}
   else try{authority.savedRouteStatus((String)f.get("backend_id"));}catch(ama.contract.FileContracts.Unavailable unavailable){status="UNAVAILABLE";reason="ROUTE_UNAVAILABLE";}
   byteStatus.add(Map.of("fileId",id,"eligibility",status,"reason",reason));
  }
  if(purpose!=JdbcTranscriptAuthority.SavedPurpose.OWNER_INSPECTION){
   var held=authority.fileRegistry().prelockSources(gate,List.copyOf(sortedFiles),false).orElseThrow(()->fail(CAPABILITY_UNAVAILABLE));
   var sources=authority.fileRegistry().sources(gate,held,List.copyOf(sortedFiles));require(sources.size()==files.size(),PROVENANCE_UNAVAILABLE);
   for(var source:sources)require("TRANSCRIPT_INPUT".equals(source.originalPurpose()),PROVENANCE_UNAVAILABLE);
  }
  var raw=revisions.values().stream().filter(r->r.string("kind").equals("RAW")).toList();require(raw.size()==1&&limits.tasks()>=1,PROVENANCE_UNAVAILABLE);
  var declaredTasks=read.read(TASK,"(input_kind='TRANSCRIPTION_INPUT' AND input_id=?) OR id=?","id",limits.tasks(),false,inputId,raw.getFirst().string("producer_task_id"));
  require(declaredTasks.size()==1&&raw.getFirst().string("producer_task_id").equals(declaredTasks.getFirst().get("id")),PROVENANCE_UNAVAILABLE);
  var task=declaredTasks.getFirst();require("SUCCEEDED".equals(task.get("state"))&&"TRANSCRIPT_REVISION".equals(task.get("output_kind"))&&raw.getFirst().string("id").equals(task.get("output_id")),PROVENANCE_UNAVAILABLE);
  budget.row(1);var maximum=j.queryForList("SELECT revision FROM transcript_revision WHERE meeting_id=? ORDER BY revision DESC LIMIT 1",Long.class,meeting);require(maximum.size()==1,PROVENANCE_UNAVAILABLE);
  require(maximum.getFirst()<=num(root,"revision"),PROVENANCE_UNAVAILABLE);
  // Every content-bearing set is now measured, reserved and retained under its actual parent/row locks.
  var inputRows=inputTransfer.get();require(inputRows.size()==1,PROVENANCE_UNAVAILABLE);rows.addAll(inputRows);for(var transfer:transfers)rows.addAll(transfer.get());
  return new SavedTranscriptGraph(meeting,discovered.selected,root,List.copyOf(rows),List.copyOf(sessions),List.copyOf(files),task,(String)profileRows.getFirst().get("profile"),List.copyOf(byteStatus),maximum.getFirst());
 }
 StoredProvenanceFacts facts(long maximum){return new StoredProvenanceFacts(rows,files.stream().map(f->new ContentFile((String)f.get("id"),(String)f.get("sha256"),num(f,"size_bytes"),meeting)).toList(),List.of(),List.of(),List.of(),List.of(),task.isEmpty()?List.of():List.of(new StoredTask((String)task.get("id"),TranscriptReadGraph.spec(task))),maximum);}
 FullRow selectedRow(){return rows.stream().filter(r->r.table()==Table.TRANSCRIPT_REVISION&&r.string("id").equals(selected)).findFirst().orElseThrow(()->fail(PROVENANCE_UNAVAILABLE));}
 FullRow input(){return rows.stream().filter(r->r.table()==Table.TRANSCRIPTION_INPUT).findFirst().orElseThrow(()->fail(PROVENANCE_UNAVAILABLE));}
 List<FullRow> segments(String id){return rows.stream().filter(r->r.table()==Table.TRANSCRIPT_SEGMENT&&r.string("revision_id").equals(id)).sorted(Comparator.comparingLong(r->r.number("ordinal"))).toList();}
 static long num(Map<String,Object> map,String name){Object n=map.get(name);require(n instanceof Long,PROVENANCE_UNAVAILABLE);return (Long)n;}
}
