package ama.transcript;

import ama.contract.MinutesContracts.*;
import ama.contract.IdentityContracts.Actor;
import ama.storage.FileRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.function.Supplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static ama.transcript.SavedTranscriptRows.*;

/** Retained root/parent/row locks precede every complete bounded content transfer.
 * Captured graph is detached data; only the private authority scope records successful acquisition. */
final class MinutesGraphCapture {
 record Graph(MinutesGraphDiscovery.Discovery discovery,List<FullRow> rows,Map<String,List<Map<String,Object>>> auxiliary) {}
 static final List<String> DOMAIN_RANK=List.of("transcript_revision","transcript_segment","transcript_segment_mark","transcript_confirmation","transcription_input","input_batch","input_batch_fingerprint_profile","input_batch_file","minutes_template","template_revision","exclusion_revision","sensitive_exclusion","generation_input","minutes_revision","element_set","minutes_element","action_item","action_suggestion","sensitive_detection","background_note","source_locator","sensitive_detection_source","background_note_source","evidence_link","result_review","result_review_evidence","final_result_review","generation_candidate");
 static final Shape TEMPLATE=new Shape("minutes_template",List.of("id"),List.of("id","owner_id","name","name_normalized","name_hash","current_revision_id","revision","created_at"),Set.of("revision"),Set.of("created_at"));
 static final Shape CANDIDATE=new Shape("generation_candidate",List.of("id"),List.of("id","meeting_id","input_id","task_id","minutes_revision_id","element_set_id","base_revision","input_fingerprint","state","created_at","decided_at"),Set.of("base_revision"),Set.of("created_at","decided_at"));
 static Graph capture(JdbcTranscriptAuthority authority,Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphDiscovery.Discovery expected,TranscriptAcquisitionBudget.Ledger budget){
  return captureMode(authority,scope,gate,actor,expected,budget,false,null);
 }
 static Graph captureCurrent(JdbcTranscriptAuthority authority,Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphDiscovery.Discovery expected,TranscriptAcquisitionBudget.Ledger budget){return captureMode(authority,scope,gate,actor,expected,budget,true,null);}
 static Graph captureTask(JdbcTranscriptAuthority authority,Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphDiscovery.Discovery expected,TranscriptAcquisitionBudget.Ledger budget,boolean currentSources,Runnable publication){return captureMode(authority,scope,gate,actor,expected,budget,currentSources,publication);}
 private static Graph captureMode(JdbcTranscriptAuthority authority,Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphDiscovery.Discovery expected,TranscriptAcquisitionBudget.Ledger budget,boolean currentSources,Runnable publication){
  authority.requireMinutesScope(scope,gate,actor,expected.meeting());budget.check();
  MinutesGraphDiscovery.lockAndCompare(authority.dataSource(),expected,"meeting",budget);
  var j=new JdbcTemplate(authority.dataSource());j.setQueryTimeout(2);var read=new SavedTranscriptRows(j,budget,authority.readBudget().maxManifestBytes());
  var rows=new ArrayList<FullRow>();var auxiliary=new TreeMap<String,List<Map<String,Object>>>();var transfers=new ArrayList<Supplier<List<FullRow>>>();var auxiliaryTransfers=new LinkedHashMap<String,List<Supplier<List<Map<String,Object>>>>>();
  // Control rows precede all domain locks. Missing editor guard remains an explicit empty set.
  auxiliary.put("meeting_minutes_editor_guard",read.read(metadata("meeting_minutes_editor_guard","meeting_id","meeting_id intent_epoch#"),"meeting_id=?","meeting_id",1,false,expected.meeting()));
  for(String name:List.of("meeting_info_revision","roster_revision","roster_member")){
   MinutesGraphDiscovery.lockAndCompare(authority.dataSource(),expected,name,budget);
   Shape control=switch(name){case "meeting_info_revision"->new Shape(name,List.of("id"),List.of("id","meeting_id","revision","name","category_id","summary","actual_meeting_date","created_by","created_at"),Set.of("revision"),Set.of("created_at"));case "roster_revision"->new Shape(name,List.of("id"),List.of("id","meeting_id","revision","state","confirmed_at","confirmed_by"),Set.of("revision"),Set.of("confirmed_at"));default->metadata(name,"roster_id,member_id","roster_id member_id enterprise_id display_name member_kind");};
   for(var row:expected.tables().getOrDefault(name,List.of()))auxiliaryTransfers.computeIfAbsent(name,k->new ArrayList<>()).add(read.deferRead(control,String.join(" AND ",control.keys().stream().map(k->k+"=?").toList()),String.join(",",control.keys()),1,false,control.keys().stream().map(row::get).toArray()));
  }
  auxiliary.put("publication",read.read(metadata("publication","id","id meeting_id confirmation_id state revision#"),"meeting_id=?","id",1,false,expected.meeting()));
  if(expected.pending()!=null){require(auxiliary.get("publication").equals(expected.pending().rows().get("publication")),PROVENANCE_UNAVAILABLE);require(auxiliary.get("publication").isEmpty(),CAPABILITY_UNAVAILABLE);}
  if(publication!=null)publication.run();
  var finalIds=new TreeSet<String>();if(expected.root().get("final_confirmation_id") instanceof String id)finalIds.add(id);for(var row:expected.tables().getOrDefault("final_confirmation",List.of()))finalIds.add((String)row.get("id"));
  MinutesGraphDiscovery.lockAndCompare(authority.dataSource(),expected,"final_confirmation",budget);
  for(String id:finalIds){if(expected.kind().equals("FINAL")&&id.equals(expected.selected()))transfers.add(read.deferRows(Table.FINAL_CONFIRMATION,"id=?","id",1,false,id));else auxiliaryTransfers.computeIfAbsent("final_control",k->new ArrayList<>()).add(read.deferRead(shape(Table.FINAL_CONFIRMATION),"id=?","id",1,false,id));}
  var byName=new HashMap<String,Table>();for(Table t:Table.values())byName.put(t.sqlName(),t);
  for(String name:DOMAIN_RANK){
   if(name.equals("minutes_revision")||name.equals("element_set")){
    budget.row(2);budget.capture(36,36,36,4);String index=name.equals("minutes_revision")?"minutes_history":"element_set_revision";
    var highest=j.queryForList("SELECT id,revision FROM "+name+" FORCE INDEX ("+index+") WHERE meeting_id=? ORDER BY revision DESC LIMIT 1 FOR UPDATE",expected.meeting());
    auxiliary.put(name+"_history_max",List.copyOf(highest));
   }
   MinutesGraphDiscovery.lockAndCompare(authority.dataSource(),expected,name,budget);
   var declared=expected.tables().getOrDefault(name,List.of());if(declared.isEmpty())continue;
   Table table=byName.get(name);Shape shape=table!=null?shape(table):name.equals("minutes_template")?TEMPLATE:name.equals("generation_candidate")?CANDIDATE:metadata("input_batch_fingerprint_profile","batch_id","batch_id profile");
   for(var row:declared){
    budget.check();String where=String.join(" AND ",shape.keys().stream().map(k->k+"=?").toList());Object[] keys=shape.keys().stream().map(row::get).toArray();String order=String.join(",",shape.keys());
    if(table!=null)transfers.add(read.deferRows(table,where,order,1,true,keys));
    else auxiliaryTransfers.computeIfAbsent(name,k->new ArrayList<>()).add(read.deferRead(shape,where,order,1,false,keys));
   }
  }
  for(String name:MinutesSourceRows.RANK){
   MinutesGraphDiscovery.lockAndCompare(authority.dataSource(),expected,name,budget);
   Shape shape=MinutesSourceRows.SHAPES.get(name);var declared=new ArrayList<>(expected.tables().getOrDefault(name,List.of()));
   if(name.equals("external_execution"))declared.sort(Comparator.comparing((Map<String,Object> r)->(String)r.get("operation_id")).thenComparingLong(r->(Long)r.get("execution_no")));
   for(var row:declared)auxiliaryTransfers.computeIfAbsent(name,k->new ArrayList<>()).add(read.deferRead(shape,String.join(" AND ",shape.keys().stream().map(k->k+"=?").toList()),String.join(",",shape.keys()),1,false,shape.keys().stream().map(row::get).toArray()));
   if(currentSources&&name.equals("file_reference")){
   var ids=expected.tables().getOrDefault("file_object",List.of()).stream().map(r->(String)r.get("id")).sorted().toList();require(!ids.isEmpty(),PROVENANCE_UNAVAILABLE);authority.requireTaskPurpose(actor,expected.meeting());
   var locked=authority.fileRegistry().prelockSources(gate,ids,false).orElseThrow(()->fail(PROVENANCE_UNAVAILABLE));var originals=authority.fileRegistry().sources(gate,locked,ids);require(originals.size()==ids.size(),PROVENANCE_UNAVAILABLE);
   for(var source:originals){var row=expected.tables().get("file_object").stream().filter(r->source.file().fileId().equals(r.get("id"))).findFirst().orElseThrow(()->fail(PROVENANCE_UNAVAILABLE));require(JdbcTranscriptAuthority.PURPOSE.equals(source.originalPurpose())&&source.file().sha256().equals(row.get("sha256"))&&source.file().size()==((Number)row.get("size_bytes")).longValue(),PROVENANCE_UNAVAILABLE);}
  }
  }
  // No existing-row lock is discovered by a deferred content SELECT; exact protected PKs only.
  for(var transfer:transfers){var found=transfer.get();require(found.size()==1,PROVENANCE_UNAVAILABLE);rows.addAll(found);}
  for(var group:auxiliaryTransfers.entrySet()){var found=new ArrayList<Map<String,Object>>();for(var transfer:group.getValue()){var one=transfer.get();require(one.size()==1,PROVENANCE_UNAVAILABLE);found.addAll(one);}auxiliary.put(group.getKey(),List.copyOf(found));}
  var graph=new Graph(expected,List.copyOf(rows),Collections.unmodifiableMap(auxiliary));authority.sealMinutesCapture(scope,gate,actor,graph);return graph;
 }
}
