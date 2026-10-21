package ama.support;

import ama.db.MysqlDataSources;
import javax.sql.DataSource;

/** Owns only disposable fixture data; never admits company/network database configuration. */
public final class MysqlTestDatabase {
    private MysqlTestDatabase() {}

    /** Destroys all objects only in the explicitly guarded disposable fixture schema. */
    public static DataSource reset() {
        var source = dataSource();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE ama_b01_fixture");
            statement.execute("CREATE DATABASE ama_b01_fixture CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            return source;
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("Owned disposable fixture schema reset failed", failure);
        }
    }

    public static DataSource dataSource() {
        String url = System.getenv("AMA_TEST_DATABASE_URL");
        String container = System.getenv("AMA_TEST_CONTAINER");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_b01_fixture")
                || container == null || !container.matches("ama-test-b01-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Use the owned loopback MySQL fixture and disposable ama_b01_fixture schema");
        }
        return MysqlDataSources.fromEnvironment("AMA_TEST_DATABASE_");
    }
}
