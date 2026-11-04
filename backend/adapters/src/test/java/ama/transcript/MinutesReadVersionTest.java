package ama.transcript;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

class MinutesReadVersionTest {
 @Test void candidateCommitWithoutMeetingRevisionChangeReacquiresTheTaskRead(){
  var candidate=new AtomicInteger();var reads=new AtomicInteger();
  var jdbc=new JdbcTemplate(){@Override public List<Map<String,Object>> queryForList(String sql,Object... args){
   if(sql.contains("FROM meeting"))return List.of(Map.of("revision",8L,"access_revision",1L,"cancel_epoch",0L));
   assertTrue(sql.contains("input_id=?"));assertEquals("input",args[1]);
   return List.of(Map.of("state","RUNNING","candidate_id",candidate.get()));
  }};
  assertEquals("complete",TranscriptReadRetry.read(()->MinutesReadVersion.capture(jdbc,"meeting","STORED_INPUT","input",(rows,bytes)->{}),()->{
   if(reads.getAndIncrement()==0){candidate.incrementAndGet();throw fail(PROVENANCE_UNAVAILABLE);}return "complete";
  }));
  assertEquals(2,reads.get());
 }
 @Test void stableTaskProvenanceFailureIsNotSuppressed(){
  var jdbc=new JdbcTemplate(){@Override public List<Map<String,Object>> queryForList(String sql,Object... args){return List.of(Map.of("revision",8L));}};
  var reads=new AtomicInteger();assertThrows(Failure.class,()->TranscriptReadRetry.read(()->MinutesReadVersion.capture(jdbc,"meeting","STORED_INPUT","input",(rows,bytes)->{}),()->{reads.incrementAndGet();throw fail(PROVENANCE_UNAVAILABLE);}));assertEquals(1,reads.get());
 }
 @Test void ordinaryReadsDoNotExpandUnrelatedTaskHistory(){
  var calls=new AtomicInteger();var jdbc=new JdbcTemplate(){@Override public List<Map<String,Object>> queryForList(String sql,Object... args){calls.incrementAndGet();assertTrue(sql.contains("FROM meeting"));return List.of();}};
  MinutesReadVersion.capture(jdbc,"meeting","SAVED_EFFECTIVE",null,(rows,bytes)->{});assertEquals(1,calls.get());
 }
}
