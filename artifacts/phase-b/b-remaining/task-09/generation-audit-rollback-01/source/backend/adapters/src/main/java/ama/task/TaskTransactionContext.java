package ama.task;

import ama.contract.OutboxContracts.EventDraft;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** One context per actual caller transaction/DataSource. No automatic flush and no network callbacks. */
final class TaskTransactionContext implements TransactionSynchronization {
    private record Key(DataSource source) {}
    final List<EventDraft> events = new ArrayList<>();
    private final Key key;
    boolean flushed;

    private TaskTransactionContext(Key key) { this.key = key; }

    static TaskTransactionContext current(DataSource source) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source)
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Task/outbox work requires the caller transaction on the same DataSource");
        }
        var key = new Key(source);
        var context = (TaskTransactionContext) TransactionSynchronizationManager.getResource(key);
        if (context == null) {
            context = new TaskTransactionContext(key);
            TransactionSynchronizationManager.bindResource(key, context);
            TransactionSynchronizationManager.registerSynchronization(context);
        }
        return context;
    }

    void requireBeforeFlush() {
        if (flushed) throw new IllegalStateException("Outbox was already flushed: no later domain/task locks are supported");
    }

    void requireOnlySupportedSynchronization() {
        if (TransactionSynchronizationManager.getSynchronizations().stream().anyMatch(sync -> sync != this)) {
            throw new IllegalStateException("Outbox transactions cannot contain other transaction synchronizations");
        }
    }

    @Override public int getOrder() { return Integer.MIN_VALUE; }

    @Override public void beforeCommit(boolean readOnly) {
        if (events.isEmpty() && !flushed) return;
        requireOnlySupportedSynchronization();
        if (!flushed) throw new IllegalStateException("Pending outbox events require an explicit final flush");
    }

    @Override public void afterCompletion(int status) {
        TransactionSynchronizationManager.unbindResourceIfPossible(key);
        events.clear();
    }
}
