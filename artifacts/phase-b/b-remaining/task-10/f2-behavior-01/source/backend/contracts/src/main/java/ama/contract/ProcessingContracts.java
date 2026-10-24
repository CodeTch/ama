package ama.contract;
import java.util.*;
/** Accepted metadata and bounded wire commands never grant authority. */
public final class ProcessingContracts {
    private ProcessingContracts() {
    }
    public record ImportRequest(long expectedRevision,List<String> orderedFileIds) {
        public ImportRequest {
            MeetingContracts.revision(expectedRevision);
            if(orderedFileIds==null||orderedFileIds.isEmpty()||orderedFileIds.size()>64)throw new IllegalArgumentException("Invalid file collection");
            orderedFileIds=List.copyOf(orderedFileIds);
            var unique=new HashSet<String>();
            for(String id:orderedFileIds) {
                OutboxContracts.uuid(id);
                if(!unique.add(id))throw new IllegalArgumentException("Duplicate file");
            }
        }
    }
    public record ImportAccepted(String meetingId,String batchId,String inputId,String taskId,long revision,String revisionToken,String inputFingerprint) {
    }
    public record Page(List<Map<String,Object>> items,String nextCursor) {
        public Page {
            items=List.copyOf(items);
        }
    }
    public static final class Failure extends RuntimeException {
        private final int status;
        private final String code;
        public Failure(int status,String code) {
            super(code);
            this.status=status;
            this.code=code;
        }
        public int status() {
            return status;
        }
        public String code() {
            return code;
        }
    }
    public static Failure conflict() {
        return new Failure(409,"PROCESSING_CONFLICT");
    }
    public static Failure unavailable() {
        return new Failure(503,"PROCESSING_UNAVAILABLE");
    }
}
