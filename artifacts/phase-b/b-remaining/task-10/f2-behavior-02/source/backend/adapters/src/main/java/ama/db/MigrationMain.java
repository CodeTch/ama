package ama.db;

/** Deliberately separate from application startup. Credentials come only from environment. */
public final class MigrationMain {
    private MigrationMain() {}

    public static void main(String[] args) {
        try {
            int applied = new SchemaMigrator(MysqlDataSources.fromEnvironment("DATABASE_")).migrate();
            System.out.println("MySQL migration complete; applied=" + applied);
        } catch (Exception failure) {
            System.err.println("MySQL migration failed; inspect database access and migration state securely");
            System.exit(1);
        }
    }
}
