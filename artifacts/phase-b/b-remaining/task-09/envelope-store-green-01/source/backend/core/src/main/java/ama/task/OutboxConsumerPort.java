package ama.task;
import ama.contract.OutboxContracts.OutboxEvent;
import java.time.Instant;import java.util.*;
public interface OutboxConsumerPort {
 record Lease(String consumerId,long epoch,String owner,Instant expiresAt){}
 void register(String consumerId);
 Optional<Lease> claim(String consumerId,String owner);
 boolean renew(Lease lease);
 List<OutboxEvent> read(Lease lease,int limit);
 boolean apply(Lease lease,List<OutboxEvent> continuousPrefix);
}
