package ama.transcript;
import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;import ama.contract.MinutesContracts.*;import java.lang.reflect.*;import java.util.*;import java.util.regex.*;import org.junit.jupiter.api.Test;import org.springframework.jdbc.core.JdbcTemplate;import static org.junit.jupiter.api.Assertions.*;
/** Actual bounded SQL reader orchestration; JDBC rows are explicit metadata substitutes. */
class PublicationGraphAdaptiveReadTest {
 TranscriptAcquisitionBudget.Ledger budget(long total){return new TranscriptAcquisitionBudget(total,8192,8192,65536,65536,8192,8,32,32).ledger(new ArchiveReadBudget(1024,total,8,16,4,10000,8,64,1024,4096,8192,4096,4,500,1000),new CanonicalBudget(65536,32,10000,4096,1000),()->false);}
 @SuppressWarnings("unchecked") List<Map<String,Object>> read(JdbcTemplate jdbc,TranscriptAcquisitionBudget.Ledger budget)throws Exception{var m=PublicationGraph.class.getDeclaredMethod("read",JdbcTemplate.class,MinutesPendingDiscovery.Query.class,boolean.class,TranscriptAcquisitionBudget.Ledger.class);m.setAccessible(true);try{return(List<Map<String,Object>>)m.invoke(null,jdbc,new MinutesPendingDiscovery.Query("durable_task",List.of("id","value"),List.of("id"),"meeting_id=?",List.of("owned")),true,budget);}catch(InvocationTargetException error){if(error.getCause() instanceof RuntimeException value)throw value;throw error;}}
 int limit(String sql){var matcher=Pattern.compile("LIMIT ([0-9]+)").matcher(sql);assertTrue(matcher.find());return Integer.parseInt(matcher.group(1));}
 int start(Object[] arguments){return arguments.length==1?0:Integer.parseInt(((String)arguments[1]).substring(24));}
 String id(int n){return "00000000-0000-4000-8000-"+String.format("%012d",n);}
 static final class MetadataJdbc extends JdbcTemplate {
  final int count;final List<Integer> fetched=new ArrayList<>();MetadataJdbc(int count){this.count=count;}
  int pageLimit(String sql){var matcher=Pattern.compile("LIMIT ([0-9]+)").matcher(sql);assertTrue(matcher.find());return Integer.parseInt(matcher.group(1));}
  int cursor(Object[] args){return args.length==1?0:Integer.parseInt(((String)args[1]).substring(24));}
  @Override public <T>T queryForObject(String sql,Class<T> type,Object...args){return type.cast(Long.valueOf(Math.min(pageLimit(sql),count-cursor(args))));}
  @Override public List<Map<String,Object>> queryForList(String sql,Object...args){int from=cursor(args),limit=pageLimit(sql),size=Math.min(limit,count-from);fetched.add(limit);var rows=new ArrayList<Map<String,Object>>();for(int i=from+1;i<=from+size;i++)rows.add(Map.of("id","00000000-0000-4000-8000-"+String.format("%012d",i),"value","abc"));return rows;}
 }
 @Test void finiteBudgetStillReadsEveryMetadataRowByReducingPagesInsteadOfRejectingAnOversizedReservation()throws Exception{
  var j=new MetadataJdbc(25);var ledger=budget(8192);var result=assertDoesNotThrow(()->read(j,ledger));assertEquals(25,result.size());for(int n=0;n<25;n++)assertEquals(id(n+1),result.get(n).get("id"));assertTrue(j.fetched.size()>1);assertTrue(j.fetched.stream().allMatch(n->n<=3));assertTrue(ledger.usage()[0]<=8192);
 }
 @Test void emptyTailNeedsNoPayloadReservation()throws Exception{var j=new MetadataJdbc(0);assertTrue(read(j,budget(256)).isEmpty());assertTrue(j.fetched.isEmpty());}
 @Test void oneWorstCaseRowMustFitBeforeAnyMetadataPayloadTransfer()throws Exception{var j=new MetadataJdbc(1);var failure=assertThrows(Failure.class,()->read(j,budget(2048)));assertEquals(FailureCode.BUDGET_EXCEEDED,failure.code());assertTrue(j.fetched.isEmpty());}
}
