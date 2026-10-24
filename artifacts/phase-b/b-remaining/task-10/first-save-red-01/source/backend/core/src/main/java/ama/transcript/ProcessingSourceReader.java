package ama.transcript;
import ama.contract.IdentityContracts.Actor;
import ama.contract.TaskContracts.TaskLease;
import ama.storage.FileRegistry;
/** Standalone original-source reads. A lease/request is a claim, never authorization. */
public interface ProcessingSourceReader {
    record WorkerRequest(TaskLease lease,String inputId,String fileId) implements FileRegistry.ReadRequest {
        public String referenceKind(){return "INPUT_BATCH_FILE";}
    }
    record HistoryRequest(String revisionId,String fileId) implements FileRegistry.ReadRequest {
        public String referenceKind(){return "INPUT_BATCH_FILE";}
    }
    FileRegistry.Download open(TaskLease lease,String inputId,String fileId);
    FileRegistry.Download history(Actor actor,String meetingId,String revisionId,String fileId,String range);
}
