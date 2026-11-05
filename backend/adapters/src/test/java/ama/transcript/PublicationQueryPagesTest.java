package ama.transcript;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static ama.contract.MinutesContracts.*;
class PublicationQueryPagesTest {
 static final class Jdbc extends JdbcTemplate {
  final int count;final List<Integer> pages=new ArrayList<>();boolean extra;
  Jdbc(int count){this.count=count;}
  @Override public <T>T queryForObject(String sql,Class<T> type,Object...args){var m=Pattern.compile("LIMIT (\\d+)").matcher(sql);assertTrue(m.find());return type.cast(Long.valueOf(Math.min(count,Integer.parseInt(m.group(1)))));}
  @Override public List<Map<String,Object>> queryForList(String sql,Object...args){var m=Pattern.compile("LIMIT (\\d+) OFFSET (\\d+) FOR UPDATE$").matcher(sql);assertTrue(m.find());int limit=Integer.parseInt(m.group(1)),offset=Integer.parseInt(m.group(2));pages.add(limit);var result=new ArrayList<Map<String,Object>>();for(int i=offset;i<Math.min(offset+limit,count+(extra?1:0));i++)result.add(Map.of("id",String.format("%036d",i),"state","SUCCEEDED"));return result;}
 }
 TranscriptAcquisitionBudget.Ledger budget(){return new TranscriptAcquisitionBudget(1000000,1000000,1000000,10000000,10000000,1000000,16,256,256).ledger(new ama.contract.ConfigArchiveContracts.ArchiveReadBudget(65536,1000000,100,100,16,10000,16,128,65536,65536,1000000,65536,100,1000,10000),new CanonicalBudget(1000000,32,100000,65536,10000),()->false);}
 @Test void thirtyRowsFitWithoutReservingThirtyUnoccupiedWorstCaseRows(){var j=new Jdbc(30);var result=JdbcPublicationQueries.boundedMetadata(j,"SELECT id,state FROM durable_task ORDER BY id",256,budget());assertEquals(30,result.size());assertTrue(j.pages.size()>1);assertTrue(j.pages.stream().allMatch(n->n<=8));assertEquals(String.format("%036d",29),result.getLast().get("id"));}
 @Test void emptyHistoryNeedsNoPayloadReservation(){var j=new Jdbc(0);var budget=new PublicationGraphAdaptiveReadTest().budget(256);assertTrue(JdbcPublicationQueries.boundedMetadata(j,"SELECT id FROM durable_task ORDER BY id",256,budget).isEmpty());assertTrue(j.pages.isEmpty());}
 @Test void completeMembershipCapacityIsEnforcedBeforeFilteringOrTransfer(){var j=new Jdbc(257);assertThrows(Failure.class,()->JdbcPublicationQueries.boundedMetadata(j,"SELECT id FROM durable_task ORDER BY id",256,budget()));assertTrue(j.pages.isEmpty());}
 @Test void historyPageKeepsItsRequestedSentinelWithoutRejectingADeeperHistory(){var j=new Jdbc(30);var result=JdbcPublicationQueries.boundedMetadata(j,"SELECT id,state FROM durable_task ORDER BY id",3,true,budget());assertEquals(3,result.size());assertEquals(String.format("%036d",2),result.getLast().get("id"));}
 @Test void addedSentinelRowCannotBeSilentlyDropped(){var j=new Jdbc(2);j.extra=true;assertThrows(Failure.class,()->JdbcPublicationQueries.boundedMetadata(j,"SELECT id FROM durable_task ORDER BY id",256,budget()));}
}
