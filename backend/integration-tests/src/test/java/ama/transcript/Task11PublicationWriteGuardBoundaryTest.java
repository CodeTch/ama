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
  java.util.function.BiConsumer<String,Map<Integer,Object>> updateObserver;
  java.util.function.Function<String,List<Map<String,Object>>> responder;java.util.function.BiFunction<String,Map<Integer,Object>,List<Map<String,Object>>> parameterResponder;
  Fixture()throws Exception{
   when(access.lock(ACTOR,MEETING)).thenReturn(root);
   when(authority.dataSource()).thenReturn(ds);when(authority.savedAccess()).thenReturn(access);
   when(authority.readBudget()).thenReturn(new ama.contract.ConfigArchiveContracts.ArchiveReadBudget(65536,16777216,64,128,128,2000000,128,255,4194304,4194304,16777216,65536,64,100000,500000));
   when(authority.canonicalBudget()).thenReturn(new ama.contract.MinutesContracts.CanonicalBudget(67108864,128,4000000,4194304,16777216));
   when(ds.getConnection()).thenAnswer(a->{var c=mock(Connection.class);var databaseMetadata=mock(DatabaseMetaData.class);when(databaseMetadata.getDriverName()).thenReturn("MySQL Connector/J");when(databaseMetadata.getDatabaseProductName()).thenReturn("MySQL");when(c.getMetaData()).thenReturn(databaseMetadata);doAnswer(z->{commits.incrementAndGet();return null;}).when(c).commit();doAnswer(z->{rollbacks.incrementAndGet();return null;}).when(c).rollback();var auto=new AtomicBoolean(true);when(c.getAutoCommit()).thenAnswer(i->auto.get());doAnswer(i->{auto.set(i.getArgument(0));return null;}).when(c).setAutoCommit(anyBoolean());var init=mock(Statement.class);when(c.createStatement()).thenReturn(init);when(init.execute(anyString())).thenAnswer(i->{assertTrue(Set.of("SET NAMES utf8mb4","SET time_zone = '+00:00'").contains(i.getArgument(0)));return false;});when(c.prepareStatement(anyString())).thenAnswer(i->{String text=i.getArgument(0);sql.add(text);assertTrue(text.startsWith("SELECT ")||allowUpdates);var st=mock(PreparedStatement.class);when(st.getConnection()).thenReturn(c);var parameterMetadata=mock(ParameterMetaData.class);when(parameterMetadata.getParameterType(anyInt())).thenReturn(Types.VARCHAR);when(st.getParameterMetaData()).thenReturn(parameterMetadata);var parameters=new HashMap<Integer,Object>();doAnswer(z->{parameters.put(z.getArgument(0),z.getArgument(1));return null;}).when(st).setString(anyInt(),anyString());doAnswer(z->{parameters.put(z.getArgument(0),z.getArgument(1));return null;}).when(st).setLong(anyInt(),anyLong());doAnswer(z->{parameters.put(z.getArgument(0),z.getArgument(1));return null;}).when(st).setObject(anyInt(),any());when(st.executeUpdate()).thenAnswer(z->{if(!allowUpdates)throw new AssertionError("Guard must perform no DML");updates.add(text);if(updateObserver!=null)updateObserver.accept(text,Collections.unmodifiableMap(new HashMap<>(parameters)));return 1;});when(st.executeQuery()).thenAnswer(q->{
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


 @Test void intentContributionMustAdmitConfiguredBudgetBeforeMetadataTransfer()throws Exception{
  var f=new Fixture();var c=JdbcTranscriptAuthority.create(f.ds,f.tx,f.access,(actor,meeting,purpose)->{},mock(ama.storage.FileBackendCatalog.class),Map.of(),f.authority.readBudget(),f.authority.canonicalBudget());var a=c.authority();
  a.minutesDomain().configure(new TranscriptAcquisitionBudget(1024,1024,1024,4096,4096,1024,16,32,32));
  f.responder=sql->sql.contains(" FROM publication ")?(sql.startsWith("SELECT COUNT(*)")?List.of(Map.of("count",1L)):List.of(Map.of("id",MEETING,"confirmation_id",MEETING,"state","READY","revision",1L))):null;
  var denied=assertThrows(ama.contract.MinutesContracts.Failure.class,()->a.prepareMinutesIntent(ACTOR,MEETING,1));
  assertEquals(ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED,denied.code());
  assertTrue(f.sql.stream().noneMatch(sql->sql.startsWith("SELECT id,confirmation_id,state,revision FROM publication")),"over-budget control metadata must not cross JDBC");
 }

 @Test void actualIntentWritesOnceAndFrozenFinalRecheckRollsBackWithoutWrite()throws Exception{
  for(boolean frozen:List.of(false,true)){
   var f=new Fixture();f.allowUpdates=true;var c=JdbcTranscriptAuthority.create(f.ds,f.tx,f.access,(actor,meeting,purpose)->{},mock(ama.storage.FileBackendCatalog.class),Map.of(),f.authority.readBudget(),f.authority.canonicalBudget());var a=c.authority();
   f.responder=sql->sql.startsWith("SELECT intent_epoch FROM meeting_minutes_editor_guard")&&!f.updates.isEmpty()?List.of(Map.of("intent_epoch",1L)):null;
   var plan=a.prepareMinutesIntent(ACTOR,MEETING,1);f.frozen.set(frozen);
   java.util.function.Supplier<Object> attempt=()->f.tx.required(()->{var gate=c.registry().adopt(ACTOR,MEETING,f.root,MeetingAccess.Action.EDIT);var held=a.captureMinutesIntent(plan,gate,ACTOR,MEETING,1);a.writeMinutesIntent(held,plan,gate);assertThrows(IllegalArgumentException.class,()->a.writeMinutesIntent(held,plan,gate));return null;});
   if(frozen){assertThrows(ama.contract.MinutesContracts.Failure.class,attempt::get);assertTrue(f.updates.isEmpty());assertEquals(1,f.rollbacks.get());}
   else{assertDoesNotThrow(attempt::get);assertEquals(1,f.updates.size());assertTrue(f.updates.getFirst().startsWith("INSERT INTO meeting_minutes_editor_guard"));assertEquals(1,f.commits.get());}
  }
 }
 @Test void intentMetadataRetainsOneCumulativeLedgerAcrossReads()throws Exception{
  var f=new Fixture();var budget=new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,33554432,128,256,256).ledger(new ama.contract.ConfigArchiveContracts.ArchiveReadBudget(65536,16777216,64,128,128,2000000,128,255,4194304,4194304,16777216,65536,64,6,500000),f.authority.canonicalBudget(),()->false);
  var j=new org.springframework.jdbc.core.JdbcTemplate(f.ds);assertEquals(4,MinutesIntentControl.read(j,MEETING,false,budget).size());
  var failure=assertThrows(ama.contract.MinutesContracts.Failure.class,()->MinutesIntentControl.read(j,MEETING,false,budget));assertEquals(ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED,failure.code());
 }
 @Test void intentMetadataConcurrentGrowthFailsClosed()throws Exception{
  var f=new Fixture();f.responder=sql->sql.startsWith("SELECT id,confirmation_id,state,revision FROM publication")?List.of(Map.of("id",MEETING,"confirmation_id",MEETING,"state","READY","revision",1L)):null;
  var budget=new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,33554432,128,256,256).ledger(f.authority.readBudget(),f.authority.canonicalBudget(),()->false);
  var failure=assertThrows(ama.contract.TranscriptContracts.Failure.class,()->MinutesIntentControl.read(new org.springframework.jdbc.core.JdbcTemplate(f.ds),MEETING,false,budget));assertEquals("INTENT_CONTRIBUTION_UNAVAILABLE",failure.code());assertTrue(f.updates.isEmpty());
 }

 @Test void finalConfirmationCannotBypassCompleteHistoryClaim()throws Exception{
  var f=new Fixture();var c=JdbcTranscriptAuthority.create(f.ds,f.tx,f.access,(actor,meeting,purpose)->{},mock(ama.storage.FileBackendCatalog.class),Map.of(),f.authority.readBudget(),f.authority.canonicalBudget());var a=c.authority();
  f.tx.required(()->{var gate=c.registry().adopt(ACTOR,MEETING,f.root,MeetingAccess.Action.EDIT);var scope=a.minutesScope(gate,ACTOR,MEETING);var declaration=new MinutesGraphDiscovery.Discovery(MEETING,"READINESS",null,Map.of("revision",1L),Map.of(),Map.of(),Set.of("owner"),List.of(),null);var graph=new MinutesGraphCapture.Graph(declaration,List.of(),Map.of());a.sealMinutesCapture(scope,gate,ACTOR,graph);
   assertThrows(ama.contract.MinutesContracts.Failure.class,()->a.claimMinutesWrite(scope,gate,ACTOR,graph,JdbcTranscriptAuthority.MinutesWritePurpose.FINAL_CONFIRM,new Object()));return null;});assertTrue(f.updates.isEmpty());
 }

 @Test void finalClaimUsesOneCompleteGuardAndPublicationSubmitKeepsItsPrivatePurpose()throws Exception{
  for(boolean publication:List.of(false,true)){
   var f=new Fixture();var c=JdbcTranscriptAuthority.create(f.ds,f.tx,f.access,(actor,meeting,purpose)->{},mock(ama.storage.FileBackendCatalog.class),Map.of(),f.authority.readBudget(),f.authority.canonicalBudget());var a=c.authority();var guard=a.publicationWriteGuard();var prepared=guard.prepare(ACTOR,MEETING);
   f.tx.required(()->{var gate=c.registry().adopt(ACTOR,MEETING,f.root,MeetingAccess.Action.EDIT);var scope=a.minutesScope(gate,ACTOR,MEETING);var declaration=new MinutesGraphDiscovery.Discovery(MEETING,publication?"FINAL":"READINESS",null,Map.of("revision",1L),Map.of(),Map.of(),Set.of("owner"),List.of(),null);var graph=new MinutesGraphCapture.Graph(declaration,List.of(),Map.of());
    PublicationWriteGuard.Held held=null;if(!publication){held=guard.controls(prepared,ACTOR,MEETING,f.root);guard.tasks(held,f.root);}a.sealMinutesCapture(scope,gate,ACTOR,graph,held);var purpose=publication?JdbcTranscriptAuthority.MinutesWritePurpose.PUBLICATION_SUBMIT:JdbcTranscriptAuthority.MinutesWritePurpose.FINAL_CONFIRM;var plan=new Object();a.claimMinutesWrite(scope,gate,ACTOR,graph,purpose,plan);a.requireMinutesInvocation(scope,gate,ACTOR,graph,purpose,plan);if(held!=null)guard.requireClaimed(held,f.root);assertThrows(IllegalArgumentException.class,()->a.claimMinutesWrite(scope,gate,ACTOR,graph,purpose,plan));return null;
   });assertTrue(f.updates.isEmpty());
  }
 }
 @Test void transitionPendingMetadataMustBeAdmittedBeforeTransfer()throws Exception{
  for(boolean shared:List.of(false,true)){
   var f=new Fixture();var a=JdbcTranscriptAuthority.create(f.ds,f.tx,f.access,(actor,meeting,purpose)->{},mock(ama.storage.FileBackendCatalog.class),Map.of(),f.authority.readBudget(),f.authority.canonicalBudget()).authority();
   a.minutesDomain().configure(new TranscriptAcquisitionBudget(1024,1024,1024,4096,4096,1024,16,32,32));
   f.responder=q->q.startsWith("SELECT COUNT(*)")&&q.contains(" FROM generation_candidate ")?List.of(Map.of("count",1L)):null;
   assertThrows(RuntimeException.class,()->{if(shared)a.prepareMinutesTransition(ACTOR,MEETING,List.of(),a.publicationBudget());else a.prepareMinutesTransition(ACTOR,MEETING);});
   assertTrue(f.sql.stream().noneMatch(q->q.startsWith("SELECT id,meeting_id,input_id,task_id")),"pending candidate metadata crossed JDBC before admission");
  }
 }

 @Test void pendingCapturesAccumulateAcrossDiscoveryAndHeldRechecks()throws Exception{
  var f=new Fixture();f.responder=q->q.startsWith("SELECT COUNT(*)")?List.of(Map.of("count",1L)):List.of(Map.of("id","x".repeat(512)));
  var limits=new TranscriptAcquisitionBudget(4500,4500,4500,50000,50000,1024,16,32,32);var q=new MinutesPendingDiscovery.Query("publication",List.of("id"),List.of("id"),"meeting_id=?",List.of(MEETING));var j=new org.springframework.jdbc.core.JdbcTemplate(f.ds);
  for(int n=0;n<2;n++)assertEquals(1,MinutesPendingDiscovery.read(j,q,false,limits.ledger(f.authority.readBudget(),f.authority.canonicalBudget(),()->false)).size());
  var budget=limits.ledger(f.authority.readBudget(),f.authority.canonicalBudget(),()->false);assertEquals(1,MinutesPendingDiscovery.read(j,q,false,budget).size());int before=f.sql.size();var denied=assertThrows(ama.contract.MinutesContracts.Failure.class,()->MinutesPendingDiscovery.read(j,q,true,budget));assertEquals(ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED,denied.code());assertTrue(f.sql.subList(before,f.sql.size()).stream().allMatch(sql->sql.startsWith("SELECT COUNT(*)")));
 }

 @Test void actualCurrentTaskAcquisitionCannotResetItsCallersLedger()throws Exception{
  var f=new Fixture();when(f.authority.transactionRunner()).thenReturn(f.tx);when(f.authority.fileRegistry()).thenReturn(mock(ama.storage.FileRegistry.class));
  var archive=new ama.contract.ConfigArchiveContracts.ArchiveReadBudget(65536,16777216,64,128,128,2000000,128,255,4194304,4194304,16777216,65536,64,24,500000);when(f.authority.readBudget()).thenReturn(archive);var limits=Task11CombinedInstallationTest.LIMITS;var pages=new MinutesMetadataPages(f.authority,limits);
  for(int n=0;n<2;n++)assertTrue(pages.allTaskRows(ACTOR,MEETING,limits.ledger(archive,f.authority.canonicalBudget(),()->false)).isEmpty());
  var budget=limits.ledger(archive,f.authority.canonicalBudget(),()->false);assertTrue(pages.allTaskRows(ACTOR,MEETING,budget).isEmpty());var denied=assertThrows(ama.contract.MinutesContracts.Failure.class,()->pages.allTaskRows(ACTOR,MEETING,budget));assertEquals(ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED,denied.code());
 }

}