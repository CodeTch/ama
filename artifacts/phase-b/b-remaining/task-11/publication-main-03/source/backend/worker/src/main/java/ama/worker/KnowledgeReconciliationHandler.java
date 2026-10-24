package ama.worker;
import ama.transcript.JdbcPublicationService;import ama.task.*;import java.util.Objects;
/** Genuine committed reconciliation holder; exact original GET only, never send authority. */
public final class KnowledgeReconciliationHandler implements ReconciliationLoop.PreparedHandler {
 private final JdbcPublicationService service;private final JdbcExternalOperations operations;private final ModelPermitPool permits;
 public KnowledgeReconciliationHandler(JdbcPublicationService service,JdbcExternalOperations operations,ModelPermitPool permits){this.service=Objects.requireNonNull(service);this.operations=Objects.requireNonNull(operations);this.permits=Objects.requireNonNull(permits);}
 public void reconcile(ExternalTaskExecution value)throws Exception {if(!(value instanceof ExternalExecutionSession session))throw new IllegalArgumentException("Genuine reconciliation session required");session.requireReconciliationBinding(operations,permits);var token=session.token();var request=service.prepareLookup(operations.recordedLookup(token),token);var observation=service.lookup(request,session.cancellation());session.observe(observation);}
}
