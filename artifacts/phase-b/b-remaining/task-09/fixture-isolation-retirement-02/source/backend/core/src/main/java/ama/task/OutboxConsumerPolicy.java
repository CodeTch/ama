package ama.task;
import ama.contract.OutboxContracts.OutboxEvent;import java.util.List;
/** Trusted local consumer: lock all subjects then roots in sorted order, before consumer. Never network here. */
public interface OutboxConsumerPolicy {
 void prelockDomains(List<OutboxEvent> events);
 /** Only already-prelocked local effects; this and cursor commit atomically. */
 void applyToPrelockedDomains(List<OutboxEvent> events);
}
