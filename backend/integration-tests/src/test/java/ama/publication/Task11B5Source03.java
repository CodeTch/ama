package ama.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh B5 fixture only; no fallback to any historical Task11 schema or resource selector. */
public final class Task11B5Source03 {
 public static final String OWNER="task11b5-079248f3030244a4adc6176583b43d24";
 public static final String SCHEMA="ama_t11b5_079248f30302_behavior";
 public static DataSource open() {
  try {
   assertEquals("task11-b5-new-owned-03",System.getenv("AMA_TASK11_FIXTURE"));
   Path repository=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT")).toRealPath();
   Path root=repository.resolve(".tools").resolve(OWNER);assertEquals(root.toString(),System.getenv("AMA_TASK09_RUN_ROOT"));
   assertFalse(Files.isSymbolicLink(root));assertEquals(root,root.toRealPath());
   var manifest=new ObjectMapper().readTree(Files.readAllBytes(root.resolve("resource.json")));
   assertEquals(OWNER,manifest.path("owner").asText());assertEquals(SCHEMA,manifest.path("schema").asText());
   String url=System.getenv("AMA_TEST_DATABASE_URL");assertEquals(manifest.path("jdbcUrl").asText(),url);assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/"+SCHEMA));
   assertEquals("t11b5_079248f30302",System.getenv("AMA_TEST_DATABASE_USERNAME"));
   var source=ama.minutes.ControlledMinutesDataSource.create(url,System.getenv("AMA_TEST_DATABASE_USERNAME"),System.getenv("AMA_TEST_DATABASE_PASSWORD"));
   var j=new org.springframework.jdbc.core.JdbcTemplate(source);j.setQueryTimeout(5);
   assertEquals(SCHEMA,j.queryForObject("SELECT DATABASE()",String.class));
   assertEquals(manifest.path("serverUuid").asText(),j.queryForObject("SELECT @@server_uuid",String.class));
   return source;
  } catch(java.io.IOException failure){throw new IllegalStateException("Fresh B5 resource manifest required",failure);}
 }
 private Task11B5Source03() {}
}
