package ama.task;

import ama.contract.OutboxContracts.EventDraft;
import ama.contract.OutboxContracts.OutboxEvent;
import java.util.List;

/** Content-free references; one shared writer and sequence for task and domain events. */
public interface OutboxPort {
    /** Stage only, in the same caller transaction as domain effects and audit. */
    void append(EventDraft event);
    /** Last operation before returning from the outer REQUIRED callback; exactly once when events exist. */
    void flush();
    /** Internal transport only. User-facing readers must authorize subjects before resolving references. */
    List<OutboxEvent> eventsAfter(long cursor, int limit);
}
