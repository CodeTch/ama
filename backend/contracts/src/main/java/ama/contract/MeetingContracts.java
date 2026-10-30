package ama.contract;

import java.time.LocalDate;
import java.util.List;
import java.util.HashSet;
import static ama.contract.IdentityContracts.stableId;

/** Saved human values only. Names/summary have no product character limit. */
public final class MeetingContracts {
    private MeetingContracts() {}
    public record SaveInfo(Long expectedRevision, String name, String category, String summary,
                           LocalDate occurredOn, List<String> rosterDraftIds, List<String> externalRosterNames) {
        public SaveInfo {
            revision(expectedRevision);
            if (category != null) stableId(category);
            if (rosterDraftIds == null) throw new IllegalArgumentException("Roster must be explicit");
            rosterDraftIds = List.copyOf(rosterDraftIds);
            rosterDraftIds.forEach(IdentityContracts::stableId);
            if (new HashSet<>(rosterDraftIds).size() != rosterDraftIds.size()) throw new IllegalArgumentException("Duplicate enterprise identity");
            externalRosterNames = externalRosterNames == null ? List.of() : List.copyOf(externalRosterNames);
            if (externalRosterNames.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("External name required");
        }
    }
    public record ConfirmRoster(Long expectedRevision) { public ConfirmRoster { revision(expectedRevision); } }
    public record DeleteMeeting(Long expectedRevision) { public DeleteMeeting { revision(expectedRevision); } }
    public record Mutation(String id, long revision) { public String getRevisionToken(){return Long.toString(revision);} }
    public record Created(String id, long revision, String stage) { public String getRevisionToken(){return Long.toString(revision);} }
    public record Member(String memberId, String enterpriseId, String displayName, String memberKind) {}
    public record Saved(String id, long revision, long accessRevision, String stage, String name, String category,
                        String summary, LocalDate occurredOn, String confirmedRosterId, List<Member> confirmedRoster,
                        String rosterDraftId, List<Member> rosterDraft) { public String getRevisionToken(){return Long.toString(revision);} public String getAccessRevisionToken(){return Long.toString(accessRevision);} }
    public static void revision(Long value) {
        if (value == null || value < 0 || value == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid expected revision");
    }
    public static final class RevisionExhausted extends RuntimeException {}
    public static final class Conflict extends RuntimeException { public Conflict() { super("Meeting revision or request conflicts"); } }
    public static final class CategoryUnavailable extends RuntimeException { public CategoryUnavailable() { super("Category dictionary unavailable"); } }
}
