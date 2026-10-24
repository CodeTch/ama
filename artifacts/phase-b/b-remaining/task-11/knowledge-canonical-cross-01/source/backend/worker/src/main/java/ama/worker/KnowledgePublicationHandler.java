package ama.worker;
import ama.transcript.*;import ama.task.*;import java.util.Objects;
/** Only the real Task7 session admits a publication POST after the durable marker. */
public final class KnowledgePublicationHandler implements WorkerLoop.ExternalHandler {
 private final JdbcPublicationService service;private final ExternalOperationPort operations;private final ModelPermitPool permits;
 public KnowledgePublicationHandler(JdbcPublicationService service,ExternalOperationPort operations,ModelPermitPool permits){this.service=Objects.requireNonNull(service);this.operations=Objects.requireNonNull(operations);this.permits=Objects.requireNonNull(permits);}
 public void handle(ExternalTaskExecution execution)throws Exception{if(!(execution instanceof ExternalExecutionSession session))throw new IllegalArgumentException("Genuine production session required");session.requireBinding(operations,permits,null);var request=service.prepare(session.lease());if(!session.begin(request))return;session.send(()->service.execute(request,session.token(),session.cancellation()));}
}
