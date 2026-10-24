package ama.task;

import ama.contract.OutboxContracts.EventDraft;

/** Domain-specific validation runs before the sequence lock, under the caller's prelocked domain root. */
@FunctionalInterface
public interface OutboxReferenceValidator {
    void validate(EventDraft event);
}
