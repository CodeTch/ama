package ama.recording;

import ama.contract.RecordingContracts;
import ama.db.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Actual database clock, row locks and independent process authorities; no lease cache. */
class RecordingWriterLeaseIT {
 static javax.sql.DataSource source;static JdbcTemplate j;static TransactionRunner tx;
 @BeforeAll static void schema(){String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Fresh owned schema required");source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");j=new JdbcTemplate(source);tx=new TransactionRunner(source,10);Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();}
 RecordingService recording(){return new RecordingService(new JdbcRecordingPersistence(source));}
 RecordingContracts.Session historicalOpen(String meeting){var s=new RecordingContracts.Session(UUID.randomUUID().toString(),meeting,UUID.randomUUID().toString(),1,"database-legacy-fixture");new JdbcRecordingPersistence(source).open(s);return s;}
 @Test void oneHistoricalActiveSessionIsAdoptedBeforeClaimingItsWriter(){
  var s=historicalOpen(UUID.randomUUID().toString());var lease=new JdbcRecordingWriterLeases(source).acquire(s.sessionId(),1,UUID.randomUUID().toString(),UUID.randomUUID().toString());assertEquals(2,lease.writerEpoch());assertEquals(s.sessionId(),j.queryForObject("SELECT session_id FROM recording_meeting_capture WHERE meeting_id=?",String.class,s.meetingId()));
 }
 @Test void ambiguousHistoricalActiveSessionsCannotClaimIndependentWriters(){
  String meeting=UUID.randomUUID().toString();var first=historicalOpen(meeting);var second=historicalOpen(meeting);var leases=new JdbcRecordingWriterLeases(source);
  for(var s:List.of(first,second))assertThrows(RecordingService.StaleWriter.class,()->leases.acquire(s.sessionId(),1,UUID.randomUUID().toString(),UUID.randomUUID().toString()));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM recording_meeting_capture WHERE meeting_id=?",Integer.class,meeting));assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM recording_session WHERE meeting_id=? AND writer_epoch=1",Integer.class,meeting));
 }
 @Test void aHistoricalSessionOutsideTheCurrentAdmissionCannotBypassTheMeetingFence(){
  String meeting=UUID.randomUUID().toString();var current=recording().open(meeting,"database-lease-fixture");var old=historicalOpen(meeting);recording().accept(old.sessionId(),1,new RecordingContracts.Chunk(old.partId(),1,1,0,2,new byte[]{1,2,3,4}));
  var leases=new JdbcRecordingWriterLeases(source);assertThrows(RecordingService.StaleWriter.class,()->leases.acquire(old.sessionId(),1,UUID.randomUUID().toString(),UUID.randomUUID().toString()));assertEquals(2,leases.acquire(current.sessionId(),1,UUID.randomUUID().toString(),UUID.randomUUID().toString()).writerEpoch());assertEquals(1,recording().ack(old.sessionId(),old.partId()).highestContiguousSeq());
 }
 @Test void oneCollectorOwnsLiveLeaseAndExactClaimReplayDoesNotAdvanceEpoch(){
  var s=recording().open(UUID.randomUUID().toString(),"database-lease-fixture");var leases=new JdbcRecordingWriterLeases(source);String collector=UUID.randomUUID().toString(),key=UUID.randomUUID().toString();
  var accepted=leases.acquire(s.sessionId(),1,collector,key);assertEquals(2,accepted.writerEpoch());assertEquals(s.backendId(),accepted.backendId());assertEquals(s.partId(),accepted.partId());
  assertEquals(accepted,leases.acquire(s.sessionId(),1,collector,key));assertThrows(RecordingService.StaleWriter.class,()->leases.acquire(s.sessionId(),2,UUID.randomUUID().toString(),UUID.randomUUID().toString()));
  assertThrows(RecordingService.StaleWriter.class,()->tx.required(()->{leases.require(s.sessionId(),2,UUID.randomUUID().toString());return null;}));
  assertDoesNotThrow(()->tx.required(()->{leases.require(s.sessionId(),2,collector);return null;}));var renewed=leases.renew(s.sessionId(),2,collector);assertTrue(renewed.expiresAt().compareTo(accepted.expiresAt())>=0);
  assertThrows(IllegalStateException.class,()->leases.require(s.sessionId(),2,collector));
 }
 @Test void expiredTakeoverFencesOldWriterButKeepsAllCommittedAudio(){
  var service=recording();var s=service.open(UUID.randomUUID().toString(),"database-lease-fixture");var a=new JdbcRecordingWriterLeases(source);String old=UUID.randomUUID().toString(),next=UUID.randomUUID().toString();var first=a.acquire(s.sessionId(),1,old,UUID.randomUUID().toString());
  tx.required(()->{a.require(s.sessionId(),2,old);service.accept(s.sessionId(),2,new RecordingContracts.Chunk(s.partId(),2,1,0,2,new byte[]{1,2,3,4}));return null;});
  j.update("UPDATE recording_session SET writer_lease_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE session_id=?",s.sessionId());assertThrows(RecordingService.StaleWriter.class,()->a.renew(s.sessionId(),2,old));
  var taken=new JdbcRecordingWriterLeases(source).acquire(s.sessionId(),2,next,UUID.randomUUID().toString());assertEquals(first.writerEpoch()+1,taken.writerEpoch());
  assertThrows(RecordingService.StaleWriter.class,()->tx.required(()->{a.require(s.sessionId(),2,old);return null;}));assertEquals(1,recording().ack(s.sessionId(),s.partId()).highestContiguousSeq());
  tx.required(()->{a.require(s.sessionId(),3,next);assertEquals(1,service.accept(s.sessionId(),3,new RecordingContracts.Chunk(s.partId(),3,1,0,2,new byte[]{1,2,3,4})).highestContiguousSeq());return null;});
 }
 @Test void reconnectAdvancesEpochAndAnOldCloseCannotReleaseTheNewConnection(){
  var service=recording();var s=service.open(UUID.randomUUID().toString(),"database-lease-fixture");var leases=new JdbcRecordingWriterLeases(source);String collector=UUID.randomUUID().toString(),old=UUID.randomUUID().toString(),next=UUID.randomUUID().toString(),key=UUID.randomUUID().toString();leases.acquire(s.sessionId(),1,collector,key);
  tx.required(()->{leases.bindConnection(s.sessionId(),2,collector,old);return null;});assertThrows(RecordingService.StaleWriter.class,()->tx.required(()->{leases.bindConnection(s.sessionId(),2,collector,next);return null;}));
  var reconnected=leases.acquire(s.sessionId(),2,collector,UUID.randomUUID().toString());assertEquals(3,reconnected.writerEpoch());tx.required(()->{leases.bindConnection(s.sessionId(),3,collector,next);return null;});leases.detachConnection(s.sessionId(),2,collector,old);
  assertThrows(RecordingService.StaleWriter.class,()->tx.required(()->{leases.requireConnection(s.sessionId(),2,collector,old);return null;}));assertDoesNotThrow(()->tx.required(()->{leases.requireConnection(s.sessionId(),3,collector,next);return null;}));assertThrows(RecordingService.StaleWriter.class,()->leases.acquire(s.sessionId(),1,collector,key));
  service.pause(s.sessionId(),3);var part=service.nextPart(s.sessionId(),3);assertEquals(4,part.writerEpoch());assertEquals(4,leases.renew(s.sessionId(),4,collector).writerEpoch());
  assertDoesNotThrow(()->tx.required(()->{leases.bindConnection(s.sessionId(),4,collector,UUID.randomUUID().toString());return null;}));
 }
 @Test void databaseRollbackAndConcurrentClaimsCannotCreateTwoOwners()throws Exception {
  var s=recording().open(UUID.randomUUID().toString(),"database-lease-fixture");var leases=new JdbcRecordingWriterLeases(source);
  assertThrows(IllegalStateException.class,()->tx.required(()->{leases.acquire(s.sessionId(),1,UUID.randomUUID().toString(),UUID.randomUUID().toString());throw new IllegalStateException("controlled rollback");}));assertEquals(1,recording().session(s.sessionId()).writerEpoch());
  var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);try{
   var futures=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)futures.add(executor.submit(()->{start.await();try{new JdbcRecordingWriterLeases(source).acquire(s.sessionId(),1,UUID.randomUUID().toString(),UUID.randomUUID().toString());return true;}catch(RecordingService.StaleWriter expected){return false;}}));start.countDown();int winners=0;for(var future:futures)if(future.get(5,TimeUnit.SECONDS))winners++;assertEquals(1,winners);assertEquals(2,recording().session(s.sessionId()).writerEpoch());
  }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
 }
}
