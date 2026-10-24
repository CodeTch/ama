package ama.transcript;

import ama.contract.MinutesContracts.*;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Bounded detached domain discovery only. It never reads LONGTEXT, takes a write capability,
 * or certifies provenance. The authentic S/F capture must lock and recheck this complete plan. */
final class MinutesGraphDiscovery {
 record Discovery(String meeting,String kind,String selected,Map<String,Object> root,
    Map<String,List<Map<String,Object>>> tables,Map<String,List<List<Object>>> memberships,Set<String> identities,List<Selector> selectors) {}
 record Selector(String table,String column,Object value,String qualifier) {}
 private record Definition(String name,List<String> keys,List<String> columns) {}
 private record Query(String table,String column,Object value,int depth,boolean required,String qualifier) {Query(String table,String column,Object value,int depth,boolean required){this(table,column,value,depth,required,"");}}
 private static final Set<String> CONTENT=Set.of("body","title","name","themes","reason","target_anchor","section_path","owner_display","owner_mention","config_manifest");
 private static final Map<String,Definition> DEFINITIONS=definitions();
 private final JdbcTemplate j;private final String meeting,kind,selected;private final int maximum;
 private final ArrayDeque<Query> pending=new ArrayDeque<>();
 private final Map<String,Map<List<Object>,Map<String,Object>>> tables=new TreeMap<>();
 private final Map<String,List<List<Object>>> memberships=new TreeMap<>();
 private final Map<String,Selector> selectors=new TreeMap<>();
 private final Set<String> queried=new HashSet<>(),identities=new TreeSet<>();
 private long scanned,bytes;private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
 private MinutesGraphDiscovery(DataSource source,String meeting,String kind,String selected,int maximum){
  uuid(meeting);require(maximum>0&&maximum<=100000,INVALID_ARGUMENT);this.j=new JdbcTemplate(source);j.setQueryTimeout(2);this.meeting=meeting;this.kind=kind;this.selected=selected;this.maximum=maximum;
 }
 static Discovery discover(DataSource source,String meeting,String kind,String selected,int maximum){return new MinutesGraphDiscovery(source,meeting,kind,selected,maximum).run();}
 private Discovery run(){
  var roots=select(new Query("meeting","id",meeting,0,true));require(roots.size()==1,PROVENANCE_UNAVAILABLE);var root=roots.getFirst();identities.add(text(root,"owner_id"));
  parent("meeting_info_revision",root.get("info_revision_id"),1);parent("roster_revision",root.get("roster_revision_id"),1);parent("final_confirmation",root.get("final_confirmation_id"),1);
  switch(kind){
   case "INITIAL_SETTINGS"->parent("template_revision",requiredSelection(),1);
   case "CURRENT_GENERATION"->{for(String col:List.of("transcript_confirmation_id","template_revision_id","exclusion_revision_id","element_set_id"))parent(tableFor(col),root.get(col),1);unsupported(root,"material_snapshot_id");}
   case "SAVED_EFFECTIVE"->{parent("minutes_revision",root.get("minutes_revision_id"),1);parent("element_set",root.get("element_set_id"),1);parent("exclusion_revision",root.get("exclusion_revision_id"),1);}
   case "OWNER_REVISION"->parent("minutes_revision",requiredSelection(),1);
   case "STORED_INPUT"->parent("generation_input",requiredSelection(),1);
   case "CANDIDATE"->parent("generation_candidate",requiredSelection(),1);
   case "REVIEW_BODY"->parent("minutes_revision",requiredSelection(),1);
   case "REVIEW_ELEMENT"->parent("minutes_element",requiredSelection(),1);
   case "FINAL"->parent("final_confirmation",requiredSelection(),1);
   default->throw fail(INVALID_ARGUMENT);
  }
  while(!pending.isEmpty()){
   check();Query query=pending.removeFirst();require(query.depth()<=128,BUDGET_EXCEEDED);String queryKey=query.table()+"|"+query.column()+"|"+query.value()+query.qualifier();
   if(!queried.add(queryKey)){if(query.required())require(!memberships.get(queryKey).isEmpty(),PROVENANCE_UNAVAILABLE);continue;}
   var found=select(query);if(query.required())require(found.size()==1,PROVENANCE_UNAVAILABLE);
   var keys=new ArrayList<List<Object>>();var definition=DEFINITIONS.get(query.table());var group=tables.computeIfAbsent(query.table(),k->new HashMap<>());
   for(var row:found){
    if(row.containsKey("meeting_id"))require(meeting.equals(row.get("meeting_id")),PROVENANCE_UNAVAILABLE);
    List<Object> key=definition.keys().stream().map(row::get).toList();keys.add(key);Map<String,Object> old=group.putIfAbsent(key,row);
    if(old==null){require(group.size()<=switch(query.table()){case "durable_task"->256;case "file_object"->64;case "generation_input","transcription_input","transcript_revision","minutes_revision"->256;default->maximum;},BUDGET_EXCEEDED);expand(query.table(),row,query.depth()+1);}else require(old.equals(row),PROVENANCE_UNAVAILABLE);
   }
   memberships.put(queryKey,List.copyOf(keys));
  }
  var complete=new TreeMap<String,List<Map<String,Object>>>();
  for(var e:tables.entrySet()){var definition=DEFINITIONS.get(e.getKey());var rows=new ArrayList<>(e.getValue().values());rows.sort((a,b)->compare(definition.keys().stream().map(a::get).toList(),definition.keys().stream().map(b::get).toList()));complete.put(e.getKey(),List.copyOf(rows));}
  return new Discovery(meeting,kind,selected,root,Collections.unmodifiableMap(complete),Collections.unmodifiableMap(new TreeMap<>(memberships)),Set.copyOf(identities),List.copyOf(selectors.values()));
 }
 private String requiredSelection(){uuid(selected);return selected;}
 private void parent(String table,Object id,int depth){if(id!=null){uuid((String)id);pending.addLast(new Query(table,DEFINITIONS.get(table).keys().getFirst(),id,depth,true));}}
 private void children(String table,String column,Object id,int depth){require(id!=null,PROVENANCE_UNAVAILABLE);pending.addLast(new Query(table,column,id,depth,false));}
 private void inputTasks(String kind,Object id,int depth){pending.addLast(new Query("durable_task","input_id",id,depth,false," AND input_kind='"+kind+"'"));}
 private void expand(String table,Map<String,Object> r,int depth){
  String id=r.containsKey("id")?text(r,"id"):null;
  switch(table){
   case "transcript_confirmation"->{parent("transcript_revision",r.get("revision_id"),depth);identity(r,"confirmed_by");}
   case "transcript_revision"->{
    require(Set.of("RAW","WORKING").contains(r.get("kind")),CAPABILITY_UNAVAILABLE);
    parent("durable_task",r.get("producer_task_id"),depth);parent("transcript_revision",r.get("parent_id"),depth);parent("transcription_input",r.get("processing_input_id"),depth);parent("input_batch",r.get("batch_id"),depth);
    children("transcript_segment","revision_id",id,depth);children("transcript_segment_mark","revision_id",id,depth);children("transcript_confirmation","revision_id",id,depth);identity(r,"created_by");
   }
   case "transcription_input"->{inputTasks("TRANSCRIPTION_INPUT",id,depth);require("IMPORT".equals(r.get("kind"))&&"EMPTY".equals(r.get("material_mode")),CAPABILITY_UNAVAILABLE);unsupported(r,"material_snapshot_id");unsupported(r,"source_revision_id");parent("input_batch",r.get("batch_id"),depth);}
   case "input_batch"->{pending.addLast(new Query("file_reference","owner_id",id,depth,false," AND owner_kind='INPUT_BATCH_FILE'"));children("input_batch_file","batch_id",id,depth);children("input_batch_fingerprint_profile","batch_id",id,depth);}
   case "template_revision"->{parent("minutes_template",r.get("template_id"),depth);identity(r,"owner_id");}
   case "minutes_template"->{identity(r,"owner_id");}
   case "exclusion_revision"->children("sensitive_exclusion","revision_id",id,depth);
   case "sensitive_exclusion"->parent("source_locator",r.get("locator_id"),depth);
   case "generation_input"->{
    inputTasks("GENERATION_INPUT",id,depth);
    require("FULL".equals(r.get("scope")),CAPABILITY_UNAVAILABLE);unsupported(r,"material_snapshot_id");unsupported(r,"target_element_id");
    parent("transcript_confirmation",r.get("transcript_confirmation_id"),depth);parent("template_revision",r.get("template_revision_id"),depth);parent("exclusion_revision",r.get("exclusion_revision_id"),depth);parent("element_set",r.get("element_set_id"),depth);children("generation_candidate","input_id",id,depth);
   }
   case "minutes_revision"->{parent("minutes_revision",r.get("parent_id"),depth);parent("generation_input",r.get("generation_input_id"),depth);children("evidence_link","minutes_revision_id",id,depth);if(!kind.equals("FINAL"))children("result_review","minutes_revision_id",id,depth);identity(r,"created_by");}
   case "element_set"->{children("minutes_element","set_id",id,depth);children("sensitive_detection","set_id",id,depth);children("background_note","set_id",id,depth);}
   case "minutes_element"->{parent("element_set",r.get("set_id"),depth);children("action_item","element_id",id,depth);children("action_suggestion","element_id",id,depth);children("evidence_link","element_id",id,depth);if(!kind.equals("FINAL"))children("result_review","element_id",id,depth);}
   case "action_item"->{parent("minutes_element",r.get("element_id"),depth);identity(r,"owner_enterprise_id");}
   case "action_suggestion"->{parent("minutes_element",r.get("element_id"),depth);parent("generation_input",r.get("input_id"),depth);}
   case "sensitive_detection"->{parent("element_set",r.get("set_id"),depth);parent("generation_input",r.get("input_id"),depth);children("sensitive_detection_source","detection_id",id,depth);}
   case "background_note"->{parent("element_set",r.get("set_id"),depth);parent("generation_input",r.get("input_id"),depth);children("background_note_source","note_id",id,depth);}
   case "sensitive_detection_source","background_note_source"->parent("source_locator",r.get("locator_id"),depth);
   case "source_locator"->{require("TRANSCRIPT".equals(r.get("kind")),CAPABILITY_UNAVAILABLE);parent("transcript_revision",r.get("transcript_revision_id"),depth);}
   case "evidence_link"->{parent("source_locator",r.get("locator_id"),depth);parent("minutes_element",r.get("element_id"),depth);parent("minutes_revision",r.get("minutes_revision_id"),depth);}
   case "result_review"->{parent("minutes_element",r.get("element_id"),depth);parent("minutes_revision",r.get("minutes_revision_id"),depth);children("result_review_evidence","review_id",id,depth);identity(r,"reviewed_by");}
   case "result_review_evidence"->parent("evidence_link",r.get("evidence_id"),depth);
   case "final_confirmation"->{if(!kind.equals("FINAL")||!id.equals(selected))break;parent("meeting_info_revision",r.get("info_revision_id"),depth);parent("roster_revision",r.get("roster_revision_id"),depth);for(String col:List.of("transcript_confirmation_id","minutes_revision_id","element_set_id","template_revision_id","exclusion_revision_id"))parent(tableFor(col),r.get(col),depth);unsupported(r,"material_snapshot_id");children("final_result_review","confirmation_id",id,depth);identity(r,"confirmed_by");}
   case "final_result_review"->parent("result_review",r.get("review_id"),depth);
   case "generation_candidate"->{parent("durable_task",r.get("task_id"),depth);parent("generation_input",r.get("input_id"),depth);parent("minutes_revision",r.get("minutes_revision_id"),depth);parent("element_set",r.get("element_set_id"),depth);}
   case "meeting_info_revision"->identity(r,"created_by");
   case "roster_revision"->{identity(r,"confirmed_by");children("roster_member","roster_id",id,depth);}
   case "roster_member"->identity(r,"enterprise_id");
   case "input_batch_file"->{parent("file_object",r.get("file_id"),depth);pending.addLast(new Query("upload_session","completed_file_id",r.get("file_id"),depth,false," AND state='COMPLETED'"));}
   case "durable_task"->{require("MEETING".equals(r.get("subject_kind"))&&meeting.equals(r.get("subject_id")),PROVENANCE_UNAVAILABLE);children("task_attempt","task_id",id,depth);children("external_execution","task_id",id,depth);}
   case "external_execution"->{parent("external_operation",r.get("operation_id"),depth);parent("durable_task",r.get("task_id"),depth);}
   case "external_operation"->{require("MEETING".equals(r.get("subject_kind"))&&meeting.equals(r.get("subject_id")),PROVENANCE_UNAVAILABLE);children("external_execution","operation_id",id,depth);}
   case "upload_session","file_object","file_reference","task_attempt","transcript_segment","transcript_segment_mark","input_batch_fingerprint_profile"->{}
   default->throw fail(CAPABILITY_UNAVAILABLE);
  }
 }
 private List<Map<String,Object>> select(Query q){
  check();selectors.putIfAbsent(q.table()+"|"+q.column()+"|"+q.value()+q.qualifier(),new Selector(q.table(),q.column(),q.value(),q.qualifier()));var d=DEFINITIONS.get(q.table());require(d!=null&&d.columns().contains(q.column()),INVALID_ARGUMENT);
  long remaining=maximum-scanned;require(remaining>0,BUDGET_EXCEEDED);
  // All selected text columns are schema-bounded metadata (at most 255 Unicode scalars).
  // Reserve a worst-case driver result including sentinel before execution, then account actual
  // detached values cumulatively. Content fields never appear in this projection.
  long rowBound=64L+d.columns().size()*1088L,capacity=(16L*1024*1024-bytes)/rowBound;
  require(capacity>1,BUDGET_EXCEEDED);long limit=Math.min(remaining,capacity-1)+1;
  return j.query("SELECT "+String.join(",",d.columns())+" FROM "+d.name()+" WHERE "+q.column()+"=?"+q.qualifier()+" ORDER BY "+String.join(",",d.keys())+" LIMIT ?",rs->{
   var rows=new ArrayList<Map<String,Object>>();while(rs.next()){
    check();require(rows.size()<limit-1,BUDGET_EXCEEDED);scanned++;var row=new LinkedHashMap<String,Object>();
    for(String col:d.columns()){
     Object v=rs.getObject(col);
     if(v instanceof Number n){try{v=new java.math.BigDecimal(n.toString()).longValueExact();}catch(ArithmeticException failure){throw fail(PROVENANCE_UNAVAILABLE);}}
     else if(v instanceof java.sql.Timestamp t)v=t.toLocalDateTime().toString();else if(v instanceof java.sql.Date date)v=date.toLocalDate().toString();
     if(v instanceof String s){require(s.length()<=1020,PROVENANCE_UNAVAILABLE);scalars(s);bytes+=ama.canonical.CanonicalValueCodec.utf8Length(s,()->false)*4+64;}
     else bytes+=32;
     require(bytes<=16L*1024*1024,BUDGET_EXCEEDED);row.put(col,v);
    }rows.add(Collections.unmodifiableMap(row));
   }return List.copyOf(rows);
  },q.value(),limit);
 }
 /** Current metadata reads occur once per rank over the complete declared selector union.
  * An earlier RR discovery snapshot never substitutes for this protected membership comparison. */
 static void lockAndCompare(DataSource source,Discovery expected,String table,TranscriptAcquisitionBudget.Ledger budget){
  var selectors=expected.selectors().stream().filter(s->s.table().equals(table)).toList();if(selectors.isEmpty())return;var definition=DEFINITIONS.get(table);var rows=table.equals("meeting")?List.of(expected.root()):expected.tables().getOrDefault(table,List.of());long limit=(long)rows.size()+1;
  long maximum=(64L+definition.columns().size()*1088L)*limit;budget.admitCapture(maximum,maximum,maximum,2);var j=new JdbcTemplate(source);j.setQueryTimeout(2);String where=String.join(" OR ",selectors.stream().map(s->"("+s.column()+"=?"+s.qualifier()+")").toList());var arguments=new ArrayList<Object>();for(var selector:selectors)arguments.add(selector.value());arguments.add(limit);
  var actual=j.query("SELECT "+String.join(",",definition.columns())+" FROM "+table+" WHERE "+where+" ORDER BY "+String.join(",",definition.keys())+" LIMIT ? FOR UPDATE",rs->{
   var found=new ArrayList<Map<String,Object>>();while(rs.next()){budget.check();require(found.size()<rows.size(),PROVENANCE_UNAVAILABLE);budget.row(definition.columns().size());var row=new LinkedHashMap<String,Object>();
    for(String column:definition.columns()){Object value=rs.getObject(column);if(value instanceof Number n){try{value=new java.math.BigDecimal(n.toString()).longValueExact();}catch(ArithmeticException failure){throw fail(PROVENANCE_UNAVAILABLE);}}else if(value instanceof java.sql.Timestamp t)value=t.toLocalDateTime().toString();else if(value instanceof java.sql.Date date)value=date.toLocalDate().toString();if(value instanceof String text){require(text.length()<=1020,PROVENANCE_UNAVAILABLE);long bytes=ama.canonical.CanonicalValueCodec.utf8Length(text,()->false);budget.capture(bytes,text.codePointCount(0,text.length()),text.length(),4);}row.put(column,value);}found.add(Collections.unmodifiableMap(row));
   }return List.copyOf(found);
  },arguments.toArray());require(actual.equals(rows),PROVENANCE_UNAVAILABLE);
 }
 private void identity(Map<String,Object> row,String col){Object id=row.get(col);if(id!=null){require(identities.size()<256||identities.contains(id),BUDGET_EXCEEDED);identities.add((String)id);}}
 private static void unsupported(Map<String,Object> row,String field){require(row.get(field)==null,CAPABILITY_UNAVAILABLE);}
 private static String text(Map<String,Object> row,String col){Object v=row.get(col);require(v instanceof String,PROVENANCE_UNAVAILABLE);return (String)v;}
 private void check(){ama.contract.MinutesContracts.check(()->System.nanoTime()>deadline);}
 private static int compare(List<Object>a,List<Object>b){for(int i=0;i<a.size();i++){Object x=a.get(i),y=b.get(i);int c=x instanceof Long l?Long.compare(l,(Long)y):ama.canonical.CanonicalValueCodec.compareUtf8((String)x,(String)y,()->false);if(c!=0)return c;}return 0;}
 private static String tableFor(String col){return switch(col){case "transcript_confirmation_id"->"transcript_confirmation";case "minutes_revision_id"->"minutes_revision";case "element_set_id"->"element_set";case "template_revision_id"->"template_revision";case "exclusion_revision_id"->"exclusion_revision";default->throw fail(INVALID_ARGUMENT);};}
 private static Map<String,Definition> definitions(){
  var defs=new HashMap<String,Definition>();for(var shape:MinutesSourceRows.SHAPES.values())defs.put(shape.table(),new Definition(shape.table(),shape.keys(),shape.columns().stream().filter(c->!MinutesSourceRows.CONTENT.contains(c)).toList()));for(Table table:Table.values())defs.put(table.sqlName(),new Definition(table.sqlName(),table.primaryKey(),table.columns().stream().map(ColumnSpec::name).filter(c->!CONTENT.contains(c)).toList()));
  add(defs,"meeting","id","id owner_id revision cancel_epoch access_revision stage lifecycle governance_state ever_published info_revision_id roster_revision_id transcript_revision_id transcript_confirmation_id minutes_revision_id element_set_id template_revision_id exclusion_revision_id material_snapshot_id final_confirmation_id generation_fingerprint needs_generation materials_pending");
  add(defs,"minutes_template","id","id owner_id name_hash current_revision_id revision created_at");
  add(defs,"generation_candidate","id","id meeting_id input_id task_id minutes_revision_id element_set_id base_revision input_fingerprint state created_at decided_at");
  add(defs,"meeting_info_revision","id","id meeting_id revision category_id actual_meeting_date created_by created_at");
  add(defs,"roster_revision","id","id meeting_id revision state confirmed_at confirmed_by");
  add(defs,"roster_member","roster_id,member_id","roster_id member_id enterprise_id member_kind");
  add(defs,"input_batch_fingerprint_profile","batch_id","batch_id profile");return Map.copyOf(defs);
 }
 private static void add(Map<String,Definition> defs,String table,String keys,String cols){defs.put(table,new Definition(table,List.of(keys.split(",")),List.of(cols.split(" "))));}
}
