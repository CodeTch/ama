package ama.transcript;

import ama.access.MeetingAccess;
import ama.meeting.JdbcMeetingAccess;
import ama.storage.FileRegistry;
import ama.task.*;
import ama.contract.TaskContracts.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual policy/transaction; bounded graph capture and JDBC rows substituted to isolate terminal replay dispatch. */
class Task11FrozenTerminalReplayTest {
 @Test void readySucceededCandidateStillCapturesOriginalGraphWithoutEffects()throws Exception{run("SUCCEEDED",false,true,true,true);}
 @Test void publishedSucceededCandidateStillCapturesOriginalGraphWithoutEffects()throws Exception{run("SUCCEEDED",true,true,true,true);}
 @Test void readyRetainedCandidateStillCapturesOriginalGraphWithoutEffects()throws Exception{run("STALE",false,true,true,true);}
 @Test void publishedRetainedCandidateStillCapturesOriginalGraphWithoutEffects()throws Exception{run("STALE",true,true,true,true);}
 @Test void activeTaskCannotBorrowHistoricalCandidateRead()throws Exception{run("RECONCILING",true,true,true,false);}
 @Test void inactiveOwnerKeepsMinimalFactOnlyAcquisition()throws Exception{run("SUCCEEDED",true,false,true,false);}
 @Test void candidateAbsentKeepsMinimalFactOnlyAcquisition()throws Exception{run("STALE",true,true,false,false);}
 @Test void differentCandidateInputCannotBorrowTerminalRead()throws Exception{run("SUCCEEDED",true,true,true,false,false);}
 static void run(String state,boolean published,boolean active,boolean candidatePresent,boolean captured)throws Exception {run(state,published,active,candidatePresent,captured,true);}
 static void run(String state,boolean published,boolean active,boolean candidatePresent,boolean captured,boolean sameInput)throws Exception {
  var f=new Task11PublicationWriteGuardBoundaryTest.Fixture();String m=Task11PublicationWriteGuardBoundaryTest.MEETING,task="12345678-1234-4234-8234-123456789ab1",candidate="12345678-1234-4234-8234-123456789ab2",hash="a".repeat(64);var actor=Task11PublicationWriteGuardBoundaryTest.ACTOR;
  var spec=new TaskSpec("MINUTES_GENERATE_V1","MINUTES_GENERATE",new SubjectRef("MEETING",m),ama.minutes.MinutesGenerateProducer.compute(m,m,hash,1,0,f.authority.canonicalBudget(),()->false),new TypedRef("GENERATION_INPUT",m),hash,1,0,0,3);
  var taskRow=new HashMap<String,Object>();taskRow.putAll(Map.of("id",task,"task_type",spec.type(),"pool",spec.pool(),"subject_kind","MEETING","subject_id",m,"producer_key",spec.producerKey(),"input_kind","GENERATION_INPUT","input_id",m,"input_fingerprint",hash));taskRow.putAll(Map.of("business_version",1L,"priority",0L,"cancel_epoch",0L,"max_attempts",3L,"attempt",1L,"state",state));
  var candidateRow=Map.<String,Object>of("id",candidate,"meeting_id",m,"task_id",task,"input_id",sameInput?m:candidate,"base_revision",1L,"input_fingerprint",hash,"state",state.equals("STALE")?"STALE":"ADOPTED");
  f.frozen.set(true);when(f.authority.publicationWriteGuard()).thenReturn(f.guard);var publication=f.prepare();var pending=new MinutesPendingDiscovery.Plan(m,Map.of(),List.of(),null,f.authority.publicationBudget());var plan=new MinutesGraphDiscovery.Discovery(m,"STORED_INPUT",m,Map.of("id",m,"owner_id","owner","revision",2L,"cancel_epoch",0L,"ever_published",published),Map.of("durable_task",List.of(taskRow),"task_attempt",List.of(Map.of("task_id",task,"attempt",1L)),"generation_candidate",candidatePresent?List.of(candidateRow):List.of()),Map.of(),Set.of("owner"),List.of(),pending,publication);var graph=new MinutesGraphCapture.Graph(plan,List.of(),Map.of());
  var registry=mock(FileRegistry.class);var gate=mock(FileRegistry.LockedMeeting.class);Object scope=new Object();when(f.authority.meetingAccess()).thenReturn(f.access);when(f.authority.fileRegistry()).thenReturn(registry);when(registry.adopt(actor,m,f.root,MeetingAccess.Action.EDIT)).thenReturn(gate);when(f.authority.minutesScope(gate,actor,m)).thenReturn(scope);when(f.authority.permitsMinutesPublication(scope,gate)).thenReturn(false);when(f.authority.minutesRetentionPublication(eq(scope),eq(gate),any())).thenReturn(mock(GenerationRetentionPolicy.PublicationPreparation.class));
  var ids=mock(MeetingAccess.LockedIdentities.class);when(f.access.tryLockHistoricalIdentities(eq(actor),any(),eq(false))).thenReturn(Optional.of(ids));when(f.access.lockPrepared(ids,m,false)).thenReturn(Optional.of(f.root));var factIds=mock(JdbcMeetingAccess.FactIdentities.class);var factRoot=mock(JdbcMeetingAccess.FactRoot.class);when(f.access.tryLockFactIdentities(eq(actor),any(),eq(false))).thenReturn(Optional.of(factIds));when(f.access.factActorActive(factIds)).thenReturn(active);when(f.access.lockFactRoot(factIds,m,false)).thenReturn(Optional.of(factRoot));when(f.access.factAllowsBusiness(factRoot,0,1)).thenReturn(active&&!published);when(f.access.authorizeFactRoot(factRoot,0,1)).thenReturn(f.root);
  f.responder=sql->sql.startsWith("SELECT id,meeting_id,base_revision,input_fingerprint FROM generation_input")?List.of(Map.of("id",m,"meeting_id",m,"base_revision",1L,"input_fingerprint",hash)):null;
  var policy=new MinutesTaskDomainPolicy(f.authority,f.ds);policy.configure(new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,33554432,128,256,256));var captures=new AtomicInteger();
  try(var discovery=mockStatic(MinutesGraphDiscovery.class);var capture=mockStatic(MinutesGraphCapture.class)){
   discovery.when(()->MinutesGraphDiscovery.discover(eq(f.ds),eq(m),eq("STORED_INPUT"),eq(m),any(TranscriptAcquisitionBudget.class),any())).thenReturn(plan);discovery.when(()->MinutesGraphDiscovery.withPending(eq(f.ds),eq(plan),eq(256),any())).thenReturn(plan);discovery.when(()->MinutesGraphDiscovery.guarded(eq(f.authority),eq(actor),eq(plan),any())).thenReturn(plan);
   capture.when(()->MinutesGraphCapture.captureTask(eq(f.authority),eq(scope),eq(gate),eq(actor),eq(plan),any(),eq(false))).thenAnswer(x->{captures.incrementAndGet();return graph;});
   f.tx.required(()->{var held=policy.prepare(List.of(spec),TaskDomainPolicy.Acquisition.EXTERNAL_FACT_OBSERVATION,false).orElseThrow();assertFalse(policy.permitsDomainEffects(held));assertTrue(policy.lockRoots(List.of(spec),false,held));assertFalse(policy.permitsDomainEffects(held));assertEquals(captured?1:0,captures.get(),"Only an existing terminal candidate may acquire its bounded historical graph");return null;});
  }
  assertTrue(f.updates.isEmpty());verify(f.authority,never()).claimMinutesPublication(any(),any());verify(registry,never()).prelockSources(any(),anyList(),anyBoolean());if(!captured)verify(f.access,never()).tryLockHistoricalIdentities(any(),any(),anyBoolean());
 }
}
