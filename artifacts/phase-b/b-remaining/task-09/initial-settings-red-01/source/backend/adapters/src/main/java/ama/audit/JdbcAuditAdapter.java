package ama.audit;

import ama.contract.AuditContracts.AuditEvent;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class JdbcAuditAdapter implements AuditPort {
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public JdbcAuditAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
        jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void append(AuditEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalStateException("Audit requires the caller's transaction on the same DataSource");
        }
        var connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (connection.getAutoCommit()) {
                throw new IllegalStateException("Audit cannot use an autocommit connection");
            }
            jdbc.update("INSERT INTO audit_event(actor_id, action, resource_id, revision, result, trace_id, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    event.actorId(), event.action(), event.resourceId(), event.revision(), event.result(),
                    event.traceId(), LocalDateTime.ofInstant(event.time().truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC));
        } catch (SQLException failure) {
            throw new IllegalStateException("Audit transaction connection unavailable", failure);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
