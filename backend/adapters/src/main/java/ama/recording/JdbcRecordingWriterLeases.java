package ama.recording;

import ama.db.TransactionRunner;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static ama.contract.OutboxContracts.uuid;

/** Collector IDs fence writers; callers still authorize the current actor and meeting before this row. */
public final class JdbcRecordingWriterLeases {
 public static final int TTL_SECONDS=30;
 public record Lease(String sessionId,String partId,long writerEpoch,String backendId,Instant expiresAt){}
 private record Row(String session,String part,long epoch,String backend,String state,String collector,String claim,Long base,Long claimedEpoch,Instant expiry,String connection) {
  Lease lease(){return new Lease(session,part,epoch,backend,expiry);}
 }
 private final DataSource source;private final JdbcTemplate j;private final TransactionRunner tx;
 public JdbcRecordingWriterLeases(DataSource source){this.source=Objects.requireNonNull(source);j=new JdbcTemplate(source);j.setQueryTimeout(2);tx=new TransactionRunner(source,10);}
 private void transaction(){if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.hasResource(source))throw new IllegalStateException("Writer guard requires caller's meeting transaction");}
 private void capture(String session){
  var meetings=j.queryForList("SELECT meeting_id FROM recording_session WHERE session_id=?",String.class,session);if(meetings.size()!=1)throw new IllegalArgumentException("Unknown recording session");String meeting=meetings.getFirst();
  j.update("INSERT INTO recording_meeting_capture(meeting_id) VALUES (?) ON DUPLICATE KEY UPDATE meeting_id=meeting_id",meeting);
  String current=j.queryForObject("SELECT session_id FROM recording_meeting_capture WHERE meeting_id=? FOR UPDATE",String.class,meeting);
  if(current==null){
   // The admission row serializes all new admissions. Discovery takes no cross-meeting range lock.
   var active=j.queryForList("SELECT session_id FROM recording_session WHERE meeting_id=? AND state IN ('OPEN','PAUSED') ORDER BY session_id LIMIT 2",String.class,meeting);
   if(!active.equals(List.of(session)))throw new RecordingService.StaleWriter();
   j.update("UPDATE recording_meeting_capture SET session_id=? WHERE meeting_id=?",session,meeting);
  }else if(!current.equals(session))throw new RecordingService.StaleWriter();
 }
 private Row row(String session){
  transaction();uuid(session);capture(session);
  var rows=j.query("SELECT session_id,part_id,writer_epoch,backend_id,state,writer_collector_id,writer_claim_id,writer_claim_base_epoch,writer_claim_epoch,writer_lease_expires_at,writer_connection_id FROM recording_session WHERE session_id=? FOR UPDATE",(r,n)->{
   var expiry=r.getObject(10,LocalDateTime.class);
   return new Row(r.getString(1),r.getString(2),r.getLong(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getObject(8,Long.class),r.getObject(9,Long.class),expiry==null?null:expiry.toInstant(ZoneOffset.UTC),r.getString(11));
  },session);if(rows.size()!=1)throw new IllegalArgumentException("Unknown recording session");return rows.getFirst();
 }
 private Instant now(){return j.queryForObject("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getObject(1,LocalDateTime.class).toInstant(ZoneOffset.UTC));}
 private static void open(Row r){if(!Set.of("OPEN","PAUSED").contains(r.state()))throw new RecordingService.Closed();}
 private static void epoch(long epoch){if(epoch<1)throw new RecordingService.StaleWriter();}
 private static void active(Row row,long epoch,String collector,Instant now){
  open(row);epoch(epoch);uuid(collector);
  if(row.epoch()!=epoch||!collector.equals(row.collector())||row.expiry()==null||!row.expiry().isAfter(now))throw new RecordingService.StaleWriter();
 }
 /** Independent REST callers hold their owner gate outside; REQUIRED preserves an ambient gate. */
 public Lease acquire(String session,long expectedEpoch,String collector,String claim){
  uuid(collector);uuid(claim);epoch(expectedEpoch);
  return tx.required(()->{
   var row=row(session);open(row);var now=now();
   if(claim.equals(row.claim())){
    if(!collector.equals(row.collector())||!Objects.equals(row.base(),expectedEpoch)||!Objects.equals(row.claimedEpoch(),row.epoch()))throw new RecordingService.StaleWriter();
    active(row,row.epoch(),collector,now);return row.lease();
   }
   if(row.epoch()!=expectedEpoch)throw new RecordingService.StaleWriter();
   if(row.collector()!=null){
    if(row.expiry()==null)throw new IllegalStateException("Unprovable writer lease");
    if(row.expiry().isAfter(now)&&!collector.equals(row.collector()))throw new RecordingService.StaleWriter();
   }
   long next=Math.addExact(row.epoch(),1);var expiry=now.plusSeconds(TTL_SECONDS);
   if(j.update("UPDATE recording_session SET writer_epoch=?,writer_collector_id=?,writer_claim_id=?,writer_claim_base_epoch=?,writer_claim_epoch=?,writer_lease_expires_at=?,writer_connection_id=NULL WHERE session_id=? AND writer_epoch=?",next,collector,claim,expectedEpoch,next,LocalDateTime.ofInstant(expiry,ZoneOffset.UTC),session,expectedEpoch)!=1)throw new RecordingService.StaleWriter();
   return new Lease(row.session(),row.part(),next,row.backend(),expiry);
  });
 }
 public Lease renew(String session,long expectedEpoch,String collector){
  return tx.required(()->{var row=row(session);var now=now();active(row,expectedEpoch,collector,now);var expiry=now.plusSeconds(TTL_SECONDS);
   j.update("UPDATE recording_session SET writer_lease_expires_at=? WHERE session_id=?",LocalDateTime.ofInstant(expiry,ZoneOffset.UTC),session);return new Lease(row.session(),row.part(),row.epoch(),row.backend(),expiry);
  });
 }
 /** Identity→meeting is already held by HTTP/socket transport. Never issue a detached authorization. */
 public void require(String session,long expectedEpoch,String collector){var row=row(session);active(row,expectedEpoch,collector,now());}
 public void bindConnection(String session,long expectedEpoch,String collector,String connection){
  uuid(connection);var row=row(session);active(row,expectedEpoch,collector,now());
  if(row.connection()!=null&&!row.connection().equals(connection))throw new RecordingService.StaleWriter();
  j.update("UPDATE recording_session SET writer_connection_id=? WHERE session_id=?",connection,session);
 }
 public void requireConnection(String session,long expectedEpoch,String collector,String connection){
  uuid(connection);var row=row(session);active(row,expectedEpoch,collector,now());if(!connection.equals(row.connection()))throw new RecordingService.StaleWriter();
 }
 /** Deny-only cleanup: it cannot renew, change ownership, or release another epoch's connection. */
 public void detachConnection(String session,long expectedEpoch,String collector,String connection){
  uuid(session);uuid(collector);uuid(connection);epoch(expectedEpoch);
  tx.required(()->{j.update("UPDATE recording_session SET writer_connection_id=NULL WHERE session_id=? AND writer_epoch=? AND writer_collector_id=? AND writer_connection_id=?",session,expectedEpoch,collector,connection);return null;});
 }
}
