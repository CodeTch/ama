package ama.task;
import ama.contract.TaskContracts.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Task05AfterTaskIT {
 @Test void actualQueueScopeRejectsWrongIdentityPhaseSuspendedAndClosedHolder(){
  var f=new TaskFixture();var holder=new P[1];var queue=JdbcTaskQueue.installConfined(f.source,f.fences,f.outbox,q->{holder[0]=new P(f,q);return Map.of("TEST_TASK",holder[0]);});var p=holder[0];
  var lease=f.claimed(queue,f.spec());p.lease=lease;p.carrier=()->{throw new AssertionError("inert");};
  assertTrue(queue.completeWithEffects(lease,p.carrier));assertEquals(1,p.writes);assertEquals(1,p.validations);
  assertThrows(RuntimeException.class,()->p.invocation.write());
  f.tx.required(()->{assertThrows(RuntimeException.class,()->p.invocation.write());assertThrows(RuntimeException.class,()->p.scope.preparedFor(queue,p,List.of(lease.spec())));return null;});
  assertFalse(queue.completeWithEffects(lease,p.carrier));
 }
 @Test void ordinaryFailedRunnableCannotEnterConfinedRegistration(){
  var f=new TaskFixture();var holder=new P[1];var queue=JdbcTaskQueue.installConfined(f.source,f.fences,f.outbox,q->{holder[0]=new P(f,q);return Map.of("TEST_TASK",holder[0]);});var p=holder[0];
  var lease=f.claimed(queue,f.spec());p.lease=lease;p.carrier=()->{throw new AssertionError("inert");};var calls=new java.util.concurrent.atomic.AtomicInteger();
  try {
   assertThrows(RuntimeException.class,()->queue.completeWithEffects(lease,new TaskResult(Outcome.FAILED,null,"CONTROLLED_FAILURE"),()->{calls.incrementAndGet();throw new IllegalStateException("Rollback controlled unsafe callback");}));
   assertEquals(0,calls.get(),"Confined registration must deny the legacy writer even for failed results");
  }finally{assertTrue(queue.completeWithEffects(lease,p.carrier));}
 }
 static final class P implements ConfinedTaskCompletionPolicy {
  final TaskFixture f;final JdbcTaskQueue q;TaskLease lease;Supplier<TaskResult> carrier;JdbcTaskQueue.AfterTask scope;CompletionInvocation invocation;int writes,validations;
  P(TaskFixture f,JdbcTaskQueue q){this.f=f;this.q=q;}
  public Optional<PreparedDomain> prepare(List<TaskSpec> s,Acquisition a,boolean skip){return f.policy.prepare(s,a,skip);}
  public void requirePrepared(List<TaskSpec>s,Acquisition a,PreparedDomain d){f.policy.requirePrepared(s,a,d);}
  public boolean permitsDomainEffects(PreparedDomain d){return f.policy.permitsDomainEffects(d);}
  public boolean lockRoot(TaskSpec s,boolean skip){return f.policy.lockRoot(s,skip);}
  public void validateInput(TaskSpec s){f.policy.validateInput(s);}
  public void validateOutput(TaskSpec s,TaskResult r){if(r.outcome()==Outcome.SUCCEEDED)throw new IllegalStateException("confined only");f.policy.validateOutput(s,r);}
  public void validateExternalRequest(TaskSpec s,ama.contract.ExternalContracts.ExternalRequest r){throw new IllegalStateException();}
  public CompletionInvocation bindCompletion(JdbcTaskQueue.AfterTask held,Supplier<TaskResult> payload){
   assertSame(carrier,payload);scope=held;held.preparedFor(q,this,List.of(lease.spec()));
   invocation=new CompletionInvocation(){
    public TaskResult write(){
     held.requireInvocation(q,P.this,this,carrier,lease,CompletionPhase.WRITING);writes++;
     assertThrows(RuntimeException.class,()->held.requireInvocation(f.queue(),P.this,this,carrier,lease,CompletionPhase.WRITING));
     assertThrows(RuntimeException.class,()->held.requireInvocation(q,P.this,this,()->f.success(lease.spec()),lease,CompletionPhase.WRITING));
     assertThrows(RuntimeException.class,()->held.requireInvocation(q,P.this,this,carrier,new TaskLease(lease.taskId(),lease.attempt()+1,lease.leaseOwner(),lease.expiresAt(),lease.spec()),CompletionPhase.WRITING));
     assertThrows(RuntimeException.class,()->held.requireInvocation(q,P.this,this,carrier,lease,CompletionPhase.VALIDATING));
     var next=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(f.source));next.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
     next.execute(status->{assertThrows(RuntimeException.class,()->held.requireInvocation(q,P.this,this,carrier,lease,CompletionPhase.WRITING));return null;});
     return f.success(lease.spec());
    }
    public void validateActual(TaskResult result){held.requireInvocation(q,P.this,this,carrier,lease,CompletionPhase.VALIDATING);held.requireResult(result);assertThrows(RuntimeException.class,this::write);assertThrows(RuntimeException.class,()->held.requireResult(f.success(lease.spec())));f.policy.validateOutput(lease.spec(),result);validations++;}
   };return invocation;
  }
 }
}
