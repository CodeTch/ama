import ama.support.MysqlTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.flywaydb.core.Flyway;
class B05Migrate {public static void main(String[] args){var s=MysqlTestDatabase.dataSource();var j=new JdbcTemplate(s);System.out.println("Pre-migration table count: "+j.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class));System.out.println("Applied migrations: "+Flyway.configure().dataSource(s).locations("filesystem:backend/adapters/src/main/resources/db/migration").target(args[0]).cleanDisabled(true).load().migrate().migrationsExecuted);}}
