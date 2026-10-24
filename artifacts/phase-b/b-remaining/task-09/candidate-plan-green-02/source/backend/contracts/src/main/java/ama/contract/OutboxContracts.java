package ama.contract;

import ama.contract.TaskContracts.SubjectRef;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class OutboxContracts {
    private OutboxContracts() {}

    public record Reference(String kind, String id) {
        public Reference { protocolCode(kind); uuid(id); }
    }

    public record EventDraft(String eventType, String taskId, SubjectRef subject,
                             long subjectRevision, Reference reference) {
        public EventDraft {
            protocolCode(eventType);
            if (taskId != null) uuid(taskId);
            Objects.requireNonNull(subject); Objects.requireNonNull(reference);
            if (subjectRevision < 0) throw new IllegalArgumentException("Negative subject revision");
        }
    }

    public record OutboxEvent(long sequence, EventDraft event, Instant occurredAt) {}

    public static void protocolCode(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}")) {
            throw new IllegalArgumentException("Invalid protocol code");
        }
    }

    public static void uuid(String value) {
        if (value == null || !UUID.fromString(value).toString().equals(value)) {
            throw new IllegalArgumentException("Canonical UUID required");
        }
    }
}
