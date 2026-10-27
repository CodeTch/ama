package ama.transcript;
import ama.access.MeetingAccess;import ama.config.*;import ama.minutes.contract.GenerationArchiveRefs;import ama.model.ControlledMinutesProtocol;import ama.storage.FileRegistry;import ama.task.*;import ama.contract.MinutesContracts.*;
import java.util.*;import java.util.concurrent.atomic.AtomicInteger;import org.junit.jupiter.api.Test;import org.mockito.MockedStatic;import org.mockito.MockedConstruction;import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static ama.canonical.CanonicalValue.*;
/** Actual preparation/command phase orchestration and Spring transactions. Graph, archive, renderer,
 * receipt and authority collaborators are explicit substitutes; not canonical/DB authorization proof. */
class Task13GenerationPhasesTest {
 static final String M=Task11PublicationWriteGuardBoundaryTest.MEETING;static final ama.contract.IdentityContracts.Actor ACTOR=Task11PublicationWriteGuardBoundaryTest.ACTOR;
 static final class Case implements AutoCloseable {
  final Task11PublicationWriteGuardBoundaryTest.Fixture f=new Task11PublicationWriteGuardBoundaryTest.Fixture();
  final AtomicInteger locks=new AtomicInteger(),captures=new AtomicInteger(),computes=new AtomicInteger();final List<String> trace=new ArrayList<>();
  final List<AutoCloseable> resources=new ArrayList<>();final JdbcMinutesGenerationPreparation preparation;final JdbcMinutesGenerationCommands commands;final MinutesTaskDomainPolicy policy=mock(MinutesTaskDomainPolicy.class);
  RuntimeException computeFailure;boolean denyFinal,changedFinal,interruptCompute;Optional<Map<String,Object>> replay=Optional.empty(),winner=Optional.empty();int receiptReads;final RuntimeException reachedClaim=new RuntimeException("stop before DML");
  Case()throws Exception{
   when(f.authority.transactionRunner()).thenReturn(f.tx);when(f.authority.minutesDomain()).thenReturn(policy);
   var root=new HashMap<String,Object>();root.put("id",M);root.put("owner_id","owner");root.put("revision",1L);root.put("cancel_epoch",0L);root.put("ever_published",0L);root.put("lifecycle","ACTIVE");root.put("governance_state","ACTIVE");root.put("stage","ENTRY");root.put("transcript_confirmation_id",M);root.put("template_revision_id",M);root.put("exclusion_revision_id",M);root.put("element_set_id",null);root.put("material_snapshot_id",null);root.put("final_confirmation_id",null);root.put("materials_pending",0L);
   var plan=new MinutesGraphDiscovery.Discovery(M,"CURRENT_GENERATION",null,root,Map.of(),Map.of(),Set.of("owner"),List.of(),null,mock(PublicationWriteGuard.Prepared.class));var graph=new MinutesGraphCapture.Graph(plan,List.of(),Map.of());var gate=mock(FileRegistry.LockedMeeting.class);Object scope=new Object();when(f.authority.minutesScope(gate,ACTOR,M)).thenReturn(scope);
   var snapshot=mock(JdbcMinutesFactsReader.Snapshot.class);when(snapshot.graph()).thenReturn(graph);when(snapshot.facts()).thenReturn(mock(StoredProvenanceFacts.class));
   var archive=mock(ConfigArchiveSource.Snapshot.class);when(archive.indexSha256()).thenReturn(Task11CombinedInstallationTest.PIN);when(archive.loadClosure(anyList(),any(),anyLong(),any())).thenReturn(List.of());
   var binding=mock(ConfigBindings.ValidatedGenerationBinding.class);when(binding.fingerprint()).thenReturn("b".repeat(64));var canonical=f.authority.canonicalBudget();when(binding.canonicalBudget()).thenReturn(canonical);
   MockedStatic<MinutesGraphDiscovery> discovery=mockStatic(MinutesGraphDiscovery.class);resources.add(discovery);
   discovery.when(()->MinutesGraphDiscovery.discover(eq(f.ds),eq(M),eq("CURRENT_GENERATION"),isNull(),any(TranscriptAcquisitionBudget.class),any())).thenReturn(plan);discovery.when(()->MinutesGraphDiscovery.withPending(eq(f.ds),eq(plan),anyInt(),any())).thenReturn(plan);discovery.when(()->MinutesGraphDiscovery.guarded(eq(f.authority),eq(ACTOR),eq(plan),any())).thenReturn(plan);
   MockedStatic<JdbcMinutesFactsReader> facts=mockStatic(JdbcMinutesFactsReader.class,CALLS_REAL_METHODS);resources.add(facts);
   facts.when(()->JdbcMinutesFactsReader.lockGraph(f.authority,ACTOR,M,plan)).thenAnswer(call->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());int n=locks.incrementAndGet();trace.add("gate"+n);if(denyFinal&&n>1)throw new MeetingAccess.AccessDeniedException();return gate;});
   MockedStatic<MinutesGraphCapture> capture=mockStatic(MinutesGraphCapture.class);resources.add(capture);
   capture.when(()->MinutesGraphCapture.captureCurrent(eq(f.authority),eq(scope),eq(gate),eq(ACTOR),eq(plan),any())).thenAnswer(call->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());int n=captures.incrementAndGet();trace.add("capture"+n);return changedFinal&&n>1?new MinutesGraphCapture.Graph(plan,List.of(),Map.of("changed",List.of())):graph;});
   resources.add(mockConstruction(JdbcMinutesFactsReader.class,(reader,context)->when(reader.prepare(any(),any())).thenAnswer(call->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());computes.incrementAndGet();trace.add("archive");if(interruptCompute)Thread.currentThread().interrupt();if(computeFailure!=null)throw computeFailure;return snapshot;})));
   resources.add(mockConstruction(GenerationArchiveRefs.class,(refs,context)->when(refs.draft(any())).thenReturn(List.of())));
   resources.add(mockConstruction(ConfigBindings.class,(bindings,context)->when(bindings.computeGeneration(any(),any(),any(),anyList(),any(),any(),any())).thenReturn(binding)));
   MockedStatic<ControlledMinutesRenderer> renderer=mockStatic(ControlledMinutesRenderer.class);resources.add(renderer);renderer.when(()->ControlledMinutesRenderer.render(anyList(),any(),any())).thenReturn(object());
   resources.add(mockStatic(ControlledMinutesProtocol.class));
   resources.add(mockConstruction(JdbcMinutesGenerationReceipts.class,(receipts,context)->{when(receipts.lookup(eq(ACTOR),eq(M),any(JdbcMinutesGenerationReceipts.Identity.class),eq(1L))).thenAnswer(call->{trace.add("receipt");return ++receiptReads>1&&winner.isPresent()?winner:replay;});when(receipts.claim(eq(ACTOR),eq(M),any())).thenAnswer(call->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());trace.add("claim-receipt");return new JdbcMinutesGenerationReceipts.Stored(null,0,true);});}));
   doAnswer(call->{trace.add("write-claim");throw reachedClaim;}).when(f.authority).claimMinutesWrite(eq(scope),eq(gate),eq(ACTOR),any(),eq(JdbcTranscriptAuthority.MinutesWritePurpose.GENERATION_INPUT),any());
   preparation=new JdbcMinutesGenerationPreparation(f.authority,()->archive,Task11CombinedInstallationTest.PIN,Task11CombinedInstallationTest.PROMPT,Task11CombinedInstallationTest.MODEL,Task11CombinedInstallationTest.LIMITS);
   commands=new JdbcMinutesGenerationCommands(f.authority,preparation,mock(TaskQueue.class),mock(OutboxPort.class),mock(ama.audit.AuditPort.class));
  }
  void enqueue(){commands.enqueue(ACTOR,M,"phase-test",1,M);}
  @Override public void close()throws Exception{Collections.reverse(resources);for(var resource:resources)resource.close();}
 }
 @Test void successfulComputationHasOnlySourceAndSerializedCommandFinalCapture()throws Exception{
  try(var c=new Case()){assertSame(c.reachedClaim,assertThrows(RuntimeException.class,c::enqueue));assertEquals(List.of("receipt","gate1","capture1","archive","claim-receipt","gate2","capture2","write-claim"),c.trace);assertEquals(2,c.captures.get());verify(c.policy,times(1)).reserveCapacity(any(),any());assertTrue(c.f.updates.isEmpty());}
 }
 @Test void standaloneReadinessRetainsItsFinalValidationWithoutWriteClaim()throws Exception{
  try(var c=new Case()){c.commands.verifyCurrentReadiness(ACTOR,M,1,Task11CombinedInstallationTest.LIMITS.ledger(c.f.authority.readBudget(),c.f.authority.canonicalBudget(),()->false));assertEquals(List.of("gate1","capture1","archive","gate2","capture2"),c.trace);verify(c.policy).reserveCapacity(any(),any());assertTrue(c.f.updates.isEmpty());}
 }
 @Test void archiveFailureStillReauthorizesBeforeErrorAndCannotFallThroughToWriteClaim()throws Exception{
  try(var c=new Case()){c.computeFailure=new Failure(FailureCode.PROFILE_UNAVAILABLE);c.denyFinal=true;assertThrows(MeetingAccess.AccessDeniedException.class,c::enqueue);assertEquals(List.of("receipt","gate1","capture1","archive","gate2"),c.trace);assertEquals(1,c.captures.get());assertTrue(c.f.updates.isEmpty());}
 }
 @Test void changedSourceInTheSerializedCommandFinalPhaseFailsBeforeCapacityAndWrite()throws Exception{
  try(var c=new Case()){c.changedFinal=true;var failure=assertThrows(Failure.class,c::enqueue);assertEquals(FailureCode.PROVENANCE_UNAVAILABLE,failure.code());assertEquals(List.of("receipt","gate1","capture1","archive","claim-receipt","gate2","capture2"),c.trace);verify(c.policy,never()).reserveCapacity(any(),any());assertTrue(c.f.updates.isEmpty());}
 }
 @Test void acknowledgedReplaySkipsAllProspectivePreparation()throws Exception{
  try(var c=new Case()){var result=Map.<String,Object>of("replayed",true);c.replay=Optional.of(result);assertSame(result,c.commands.enqueue(ACTOR,M,"phase-test",1,M));assertEquals(List.of("receipt"),c.trace);assertEquals(0,c.computes.get());}
 }
 @Test void ordinaryArchiveFailureCanUseConcurrentWinnerOnlyAfterFreshFinalGate()throws Exception{
  try(var c=new Case()){c.computeFailure=new Failure(FailureCode.ARCHIVE_IO_UNAVAILABLE);var result=Map.<String,Object>of("replayed",true);c.winner=Optional.of(result);assertSame(result,c.commands.enqueue(ACTOR,M,"phase-test",1,M));assertEquals(List.of("receipt","gate1","capture1","archive","gate2","receipt"),c.trace);verify(c.policy,never()).reserveCapacity(any(),any());}
 }
 @Test void integrityFailureCannotBeHiddenByConcurrentWinner()throws Exception{
  for(var code:List.of(FailureCode.PROFILE_UNAVAILABLE,FailureCode.PROVENANCE_UNAVAILABLE,FailureCode.FINGERPRINT_MISMATCH))try(var c=new Case()){c.computeFailure=new Failure(code);c.winner=Optional.of(Map.of("replayed",true));assertSame(c.computeFailure,assertThrows(Failure.class,c::enqueue));assertEquals(List.of("receipt","gate1","capture1","archive","gate2"),c.trace);}
 }
 @Test void interruptedComputationCannotReenterWinnerLookup()throws Exception{
  try(var c=new Case()){c.computeFailure=new Failure(FailureCode.ARCHIVE_IO_UNAVAILABLE);c.interruptCompute=true;c.winner=Optional.of(Map.of("replayed",true));try{assertSame(c.computeFailure,assertThrows(Failure.class,c::enqueue));assertTrue(Thread.currentThread().isInterrupted());assertEquals(1,c.receiptReads);assertFalse(c.trace.contains("write-claim"));}finally{Thread.interrupted();}}
 }
 @Test void commandFinalAuthorizationDenialStillPrecedesSourceCaptureAndWrite()throws Exception{
  try(var c=new Case()){c.denyFinal=true;assertThrows(MeetingAccess.AccessDeniedException.class,c::enqueue);assertEquals(List.of("receipt","gate1","capture1","archive","claim-receipt","gate2"),c.trace);verify(c.policy,never()).reserveCapacity(any(),any());assertTrue(c.f.updates.isEmpty());}
 }

}
