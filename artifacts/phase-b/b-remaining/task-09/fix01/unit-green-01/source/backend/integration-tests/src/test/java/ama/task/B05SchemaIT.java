package ama.task;
import ama.support.MysqlTestDatabase;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class B05SchemaIT {
 @org.junit.jupiter.api.BeforeEach void prepareHistoricalTarget(){migrateTaskSchema(MysqlTestDatabase.dataSource());}
 /** Historical V010 oracle on an owned target-010 fixture; never resets or downgrades existing history. */
 private int migrateTaskSchema(javax.sql.DataSource source){
  return Flyway.configure().dataSource(source).locations("classpath:db/migration")
   .initSql("SET NAMES utf8mb4; SET time_zone = '+00:00'")
   .target("010").cleanDisabled(true).load().migrate().migrationsExecuted;
 }
 @Test void tenTablesAndExecutionFenceExist() {
  var source=MysqlTestDatabase.dataSource(); var j=new JdbcTemplate(source);
  assertEquals(10,j.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('task_subject','durable_task','task_attempt','external_operation','external_execution','task_outbox','task_outbox_sequence','outbox_consumer','model_capacity','model_reservation')",Integer.class));
 }

 @Test void allMetadataMatchesIndependentAdoptedMysqlEvidence()throws Exception{
  var j=new JdbcTemplate(MysqlTestDatabase.dataSource());var json=new com.fasterxml.jackson.databind.ObjectMapper();
  com.fasterxml.jackson.databind.JsonNode expected;try(var input=getClass().getResourceAsStream("/ama/task/task-schema-b00-1.2.json")){expected=json.readTree(input).get("metadata");}
  var names=new java.util.HashSet<String>();expected.get("tables").forEach(row->names.add(row.get("table").asText()));
  java.util.Map<String,String> queries=new java.util.LinkedHashMap<>();
  queries.put("tables","SELECT JSON_OBJECT('table',TABLE_NAME,'engine',ENGINE,'collation',TABLE_COLLATION) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME;");
  queries.put("columns","SELECT JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'ordinal',ORDINAL_POSITION,'type',COLUMN_TYPE,'nullable',IS_NULLABLE,'default',COLUMN_DEFAULT,'collation',COLLATION_NAME,'extra',EXTRA) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION;");
  queries.put("indexes","SELECT JSON_OBJECT('table',TABLE_NAME,'name',INDEX_NAME,'unique',1-NON_UNIQUE,'seq',SEQ_IN_INDEX,'column',COLUMN_NAME,'direction',COLLATION,'prefix',SUB_PART,'type',INDEX_TYPE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;");
  queries.put("checks","SELECT JSON_OBJECT('table',t.TABLE_NAME,'name',t.CONSTRAINT_NAME,'clause',c.CHECK_CLAUSE,'enforced',t.ENFORCED) FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS c ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.CONSTRAINT_SCHEMA=DATABASE() AND t.CONSTRAINT_TYPE='CHECK' ORDER BY t.TABLE_NAME,t.CONSTRAINT_NAME;");
  queries.put("foreign_keys","SELECT JSON_OBJECT('table',k.TABLE_NAME,'name',k.CONSTRAINT_NAME,'column',k.COLUMN_NAME,'ordinal',k.ORDINAL_POSITION,'target',k.REFERENCED_TABLE_NAME,'remote',k.REFERENCED_COLUMN_NAME,'delete',r.DELETE_RULE,'update',r.UPDATE_RULE) FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.CONSTRAINT_SCHEMA=DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL ORDER BY k.TABLE_NAME,k.CONSTRAINT_NAME,k.ORDINAL_POSITION;");
  for(var query:queries.entrySet()){var actual=new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();for(var text:j.queryForList(query.getValue(),String.class)){var row=json.readTree(text);if(names.contains(row.get("table").asText()))actual.add(row);}assertEquals(expected.get(query.getKey()),json.valueToTree(actual),query.getKey());}
 }
 @Test void orderedHistoryAndFrozenChecksumRerunZero(){var source=MysqlTestDatabase.dataSource();var j=new JdbcTemplate(source);assertEquals(java.util.List.of("001","002","005","006","007","008","009","010"),j.queryForList("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank",String.class));assertEquals(-1187397378,j.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='010'",Integer.class));assertEquals(0,migrateTaskSchema(source));}
}
