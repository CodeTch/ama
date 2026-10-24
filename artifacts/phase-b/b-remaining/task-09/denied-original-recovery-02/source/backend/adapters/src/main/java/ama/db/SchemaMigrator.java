package ama.db;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/** Only explicit deployment tooling calls this; application startup never migrates. */
public final class SchemaMigrator {
    private final DataSource dataSource;

    public SchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public int migrate() {
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("SELECT GET_LOCK('ama.schema.migration', 20)");
                 var result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new IllegalStateException("MySQL migration lock was not acquired");
                }
            }
            try {
                return Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration")
                        .initSql("SET NAMES utf8mb4; SET time_zone = '+00:00'")
                        .cleanDisabled(true)
                        .load().migrate().migrationsExecuted;
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("SELECT RELEASE_LOCK('ama.schema.migration')");
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("MySQL migration coordination failed", failure);
        }
    }
}
