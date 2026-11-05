package ama.transcript;
import ama.db.TransactionRunner;
import ama.support.MysqlTestDatabase;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static ama.contract.MinutesContracts.*;
class PublicationMetadataComparisonIT {
 void fixture(java.util.function.BiConsumer<JdbcTemplate,List<Map<String,Object>>> test){var source=MysqlTestDatabase.dataSource();new TransactionRunner(source).required(()->{var j=new JdbcTemplate(source);j.execute("CREATE TEMPORARY TABLE durable_task(id VARCHAR(36) PRIMARY KEY,state VARCHAR(64),business_version BIGINT,expires_at DATETIME(6)) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");try{var rows=new ArrayList<Map<String,Object>>();for(int n=1;n<=50;n++){String id=new MinutesMetadataPagesIT().id(n);j.update("INSERT INTO durable_task VALUES(?,'SUCCEEDED',9007199254740993,'2026-11-05 08:31:02.123456')",id);rows.add(Map.of("id",id,"state","SUCCEEDED","business_version",9007199254740993L,"expires_at",java.time.LocalDateTime.parse("2026-11-05T08:31:02.123456"),"state_bytes",9L,"has_expiry",1L));}test.accept(j,rows);}finally{j.execute("DROP TEMPORARY TABLE durable_task");}return null;});}
 MinutesPendingDiscovery.Query query(){return new MinutesPendingDiscovery.Query("durable_task",List.of("id","state","business_version","expires_at","OCTET_LENGTH(state) AS state_bytes","CASE WHEN expires_at IS NOT NULL THEN 1 ELSE 0 END AS has_expiry"),List.of("id"),"1=1",List.of());}
 void compare(JdbcTemplate jdbc,List<Map<String,Object>> expected){PublicationMetadataComparison.requireSame(jdbc,query(),expected,new MinutesMetadataPagesIT().budget());}
 @Test void completeTaskMetadataAndComputedProjectionsNeedNoSecondRawTransfer(){fixture((j,rows)->{var budget=new MinutesMetadataPagesIT().budget();PublicationMetadataComparison.requireSame(j,query(),rows,budget);assertTrue(budget.usage()[0]<1000,Arrays.toString(budget.usage()));});}
 @Test void renewalMustBeRecapturedAndFullyComparedBeforeAnObservationCanContinue(){fixture((j,rows)->{
  var budget=new MinutesMetadataPagesIT().budget();assertTrue(PublicationMetadataComparison.matches(j,query(),rows,budget));long before=budget.usage()[0];
  j.update("UPDATE durable_task SET expires_at=DATE_ADD(expires_at,INTERVAL 1 SECOND) WHERE id=?",rows.getFirst().get("id"));assertFalse(PublicationMetadataComparison.matches(j,query(),rows,budget));
  var refreshed=new ArrayList<>(rows);var first=new LinkedHashMap<>(rows.getFirst());first.put("expires_at",java.time.LocalDateTime.parse("2026-11-05T08:31:03.123456"));refreshed.set(0,first);
  assertTrue(PublicationMetadataComparison.matches(j,query(),refreshed,budget));assertTrue(budget.usage()[0]>before);
  j.update("UPDATE durable_task SET state='FAILED' WHERE id=?",rows.getFirst().get("id"));assertFalse(PublicationMetadataComparison.matches(j,query(),refreshed,budget));
 });}
 @Test void sameCountReplacementAndExtraMembershipFail(){for(boolean replace:List.of(true,false))fixture((j,rows)->{if(replace)j.update("DELETE FROM durable_task WHERE id=?",rows.getFirst().get("id"));j.update("INSERT INTO durable_task VALUES(?,'SUCCEEDED',9007199254740993,'2026-11-05 08:31:02.123456')",new MinutesMetadataPagesIT().id(51));assertThrows(Failure.class,()->compare(j,rows));});}
 @Test void caseTrailingSpacesMicrosecondsLargeIntegersAndNullCannotBeHidden(){for(String change:List.of("state='succeeded'","state='SUCCEEDED '","expires_at='2026-11-05 08:31:02.123457'","business_version=9007199254740992","expires_at=NULL"))fixture((j,rows)->{j.update("UPDATE durable_task SET "+change+" WHERE id=?",rows.getFirst().get("id"));assertThrows(Failure.class,()->compare(j,rows));});}
 @Test void emptySetStillRejectsConcurrentRowsAndDuplicateExpectedKeys(){fixture((j,rows)->{assertThrows(Failure.class,()->compare(j,List.of()));var duplicated=new ArrayList<>(rows);duplicated.set(1,rows.getFirst());assertThrows(Failure.class,()->compare(j,duplicated));j.update("DELETE FROM durable_task");compare(j,List.of());});}
}
