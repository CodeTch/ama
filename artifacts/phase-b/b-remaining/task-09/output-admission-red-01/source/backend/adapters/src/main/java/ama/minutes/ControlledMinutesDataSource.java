package ama.minutes;
import org.springframework.jdbc.datasource.AbstractDataSource;import javax.sql.DataSource;import java.sql.*;import java.util.Objects;
/** Every controlled application/diagnostic connection starts with the same finite session contract. */
public final class ControlledMinutesDataSource extends AbstractDataSource {
 private final DataSource delegate;public ControlledMinutesDataSource(DataSource delegate){this.delegate=Objects.requireNonNull(delegate);}
 public Connection getConnection()throws SQLException{return initialize(delegate.getConnection());}public Connection getConnection(String user,String password)throws SQLException{return initialize(delegate.getConnection(user,password));}
 private Connection initialize(Connection connection)throws SQLException{try(var statement=connection.createStatement()){statement.setQueryTimeout(5);statement.execute("SET NAMES utf8mb4");statement.execute("SET time_zone='+00:00'");statement.execute("SET SESSION lock_wait_timeout=5");statement.execute("SET SESSION innodb_lock_wait_timeout=5");return connection;}catch(SQLException|RuntimeException failure){try{connection.close();}catch(SQLException close){failure.addSuppressed(close);}throw failure;}}
}
