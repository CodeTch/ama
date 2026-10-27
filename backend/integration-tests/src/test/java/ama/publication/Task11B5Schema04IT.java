package ama.publication;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Existing reviewed migrations on the fresh empty B5 schema only; never drops or resets data. */
class Task11B5Schema04IT {
 @Test void applyExistingFrozenMigrationsOnceToFreshEmptySchema()throws Exception {
  var source=Task11B5Source04.open();var j=new org.springframework.jdbc.core.JdbcTemplate(source);j.setQueryTimeout(5);
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class));
  Path root=Path.of(System.getenv("AMA_TASK09_RUN_ROOT"));Files.writeString(root.resolve("schema-application-consumed.json"),"{\"owner\":\""+Task11B5Source04.OWNER+"\",\"consumedBeforeDdl\":true}",StandardOpenOption.CREATE_NEW);
  assertEquals(16,new ama.db.SchemaMigrator(source).migrate());
  var before=j.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank");assertEquals(16,before.size());assertEquals("013.4",before.getLast().get("version"));for(var row:before)assertEquals(true,row.get("success"));
  assertEquals(0,new ama.db.SchemaMigrator(source).migrate());assertEquals(before,j.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank"));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting",Integer.class));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM publication_submission",Integer.class));
  Files.writeString(Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW")).resolve("fresh-schema-applied.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("owner",Task11B5Source04.OWNER,"schema",Task11B5Source04.SCHEMA,"history",before,"initialTables",0,"secondMigrationCount",0,"businessRows",0)),StandardOpenOption.CREATE_NEW);
 }
}
