package ama.transcript;
import ama.db.TransactionRunner;
import ama.support.MysqlTestDatabase;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static ama.contract.MinutesContracts.*;
class SavedTranscriptComparisonIT {
 FullRow row(Object... values){return new FullRow(Table.MINUTES_REVISION,Arrays.stream(values).map(v->v==null?(Cell)new NullCell():v instanceof Long n?cell(n):cell((String)v)).toList());}
 String id(int n){return new MinutesMetadataPagesIT().id(n);}
 void fixture(java.util.function.BiConsumer<JdbcTemplate,List<FullRow>> test){var source=MysqlTestDatabase.dataSource();new TransactionRunner(source).required(()->{var j=new JdbcTemplate(source);j.execute("CREATE TEMPORARY TABLE minutes_revision(id VARCHAR(36) PRIMARY KEY,meeting_id VARCHAR(36),revision BIGINT,parent_id VARCHAR(36),title TEXT,title_origin VARCHAR(16),body LONGTEXT,generation_input_id VARCHAR(36),created_by VARCHAR(255),created_at DATETIME(6)) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");try{var rows=new ArrayList<FullRow>();for(int n=1;n<=5;n++){String body="中文😀 Case Sensitive ".repeat(500);j.update("INSERT INTO minutes_revision VALUES(?,?,?,NULL,'标题😀','HUMAN',?,?,'owner','2026-11-05 08:31:02.123456')",id(n),id(100),9007199254740993L+n,body,id(200));rows.add(row(id(n),id(100),9007199254740993L+n,null,"标题😀","HUMAN",body,id(200),"owner","2026-11-05T08:31:02.123456Z"));}test.accept(j,rows);}finally{j.execute("DROP TEMPORARY TABLE minutes_revision");}return null;});}
 List<Map<String,Object>> declaration(List<FullRow> rows){return rows.stream().map(r->Map.<String,Object>of("id",r.string("id"))).toList();}
 List<FullRow> compare(JdbcTemplate j,List<FullRow> rows){return SavedTranscriptComparison.rows(j,new MinutesMetadataPagesIT().budget(),Table.MINUTES_REVISION,declaration(rows),rows);}
 @Test void largeFullContentMatchesExactlyWithoutAnotherRawAcquisition(){fixture((j,rows)->{var budget=new MinutesMetadataPagesIT().budget();var actual=SavedTranscriptComparison.rows(j,budget,Table.MINUTES_REVISION,declaration(rows),rows);assertEquals(rows,actual);assertSame(rows.getFirst(),actual.getFirst());assertTrue(budget.usage()[0]<1000);assertTrue(budget.usage()[3]>200000,"JDBC binding copies remain budgeted");});}
 @Test void sameLengthCaseUnicodeAndTrailingContentChangesFail(){for(String change:List.of("body=REPLACE(body,'Case','case')","title='标题😃'","body=CONCAT(body,' ')","title=NULL","parent_id=''","revision=9007199254740993","created_at='2026-11-05 08:31:02.123457'"))fixture((j,rows)->{j.update("UPDATE minutes_revision SET "+change+" WHERE id=?",id(1));assertThrows(Failure.class,()->compare(j,rows));});}
 @Test void removedOrReplacedSavedRowsCannotPass(){for(boolean replace:List.of(false,true))fixture((j,rows)->{if(replace)j.update("UPDATE minutes_revision SET id=? WHERE id=?",id(6),id(1));else j.update("DELETE FROM minutes_revision WHERE id=?",id(1));assertThrows(Failure.class,()->compare(j,rows));});}
 @Test void copiedBindingBytesStillCannotExceedTheOriginalCopyBudget(){fixture((j,rows)->{var budget=new MinutesMetadataPagesIT().budget();budget.copies(1666500,0,6);assertThrows(Failure.class,()->SavedTranscriptComparison.rows(j,budget,Table.MINUTES_REVISION,declaration(rows),rows));});}
 @Test void comparisonUsesPrimaryKeyRangesAmongUnrelatedMeetings(){fixture((j,rows)->{
  for(int n=10;n<510;n++)j.update("INSERT INTO minutes_revision VALUES(?,?,1,NULL,'other','HUMAN','unrelated',?,'other','2026-11-05 08:31:02.123456')",id(n),id(900),id(901));
  var checked=new JdbcTemplate(j.getDataSource()){@Override public <T>T queryForObject(String sql,Class<T> type,Object...args){var plan=j.queryForList("EXPLAIN "+sql,args);var actual=plan.stream().filter(r->"minutes_revision".equals(r.get("table"))).findFirst().orElseThrow();assertEquals("PRIMARY",actual.get("key"));assertNotEquals("ALL",actual.get("type"));return super.queryForObject(sql,type,args);}};
  assertEquals(rows,compare(checked,rows));
 });}
 @Test void auxiliaryCompositeKeysManifestAndMicrosecondTimestampAreExact(){
  var source=MysqlTestDatabase.dataSource();new TransactionRunner(source).required(()->{var j=new JdbcTemplate(source);j.execute("CREATE TEMPORARY TABLE comparison_aux(task_id VARCHAR(36),attempt BIGINT,manifest LONGTEXT,created_at DATETIME(6),nullable_value TEXT,PRIMARY KEY(task_id,attempt)) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");try{
   String manifest="原始 Manifest😀 ".repeat(2000);j.update("INSERT INTO comparison_aux VALUES(?,2,?,'2026-11-05 08:31:02.123456',NULL)",id(1),manifest);
   var shape=new SavedTranscriptRows.Shape("comparison_aux",List.of("task_id","attempt"),List.of("task_id","attempt","manifest","created_at","nullable_value"),Set.of("attempt"),Set.of("created_at"));var original=new LinkedHashMap<String,Object>();original.put("task_id",id(1));original.put("attempt",2L);original.put("manifest",manifest);original.put("created_at","2026-11-05T08:31:02.123456Z");original.put("nullable_value",null);var declared=List.<Map<String,Object>>of(Map.of("task_id",id(1),"attempt",2L));
   var budget=new MinutesMetadataPagesIT().budget();assertSame(original,SavedTranscriptComparison.auxiliary(j,budget,shape,declared,List.of(original)).getFirst());assertTrue(budget.usage()[0]<1000);assertTrue(budget.usage()[3]>100000);
   for(String change:List.of("manifest=REPLACE(manifest,'Manifest','manifest')","created_at='2026-11-05 08:31:02.123457'","nullable_value=''","attempt=3")){j.update("UPDATE comparison_aux SET "+change);assertThrows(Failure.class,()->SavedTranscriptComparison.auxiliary(j,new MinutesMetadataPagesIT().budget(),shape,declared,List.of(original)));j.update("UPDATE comparison_aux SET attempt=2,manifest=?,created_at='2026-11-05 08:31:02.123456',nullable_value=NULL",manifest);}
  }finally{j.execute("DROP TEMPORARY TABLE comparison_aux");}return null;});
 }
}
