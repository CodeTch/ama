package ama.task;

import ama.contract.TaskContracts.SubjectRef;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcSubjectFences implements SubjectFencePort {
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final Set<String> allowedKinds;
    private record Held(SubjectRef subject, long businessVersion, long cancelEpoch, boolean deleted,
                        TaskTransactionContext context, JdbcSubjectFences issuer) implements LockedSubject {}

    public JdbcSubjectFences(DataSource source, Set<String> allowedKinds) {
        this.source = source; this.jdbc = new JdbcTemplate(source); this.allowedKinds = Set.copyOf(allowedKinds);
    }

    @Override public LockedSubject create(SubjectRef subject, long version, long epoch) {
        var context = context(subject);
        if (version < 0 || epoch < 0) throw new IllegalArgumentException("Negative fence");
        jdbc.update("INSERT INTO task_subject(subject_kind,subject_id,business_version,cancel_epoch,deleted) VALUES (?,?,?,?,0)",
                subject.kind(), subject.id(), version, epoch);
        return new Held(subject, version, epoch, false, context, this);
    }

    @Override public Optional<LockedSubject> lock(SubjectRef subject, boolean skipLocked) {
        var context = context(subject);
        return jdbc.query("SELECT business_version,cancel_epoch,deleted FROM task_subject WHERE subject_kind=? AND subject_id=? FOR UPDATE" + (skipLocked ? " SKIP LOCKED" : ""),
                (row,index) -> (LockedSubject) new Held(subject, row.getLong(1), row.getLong(2), row.getBoolean(3), context, this),
                subject.kind(), subject.id()).stream().findFirst();
    }

    @Override public LockedSubject advance(LockedSubject locked, long version, long epoch, boolean deleted) {
        var context = context(locked.subject());
        if (!(locked instanceof Held held) || held.context() != context || held.issuer() != this) {
            throw new IllegalArgumentException("Fence handle belongs to another transaction or issuer");
        }
        if (locked.deleted() || version < locked.businessVersion() || epoch < locked.cancelEpoch()
                || (version == locked.businessVersion() && epoch == locked.cancelEpoch() && !deleted)
                || (deleted && epoch <= locked.cancelEpoch())) {
            throw new IllegalArgumentException("Fence must advance monotonically; tombstones cannot be revived");
        }
        int changed = jdbc.update("UPDATE task_subject SET business_version=?,cancel_epoch=?,deleted=? WHERE subject_kind=? AND subject_id=? AND business_version=? AND cancel_epoch=? AND deleted=0",
                version, epoch, deleted, locked.subject().kind(), locked.subject().id(), locked.businessVersion(), locked.cancelEpoch());
        if (changed != 1) throw new IllegalStateException("Fence changed or tombstoned");
        return new Held(locked.subject(), version, epoch, deleted, context, this);
    }

    private TaskTransactionContext context(SubjectRef subject) {
        if (!allowedKinds.contains(subject.kind())) throw new IllegalArgumentException("Unregistered subject kind");
        var context = TaskTransactionContext.current(source);
        context.requireBeforeFlush();
        return context;
    }
}
