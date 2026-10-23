import java.sql.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import ama.db.SchemaMigrator;

/** Controlled migration evidence only; no application authority or test runtime installation. */
public class Task06IndexMigrationProbe {
 static final ObjectMapper JSON=new ObjectMapper();
 static final String INDEX="task_input_lookup";
 static final Path ROOT=Path.of("artifacts/phase-b/b-remaining/task-06/fix-01/index-migration");
 static DriverManagerDataSource source(){
  var s=new DriverManagerDataSource(){public Connection getConnection()throws SQLException {Connection c=super.getConnection();try(var q=c.createStatement()){q.execute("SET SESSION lock_wait_timeout=5");q.execute("SET time_zone='+00:00'");q.execute("SET NAMES utf8mb4");}return c;}};
  s.setUrl(System.getenv("AMA_TEST_DATABASE_URL"));s.setUsername(System.getenv("AMA_TEST_DATABASE_USERNAME"));s.setPassword(System.getenv("AMA_TEST_DATABASE_PASSWORD"));return s;
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static List<Map<String,Object>> query(Connection c,String sql)throws Exception{
  try(var st=c.createStatement();var r=st.executeQuery(sql)){var result=new ArrayList<Map<String,Object>>();var m=r.getMetaData();while(r.next()){var row=new LinkedHashMap<String,Object>();for(int i=1;i<=m.getColumnCount();i++)row.put(m.getColumnLabel(i),r.getString(i));result.add(row);}return result;}
 }
 static String quote(String s){check(s.matches("[a-zA-Z0-9_]+"),"Unexpected identifier");return "`"+s+"`";}
 static void frame(MessageDigest h,byte[] value){if(value==null){h.update((byte)0);return;}h.update((byte)1);h.update(ByteBuffer.allocate(8).putLong(value.length).array());h.update(value);}
 static Map<String,Object> capture()throws Exception{
  try(var c=source().getConnection()){
   c.setAutoCommit(false);c.setReadOnly(true);
   var result=new LinkedHashMap<String,Object>();
   result.put("engine",query(c,"SELECT VERSION() AS version,@@session.time_zone AS timeZone,@@session.lock_wait_timeout AS lockWaitSeconds"));
   result.put("tables",query(c,"SELECT TABLE_NAME,ENGINE,ROW_FORMAT,TABLE_COLLATION,CREATE_OPTIONS,TABLE_COMMENT,AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME"));
   result.put("columns",query(c,"SELECT TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION,COLUMN_DEFAULT,IS_NULLABLE,DATA_TYPE,CHARACTER_MAXIMUM_LENGTH,CHARACTER_OCTET_LENGTH,NUMERIC_PRECISION,NUMERIC_SCALE,DATETIME_PRECISION,CHARACTER_SET_NAME,COLLATION_NAME,COLUMN_TYPE,EXTRA,COLUMN_COMMENT,GENERATION_EXPRESSION,SRS_ID FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION"));
   result.put("indexes",query(c,"SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,COLLATION,SUB_PART,PACKED,NULLABLE,INDEX_TYPE,COMMENT,INDEX_COMMENT,IS_VISIBLE,EXPRESSION FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX"));
   result.put("constraints",query(c,"SELECT TABLE_NAME,CONSTRAINT_NAME,CONSTRAINT_TYPE,ENFORCED FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() ORDER BY TABLE_NAME,CONSTRAINT_NAME"));
   result.put("checks",query(c,"SELECT CONSTRAINT_NAME,CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() ORDER BY CONSTRAINT_NAME"));
   result.put("keys",query(c,"SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,ORDINAL_POSITION,POSITION_IN_UNIQUE_CONSTRAINT,REFERENCED_TABLE_SCHEMA,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE CONSTRAINT_SCHEMA=DATABASE() ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION"));
   result.put("references",query(c,"SELECT CONSTRAINT_NAME,UNIQUE_CONSTRAINT_SCHEMA,UNIQUE_CONSTRAINT_NAME,MATCH_OPTION,UPDATE_RULE,DELETE_RULE,TABLE_NAME,REFERENCED_TABLE_NAME FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() ORDER BY TABLE_NAME,CONSTRAINT_NAME"));
   var tables=(List<Map<String,Object>>)result.get("tables");var shows=new TreeMap<String,Object>();var data=new TreeMap<String,Object>();
   for(var table:tables){String name=(String)table.get("TABLE_NAME");shows.put(name,query(c,"SHOW CREATE TABLE "+quote(name)));var cols=query(c,"SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"+name+"' ORDER BY ORDINAL_POSITION").stream().map(x->(String)x.get("COLUMN_NAME")).toList();var pk=query(c,"SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"+name+"' AND INDEX_NAME='PRIMARY' ORDER BY SEQ_IN_INDEX").stream().map(x->(String)x.get("COLUMN_NAME")).toList();check(!pk.isEmpty(),"Complete deterministic PK required: "+name);
    var digest=MessageDigest.getInstance("SHA-256");long count=0;String sql="SELECT "+String.join(",",cols.stream().map(Task06IndexMigrationProbe::quote).toList())+" FROM "+quote(name)+" ORDER BY "+String.join(",",pk.stream().map(Task06IndexMigrationProbe::quote).toList());
    try(var st=c.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)){st.setFetchSize(Integer.MIN_VALUE);try(var r=st.executeQuery(sql)){while(r.next()){digest.update((byte)2);for(int i=1;i<=cols.size();i++)frame(digest,r.getBytes(i));count++;}}}
    data.put(name,Map.of("columns",cols,"primaryKey",pk,"rows",count,"sha256",HexFormat.of().formatHex(digest.digest())));
   }
   result.put("showCreate",shows);result.put("data",data);result.put("history",query(c,"SELECT * FROM flyway_schema_history ORDER BY installed_rank"));c.rollback();return result;
  }
 }
 static void save(String file,Object data)throws Exception{JSON.writerWithDefaultPrettyPrinter().writeValue(ROOT.resolve(file).toFile(),data);}
 static Map<String,Object> load(String file)throws Exception{return JSON.readValue(ROOT.resolve(file).toFile(),Map.class);}
 static void compare(Map<String,Object> before,Map<String,Object> after,boolean added)throws Exception{
  for(String key:List.of("engine","tables","columns","constraints","checks","keys","references"))check(before.get(key).equals(after.get(key)),"Physical metadata changed: "+key);
  var oldIndexes=(List<Map<String,Object>>)before.get("indexes");var newIndexes=(List<Map<String,Object>>)after.get("indexes");var actual=newIndexes.stream().filter(x->INDEX.equals(x.get("INDEX_NAME"))).toList();
  if(added){check(actual.size()==3,"New index parts");for(int i=0;i<3;i++){var x=actual.get(i);check("durable_task".equals(x.get("TABLE_NAME"))&&"1".equals(x.get("NON_UNIQUE"))&&Integer.toString(i+1).equals(x.get("SEQ_IN_INDEX"))&&List.of("input_kind","input_id","id").get(i).equals(x.get("COLUMN_NAME"))&&"A".equals(x.get("COLLATION"))&&"BTREE".equals(x.get("INDEX_TYPE"))&&"YES".equals(x.get("IS_VISIBLE"))&&x.get("SUB_PART")==null&&x.get("EXPRESSION")==null,"Exact new index part");}check(oldIndexes.equals(newIndexes.stream().filter(x->!INDEX.equals(x.get("INDEX_NAME"))).toList()),"Old indexes unchanged");}else check(oldIndexes.equals(newIndexes),"Rerun indexes unchanged");
  var oldData=new TreeMap<>((Map<String,Object>)before.get("data"));var newData=new TreeMap<>((Map<String,Object>)after.get("data"));oldData.remove("flyway_schema_history");newData.remove("flyway_schema_history");check(oldData.equals(newData),"Complete old business/allocator data changed");
  var oldHistory=(List<?>)before.get("history");var newHistory=(List<?>)after.get("history");check(newHistory.size()==oldHistory.size()+(added?1:0)&&newHistory.subList(0,oldHistory.size()).equals(oldHistory),"Exact prior history prefix");if(added){var row=(Map<?,?>)newHistory.getLast();check("013.2".equals(row.get("version"))&&"V013_2__task_input_lookup.sql".equals(row.get("script"))&&"1".equals(row.get("success")),"New history identity");}else check(before.get("data").equals(after.get("data")),"Full rerun data including history");
  String oldShow=JSON.writeValueAsString(before.get("showCreate"));var newShows=(Map<String,Object>)after.get("showCreate");if(added){newShows=JSON.readValue(JSON.writeValueAsString(newShows),Map.class);var row=(Map<String,Object>)((List<?>)newShows.get("durable_task")).getFirst();String ddl=(String)row.get("Create Table");String line="  KEY `task_input_lookup` (`input_kind`,`input_id`,`id`),\n";check(ddl.contains(line),"Exact SHOW CREATE index line");row.put("Create Table",ddl.replace(line,""));}check(oldShow.equals(JSON.writeValueAsString(newShows)),"SHOW CREATE changed beyond one index");
 }
 public static void main(String[] args)throws Exception{
  check("jdbc:mysql://127.0.0.1:46653/ama_b01_fixture".equals(System.getenv("AMA_TEST_DATABASE_URL")),"Exact target");
  if(args[0].equals("before")){var before=capture();save("before.json",before);check(((List<?>)before.get("history")).size()==13,"Expected13history");try(var c=source().getConnection();var s=c.createStatement()){try{s.executeQuery("SELECT id FROM durable_task FORCE INDEX(task_input_lookup) WHERE input_kind='TRANSCRIPTION_INPUT' AND input_id='00000000-0000-0000-0000-000000000000' ORDER BY id LIMIT 65");throw new AssertionError("Missing index unexpectedly worked");}catch(SQLException failure){check(failure.getErrorCode()==1176,"Missing-index expected1176");save("missing-index.json",Map.of("sqlState",failure.getSQLState(),"errorCode",failure.getErrorCode(),"claim","Actual forced-index SQL fails closed before migration; not installed-service-path TDD"));}}System.out.println("BEFORE captured complete tables="+((List<?>)before.get("tables")).size()+"; missing-index1176");return;}
  var before=load("before.json");long start=System.nanoTime();int count=new SchemaMigrator(source()).migrate();save("first-apply.json",Map.of("migrationsExecuted",count,"elapsedNanos",System.nanoTime()-start,"lockWaitSeconds",5,"pid",ProcessHandle.current().pid()));check(count==1,"First migration count");var after=capture();save("after.json",after);compare(before,JSON.readValue(JSON.writeValueAsString(after),Map.class),true);
  start=System.nanoTime();count=new SchemaMigrator(source()).migrate();save("rerun.json",Map.of("migrationsExecuted",count,"elapsedNanos",System.nanoTime()-start,"lockWaitSeconds",5));check(count==0,"Normal rerun count");var rerun=capture();save("after-rerun.json",rerun);compare(JSON.readValue(JSON.writeValueAsString(after),Map.class),JSON.readValue(JSON.writeValueAsString(rerun),Map.class),false);save("PASS.json",Map.of("firstApply",1,"rerun",0,"oldHistoryPrefix",13,"newHistory",14,"allOldBusinessDataAndAllocatorValuesUnchanged",true,"allOldPhysicalMetadataUnchangedExceptExactIndex",true));System.out.println("PASS first1/rerun0 complete data/schema/history preservation");
 }
}
