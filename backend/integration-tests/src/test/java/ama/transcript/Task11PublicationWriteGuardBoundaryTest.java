package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.db.TransactionRunner;
import ama.meeting.JdbcMeetingAccess;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real TransactionRunner and guard SQL/capability paths; access and JDBC rows are explicit substitutes. */
public class Task11PublicationWriteGuardBoundaryTest {
 static final String MEETING="12345678-1234-4234-8234-123456789ac1";
 static final Actor ACTOR=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));
 static final class Fixture {
  final DataSource ds=mock(DataSource.class);final JdbcTranscriptAuthority authority=mock(JdbcTranscriptAuthority.class);
  final JdbcMeetingAccess access=mock(JdbcMeetingAccess.class);final MeetingAccess.Locked root=mock(MeetingAccess.Locked.class);
  final TransactionRunner tx=new TransactionRunner(ds);final PublicationWriteGuard guard;
  final AtomicBoolean frozen=new AtomicBoolean();final List<String> sql=new ArrayList<>();
  boolean allowUpdates;final List<String> updates=new ArrayList<>();final AtomicInteger commits=new AtomicInteger(),rollbacks=new AtomicInteger();
  java.util.function.Function<String,List<Map<String,Object>>> responder;java.util.function.BiFunction<String,Map<Integer,Object>,List<Map<String,Object>>> parameterResponder;
  Fixture()throws Exception{
   when(authority.dataSource()).thenReturn(ds);when(authority.savedAccess()).thenReturn(access);
   when(authority.readBudget()).thenReturn(new ama.contract.ConfigArchiveContracts.ArchiveReadBudget(65536,16777216,64,128,128,2000000,128,255,4194304,4194304,16777216,65536,64,100000,500000));
   when(authority.canonicalBudget()).thenReturn(new ama.contract.MinutesContracts.CanonicalBudget(67108864,128,4000000,4194304,16777216));
   when(ds.getConnection()).thenAnswer(a->{var c=mock(Connection.class);doAnswer(z->{commits.incrementAndGet();return null;}).when(c).commit();doAnswer(z->{rollbacks.incrementAndGet();return null;}).when(c).rollback();var auto=new AtomicBoolean(true);when(c.getAutoCommit()).thenAnswer(i->auto.get());doAnswer(i->{auto.set(i.getArgument(0));return null;}).when(c).setAutoCommit(anyBoolean());var init=mock(Statement.class);when(c.createStatement()).thenReturn(init);when(init.execute(anyString())).thenAnswer(i->{assertTrue(Set.of("SET NAMES utf8mb4","SET time_zone = '+00:00'").contains(i.getArgument(0)));return false;});when(c.prepareStatement(anyString())).thenAnswer(i->{String text=i.getArgument(0);sql.add(text);assertTrue(text.startsWith("SELECT ")||allowUpdates);var st=mock(PreparedStatement.class);var parameters=new HashMap<Integer,Object>();doAnswer(z->{parameters.put(z.getArgument(0),z.getArgument(1));return null;}).when(st).setString(anyInt(),anyString());doAnswer(z->{parameters.put(z.getArgument(0),z.getArgument(1));return null;}).when(st).setLong(anyInt(),anyLong());doAnswer(z->{parameters.put(z.getArgument(0),z.getArgument(1));return null;}).when(st).setObject(anyInt(),any());when(st.executeUpdate()).thenAnswer(z->{if(!allowUpdates)throw new AssertionError("Guard must perform no DML");updates.add(text);return 1;});when(st.executeQuery()).thenAnswer(q->{
    List<Map<String,Object>> rows;boolean meeting=text.contains(" FROM meeting ");if(text.startsWith("SELECT COUNT(*)"))rows=List.of(Map.of("count",meeting?1L:0L));else if(meeting){var m=new LinkedHashMap<String,Object>();m.put("id",MEETING);m.put("revision",1L);m.put("access_revision",1L);m.put("cancel_epoch",0L);m.put("ever_published",frozen.get()?1L:0L);m.put("stage",frozen.get()?"PUBLISHED":"ENTRY");m.put("lifecycle","ACTIVE");m.put("governance_state","ACTIVE");m.put("owner_id","owner");m.put("final_confirmation_id",null);String selected=text.substring(7,text.indexOf(" FROM meeting "));if(!selected.equals("*")){var only=new LinkedHashMap<String,Object>();for(String field:selected.split(","))only.put(field,m.get(field));m=only;}rows=List.of(m);}else rows=List.of();
    if(responder!=null){var response=responder.apply(text);if(response!=null)rows=response;}if(parameterResponder!=null){var response=parameterResponder.apply(text,parameters);if(response!=null)rows=response;}final var answerRows=rows;
    var rs=mock(ResultSet.class);var metadata=mock(ResultSetMetaData.class);var names=rows.isEmpty()?List.<String>of():new ArrayList<>(rows.getFirst().keySet());when(metadata.getColumnCount()).thenReturn(names.size());when(metadata.getColumnLabel(anyInt())).thenAnswer(z->names.get((int)z.getArgument(0)-1));when(rs.getMetaData()).thenReturn(metadata);var cursor=new AtomicInteger(-1);when(rs.next()).thenAnswer(z->cursor.incrementAndGet()<answerRows.size());when(rs.getObject(anyInt())).thenAnswer(z->answerRows.get(cursor.get()).get(names.get((int)z.getArgument(0)-1)));when(rs.getLong(anyInt())).thenAnswer(z->((Number)answerRows.get(cursor.get()).get(names.get((int)z.getArgument(0)-1))).longValue());when(rs.getBoolean(anyInt())).thenAnswer(z->{Object v=answerRows.get(cursor.get()).get(names.get((int)z.getArgument(0)-1));return v instanceof Number n?n.longValue()!=0:Boolean.TRUE.equals(v);});when(rs.getInt(anyInt())).thenAnswer(z->((Number)answerRows.get(cursor.get()).get(names.get((int)z.getArgument(0)-1))).intValue());when(rs.getString(anyInt())).thenAnswer(z->(String)answerRows.get(cursor.get()).get(names.get((int)z.getArgument(0)-1)));when(rs.getString(anyString())).thenAnswer(z->(String)answerRows.get(cursor.get()).get(z.getArgument(0)));when(rs.getLong(anyString())).thenAnswer(z->((Number)answerRows.get(cursor.get()).get(z.getArgument(0))).longValue());when(rs.getBoolean(anyString())).thenAnswer(z->Boolean.TRUE.equals(answerRows.get(cursor.get()).get(z.getArgument(0))));when(rs.getBigDecimal(anyString())).thenAnswer(z->{Object v=answerRows.get(cursor.get()).get(z.getArgument(0));return v==null?null:new java.math.BigDecimal(v.toString());});return rs;
   });return st;});return c;});guard=new PublicationWriteGuard(authority);
  }
  PublicationWriteGuard.Prepared prepare(){return guard.prepare(ACTOR,MEETING,new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,33554432,128,256,256).ledger(authority.readBudget(),authority.canonicalBudget(),()->false));}
 }
 public record RetentionFixture(DataSource source,TransactionRunner tx,PublicationWriteGuard guard,PublicationWriteGuard.Prepared prepared,MeetingAccess.Locked gate,AtomicBoolean frozen) {}
 public static RetentionFixture retentionFixture()throws Exception{var f=new Fixture();return new RetentionFixture(f.ds,f.tx,f.guard,f.prepare(),f.root,f.frozen);}
 @Test void realTransactionOwnsControlsTasksAndSingleClaim()throws Exception{var f=new Fixture();var p=f.prepare();f.tx.required(()->{var h=f.guard.controls(p,ACTOR,MEETING,f.root);assertThrows(IllegalArgumentException.class,()->f.guard.claim(h,f.root));f.guard.tasks(h,f.root);f.guard.claim(h,f.root);f.guard.requireClaimed(h,f.root);assertThrows(IllegalArgumentException.class,()->f.guard.claim(h,f.root));return null;});verify(f.access,atLeastOnce()).requirePrepared(f.root,ACTOR,MEETING,List.of("owner"),MeetingAccess.Action.EDIT);assertTrue(f.sql.stream().anyMatch(s->s.endsWith("FOR UPDATE")));}
 @Test void foreignIssuerGateActorAndExpiredHolderCannotClaim()throws Exception{var f=new Fixture();var other=new Fixture();var p=f.prepare();f.tx.required(()->{assertThrows(IllegalArgumentException.class,()->other.guard.controls(p,ACTOR,MEETING,f.root));assertThrows(IllegalArgumentException.class,()->f.guard.controls(p,new Actor("other",Status.ACTIVE,Set.of(Role.USER)),MEETING,f.root));return null;});var scope=f.tx.required(()->{var h=f.guard.controls(p,ACTOR,MEETING,f.root);assertThrows(IllegalArgumentException.class,()->f.guard.tasks(h,mock(MeetingAccess.Locked.class)));return h;});assertThrows(IllegalArgumentException.class,()->f.guard.tasks(scope,f.root));}
 @Test void currentPublicationFenceAfterPreparationDeniesBeforeClaim()throws Exception{var f=new Fixture();var p=f.prepare();f.frozen.set(true);assertThrows(RuntimeException.class,()->f.tx.required(()->{var h=f.guard.controls(p,ACTOR,MEETING,f.root);f.guard.tasks(h,f.root);f.guard.claim(h,f.root);return null;}));}
 @Test void deniedCurrentOwnerCannotAcquireHistoryLocks()throws Exception{var f=new Fixture();var p=f.prepare();f.sql.clear();doThrow(new MeetingAccess.AccessDeniedException()).when(f.access).requirePrepared(f.root,ACTOR,MEETING,List.of("owner"),MeetingAccess.Action.EDIT);assertThrows(MeetingAccess.AccessDeniedException.class,()->f.tx.required(()->f.guard.controls(p,ACTOR,MEETING,f.root)));assertTrue(f.sql.isEmpty());}
 @Test void actualAuthorityTransitionUsesTheSameGuardBeforeItsWritePhase()throws Exception{var f=new Fixture();var components=JdbcTranscriptAuthority.create(f.ds,f.tx,f.access,(actor,meeting,purpose)->{},mock(ama.storage.FileBackendCatalog.class),Map.of(),f.authority.readBudget(),f.authority.canonicalBudget());var actual=components.authority();var prepared=actual.prepareMinutesTransition(ACTOR,MEETING);f.tx.required(()->{assertNotNull(actual.lockMinutesTransition(prepared,ACTOR,MEETING,f.root));return null;});assertTrue(f.sql.stream().anyMatch(s->s.contains("publication_submission")));assertTrue(f.sql.stream().anyMatch(s->s.contains("final_confirmation")&&s.endsWith("FOR UPDATE")));}

 @Test void actualAssemblyUsesTheSmallestConfiguredPublicationAcquisitionBudget()throws Exception{
  var f=new Fixture();var c=JdbcTranscriptAuthority.create(f.ds,f.tx,f.access,(actor,meeting,purpose)->{},mock(ama.storage.FileBackendCatalog.class),Map.of(),f.authority.readBudget(),f.authority.canonicalBudget());var a=c.authority();
  a.minutesDomain().configure(new TranscriptAcquisitionBudget(1024,1024,1024,4096,4096,1024,16,32,32));
  a.minutesDomain().configure(new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,33554432,128,256,256));
  var p=a.publicationWriteGuard().prepare(ACTOR,MEETING);var denied=assertThrows(ama.contract.MinutesContracts.Failure.class,()->f.tx.required(()->a.publicationWriteGuard().controls(p,ACTOR,MEETING,f.root)));assertEquals(ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED,denied.code());
 }

 @Test void realRootTrackerRejectsPreparationAfterActualRootAndResetsForFreshHolder()throws Exception{
  var f=new Fixture();var fences=mock(ama.task.SubjectFencePort.class);var subject=mock(ama.task.SubjectFencePort.LockedSubject.class);when(subject.businessVersion()).thenReturn(1L);when(fences.lock(any(),eq(false))).thenReturn(Optional.of(subject));var access=new JdbcMeetingAccess(f.ds,f.tx,fences);when(f.authority.savedAccess()).thenReturn(access);f.responder=sql->sql.contains(" FROM enterprise_identity ")?List.of(sql.startsWith("SELECT status ")?Map.of("status","ACTIVE"):Task11ImportPublicationGuardTest.row("enterprise_id","owner","status","ACTIVE")):null;
  assertDoesNotThrow(f::prepare);f.tx.required(()->{var gate=access.lock(ACTOR,MEETING);access.require(gate,MeetingAccess.Action.EDIT);int before=f.sql.size();assertThrows(IllegalStateException.class,f::prepare);assertEquals(before,f.sql.size(),"late preparation must fail before history discovery");return null;});
  f.tx.required(()->{assertDoesNotThrow(f::prepare);return null;});
 }
 @Test void readOnlyPermissionDoesNotConsumeTheActualWriteClaim()throws Exception{var f=new Fixture();var p=f.prepare();f.tx.required(()->{var h=f.guard.controls(p,ACTOR,MEETING,f.root);f.guard.tasks(h,f.root);f.guard.requirePermitted(h,f.root);assertThrows(IllegalArgumentException.class,()->f.guard.requireClaimed(h,f.root));f.guard.claim(h,f.root);f.guard.requireClaimed(h,f.root);return null;});}

}