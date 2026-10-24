package ama.minutes;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class Task09GraphDiscoveryIT {
 String id(){return UUID.randomUUID().toString();}
 String meeting(JdbcTemplate j){String m=id(),o="task09-graph-"+id();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",o,o);j.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",m,o);return m;}
 String body(JdbcTemplate j,String m,String parent,long rev){String b=id();j.update("INSERT INTO minutes_revision(id,meeting_id,revision,parent_id,title,title_origin,body,created_by,created_at) VALUES (?,?,?,?,'secret title','HUMAN','secret body','task09',UTC_TIMESTAMP(6))",b,m,rev,parent);return b;}
 Object discover(DataSource s,String m,String kind,String selected,int max)throws Exception {
  Class<?> c=assertDoesNotThrow(()->Class.forName("ama.transcript.MinutesGraphDiscovery"),"complete bounded graph discovery must be installed");
  var method=c.getDeclaredMethod("discover",DataSource.class,String.class,String.class,String.class,int.class);method.setAccessible(true);
  try{return method.invoke(null,s,m,kind,selected,max);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException x)throw x;throw e;}
 }
 @SuppressWarnings("unchecked") <T>T field(Object value,String name)throws Exception{var m=value.getClass().getDeclaredMethod(name);m.setAccessible(true);return (T)m.invoke(value);}
 @Test void followsEveryBodyParentAndRetainsExplicitEmptyEvidenceSetsWithoutTextTransfer()throws Exception {
  var s=Task09Source.open();var j=new JdbcTemplate(s);String m=meeting(j),first=body(j,m,null,1),second=body(j,m,first,2);
  Object plan=discover(s,m,"OWNER_REVISION",second,100);
  Map<String,List<Map<String,Object>>> tables=field(plan,"tables");assertEquals(2,tables.get("minutes_revision").size());
  assertTrue(tables.get("minutes_revision").stream().noneMatch(r->r.containsKey("body")||r.containsKey("title")));
  Map<String,List<List<Object>>> sets=field(plan,"memberships");assertTrue(sets.containsKey("evidence_link|minutes_revision_id|"+first));assertEquals(List.of(),sets.get("evidence_link|minutes_revision_id|"+second));
 }
 @Test void setEnumeratesEveryCategoryActionAndEmptyDirectoryMembership()throws Exception {
  var s=Task09Source.open();var j=new JdbcTemplate(s);String m=meeting(j),set=id();j.update("INSERT INTO element_set(id,meeting_id,revision,created_at) VALUES (?,?,1,UTC_TIMESTAMP(6))",set,m);
  for(String category:List.of("OVERVIEW","DISCUSSION","CONCLUSION","ACTION","RISK","OPEN_QUESTION","DISAGREEMENT"))j.update("INSERT INTO minutes_element(id,set_id,category,ordinal,body,support_state) VALUES (?,?,?,1,'held text','UNSUPPORTED')",id(),set,category);
  j.update("UPDATE meeting SET element_set_id=? WHERE id=?",set,m);Object plan=discover(s,m,"SAVED_EFFECTIVE",null,200);
  Map<String,List<Map<String,Object>>> tables=field(plan,"tables");assertEquals(7,tables.get("minutes_element").size());
  Map<String,List<List<Object>>> sets=field(plan,"memberships");assertEquals(List.of(),sets.get("sensitive_detection|set_id|"+set));assertEquals(List.of(),sets.get("background_note|set_id|"+set));
  assertEquals(7,sets.keySet().stream().filter(k->k.startsWith("action_item|element_id|")).count());
 }
 @Test void overflowRejectsWholeGraphAndForeignMeetingIsNeverAdopted()throws Exception {
  var s=Task09Source.open();var j=new JdbcTemplate(s);String m=meeting(j),other=meeting(j),first=body(j,m,null,1),second=body(j,m,first,2);
  assertThrows(RuntimeException.class,()->discover(s,m,"OWNER_REVISION",second,1));assertThrows(RuntimeException.class,()->discover(s,other,"OWNER_REVISION",second,100));
 }
 @Test void currentSettingsCaptureExactZeroExclusionMembers()throws Exception {
  var s=Task09Source.open();var j=new JdbcTemplate(s);String m=meeting(j),ex=id();j.update("INSERT INTO exclusion_revision(id,meeting_id,revision,created_at) VALUES (?,?,1,UTC_TIMESTAMP(6))",ex,m);j.update("UPDATE meeting SET exclusion_revision_id=? WHERE id=?",ex,m);
  Object plan=discover(s,m,"CURRENT_GENERATION",null,100);Map<String,List<List<Object>>> sets=field(plan,"memberships");assertEquals(List.of(),sets.get("sensitive_exclusion|revision_id|"+ex));
 }
}
