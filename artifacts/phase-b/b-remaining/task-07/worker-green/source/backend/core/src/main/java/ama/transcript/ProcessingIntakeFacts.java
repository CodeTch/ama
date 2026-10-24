package ama.transcript;
import ama.contract.IdentityContracts.Actor;
import ama.storage.FileRegistry.CompletedSource;
import java.util.List;
import java.util.function.BooleanSupplier;
/** Detached metadata only. It confers neither business membership nor byte/write authority. */
public interface ProcessingIntakeFacts {
    record Draft(Actor actor,String meetingId,long revision,long cancelEpoch,List<CompletedSource> orderedSources) {
        public Draft {orderedSources=List.copyOf(orderedSources);}
    }
    Draft readDraft(Actor actor,String meetingId,List<String> orderedFileIds,BooleanSupplier cancelled);
}
