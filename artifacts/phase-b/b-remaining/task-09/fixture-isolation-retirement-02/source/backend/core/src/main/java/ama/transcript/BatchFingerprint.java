package ama.transcript;

import ama.canonical.CanonicalValue;
import ama.canonical.CanonicalValueCodec;
import ama.contract.MinutesContracts.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/**
 * Explicit ama.input-batch.v1 hashing of new TRANSCRIPT batches.
 * Supplied rows/metadata are consistency facts, not proof of existence, completeness or file authority.
 * Callers must acquire them within their own limits and must not mutate lists concurrently.
 * This helper never reinterprets historical batch fingerprints or selects a default profile.
 */
public final class BatchFingerprint {
    public static final String PROFILE = "ama.input-batch.v1";

    public record Member(long ordinal, String fileId, String sha256, long sizeBytes) {
        public Member {
            uint(ordinal);
            require(ordinal > 0, INPUT_INVALID);
            uuid(fileId);
            hash(sha256);
            uint(sizeBytes);
        }
    }

    public String compute(String meetingId, String batchId, String kind, long baseRevision,
                          List<Member> members, CanonicalBudget budget, BooleanSupplier cancelled) {
        require(members != null && budget != null && cancelled != null, INVALID_ARGUMENT);
        check(cancelled);
        uuid(meetingId);
        uuid(batchId);
        require("TRANSCRIPT".equals(kind), INPUT_INVALID);
        uint(baseRevision);
        boundMembers(members.size(), budget);
        var seen = new HashSet<String>();
        var values = new ArrayList<CanonicalValue>();
        long ordinal = 1;
        for (var member : members) {
            check(cancelled);
            require(member != null && member.ordinal() == ordinal++, INPUT_INVALID);
            require(seen.add(member.fileId()), INPUT_INVALID);
            values.add(object(field("ordinal", number(member.ordinal())),
                    field("fileId", string(member.fileId())), field("sha256", string(member.sha256())),
                    field("sizeBytes", number(member.sizeBytes()))));
        }
        check(cancelled);
        var envelope = object(field("profile", string(PROFILE)), field("kind", string("INPUT_BATCH")),
                field("data", object(field("meetingId", string(meetingId)), field("batchId", string(batchId)),
                        field("kind", string(kind)), field("baseRevision", number(baseRevision)),
                        field("files", new Sequence(values)))));
        return new CanonicalValueCodec().sha256(envelope, budget, cancelled);
    }

    /**
     * Verifies exact supplied batch/member/file consistency and compares the actual saved fingerprint.
     * Members must be supplied in request/ordinal order; metadata may arrive in any order.
     * Only four-field ContentFile facts with the exact batch meeting are accepted.
     */
    public String verify(FullRow batch, List<FullRow> members, List<ContentFile> files,
                         CanonicalBudget budget, BooleanSupplier cancelled) {
        require(batch != null && members != null && files != null && budget != null && cancelled != null,
                INVALID_ARGUMENT);
        check(cancelled);
        require(batch.table() == Table.INPUT_BATCH, INPUT_INVALID);
        boundMembers(members.size(), budget);
        require(files.size() == members.size(), INPUT_INVALID);
        String meetingId = batch.string("meeting_id");
        var metadata = new HashMap<String, ContentFile>();
        for (var file : files) {
            check(cancelled);
            require(file != null && meetingId.equals(file.meetingId()), INPUT_INVALID);
            require(metadata.putIfAbsent(file.id(), file) == null, INPUT_INVALID);
        }
        var values = new ArrayList<Member>();
        long ordinal = 1;
        for (var row : members) {
            check(cancelled);
            require(row != null && row.table() == Table.INPUT_BATCH_FILE, INPUT_INVALID);
            require(batch.string("id").equals(row.string("batch_id")) && row.number("ordinal") == ordinal++,
                    INPUT_INVALID);
            var file = metadata.remove(row.string("file_id"));
            require(file != null && file.sha256().equals(row.string("sha256")), INPUT_INVALID);
            values.add(new Member(row.number("ordinal"), file.id(), file.sha256(), file.sizeBytes()));
        }
        require(metadata.isEmpty(), INPUT_INVALID);
        String computed = compute(meetingId, batch.string("id"), batch.string("kind"),
                batch.number("base_revision"), values, budget, cancelled);
        require(computed.equals(batch.string("input_fingerprint")), FINGERPRINT_MISMATCH);
        check(cancelled);
        return computed;
    }

    private static void boundMembers(int count, CanonicalBudget budget) {
        require(count > 0, INPUT_INVALID);
        require(count <= budget.maxContainerEntries(), BUDGET_EXCEEDED);
        // Each fixed-shape member necessarily has 9 codec nodes and 209 encoded bytes.
        // Reject impossible budgets before allocating projected lists/maps; the codec then
        // preflights the entire envelope, including its fixed overhead, keys and all bounds.
        require(count <= budget.maxNodes() / 9 && count <= budget.maxEncodedBytes() / 209, BUDGET_EXCEEDED);
    }
}
