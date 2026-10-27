package ama.transcript;

import ama.contract.TaskContracts.*;
import ama.meeting.WorkspaceReadBudget;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.contract.OutboxContracts.uuid;

/** Fixed metadata discovery only. Callers must authorize and prove producer relations separately. */
final class WorkspaceTaskScan {
 private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS",Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
 private static final Set<String> STATES=Set.of("READY","RUNNING","RECONCILING","SUCCEEDED","FAILED","CANCELLED","STALE");
 record Position(LocalDateTime created,String id) {
  Position {Objects.requireNonNull(created);uuid(id);if(created.getYear()<1000||created.getYear()>9999||created.getNano()%1000!=0)throw new IllegalArgumentException("Invalid task timestamp");}
  String encode(){return TIME.format(created)+"|"+id;}
  static Position parse(String text){try{if(text==null||text.length()!=63)throw new IllegalArgumentException();var p=new Position(LocalDateTime.parse(text.substring(0,26),TIME),text.substring(27));if(!p.encode().equals(text))throw new IllegalArgumentException();return p;}catch(RuntimeException e){throw new IllegalArgumentException("Invalid task cursor",e);}}
 }
 record Query(String sql,List<Object> args) {Query{args=List.copyOf(args);}}
 record Metadata(String id,TaskSpec spec,String state,long attempt,Position position){}
 private final JdbcTemplate jdbc;
 WorkspaceTaskScan(JdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc);}
 static Query query(String meeting,int limit,Position after){
  uuid(meeting);if(limit<1||limit>50)throw new IllegalArgumentException("Invalid task page size");
  String where="subject_kind='MEETING' AND subject_id=?";var args=new ArrayList<Object>();args.add(meeting);
  if(after!=null){where+=" AND (created_at<? OR (created_at=? AND id<?))";args.add(after.created());args.add(after.created());args.add(after.id());}
  args.add(limit+1);
  return new Query("SELECT id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,priority,cancel_epoch,max_attempts,state,attempt,created_at FROM durable_task FORCE INDEX (task_subject_history) WHERE "+where+" ORDER BY created_at DESC,id DESC LIMIT ?",args);
 }
 List<Metadata> discover(String meeting,int limit,Position after,WorkspaceReadBudget budget){
  var q=query(meeting,limit,after);int maximum=limit+1;
  // Reserve DDL maxima before JDBC, including subject_id VARCHAR(255) utf8mb4 and copies.
  budget.reserve(maximum,maximum*4L,maximum*4096L,maximum*2048L,maximum*2048L,4,false);
  var rows=jdbc.query(q.sql(),rs->{var result=new ArrayList<Metadata>();Position previous=after;
   while(rs.next()){
    budget.check();if(result.size()>=maximum)throw new WorkspaceReadBudget.Unavailable();
    var row=readRow(rs);
    if(!meeting.equals(row.spec().subject().id())||previous!=null&&compare(row.position(),previous)>=0)throw new WorkspaceReadBudget.Unavailable();
    previous=row.position();result.add(row);
   }return List.copyOf(result);
  },q.args().toArray());budget.check();return rows;
 }
 Metadata lockExact(Metadata expected,WorkspaceReadBudget budget){
  budget.reserve(1,4,4096,2048,2048,4,false);
  String columns=query(expected.spec().subject().id(),1,null).sql().split(" FROM ")[0];
  var actual=jdbc.query(columns+" FROM durable_task WHERE id=? FOR UPDATE",rs->{if(!rs.next())throw new WorkspaceReadBudget.Unavailable();var row=readRow(rs);if(rs.next())throw new WorkspaceReadBudget.Unavailable();return row;},expected.id());
  budget.check();if(!expected.equals(actual))throw new WorkspaceReadBudget.Unavailable();return actual;
 }
 private static Metadata readRow(java.sql.ResultSet rs)throws java.sql.SQLException{
    long version=required(rs,"business_version"),priority=required(rs,"priority"),epoch=required(rs,"cancel_epoch"),max=required(rs,"max_attempts"),attempt=required(rs,"attempt");
    if(priority!=0||max!=3)throw new WorkspaceReadBudget.Unavailable();
    return metadata(rs.getString("id"),rs.getString("task_type"),rs.getString("pool"),rs.getString("subject_kind"),rs.getString("subject_id"),rs.getString("producer_key"),rs.getString("input_kind"),rs.getString("input_id"),rs.getString("input_fingerprint"),version,0,epoch,3,rs.getString("state"),attempt,rs.getObject("created_at",LocalDateTime.class));
 }
 private static long required(java.sql.ResultSet rs,String column)throws java.sql.SQLException{long value=rs.getLong(column);if(rs.wasNull())throw new WorkspaceReadBudget.Unavailable();return value;}
 private static int compare(Position a,Position b){int time=a.created().compareTo(b.created());return time!=0?time:a.id().compareTo(b.id());}
 static Metadata metadata(String id,String type,String pool,String subjectKind,String meeting,String producer,String inputKind,String input,String fingerprint,long version,int priority,long epoch,int max,String state,long attempt,LocalDateTime created){
  try{
   uuid(meeting);var position=new Position(created,id);
   String expectedPool,expectedInput;
   switch(type){case "TRANSCRIPT_IMPORT_V1" -> {expectedPool="TRANSCRIPT_IMPORT";expectedInput="TRANSCRIPTION_INPUT";}case "MINUTES_GENERATE_V1" -> {expectedPool="MINUTES_GENERATE";expectedInput="GENERATION_INPUT";}case "KNOWLEDGE_PUBLISH_V1" -> {expectedPool="KNOWLEDGE_PUBLISH";expectedInput="PUBLICATION";}default -> throw new IllegalArgumentException();}
   if(!"MEETING".equals(subjectKind)||!expectedPool.equals(pool)||!expectedInput.equals(inputKind)||!STATES.contains(state)||priority!=0||max!=3||version<0||version==Long.MAX_VALUE||epoch<0||epoch==Long.MAX_VALUE||attempt<0||attempt>max)throw new IllegalArgumentException();
   return new Metadata(id,new TaskSpec(type,pool,new SubjectRef(subjectKind,meeting),producer,new TypedRef(inputKind,input),fingerprint,version,priority,epoch,max),state,attempt,position);
  }catch(RuntimeException e){throw new WorkspaceReadBudget.Unavailable(e);}
 }
}
