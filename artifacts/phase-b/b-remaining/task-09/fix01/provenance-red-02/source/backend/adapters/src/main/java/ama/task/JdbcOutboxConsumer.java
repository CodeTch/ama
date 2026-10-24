package ama.task;

import ama.contract.OutboxContracts.*;
import ama.db.TransactionRunner;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.task.JdbcTaskQueue.*;
import static ama.contract.OutboxContracts.protocolCode;

public final class JdbcOutboxConsumer implements OutboxConsumerPort {
 private final DataSource source;private final JdbcTemplate j;private final TransactionRunner tx;private final JdbcOutbox outbox;private final Map<String,OutboxConsumerPolicy> policies;
 private record State(String id,long cursor,long epoch,String owner,Instant expiry){}
 public JdbcOutboxConsumer(DataSource source,JdbcOutbox outbox,Map<String,OutboxConsumerPolicy> policies){this.source=source;j=new JdbcTemplate(source);tx=new TransactionRunner(source);this.outbox=outbox;this.policies=Map.copyOf(policies);}
 private OutboxConsumerPolicy policy(String id){protocolCode(id);var p=policies.get(id);if(p==null)throw new IllegalArgumentException("Unregistered consumer");return p;}
 private Optional<State> state(String id,boolean lock){return j.query("SELECT * FROM outbox_consumer WHERE consumer_id=?"+(lock?" FOR UPDATE":""),(r,n)->new State(id,r.getLong("cursor"),r.getLong("epoch"),r.getString("lease_owner"),instant(r,"expires_at")),id).stream().findFirst();}
 private Instant now(){return j.queryForObject("SELECT UTC_TIMESTAMP(6)",(r,n)->instant(r,1));}
 private boolean current(State s,Lease l){return s.epoch()==l.epoch()&&Objects.equals(s.owner(),l.owner())&&s.expiry()!=null&&s.expiry().isAfter(now());}
 @Override public void register(String id){independent();policy(id);tx.required(()->{j.update("INSERT INTO outbox_consumer(consumer_id) VALUES (?) ON DUPLICATE KEY UPDATE consumer_id=consumer_id",id);return null;});}
 @Override public Optional<Lease> claim(String id,String owner){independent();policy(id);protocolCode(owner);return tx.required(()->{var row=state(id,true);if(row.isEmpty())return Optional.empty();var s=row.orElseThrow();Instant now=now();if(s.expiry()!=null&&s.expiry().isAfter(now))return Optional.empty();Instant expiry=now.plusSeconds(20);j.update("UPDATE outbox_consumer SET epoch=epoch+1,lease_owner=?,expires_at=? WHERE consumer_id=?",owner,sqlTime(expiry),id);return Optional.of(new Lease(id,s.epoch()+1,owner,expiry));});}
 @Override public boolean renew(Lease lease){independent();policy(lease.consumerId());return tx.required(()->{var row=state(lease.consumerId(),true);if(row.isEmpty()||!current(row.orElseThrow(),lease))return false;j.update("UPDATE outbox_consumer SET expires_at=UTC_TIMESTAMP(6)+INTERVAL 20 SECOND WHERE consumer_id=?",lease.consumerId());return true;});}
 @Override public List<OutboxEvent> read(Lease lease,int limit){policy(lease.consumerId());var row=state(lease.consumerId(),false);if(row.isEmpty()||!current(row.orElseThrow(),lease))return List.of();return outbox.eventsAfter(row.orElseThrow().cursor(),limit);}
 @Override public boolean apply(Lease lease,List<OutboxEvent> events){
  independent();var p=policy(lease.consumerId());events=List.copyOf(events);if(events.isEmpty()||events.size()>1000)throw new IllegalArgumentException("Bounded nonempty prefix required");final var batch=events;
  return tx.required(()->{
   // Validate visible prefix before prelocking domains, and again after the consumer lock.
   var seen=state(lease.consumerId(),false);if(seen.isEmpty()||!current(seen.orElseThrow(),lease)||!outbox.eventsAfter(seen.orElseThrow().cursor(),batch.size()).equals(batch))return false;
   p.prelockDomains(batch);
   var held=state(lease.consumerId(),true).orElseThrow();if(!current(held,lease)||held.cursor()!=seen.orElseThrow().cursor())return false;
   long sequence=held.cursor();for(var event:batch)if(event.sequence()!=++sequence)return false;
   p.applyToPrelockedDomains(batch);
   j.update("UPDATE outbox_consumer SET `cursor`=? WHERE consumer_id=?",sequence,lease.consumerId());
   outbox.flush();return true;
  });
 }
}
