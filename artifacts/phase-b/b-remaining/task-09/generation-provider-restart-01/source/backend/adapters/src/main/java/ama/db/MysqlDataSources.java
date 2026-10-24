package ama.db;

import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class MysqlDataSources {
    private MysqlDataSources() {}

    public static DataSource fromEnvironment(String prefix) {
        String url = System.getenv(prefix + "URL");
        String username = System.getenv(prefix + "USERNAME");
        String password = System.getenv(prefix + "PASSWORD");
        if (url == null || !url.startsWith("jdbc:mysql:") || username == null || password == null) {
            throw new IllegalArgumentException("Explicit MySQL URL, username and password are required");
        }
        var source = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("characterEncoding", "UTF-8");
        properties.setProperty("connectionCollation", "utf8mb4_0900_ai_ci");
        properties.setProperty("connectionTimeZone", "UTC");
        properties.setProperty("forceConnectionTimeZoneToSession", "true");
        properties.setProperty("connectTimeout", "3000");
        properties.setProperty("socketTimeout", "30000");
        source.setConnectionProperties(properties);
        return source;
    }
}
