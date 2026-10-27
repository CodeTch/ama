package ama.storage;

import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Once-only genuine Flyway preparation of the two empty selected schemas. */
public final class Bl5SchemaPrepare {
 static String schema(String mode) {
  return switch(mode){case "local" -> "ama_bl5_5d9729e1a748_local2";case "s3" -> "ama_bl5_5d9729e1a748_s3";default -> throw new IllegalArgumentException("Foreign schema mode");};
 }
 public static void main(String[] args)throws Exception {
  Path root=Path.of(System.getenv("AMA_BL5_ROOT"));
  if(args.length!=0||!root.equals(Path.of("/home/tanren/ama/.worktrees/phase-b-completion/.tools/bl5-5d9729e1-a748-4c50-a1f2-9e1d76eae33a"))||!root.toRealPath().equals(root))throw new IllegalArgumentException("Exact owned schema preparation required");
  var json=new com.fasterxml.jackson.databind.ObjectMapper();var config=json.readTree(Files.readString(root.resolve("schema-preparation.json")));int port=config.path("mysqlPort").asInt();
  if(port<1||port>65535)throw new IllegalArgumentException("Owned MySQL port required");
  String password=json.readTree(Files.readString(root.resolve("credentials.json"))).path("mysql").asText();var results=new LinkedHashMap<String,Object>();
  for(String mode:List.of("local","s3")){
   if(Files.exists(root.resolve("stopping.json")))throw new IllegalStateException("Batch stopped");
   var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+port+"/"+schema(mode)+"?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&connectTimeout=3000&socketTimeout=20000","ama_bl5",password);var jdbc=new JdbcTemplate(source);
   requireEmpty(jdbc);
   int migrated=new ama.db.SchemaMigrator(source).migrate();
   var history=jdbc.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank");
   if(migrated!=16||history.size()!=16||jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success<>1",Integer.class)!=0)throw new IllegalStateException("Exact sixteen migrations required");
   results.put(mode,Map.of("schema",schema(mode),"migrationsExecuted",migrated,"history",history));
  }
  Files.writeString(root.resolve("schema-prepared.json"),json.writeValueAsString(results),StandardOpenOption.CREATE_NEW);
 }
 static void requireEmpty(JdbcTemplate jdbc){if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class)!=0)throw new IllegalArgumentException("Nonempty schema must be retained untouched");}
}
