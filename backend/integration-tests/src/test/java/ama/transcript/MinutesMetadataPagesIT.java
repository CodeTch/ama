package ama.transcript;
import ama.db.TransactionRunner;
import ama.support.MysqlTestDatabase;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static ama.contract.MinutesContracts.*;
class MinutesMetadataPagesIT {
 String id(int n){return "00000000-0000-4000-8000-"+String.format("%012d",n);}
 TranscriptAcquisitionBudget.Ledger budget(){var read=new ama.contract.ConfigArchiveContracts.ArchiveReadBudget(65536,12000,100,100,16,10000,16,128,65536,65536,12000,65536,100,1000,10000);return new TranscriptAcquisitionBudget(12000,12000,12000,10000000,10000000,1000000,16,256,256).ledger(read,new CanonicalBudget(1000000,32,100000,65536,10000),()->false);}
 void fixture(java.util.function.BiConsumer<javax.sql.DataSource,MinutesGraphDiscovery.Discovery> test){var source=MysqlTestDatabase.dataSource();new TransactionRunner(source).required(()->{var j=new JdbcTemplate(source);j.execute("CREATE TEMPORARY TABLE minutes_element(id VARCHAR(36) PRIMARY KEY,set_id VARCHAR(36),category VARCHAR(64),ordinal BIGINT,body LONGTEXT,support_state VARCHAR(64)) CHARACTER SET utf8mb4");try{var rows=new ArrayList<Map<String,Object>>();for(int n=1;n<=50;n++){j.update("INSERT INTO minutes_element VALUES(?,?,'DISCUSSION',?,'不得读取正文','NOT_EXTRACTED')",id(n),id(100),n);rows.add(Map.of("id",id(n),"set_id",id(100),"category","DISCUSSION","ordinal",(long)n,"support_state","NOT_EXTRACTED"));}var d=new MinutesGraphDiscovery.Discovery(id(200),"SAVED_EFFECTIVE",null,Map.of(),Map.of("minutes_element",rows),Map.of(),Set.of(),List.of(new MinutesGraphDiscovery.Selector("minutes_element","set_id",id(100),"")));test.accept(source,d);}finally{j.execute("DROP TEMPORARY TABLE minutes_element");}return null;});}
 @Test void completeUnionFitsSmallOriginalBudgetWithoutTransferringContent(){fixture((s,d)->{var ledger=budget();var compared=MinutesGraphDiscovery.lockAndRead(s,d,"minutes_element",ledger);assertEquals(compared.expected(),compared.actual());assertEquals(50,compared.actual().size());assertTrue(ledger.usage()[0]<12000);});}
 @Test void changedMembershipAndSameCountReplacementCannotPass(){for(boolean sameCount:List.of(false,true))fixture((s,d)->{var j=new JdbcTemplate(s);if(sameCount)j.update("DELETE FROM minutes_element WHERE id=?",id(1));j.update("INSERT INTO minutes_element VALUES(?,?,'DISCUSSION',51,'new','NOT_EXTRACTED')",id(51),id(100));assertThrows(Failure.class,()->MinutesGraphDiscovery.lockAndCompare(s,d,"minutes_element",budget()));});}
 @Test void changedMetadataCannotPassEvenWithIdenticalKeysAndCount(){fixture((s,d)->{new JdbcTemplate(s).update("UPDATE minutes_element SET support_state='SUPPORTED' WHERE id=?",id(25));assertThrows(Failure.class,()->MinutesGraphDiscovery.lockAndCompare(s,d,"minutes_element",budget()));});}
 @Test void exactComparisonDoesNotReacquireFiftyRowsOfMetadata(){fixture((s,d)->{var ledger=budget();MinutesGraphDiscovery.lockAndCompare(s,d,"minutes_element",ledger);assertTrue(ledger.usage()[0]<1000,Arrays.toString(ledger.usage()));});}
 @Test void binaryComparisonRejectsCaseAccentAndTrailingSpaceChanges(){for(String changed:List.of("discussion","DÍSCUSSION","DISCUSSION "))fixture((s,d)->{new JdbcTemplate(s).update("UPDATE minutes_element SET category=? WHERE id=?",changed,id(25));assertThrows(Failure.class,()->MinutesGraphDiscovery.lockAndCompare(s,d,"minutes_element",budget()));});}
 @Test void typedMicrosecondTimestampNumbersAndNullableUnicodeRemainExact(){
  var source=MysqlTestDatabase.dataSource();new TransactionRunner(source).required(()->{var j=new JdbcTemplate(source);j.execute("CREATE TEMPORARY TABLE template_revision(id VARCHAR(36) PRIMARY KEY,template_id VARCHAR(36),revision BIGINT,owner_id VARCHAR(255),theme_count BIGINT,created_at DATETIME(6)) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");try{
   j.update("INSERT INTO template_revision VALUES(?,?,?,NULL,8,'2026-11-05 08:31:02.123456')",id(1),id(2),9007199254740993L);
   var row=new LinkedHashMap<String,Object>();row.put("id",id(1));row.put("template_id",id(2));row.put("revision",9007199254740993L);row.put("owner_id",null);row.put("theme_count",8L);row.put("created_at","2026-11-05T08:31:02.123456");
   var d=new MinutesGraphDiscovery.Discovery(id(200),"INITIAL_SETTINGS",id(1),Map.of(),Map.of("template_revision",List.of(row)),Map.of(),Set.of(),List.of(new MinutesGraphDiscovery.Selector("template_revision","id",id(1),"")));
   MinutesGraphDiscovery.lockAndCompare(source,d,"template_revision",budget());
   j.update("UPDATE template_revision SET owner_id='中文😀'");assertThrows(Failure.class,()->MinutesGraphDiscovery.lockAndCompare(source,d,"template_revision",budget()));row.put("owner_id","中文😀");MinutesGraphDiscovery.lockAndCompare(source,d,"template_revision",budget());
   j.update("UPDATE template_revision SET created_at='2026-11-05 08:31:02.123457'");assertThrows(Failure.class,()->MinutesGraphDiscovery.lockAndCompare(source,d,"template_revision",budget()));
   j.update("UPDATE template_revision SET created_at='2026-11-05 08:31:02.123456',revision=9007199254740992");assertThrows(Failure.class,()->MinutesGraphDiscovery.lockAndCompare(source,d,"template_revision",budget()));
  }finally{j.execute("DROP TEMPORARY TABLE template_revision");}return null;});
 }

 @Test void groupedKeysRetainTheirDistinctQualifiersAndExactMembership(){
  fixture((source,original)->{
   var jdbc=new JdbcTemplate(source);
   var rows=new ArrayList<Map<String,Object>>(original.tables().get("minutes_element"));
   var selectors=new ArrayList<MinutesGraphDiscovery.Selector>();
   for(int i=0;i<rows.size();i++){
    String category=i%2==0?"DISCUSSION":"RISK";
    var row=new LinkedHashMap<>(rows.get(i));row.put("category",category);rows.set(i,row);
    jdbc.update("UPDATE minutes_element SET category=? WHERE id=?",category,row.get("id"));
    selectors.add(new MinutesGraphDiscovery.Selector("minutes_element","id",row.get("id")," AND category='"+category+"'"));
   }
   var selected=new MinutesGraphDiscovery.Discovery(original.meeting(),"SAVED_EFFECTIVE",null,Map.of(),
       Map.of("minutes_element",rows),Map.of(),Set.of(),selectors);
   MinutesGraphDiscovery.lockAndCompare(source,selected,"minutes_element",budget());
   // Update the expected cell too. Only the original selector now rules this row out.
   jdbc.update("UPDATE minutes_element SET category='RISK' WHERE id=?",rows.getFirst().get("id"));
   rows.getFirst().put("category","RISK");
   assertThrows(Failure.class,()->MinutesGraphDiscovery.lockAndCompare(source,selected,"minutes_element",budget()));
  });
 }

}
