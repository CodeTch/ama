package ama.meeting;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** JDBC/authorization doubles; actual MySQL protection/race qualification remains a separate gate. */
class Task12BoundedMeetingMetadataTest {
 static final String ID="00000000-0000-0000-0000-000000000001";
 DataSource source;Connection connection;PreparedStatement metadataStatement,contentStatement;ResultSet metadata,content;JdbcMeetingAccess access;MeetingAccess.Locked gate;Actor actor;ConnectionHolder holder;
 @BeforeEach void setup()throws Exception {
  source=mock(DataSource.class);connection=mock(Connection.class);metadataStatement=mock(PreparedStatement.class);contentStatement=mock(PreparedStatement.class);metadata=mock(ResultSet.class);content=mock(ResultSet.class);access=mock(JdbcMeetingAccess.class);gate=mock(MeetingAccess.Locked.class);actor=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));
  when(source.getConnection()).thenReturn(connection);when(connection.prepareStatement(anyString())).thenAnswer(i->((String)i.getArgument(0)).contains("__n0")?metadataStatement:contentStatement);
  when(metadataStatement.executeQuery()).thenReturn(metadata);when(contentStatement.executeQuery()).thenReturn(content);when(metadata.next()).thenReturn(true,false);when(content.next()).thenReturn(true,false);when(metadata.getString("id")).thenReturn(ID);when(content.getString("id")).thenReturn(ID);
  holder=new ConnectionHolder(connection);TransactionSynchronizationManager.bindResource(source,holder);TransactionSynchronizationManager.setActualTransactionActive(true);
 }
 @AfterEach void cleanup(){if(TransactionSynchronizationManager.hasResource(source))TransactionSynchronizationManager.unbindResource(source);TransactionSynchronizationManager.setActualTransactionActive(false);}
 BoundedMeetingMetadata reader(){return new BoundedMeetingMetadata(source,access,actor,ID,gate,new WorkspaceReadBudget());}
 void length(int field,long size)throws Exception {when(metadata.getLong("__b"+field)).thenReturn(size);when(metadata.getLong("__p"+field)).thenReturn(size);when(metadata.getLong("__u"+field)).thenReturn(size);}
 @Test void deniedReservationIssuesOnlyNumericMetadataAndNeverContentQuery()throws Exception {
  length(0,12_000_000);var reader=reader();
  assertThrows(IllegalStateException.class,()->reader.capture("meeting_info_revision","name","id=?",1,ID));
  verify(metadataStatement).executeQuery();verify(contentStatement,never()).executeQuery();verify(metadata,never()).getString("name");
 }
 @Test void exactHeldSelectionTransfersOnlyAfterMetadataAndChecksLengths()throws Exception {
  length(0,3);when(content.getString("name")).thenReturn("abc");var reader=reader();var selected=reader.capture("meeting_info_revision","name","id=?",1,ID);
  verify(contentStatement,never()).executeQuery();assertEquals("abc",reader.read(selected).getFirst().get("name"));
  verify(access,atLeastOnce()).requirePrepared(gate,actor,ID,List.of(),MeetingAccess.Action.READ);
 }
 @Test void lostHolderAndForeignSelectionCannotAuthorizeContent()throws Exception {
  length(0,3);var first=reader();var selection=first.capture("meeting_info_revision","name","id=?",1,ID);var second=reader();
  assertThrows(IllegalStateException.class,()->second.read(selection));TransactionSynchronizationManager.unbindResource(source);
  assertThrows(IllegalStateException.class,()->first.read(selection));verify(contentStatement,never()).executeQuery();
 }
 @Test void cumulativeFieldsRejectBeforeAnyTextAndChildrenRequireCapturedParent()throws Exception {
  for(int i=0;i<3;i++)length(i,200000);var reader=reader();
  assertThrows(IllegalStateException.class,()->reader.capture("meeting_info_revision","name summary category_id","id=?",1,ID));verify(contentStatement,never()).executeQuery();
  assertThrows(IllegalStateException.class,()->reader().capture("roster_member","display_name","roster_id=?",128,ID));
 }
 @Test void actualAccessIssuerRejectsDifferentDataSourceBeforeContent() {
  var other=mock(DataSource.class);var actualAccess=new JdbcMeetingAccess(other,mock(ama.db.TransactionRunner.class),mock(ama.task.SubjectFencePort.class));
  assertThrows(IllegalArgumentException.class,()->new BoundedMeetingMetadata(source,actualAccess,actor,ID,gate,new WorkspaceReadBudget()));
  assertThrows(IllegalArgumentException.class,()->new JdbcMeetingReadProjection(source,mock(ama.db.TransactionRunner.class),actualAccess));
 }
 @Test void selectionCannotRepeatContentTransferWithoutAnotherReservation()throws Exception {
  length(0,3);when(content.getString("name")).thenReturn("abc");var reader=reader();var selection=reader.capture("meeting_info_revision","name","id=?",1,ID);
  reader.read(selection);assertThrows(IllegalStateException.class,()->reader.read(selection));verify(contentStatement,times(1)).executeQuery();
 }
 @Test void fiftyShortListProjectionsShareOneCumulativeBudget()throws Exception {
  var budget=new WorkspaceReadBudget();length(0,3);when(content.getString("name")).thenReturn("abc");
  for(int i=0;i<50;i++) {
   when(metadata.next()).thenReturn(true,false);when(content.next()).thenReturn(true,false);
   var reader=new BoundedMeetingMetadata(source,access,actor,ID,gate,budget);
   var selected=reader.capture("meeting_info_revision","name","id=?",1,ID);assertEquals("abc",reader.read(selected).getFirst().get("name"));
  }
  assertTrue(budget.usage().bytes()>0);verify(contentStatement,times(50)).executeQuery();
 }

}
