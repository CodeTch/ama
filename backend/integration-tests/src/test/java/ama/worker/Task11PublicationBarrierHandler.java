package ama.worker;
import ama.transcript.JdbcPublicationService;
import ama.task.*;
/** Test-only pause after genuine committed begin; production binding, begin, send, observe remain intact. */
public final class Task11PublicationBarrierHandler implements WorkerLoop.ExternalHandler {
 @FunctionalInterface public interface Barrier {void run()throws Exception;}
 private final JdbcPublicationService service;private final ExternalOperationPort operations;private final ModelPermitPool permits;private final Barrier barrier;
 public Task11PublicationBarrierHandler(JdbcPublicationService s,ExternalOperationPort o,ModelPermitPool p,Barrier b){service=s;operations=o;permits=p;barrier=b;}
 public void handle(ExternalTaskExecution value)throws Exception {if(!(value instanceof ExternalExecutionSession session))throw new IllegalArgumentException("Genuine production session required");session.requireBinding(operations,permits,null);var request=service.prepare(session.lease());if(!session.begin(request))return;barrier.run();session.send(()->service.execute(request,session.token(),session.cancellation()));}
}
