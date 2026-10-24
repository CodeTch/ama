package ama.worker;
import ama.transcript.MinutesExternalPreparation;import ama.model.*;import ama.task.*;
import java.util.*;
/** Fixed production send chain; arbitrary ExternalTaskExecution implementations cannot enter it. */
public final class MinutesGenerationHandler implements WorkerLoop.ExternalHandler {
 private final MinutesExternalPreparation preparation;private final ControlledMinutesClient client;private final ExternalOperationPort operations;private final ModelPermitPool permits;
 public MinutesGenerationHandler(MinutesExternalPreparation preparation,ControlledMinutesClient client,ExternalOperationPort operations,ModelPermitPool permits){this.preparation=Objects.requireNonNull(preparation);this.client=Objects.requireNonNull(client);this.operations=Objects.requireNonNull(operations);this.permits=Objects.requireNonNull(permits);preparation.requireClient(client);}
 public void handle(ExternalTaskExecution value)throws Exception {
  if(!(value instanceof ExternalExecutionSession session))throw new IllegalArgumentException("Genuine production session required");session.requireBinding(operations,permits,ControlledMinutesProtocol.MODEL);var request=preparation.prepare(session.lease());if(!session.begin(request))return;
  session.send(()->{var token=session.token();var wire=client.prepare(request.body(token.executionId()),request.envelope());var receipt=client.execute(wire,session.cancellation());return preparation.accept(request,token,wire,receipt,session.cancellation());});
 }
}
