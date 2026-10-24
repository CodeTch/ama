package ama.contract;

import java.time.Instant;
import java.util.Objects;
import static ama.contract.OutboxContracts.protocolCode;
import static ama.contract.OutboxContracts.uuid;

/** Queue metadata only. Bodies remain in domain-owned storage behind stable references. */
public final class TaskContracts {
    private TaskContracts() {}

    public record SubjectRef(String kind, String id) {
        public SubjectRef {
            protocolCode(kind);
            text(id, 255);
        }
    }

    public record TypedRef(String kind, String id) {
        public TypedRef { protocolCode(kind); uuid(id); }
    }

    public record TaskSpec(String type, String pool, SubjectRef subject, String producerKey, TypedRef input,
                           String inputFingerprint, long businessVersion, int priority, long cancelEpoch, int maxAttempts) {
        public TaskSpec {
            protocolCode(type); protocolCode(pool); Objects.requireNonNull(subject); Objects.requireNonNull(input);
            sha256(producerKey); sha256(inputFingerprint);
            if (businessVersion < 0 || cancelEpoch < 0 || maxAttempts < 1) throw new IllegalArgumentException("Invalid fence or task policy");
        }
    }

    public record TaskLease(String taskId, long attempt, String leaseOwner, Instant expiresAt, TaskSpec spec) {
        public TaskLease {
            uuid(taskId); protocolCode(leaseOwner); Objects.requireNonNull(expiresAt); Objects.requireNonNull(spec);
            expiresAt = expiresAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            if (attempt < 1) throw new IllegalArgumentException("Invalid attempt");
        }
    }

    public enum Outcome { SUCCEEDED, RECONCILING, RETRYABLE, FAILED }
    public record TaskResult(Outcome outcome, TypedRef output, String errorCode) {
        public TaskResult {
            Objects.requireNonNull(outcome);
            if (errorCode != null) protocolCode(errorCode);
            if (outcome == Outcome.SUCCEEDED && output == null) throw new IllegalArgumentException("Success requires a typed output");
            if (outcome == Outcome.RETRYABLE && errorCode == null) throw new IllegalArgumentException("Safe retry requires a diagnostic code");
        }
    }
    public record Permit(String id, String model, String executionId, long configRevision, Instant safeDeadline) {
        public Permit {
            uuid(id); protocolCode(model); uuid(executionId);
            if (configRevision < 0) throw new IllegalArgumentException("Invalid config revision");
            if (safeDeadline != null) safeDeadline = safeDeadline.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        }
    }

    public static void sha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Canonical SHA256 required");
    }

    private static void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException("Invalid task metadata");
    }
}
