package ama.transcript;

import ama.contract.MinutesContracts.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Explicit new batch v1 helper; does not grant file or storage authority. */
public final class BatchFingerprint {
    public record Member(long ordinal, String fileId, String sha256, long sizeBytes) {}
    public String compute(String meetingId, String batchId, String kind, long baseRevision,
                          List<Member> members, CanonicalBudget budget, BooleanSupplier cancelled) {
        return null;
    }
    public String verify(FullRow batch, List<FullRow> members, List<ContentFile> files,
                         CanonicalBudget budget, BooleanSupplier cancelled) {
        return null;
    }
}
