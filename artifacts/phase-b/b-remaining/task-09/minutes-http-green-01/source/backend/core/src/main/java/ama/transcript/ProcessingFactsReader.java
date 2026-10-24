package ama.transcript;

import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MinutesContracts.StoredProvenanceFacts;
import java.util.function.BooleanSupplier;

/** Standalone authorized acquisition only; detached facts confer no write capability. */
public interface ProcessingFactsReader {
    StoredProvenanceFacts readBatch(Actor actor, String meetingId, String batchId,
                                    ArchiveReadBudget budget, BooleanSupplier cancelled);
    StoredProvenanceFacts readStored(Actor actor, String meetingId, String inputId,
                                     ArchiveReadBudget budget, BooleanSupplier cancelled);
}
