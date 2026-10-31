package ama.identity;

import ama.db.MysqlDataSources;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Connection-local MySQL clock skew. Never changes the host/container clock or shared fixture rows. */
class IdentitySessionClockIT {
 static DataSource source;
 @BeforeAll static void schema(){String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Owned local C schema required");source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();}
 static DataSource at(Instant clock){return new AbstractDataSource(){
  public Connection getConnection()throws SQLException {var connection=source.getConnection();try(var statement=connection.createStatement()){statement.execute("SET timestamp="+clock.getEpochSecond());}catch(SQLException failure){connection.close();throw failure;}return connection;}
  public Connection getConnection(String user,String password)throws SQLException {throw new SQLException("Explicit fixture source only");}
 };}
 @Test void issuanceAndResolutionUseTheSameDatabaseClockDespiteAJvmOffset(){
  Instant time=Instant.now().minusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);var db=at(time);var sessions=new JdbcIdentitySessions(db);String actor="clock-"+UUID.randomUUID();sessions.ensureCasIdentity(actor,"clock fixture","owned integration");
  var issued=sessions.issueForVerifiedIdentity(actor,Duration.ofSeconds(120));
  assertEquals(time.plusSeconds(120),issued.expiresAt(),"the shared DB is already the validity clock");
  assertEquals(actor,new JdbcIdentitySessions(db).resolve(issued.token()).orElseThrow().actor().enterpriseId());
  assertTrue(new JdbcIdentitySessions(at(time.plusSeconds(121))).resolve(issued.token()).isEmpty());
 }
 @Test void futureDatedRevokedAndWrongGenerationSessionsStillFailClosed(){
  Instant time=Instant.now().minusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);var db=at(time);var sessions=new JdbcIdentitySessions(db);var j=new JdbcTemplate(db);String actor="clock-"+UUID.randomUUID();sessions.ensureCasIdentity(actor,"clock fixture","owned integration");
  var future=sessions.issueForVerifiedIdentity(actor,Duration.ofSeconds(120));j.update("UPDATE identity_session SET issued_at=UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,expires_at=UTC_TIMESTAMP(6)+INTERVAL 2 HOUR WHERE enterprise_id=?",actor);assertTrue(sessions.resolve(future.token()).isEmpty());
  var revoked=sessions.issueForVerifiedIdentity(actor,Duration.ofSeconds(120));assertTrue(sessions.resolve(revoked.token()).isPresent());sessions.revoke(revoked.token(),"CLOCK_REVOKE");assertTrue(sessions.resolve(revoked.token()).isEmpty());
  var generation=sessions.issueForVerifiedIdentity(actor,Duration.ofSeconds(120));assertTrue(sessions.resolve(generation.token()).isPresent());sessions.revokeAll(actor,"CLOCK_REVOKE_ALL");assertTrue(new JdbcIdentitySessions(db).resolve(generation.token()).isEmpty());
 }
 @Test void backwardDatabaseStepCannotRejectAnAlreadyIssuedSession(){
  Instant time=Instant.now().minusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);var sessions=new JdbcIdentitySessions(at(time));String actor="clock-backward-"+UUID.randomUUID();sessions.ensureCasIdentity(actor,"clock fixture","owned integration");
  var issued=sessions.issueForVerifiedIdentity(actor,Duration.ofSeconds(120));assertTrue(sessions.resolve(issued.token()).isPresent());
  assertEquals(actor,new JdbcIdentitySessions(at(time.minusSeconds(1))).resolve(issued.token()).orElseThrow().actor().enterpriseId());
 }
 @Test void anObservedExpiryCannotReviveAfterClockStepsBack(){
  Instant time=Instant.now().minusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);var sessions=new JdbcIdentitySessions(at(time));String actor="clock-expiry-"+UUID.randomUUID();sessions.ensureCasIdentity(actor,"clock fixture","owned integration");
  var issued=sessions.issueForVerifiedIdentity(actor,Duration.ofSeconds(120));assertTrue(new JdbcIdentitySessions(at(time.plusSeconds(121))).resolve(issued.token()).isEmpty());
  assertTrue(new JdbcIdentitySessions(at(time.plusSeconds(60))).resolve(issued.token()).isEmpty(),"an expired session cannot revive after a backward wall-clock adjustment");
 }
 @Test void committedRevocationIsCurrentEvenInsideAnOlderRepeatableReadView()throws Exception {
  Instant time=Instant.now().minusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);var db=at(time);var sessions=new JdbcIdentitySessions(db);String actor="clock-current-"+UUID.randomUUID();sessions.ensureCasIdentity(actor,"clock fixture","owned integration");var issued=sessions.issueForVerifiedIdentity(actor,Duration.ofSeconds(120));
  try(var pool=java.util.concurrent.Executors.newSingleThreadExecutor()){
   new ama.db.TransactionRunner(db).required(()->{
    var j=new JdbcTemplate(db);assertEquals(0L,j.queryForObject("SELECT session_generation FROM enterprise_identity WHERE enterprise_id=?",Long.class,actor));
    pool.submit(()->new JdbcIdentitySessions(db).revokeAll(actor,"CLOCK_CURRENT_VIEW")).get(8,java.util.concurrent.TimeUnit.SECONDS);
    assertTrue(sessions.resolve(issued.token()).isEmpty(),"a preexisting RR view cannot restore committed revoked session authority");return null;
   });
  }
 }
}
