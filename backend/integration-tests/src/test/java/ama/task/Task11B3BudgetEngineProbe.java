package ama.task;
import ama.contract.TaskContracts.*;import ama.contract.ExternalContracts.ExternalRequest;import java.util.*;import javax.sql.DataSource;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
/** Offline actual replacement engine SQL. Policy/fence and existing inserted task are inert boundaries, no business qualification. */
public final class Task11B3BudgetEngineProbe {
 public static PublicationReplacementPolicy.ReplacementAccepted replace(DataSource source,String task,String meeting,String publication,String operation){
  var spec=new TaskSpec("KNOWLEDGE_PUBLISH_V1","KNOWLEDGE_PUBLISH",new SubjectRef("MEETING",meeting),"2".repeat(64),new TypedRef("PUBLICATION",publication),"1".repeat(64),13,0,0,3);
  var issuer=new JdbcTaskPreparations(source);var held=issuer.issue(List.of(spec),TaskDomainPolicy.Acquisition.TASK_MUTATION,new Object(),true);var policy=mock(PublicationReplacementPolicy.class);
  doAnswer(call->{issuer.require(call.getArgument(0),call.getArgument(1),call.getArgument(2));return null;}).when(policy).requirePrepared(anyList(),any(),any());when(policy.permitsDomainEffects(held)).thenReturn(true);when(policy.lockRoots(eq(List.of(spec)),eq(false),same(held))).thenReturn(true);
  var request=new ExternalRequest("KNOWLEDGE_PUBLISH","selected-business-key","1".repeat(64),"3".repeat(64),"offline-safe-manifest");
  PublicationReplacementPolicy.PreparedPublicationReplacement carrier=()->spec;when(policy.requireReplacement(carrier)).thenReturn(new PublicationReplacementPolicy.ReplacementBinding(spec,task,List.of(spec),Map.of(),held,operation,6,"0".repeat(64),"00000000-0000-4000-8000-000000000003",request));
  var fences=mock(JdbcSubjectFences.class);var subject=mock(SubjectFencePort.LockedSubject.class);when(subject.businessVersion()).thenReturn(13L);when(subject.cancelEpoch()).thenReturn(0L);when(subject.deleted()).thenReturn(false);when(fences.lock(spec.subject(),false)).thenReturn(Optional.of(subject));
  var outbox=mock(JdbcOutbox.class);var queue=spy(new JdbcTaskQueue(source,fences,outbox,Map.of(spec.type(),policy)));
  // The test already delegated the real task INSERT via the guard. Avoid a fake ResultSet authority graph here.
  doReturn(task).when(queue).insertNew(spec,task);
  var accepted=new JdbcExternalOperations(queue).replaceZeroEffectPublication(carrier);assertEquals(task,accepted.taskId());assertEquals(7L,accepted.executionNo());verify(queue).insertNew(spec,task);verifyNoInteractions(outbox);return accepted;
 }
}
