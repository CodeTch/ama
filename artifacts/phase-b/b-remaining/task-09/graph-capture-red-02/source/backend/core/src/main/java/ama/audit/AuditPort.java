package ama.audit;

import ama.contract.AuditContracts.AuditEvent;

public interface AuditPort {
    /** Append metadata within the caller's business transaction. */
    void append(AuditEvent event);
}
