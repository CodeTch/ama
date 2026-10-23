package ama.db;

import java.util.Objects;
import java.util.concurrent.Callable;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class TransactionRunner {
    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbc;

    public TransactionRunner(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc = new JdbcTemplate(dataSource);
    }

    /** REQUIRED joins nested work; all exceptions mark the caller transaction for rollback. */
    public <T> T required(Callable<T> work) {
        Objects.requireNonNull(work, "work");
        return transactions.execute(status -> {
            jdbc.execute("SET NAMES utf8mb4");
            jdbc.execute("SET time_zone = '+00:00'");
            try {
                return work.call();
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException("Transaction work failed", failure);
            }
        });
    }
}
