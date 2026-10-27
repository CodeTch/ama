package ama.storage;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
public final class Bl5S3RepairSchema {
 public static void main(String[] args)throws Exception {
  Path root=Path.of(System.getenv("AMA_BL5_ROOT"));if(args.length!=0||!root.equals(Path.of("/home/tanren/ama/.worktrees/phase-b-completion/.tools/bl5-5d9729e1-a748-4c50-a1f2-9e1d76eae33a"))||!root.toRealPath().equals(root)||Files.exists(root.resolve("stopping.json")))throw new IllegalArgumentException("Exact live owned batch required");
  var json=new com.fasterxml.jackson.databind.ObjectMapper();int port=json.readTree(Files.readString(root.resolve("s3-repair-schema.json"))).path("mysqlPort").asInt();if(port<1||port>65535)throw new IllegalArgumentException("Owned port required");
  String password=json.readTree(Files.readString(root.resolve("credentials.json"))).path("mysql").asText();
  var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+port+"/ama_bl5_5d9729e1a748_s3r2?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&connectTimeout=3000&socketTimeout=20000","ama_bl5",password);var jdbc=new JdbcTemplate(source);Bl5SchemaPrepare.requireEmpty(jdbc);
  int count=new ama.db.SchemaMigrator(source).migrate();var history=jdbc.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank");
  if(count!=16||history.size()!=16||jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success<>1",Integer.class)!=0)throw new IllegalStateException("Exact sixteen migrations required");
  Files.writeString(root.resolve("s3-repair-schema-prepared.json"),json.writeValueAsString(Map.of("schema","ama_bl5_5d9729e1a748_s3r2","migrationsExecuted",count,"history",history)),StandardOpenOption.CREATE_NEW);
 }
}
