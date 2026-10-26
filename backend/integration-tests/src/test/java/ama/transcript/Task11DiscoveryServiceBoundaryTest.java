package ama.transcript;
import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.task.*;
import ama.db.TransactionRunner;
import java.sql.*;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class Task11DiscoveryServiceBoundaryTest {
 static final String M="12345678-1234-4234-8234-123456789abc";
 static final Actor ACTOR=new Actor("real-viewer",Status.ACTIVE,Set.of(Role.USER));
 static class Fixture {
  final JdbcTranscriptAuthority authority=mock(JdbcTranscriptAuthority.class);
  final ControlledKnowledgeClient client;
  final MeetingAccess access=mock(MeetingAccess.class);
  final AtomicInteger roots=new AtomicInteger(),checks=new AtomicInteger();
  final AtomicBoolean stopped=new AtomicBoolean();final List<String> events=new ArrayList<>();
  boolean drift,denyS,denyF,cancelAfterNetwork;final JdbcPublicationService service;
  final PreparationCancellation cancellation=new PreparationCancellation(){public boolean cancelled(){return stopped.get();}public AutoCloseable register(Runnable abort){throw new AssertionError("Mock transport must not open resources");}};
  Fixture()throws Exception {this(null);}
  Fixture(ControlledKnowledgeClient transport)throws Exception {
   client=transport==null?mock(ControlledKnowledgeClient.class):transport;
   DataSource ds=mock(DataSource.class);when(authority.dataSource()).thenReturn(ds);when(authority.savedAccess()).thenReturn(access);when(authority.publicationDomain()).thenReturn(mock(PublicationTaskDomainPolicy.class));
   when(authority.readBudget()).thenReturn(new ama.contract.ConfigArchiveContracts.ArchiveReadBudget(65536,1000000,100,100,16,10000,16,128,65536,65536,1000000,65536,100,1000,10000));
   when(authority.canonicalBudget()).thenReturn(new ama.contract.MinutesContracts.CanonicalBudget(1000000,32,100000,65536,10000));
   when(ds.getConnection()).thenAnswer(call->{
    var connection=mock(Connection.class);var auto=new AtomicBoolean(true);when(connection.getAutoCommit()).thenAnswer(i->auto.get());doAnswer(i->{auto.set(i.getArgument(0));return null;}).when(connection).setAutoCommit(anyBoolean());
    var init=mock(Statement.class);when(connection.createStatement()).thenReturn(init);when(init.execute(anyString())).thenAnswer(i->{assertTrue(Set.of("SET NAMES utf8mb4","SET time_zone = '+00:00'").contains(i.getArgument(0)));return false;});
    when(connection.prepareStatement(anyString())).thenAnswer(i->{assertEquals("SELECT id,owner_id,lifecycle,governance_state,revision,access_revision,cancel_epoch FROM meeting WHERE id=?",i.getArgument(0));assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
     var statement=mock(PreparedStatement.class);var bound=new AtomicReference<String>();doAnswer(a->{assertEquals(1,(int)a.getArgument(0));bound.set(a.getArgument(1));return null;}).when(statement).setString(anyInt(),anyString());
     when(statement.executeQuery()).thenAnswer(q->{assertEquals(M,bound.get());int n=roots.incrementAndGet();events.add("root"+n);var row=new LinkedHashMap<String,Object>();row.put("id",M);row.put("owner_id","real-viewer");row.put("lifecycle","ACTIVE");row.put("governance_state","ACTIVE");row.put("revision",drift&&n==2?2L:1L);row.put("access_revision",1L);row.put("cancel_epoch",0L);
      var rs=mock(ResultSet.class);var meta=mock(ResultSetMetaData.class);var names=new ArrayList<>(row.keySet());when(meta.getColumnCount()).thenReturn(names.size());when(meta.getColumnLabel(anyInt())).thenAnswer(a->names.get((int)a.getArgument(0)-1));when(rs.getMetaData()).thenReturn(meta);when(rs.next()).thenReturn(true,false);when(rs.getObject(anyInt())).thenAnswer(a->row.get(names.get((int)a.getArgument(0)-1)));return rs;
     });when(statement.executeUpdate()).thenThrow(new AssertionError("No business DML"));return statement;
    });return connection;
   });
   when(authority.transactionRunner()).thenReturn(new TransactionRunner(ds));
   doAnswer(i->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());int n=checks.incrementAndGet();events.add("owner"+n);if(denyS&&n==1||denyF&&n==2)throw new MeetingAccess.AccessDeniedException();return null;}).when(access).require(any(),eq(MeetingAccess.Action.EDIT));
   if(transport==null){when(client.identities()).thenReturn(Map.of("providerId","kb","storeInstanceId","store"));
   var call=mock(ControlledKnowledgeClient.Call.class);
   when(client.authorized(any(),anyString(),anyString(),anyString(),isNull())).thenAnswer(i->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());var auth=(JdbcPublicationService.Authorization)i.getArgument(0);auth.require(client,"DESTINATION_READ");assertEquals("real-viewer",auth.actor());assertEquals("DESTINATION_READ",i.getArgument(1));assertEquals("GET",i.getArgument(2));assertEquals("/v1/knowledge/libraries?limit=20",i.getArgument(3));return call;});
   when(client.exchange(eq(call),eq(262144),same(cancellation))).thenAnswer(i->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());events.add("network");if(cancelAfterNetwork)stopped.set(true);var page=new LinkedHashMap<String,Object>();page.put("providerId","kb");page.put("storeInstanceId","store");page.put("items",List.of());page.put("cursor",null);return page;});
   }
   service=new JdbcPublicationService(authority,()->{throw new AssertionError("No archive");},"a".repeat(64),new TranscriptAcquisitionBudget(1000000,1000000,1000000,10000000,10000000,1000000,16,256,256),null,null,null,client);
  }
  Map<String,Object> run()throws Exception{return service.destinations(ACTOR,M,null,Map.of(),cancellation);}
 }
 @Test void realTransactionRunnerBracketsNetworkWithTwoFreshOwnerChecks()throws Exception {var f=new Fixture();assertEquals(List.of(),f.run().get("items"));assertEquals(List.of("owner1","root1","network","owner2","root2"),f.events);verify(f.access,times(2)).lock(ACTOR,M);}
 @Test void initialDenialDoesNotMintOrSend()throws Exception {var f=new Fixture();f.denyS=true;assertThrows(MeetingAccess.AccessDeniedException.class,f::run);verifyNoInteractions(f.client);assertEquals(0,f.roots.get());}
 @Test void finalRevocationOrRootChangeDiscardsPage()throws Exception {var denied=new Fixture();denied.denyF=true;assertThrows(MeetingAccess.AccessDeniedException.class,denied::run);assertEquals(2,denied.checks.get());var drift=new Fixture();drift.drift=true;assertThrows(ama.contract.MinutesContracts.Failure.class,drift::run);assertEquals(2,drift.roots.get());}
 @Test void cancellationBeforeOrAfterNetworkStopsRemainingPhases()throws Exception {var early=new Fixture();early.stopped.set(true);assertThrows(ama.contract.MinutesContracts.Failure.class,early::run);assertEquals(0,early.checks.get());var late=new Fixture();late.cancelAfterNetwork=true;assertThrows(ama.contract.MinutesContracts.Failure.class,late::run);assertEquals(1,late.checks.get());assertEquals(1,late.roots.get());}
}
