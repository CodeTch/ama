package ama.contract;

import ama.contract.TaskContracts.SubjectRef;
import ama.contract.TaskContracts.TypedRef;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import static ama.contract.OutboxContracts.*;
import static ama.contract.TaskContracts.sha256;

/** Durable logical operation and individual execution are distinct. All request manifests are domain-validated. */
public final class ExternalContracts {
    private ExternalContracts() {}

    public record ExternalRequest(String operationKind, String businessKey, String logicalRequestHash,
                                  String executionRequestHash, String requestManifest) {
        public ExternalRequest {
            protocolCode(operationKind); sha256(logicalRequestHash); sha256(executionRequestHash);
            if (businessKey == null || businessKey.isBlank() || businessKey.length() > 255) throw new IllegalArgumentException("Invalid business key");
            if (requestManifest == null || requestManifest.isBlank()) throw new IllegalArgumentException("Versioned safe manifest required");
        }
    }

    public record OperationToken(String operationId, String executionId, long executionNo, long reconcileEpoch,
                                 String owner, Instant expiresAt, SubjectRef subject) {
        public OperationToken {
            uuid(operationId); uuid(executionId); protocolCode(owner); Objects.requireNonNull(subject);
            expiresAt = Objects.requireNonNull(expiresAt).truncatedTo(ChronoUnit.MICROS);
            if (executionNo < 1 || reconcileEpoch < 1) throw new IllegalArgumentException("Invalid execution fence");
        }
    }

    public enum ObservationResult { STALE_TOKEN, RECORDED_FENCED, APPLIED, QUARANTINED }

    public enum ExternalOutcome { SUCCEEDED, UNKNOWN, FAILED_ZERO_EFFECT, MANUAL_REQUIRED }
    public enum NoEffectBasis { NOT_SENT, DEFINITIVE_REJECTION, PROVEN_CUTOFF_FENCED }
    public record NoEffectProof(NoEffectBasis basis, String safeEvidenceReference, Instant verifiedAt) {
        public NoEffectProof {
            Objects.requireNonNull(basis);
            if (safeEvidenceReference == null || safeEvidenceReference.isBlank()) throw new IllegalArgumentException("Controlled evidence required");
            verifiedAt = Objects.requireNonNull(verifiedAt).truncatedTo(ChronoUnit.MICROS);
        }
    }

    public record ExternalObservation(ExternalOutcome outcome, String externalResultId, TypedRef output,
                                      String errorCode, NoEffectProof noEffectProof) {
        public ExternalObservation {
            Objects.requireNonNull(outcome);
            if (errorCode != null) protocolCode(errorCode);
            if ((outcome == ExternalOutcome.FAILED_ZERO_EFFECT) != (noEffectProof != null)) {
                throw new IllegalArgumentException("Only definitive zero-effect failures carry proof");
            }
            if (outcome == ExternalOutcome.SUCCEEDED && (externalResultId == null || externalResultId.isBlank())) {
                throw new IllegalArgumentException("Authoritative success requires an external result identity");
            }
        }
    }
}
