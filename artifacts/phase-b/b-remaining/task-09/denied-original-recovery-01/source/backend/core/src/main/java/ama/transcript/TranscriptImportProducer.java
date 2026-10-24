package ama.transcript;
import ama.canonical.CanonicalValueCodec;
import ama.contract.MinutesContracts.CanonicalBudget;
import java.util.function.BooleanSupplier;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
/** Versioned pure producer identity. This fingerprint conveys no authorization. */
public final class TranscriptImportProducer {
    private TranscriptImportProducer() {
    }
    public static String compute(String meeting,String input,String fingerprint,long version,long epoch,CanonicalBudget budget,BooleanSupplier cancelled) {
        uuid(meeting);
        uuid(input);
        hash(fingerprint);
        uint(version);
        uint(epoch);
        return new CanonicalValueCodec().sha256(object( field("profile",string("ama.transcript-import-producer.v1")),field("kind",string("TASK_PRODUCER")), field("data",object(field("taskType",string("TRANSCRIPT_IMPORT_V1")),field("meetingId",string(meeting)),field("inputId",string(input)),field("inputFingerprint",string(fingerprint)),field("businessVersion",number(version)),field("cancelEpoch",number(epoch))))),budget,cancelled);
    }
}
