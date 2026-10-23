import java.sql.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import ama.db.SchemaMigrator;

/** Controlled migration evidence only; no application authority or test runtime installation. */
public class Task08MigrationProbe {
 static final ObjectMapper JSON=new ObjectMapper();

 static Path ROOT;
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
   result.put("ownConnections",query(c,"SHOW FULL PROCESSLIST"));result.put("engine",query(c,"SELECT VERSION() AS version,@@session.time_zone AS timeZone,@@session.lock_wait_timeout AS lockWaitSeconds"));
   result.put("tables",query(c,"SELECT TABLE_NAME,ENGINE,ROW_FORMAT,TABLE_COLLATION,CREATE_OPTIONS,TABLE_COMMENT,AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME"));
   result.put("columns",query(c,"SELECT TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION,COLUMN_DEFAULT,IS_NULLABLE,DATA_TYPE,CHARACTER_MAXIMUM_LENGTH,CHARACTER_OCTET_LENGTH,NUMERIC_PRECISION,NUMERIC_SCALE,DATETIME_PRECISION,CHARACTER_SET_NAME,COLLATION_NAME,COLUMN_TYPE,EXTRA,COLUMN_COMMENT,GENERATION_EXPRESSION,SRS_ID FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION"));
   result.put("indexes",query(c,"SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,COLLATION,SUB_PART,PACKED,NULLABLE,INDEX_TYPE,COMMENT,INDEX_COMMENT,IS_VISIBLE,EXPRESSION FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX"));
   result.put("constraints",query(c,"SELECT TABLE_NAME,CONSTRAINT_NAME,CONSTRAINT_TYPE,ENFORCED FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() ORDER BY TABLE_NAME,CONSTRAINT_NAME"));
   result.put("checks",query(c,"SELECT CONSTRAINT_NAME,CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() ORDER BY CONSTRAINT_NAME"));
   result.put("keys",query(c,"SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,ORDINAL_POSITION,POSITION_IN_UNIQUE_CONSTRAINT,REFERENCED_TABLE_SCHEMA,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE CONSTRAINT_SCHEMA=DATABASE() ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION"));
   result.put("references",query(c,"SELECT CONSTRAINT_NAME,UNIQUE_CONSTRAINT_SCHEMA,UNIQUE_CONSTRAINT_NAME,MATCH_OPTION,UPDATE_RULE,DELETE_RULE,TABLE_NAME,REFERENCED_TABLE_NAME FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() ORDER BY TABLE_NAME,CONSTRAINT_NAME"));
   var tables=(List<Map<String,Object>>)result.get("tables");var shows=new TreeMap<String,Object>();var data=new TreeMap<String,Object>();
   for(var table:tables){String name=(String)table.get("TABLE_NAME");shows.put(name,query(c,"SHOW CREATE TABLE "+quote(name)));var cols=query(c,"SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"+name+"' ORDER BY ORDINAL_POSITION").stream().map(x->(String)x.get("COLUMN_NAME")).toList();cols=cols.stream().filter(x->!x.equals("reconcile_scan_seq")).toList();var pk=query(c,"SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"+name+"' AND INDEX_NAME='PRIMARY' ORDER BY SEQ_IN_INDEX").stream().map(x->(String)x.get("COLUMN_NAME")).toList();check(!pk.isEmpty(),"Complete deterministic PK required: "+name);
    var digest=MessageDigest.getInstance("SHA-256");long count=0;String sql="SELECT "+String.join(",",cols.stream().map(Task08MigrationProbe::quote).toList())+" FROM "+quote(name)+" ORDER BY "+String.join(",",pk.stream().map(Task08MigrationProbe::quote).toList());
    try(var st=c.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)){st.setFetchSize(Integer.MIN_VALUE);try(var r=st.executeQuery(sql)){while(r.next()){digest.update((byte)2);for(int i=1;i<=cols.size();i++)frame(digest,r.getBytes(i));count++;}}}
    data.put(name,Map.of("columns",cols,"primaryKey",pk,"rows",count,"sha256",HexFormat.of().formatHex(digest.digest())));
   }
   result.put("showCreate",shows);result.put("data",data);if(query(c,"SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='external_operation' AND COLUMN_NAME='reconcile_scan_seq'").size()==1)result.put("ordinalMap",query(c,"SELECT id,reconcile_scan_seq FROM external_operation ORDER BY id"));result.put("history",query(c,"SELECT * FROM flyway_schema_history ORDER BY installed_rank"));c.rollback();return result;
  }
 }
 static void save(String file,Object data)throws Exception{JSON.writerWithDefaultPrettyPrinter().writeValue(ROOT.resolve(file).toFile(),data);}
 static Map<String,Object> load(String file)throws Exception{return JSON.readValue(ROOT.resolve(file).toFile(),Map.class);}
 public static void main(String[] args)throws Exception {
 ROOT=Path.of(args[1]);Files.createDirectories(ROOT);
 String url=System.getenv("AMA_TEST_DATABASE_URL");check(url.equals("jdbc:mysql://127.0.0.1:46653/ama_b01_fixture")||url.matches("jdbc:mysql://127.0.0.1:44727/ama_t08_848e93aace8b_(behavior|fairheld|fairunregistered|overflow)"),"Exact target");
 if(args[0].equals("bootstrap")){try(var c=source().getConnection()){check(query(c,"SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()").isEmpty(),"Fresh target must be empty");}long start=System.nanoTime();int n=new SchemaMigrator(source()).migrate();save("bootstrap.json",Map.of("migrationsExecuted",n,"elapsedNanos",System.nanoTime()-start,"pid",ProcessHandle.current().pid()));check(n==14,"Normal old14 bootstrap");save("old14.json",capture());return;}
 if(args[0].equals("before")){var before=capture();save("before.json",before);check(((List<?>)before.get("history")).size()==14,"Actual14history");try(var c=source().getConnection();var q=c.createStatement()){for(String sql:List.of("SELECT reconcile_scan_seq FROM external_operation LIMIT 1","SELECT id FROM external_operation FORCE INDEX (external_reconcile_scan) LIMIT 1")){try{q.executeQuery(sql);throw new AssertionError("New schema unexpectedly present");}catch(SQLException e){save(e.getErrorCode()+"-missing-schema.json",Map.of("sql",sql,"sqlState",e.getSQLState(),"errorCode",e.getErrorCode()));check(e.getErrorCode()==1054||e.getErrorCode()==1176,"Expected missing schema");}}}return;}
 long start=System.nanoTime();int n=new SchemaMigrator(source()).migrate();save(args[0]+"-migration.json",Map.of("migrationsExecuted",n,"elapsedNanos",System.nanoTime()-start,"pid",ProcessHandle.current().pid()));check(n==(args[0].equals("apply")?1:0),"Exact migration count");save(args[0]+".json",capture());
 }
}
