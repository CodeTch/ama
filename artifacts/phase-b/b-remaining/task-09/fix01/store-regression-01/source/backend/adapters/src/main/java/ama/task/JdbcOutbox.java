package ama.task;

import ama.contract.OutboxContracts.*;
import ama.contract.TaskContracts.SubjectRef;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcOutbox implements OutboxPort {
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final Map<String, OutboxReferenceValidator> validators;

    /** No generic fallback validator: every reference kind must have a domain-owned registered validator. */
    public JdbcOutbox(DataSource source, Map<String, OutboxReferenceValidator> validators) {
        this.source = Objects.requireNonNull(source);
        this.jdbc = new JdbcTemplate(source);
        this.validators = Map.copyOf(validators);
    }

    @Override public void append(EventDraft event) {
        Objects.requireNonNull(event);
        var context = TaskTransactionContext.current(source);
        context.requireBeforeFlush();
        var validator = validators.get(event.reference().kind());
        if (validator == null) throw new IllegalArgumentException("Unregistered outbox reference kind");
        validator.validate(event);
        context.events.add(event);
    }

    @Override public void flush() {
        var context = TaskTransactionContext.current(source);
        context.requireBeforeFlush();
        context.requireOnlySupportedSynchronization();
        if (!context.events.isEmpty()) {
            long sequence = jdbc.queryForObject("SELECT next_id FROM task_outbox_sequence WHERE id=1 FOR UPDATE", Long.class);
            for (var event : context.events) {
                jdbc.update("INSERT INTO task_outbox(sequence_id,event_type,task_id,subject_kind,subject_id,subject_revision,reference_kind,reference_id,occurred_at) VALUES (?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",
                        sequence++, event.eventType(), event.taskId(), event.subject().kind(), event.subject().id(),
                        event.subjectRevision(), event.reference().kind(), event.reference().id());
            }
            jdbc.update("UPDATE task_outbox_sequence SET next_id=? WHERE id=1", sequence);
        }
        context.flushed = true;
    }

    @Override public List<OutboxEvent> eventsAfter(long cursor, int limit) {
        if (cursor < 0 || limit < 1 || limit > 1000) throw new IllegalArgumentException("Invalid outbox page");
        return jdbc.query("SELECT * FROM task_outbox WHERE sequence_id>? ORDER BY sequence_id LIMIT ?", (row,index) -> {
            var subject = new SubjectRef(row.getString("subject_kind"), row.getString("subject_id"));
            var reference = new Reference(row.getString("reference_kind"), row.getString("reference_id"));
            var event = new EventDraft(row.getString("event_type"), row.getString("task_id"), subject, row.getLong("subject_revision"), reference);
            return new OutboxEvent(row.getLong("sequence_id"), event, row.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
        }, cursor, limit);
    }
}
