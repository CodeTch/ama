package ama.task;
import ama.contract.TaskContracts.TypedRef;
import ama.task.ExternalOperationPort.*;
import java.util.Optional;
/** Explicit generation-only extension. Interface membership alone grants no retention authority. */
public interface GenerationRetentionPolicy extends PreparedExternalPolicy {
 interface Retention {}
 /** Opaque authentic control-rank acquisition, never a caller-supplied publication row list. */
 interface PublicationPreparation {}
 default Optional<PublicationPreparation> prelockedPublication(PreparedDomain held){return Optional.empty();}
 Optional<Retention> prepareRetention(PreparedDomain held,ExecutionContext context,PreparedObservation observation);
 TypedRef retainStale(Retention retention,PreparedObservation observation);
 void validateRetained(Retention retention,PreparedObservation observation,TypedRef candidate);
}
