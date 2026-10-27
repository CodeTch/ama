package ama.meeting;
import ama.contract.IdentityContracts.*;
import java.sql.*;import java.time.*;import java.util.*;import javax.sql.DataSource;
import org.junit.jupiter.api.*;import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
/** JDBC doubles prove bounded query construction/merge, not actual MySQL index choice or ACL. */
class Task12MeetingListScanTest {
 DataSource source;Connection connection;Actor actor=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));List<String> sql;
 @BeforeEach void setup()throws Exception {source=mock(DataSource.class);connection=mock(Connection.class);when(source.getConnection()).thenReturn(connection);sql=new ArrayList<>();}
 void empty()throws Exception {when(connection.prepareStatement(anyString())).thenAnswer(call->{sql.add(call.getArgument(0));var statement=mock(PreparedStatement.class);when(statement.executeQuery()).thenReturn(mock(ResultSet.class));return statement;});}
 @Test void joinedUsesEightBoundedExistingStageIndexRangesAndNeverContent()throws Exception {
  empty();var result=new MeetingListScan(new JdbcTemplate(source)).discover(actor,"joined",20,null,new WorkspaceReadBudget());assertTrue(result.isEmpty());assertEquals(8,sql.size());for(String query:sql){assertTrue(query.contains("FORCE INDEX (meeting_stage_updated)"));assertTrue(query.contains("stage=?"));assertTrue(query.endsWith("LIMIT ?"));assertFalse(query.contains("summary"));assertFalse(query.contains("display_name"));assertFalse(query.contains("SELECT *"));}
 }
 @Test void ownedUsesExistingOwnerIndexAndRejectsUnboundedInputsBeforeSql()throws Exception {
  empty();var scan=new MeetingListScan(new JdbcTemplate(source));scan.discover(actor,"owned",50,null,new WorkspaceReadBudget());assertEquals(1,sql.size());assertTrue(sql.getFirst().contains("FORCE INDEX (meeting_owner_updated)"));assertThrows(IllegalArgumentException.class,()->scan.discover(actor,"owned",51,null,new WorkspaceReadBudget()));assertThrows(IllegalArgumentException.class,()->scan.discover(actor,"other",20,null,new WorkspaceReadBudget()));assertEquals(1,sql.size());
 }
 @Test void cursorPositionRetainsMicrosecondOrderAndExactId() {
  var key=new MeetingListScan.Position(LocalDateTime.parse("2026-10-27T01:02:03.123456"),"00000000-0000-0000-0000-000000000001");assertEquals(key,MeetingListScan.Position.parse(key.encode()));assertThrows(IllegalArgumentException.class,()->MeetingListScan.Position.parse("2026-10-27T01:02:03.123456789|"+key.id()));
 }
 @Test void partitionRowsMergeInGlobalTimeAndIdOrder()throws Exception {
  when(connection.prepareStatement(anyString())).thenAnswer(call->{int n=sql.size();sql.add(call.getArgument(0));var statement=mock(PreparedStatement.class);var rs=mock(ResultSet.class);when(statement.executeQuery()).thenReturn(rs);when(rs.next()).thenReturn(true,false);when(rs.getString("id")).thenReturn(String.format("00000000-0000-0000-0000-%012d",n+1));when(rs.getObject("updated_at",LocalDateTime.class)).thenReturn(LocalDateTime.of(2026,10,27,0,0,n));when(rs.getLong("revision")).thenReturn(0L);when(rs.getString("stage")).thenReturn(MeetingListScan.STAGES.get(n));return statement;});
  var rows=new MeetingListScan(new JdbcTemplate(source)).discover(actor,"joined",20,null,new WorkspaceReadBudget());assertEquals(8,rows.size());assertTrue(rows.getFirst().position().updated().isAfter(rows.getLast().position().updated()));assertTrue(rows.getFirst().position().id().endsWith("000008"));
 }

}
