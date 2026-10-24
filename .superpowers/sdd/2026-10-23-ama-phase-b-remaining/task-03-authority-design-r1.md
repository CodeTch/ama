# Task 3 / 4 authority design R1 — ADR-01..03 closure

2026-10-23. DESIGN ONLY, before Task 3 implementation. Original design SHA256 `121b58e04c0077d8a7e46b5efbae49d5d0b2955308d2ad486fdb0c017a0155ba`; original independent review SHA256 `4574ca23691aca4b740006045d84b1ab07d4b8a232e5e0f837ea8735f2083797`. Both were read, independently hashed and remain unchanged. Review source context is coordinator-declared HEAD `64908b1aef4c28f0806846128fcb44005f1b75e1`; no Git verification performed. Task 1 is independently adopted. Concurrent Task 2 schema/creator implementation is excluded.

This document is the normative R1 replacement for the three affected contracts in the original design. The original design's accepted first-binding/new-row seal, original-source/epoch/route rules, operation eligibility, atomic intake, private post-commit pin and prospective tests are incorporated unchanged except where explicitly tightened here. In particular, the original `sources(LockedSources)` signature and default extension-method proposals are superseded and must not be implemented. A source author must use R1 together with those accepted original sections; ambiguous shorthand resolves to the explicit APIs and phases here.

Only this R1 and its brief closure matrix are new artifacts. No source, Git, DB, Docker, tests, private environment or historical artifacts changed. No runtime/DDL/Phase B completion approval follows. Root obtains bounded independent re-review before Task 3 authorship.

## Evidence checked for this revision

Full independent review; actual MeetingAccess/JdbcMeetingAccess, JdbcMeetingRepository name and identity-coverage consumers, FileReferencePolicy, `ama.api.MeetingConfiguration` and `ama.api.FileConfiguration` injection sites. Prior full controlled-runtime spec/Normative R1, remaining plan, adopted B04/B06 consumer guides and actual source/schema examination remain the original design's basis. Review findings are accepted after checking those real consumers, not because a review label itself grants authority.

## ADR-01: authorization-only identity capture in the same B03 issuer

Choose a concrete status-only projection for public authorization entries. Keep the existing MeetingAccess public signatures and single JdbcMeetingAccess bean; do not introduce a configuration switch, another access implementation, a second ACL path or a display-name limit.

Proposed JdbcMeetingAccess internal shape:

```java
record IdentityStatus(String id, String status) {}
abstract class IdentityCapture implements MeetingAccess.LockedIdentities {
    final Object transaction;
    final String actor;
    final Map<String, IdentityStatus> statuses;
    Set<String> ids(); // immutable set of actually captured IDs
    JdbcMeetingAccess issuer();
}
final class AuthorizationIdentities extends IdentityCapture { /* no names */ }
final class Identities extends IdentityCapture {
    final Map<String, Person> people; // existing complete genuine name/status values
}
// Existing private genuine Held stores IdentityCapture instead of only Identities.
// Its root/subject/issuer/holder authorization semantics remain the same.
private Optional<LockedIdentities> captureAuthorizationIdentities(
    Actor actor, Collection<String> affected, boolean skip, boolean allActive);
// Existing package-private repository/roster entry remains:
Identities lockIdentities(Actor actor, Collection<String> affected);
```

IdentityCapture is an internal class with construction confined to the adapter's capture paths. Authenticity checks accept only those issued inner classes, configured JdbcMeetingAccess instance and actual current DataSource transaction holder. Public opaque LockedIdentities implementations cannot become a capture. Neither AuthorizationIdentities nor a status-only path constructs a dummy Person with null/empty/truncated name.

Share canonical identity-set construction, rootsStarted phase guard, actual-holder check and ACTIVE/complete-union validation between both captures. The status-only SQL for each canonical identity is exactly:

```sql
SELECT enterprise_id,status FROM enterprise_identity
 WHERE enterprise_id=? FOR UPDATE /* append SKIP LOCKED when requested */
```

No SELECT *, display_name column, getString(display_name), lazy name access or name-length query occurs on this path. Schema-bounded enterprise_id/status values alone are transferred. B06 import's complete union contains the discovered current owner/actor; B06 validates its one-owner scope and bounded full-spec/input sets before allocating collections. A future unrelated domain cannot smuggle an unbounded affected set through the B06 policy. This projection needs no product name cap and authorizes even very large or supplementary-character display names without transferring those names.

Fixed caller routing:

1. Public `JdbcMeetingAccess.lock(actor,meetingId)` calls captureAuthorizationIdentities(actor,List.of(),false,false), then existing lockPrepared. It no longer delegates to the full-name package-private lockIdentities.
2. Public `tryLockIdentities(actor,affected,skip)` calls captureAuthorizationIdentities(actor,affected,skip,true). It preserves strict complete current-ACTIVE identity semantics.
3. `require(actor,id,action)` retains standalone rejection/composition and uses that public lock. `lockPrepared`, `require(held,action)` and `requirePrepared` use IdentityCapture.statuses/ids for identity state/coverage and the exact existing subject/root/current owner/grant/confirmed roster/member ACL algorithm. No duplicated authorization implementation is permitted.
4. Package-private `lockIdentities(actor,affected)` used by JdbcMeetingRepository.create/save/confirm retains full real Person values and its existing semantics. Existing save still writes exact Person.name; confirm still reads actual status. `lockMeeting(Identities,id)` still delegates to the same lockPrepared. Change only repository `requireDiscoveredIdentities` from `held.identities.people.keySet()` to `held.identities.ids()` because coverage is independent of projection. Do not change creator logic or roster writes.

Complete unique identity union is sorted unsigned UTF8 before any subject/root. skip=true applies to each identity SQL. Any locked/missing/non-ACTIVE required identity returns Optional.empty before subject/meeting acquisition; no blocking fallback or late additional identity. Partial identity locks end at that candidate transaction boundary. Discover owner without locks, then verify the exact discovered owner and coverage under the locked meeting; changed owner fails the candidate instead of acquiring the replacement owner late.

Exact configuration: existing `ama.api.MeetingConfiguration.meetingAccess(DataSource,TransactionRunner,JdbcSubjectFences)` constructs the single unchanged-constructor JdbcMeetingAccess. Existing `ama.api.FileConfiguration.fileRegistry(...)` receives that same MeetingAccess bean. Therefore registry.lock automatically selects the status-only public entry for draft/intake/current/history/worker acquisition. New B06 configuration injects that identical instance into JdbcTranscriptAuthority and ImportTaskDomainPolicy. Queue prepare uses its public tryLockIdentities; queue lockRoots uses its lockPrepared, then registry.adopt of that same genuine B03 gate. Intake policy adoption uses the very same issuer. Direct-construction tests must share one instance; equal source URLs or a new access wrapper do not qualify.

Standalone JdbcProcessingFactsReader preserves its ambient rejection and current explicit pre-transfer display-name measurement. Its separately owned access instance naturally uses the new status-only public entry; the retained measurement remains conservative historical reader behavior and is not a prerequisite for the new registry paths. Do not rewrite reader evidence or remove its limits merely because public access no longer needs names.

Task 3 source ownership expands narrowly to JdbcMeetingAccess and the repository coverage accessor. Because Task 2 is concurrently changing creator/schema only, schedule these edits after root's base freeze and do not overlap creator work. No global roster name cap is added by this task.

ADR-01 future proof: SQL/result-set instrumentation must fail if registry draft/intake/current/history/worker or ordinary queue authorization selects/materializes display_name; extremely large and supplementary names authorize without name transfer. A separate real roster-save case preserves the complete genuine long/supplementary name. skip=true lock contention must return before any root; owner change fails without late identity; foreign issuer/holder fails. Normal owner/member paths and outsider/admin-only denial still pass. These are required implementation tests, not executed results.

## ADR-02: mandatory complete-read extension with explicit legacy dispatch

Leave FileReferencePolicy unchanged with its two current abstract methods: prelock and validate. Add the following separate extension. All listed extension methods are abstract; none may inherit a no-op or fail-closed default that masquerades as protocol participation.

```java
public interface CompleteReadPolicy extends FileReferencePolicy {
    Object prelock(FileRegistry.LockedMeeting gate, String meetingId,
                   FileRegistry.Reference ref, String fileId,
                   FileRegistry.ReferenceContext context);
    ReadPlan prelockRead(FileRegistry.LockedMeeting gate, String meetingId,
                        FileRegistry.ReadRequest request);
    List<ReadMember> readMembers(Object domain, FileRegistry.Reference ref,
                                String requestedFileId);
    void prelockReadTasks(List<Object> completeAuthenticScopes);
    void finishRead(Object domain, List<FileRegistry.CompletedSource> actualSources);

    record ReadPlan(FileRegistry.ReferenceContext context,
                    List<ReadMember> completeMembers, String requestedFileId) {}
    record ReadMember(FileRegistry.Reference reference, String fileId) {}
}
```

InputBatchFileReferencePolicy explicitly implements CompleteReadPolicy and every method. Production B06 composition rejects a legacy-only registration for INPUT_BATCH_FILE. Legacy policies for other existing kinds keep their unmodified registration/behavior. New FileRegistry methods themselves may default to unavailable for unchanged registry implementations; that compatibility rule must not be copied into CompleteReadPolicy.

Registry Proof retains its exact configured policy instance and private domain. Dispatch derives solely from that trusted policy instance (`instanceof CompleteReadPolicy`) and authentic Proof, never a caller flag, enum, request type claim, UUID possession or arbitrary DTO. New contextual prelock and standalone registry.read require CompleteReadPolicy; legacy provider rejects as unavailable before source I/O, with no fallback to old prelock to authorize the new request. Callers cannot inject ReadPlan/Proof/domain. The registered policy issues a plan; registry invokes contextual prelock for every member and checks every returned private proof/scope.

Generic download algorithm:

1. Acquire its single fresh READ gate and inspect actual references. For unchanged legacy providers, retain old context-free prelock/validate and singleton source behavior, with no extension-method call.
2. For complete providers, context-free prelock is fixed to CURRENT_EFFECTIVE. B06 verifies actual current WORKING revision/input/batch/profile, coalesces same-current-graph references into one genuine scope for this call, and returns complete members through readMembers. Complete all existing domain acquisition and expansion before sources. No global or ThreadLocal authorization cache. History/worker/first-bind mode cannot be chosen through generic download.
3. Acquire full original-session/file union once; validate actual references, original purposes and files. Legacy validity follows its original validate path. Complete scope is not readable yet.
4. Call mandatory prelockReadTasks once with the entire distinct complete-scope list; acquire every required task UUID in sorted union order, without any attempts. Then call mandatory finishRead exactly once per authentic scope. It may acquire only its previously declared attempts and must perform actual producer/live lease checks. Readability follows successful finalization, not prior DTO or source matching.
5. Open only if at least one independently complete valid reference/scope authorizes the requested file and the transaction commits. Unexpected finalizer errors, unavailable/incomplete finalization and missing protocol support abort; they never become readable=true. Genuine NotFound means only that reference/scope did not authorize; a different independently valid reference may still authorize. Do not recast arbitrary exceptions as NotFound. A previously valid legacy alternative cannot cause arbitrary complete-finalizer errors to be swallowed.

Per-call deduplication is based on configured policy identity plus genuine private scope identity, not caller equals/hashCode. B06 reuses the same scope for the same actual current revision/input graph in its call-owned acquisition object. prelockReadTasks verifies all scope issuer/source/holder/gate bindings and complete declared task sets, then advances each scope from SOURCES_VALIDATED to TASKS_HELD only after the whole union is held. finishRead requires this state and records FINALIZED; repeated/incomplete calls fail. Its body is implemented by production policy, not a public caller callback.

Supported composition is explicit and bounded: any one complete-provider instance may own multiple authentic scopes in one standalone read, alongside unchanged legacy references. If actual eligible references require distinct complete-provider instances, registry rejects unavailable before source acquisition. This covers current B06 production and unchanged legacy providers while avoiding an invented cross-policy task ordering protocol. It is not a caller-controlled legacy bypass. Future multi-extension support needs its own complete global plan. Within the supported provider all tasks across all scopes must be held before ANY scope takes an attempt lock. Shared task IDs deduplicate; conflicting expectations fail.

Required ADR-02 tests: unchanged two-method legacy policy download succeeds; new read request to it rejects; production INPUT_BATCH_FILE cannot register/use legacy bypass. Generic B06 effective owner/member positives run finishRead exactly once; duplicate references to one scope do not double-finalize. Multiple scopes acquire complete task union first. Throwing/unsupported finalizer never opens; missing methods on a concrete complete provider are compile errors. Pending/obsolete/history generic negatives remain. Genuine NotFound alternative-reference fallback succeeds only via another fully valid path; arbitrary exceptions abort even with an apparently valid legacy alternative. Compile compatibility alone is insufficient.

## ADR-03: B04 validates source receipt against expected gate and full set

Frozen additive FileRegistry signatures (all have fail-closed defaults for old registry implementations):

```java
interface LockedSources {}
interface ReferenceContext {}
interface ReadRequest { String referenceKind(); }
record CompletedSource(FileRecord file, String uploadId,
                       String originalPurpose, long uploadCancelEpoch) {}

LockedMeeting adopt(Actor actor, String meetingId, MeetingAccess.Locked access,
                    MeetingAccess.Action action);
LockedReference prelock(LockedMeeting gate, Reference ref, String fileId,
                        ReferenceContext context);
Optional<LockedSources> prelockSources(LockedMeeting gate,
                        List<String> completeFileIds, boolean skipLocked);
List<CompletedSource> sources(LockedMeeting expectedGate, LockedSources held,
                              List<String> exactFileIds);
void bindAll(LockedSources sources, List<Binding> bindings);
Download read(Actor actor, String meetingId, ReadRequest request, String range);
```

Delete the proposed getter without expectedGate/exactFileIds from the implementation plan. The verifier/getter owns B04 enforcement and does no row insertion, reference mutation or byte/pin issuance.

Before returning metadata, registry checks: active actual DataSource transaction; expectedGate's private class/issuer/source/actual holder; receipt's private class/issuer/source/actual holder; exact object identity receipt.gate == expectedGate; phase SOURCES_HELD, neither BINDING nor CONSUMED; expected list nonempty/bounded/unique; canonical UUID set equals receipt's complete immutable set. Duplicate expected IDs reject rather than being silently set-normalized. Metadata string equality is never gate proof. Another source wrapper, suspended transaction holder or matching DTO copy does not satisfy actual-holder identity. No global authority cache is introduced.

B06 insertAndReadNew first verifies its own reservation, then calls:

```java
var actual = registry.sources(reservation.gate, sourceReceipt,
                              reservation.completeFileIds);
```

Only then compare original metadata/route/epoch/location/reference observations and exact proposed graph. B04 validates membership as an unordered unique set; B06 independently validates immutable member ordinal/file/hash order. Wrong order with identical file set must fail B06 even though B04 set equality succeeds. The detached actual list still grants no row/read permission by itself.

Binding consumes the same receipt: bindAll(receipt,bindings) authenticates every private registry Proof, derives one expected gate and the complete unique file set from those proofs, then calls the same verifier. It must not reacquire a subset/expanded source set. Before mutating references transition receipt to BINDING; on success CONSUMED. Getter fails in either phase. Any failure dooms the caller transaction and leaves that local receipt unusable; it cannot be reset to retry in place. Rollback restores DB refs/epochs/graph/fences, not an expired in-memory capability. A later transaction reacquires a new gate/source set.

Policy validation during bind uses the already-checked snapshots; it does not invoke sources again after transition/consumption. B06 may keep checked detached observations as data for later held-row equality, but they cannot revive source or first-bind authority. Multiple metadata inspections before consumption are allowed; exactly one binding handoff is allowed.

ADR-03 tests must fail before INSERT/reference mutation/open for fake receipt, different registry/source/issuer, ended/other/suspended holder, wrong expected gate, subset/extra/duplicate IDs, BINDING/CONSUMED receipt. Exact authentic receipt supports inspection then one handoff. Same set with wrong B06 order fails graph validation. Cloned metadata cannot issue authority. No additional acquisition phase or implicit retry is introduced.

## Fixed complete ordering and different producer proofs

The canonical existing-domain rank is fixed, not optional:

```text
identity union unsigned UTF8 -> subject + meeting (+ actual READ grant provenance)
 -> complete transcript_revision/parent closure by UUID
 -> transcription_input UUID
 -> input_batch UUID
 -> input_batch_fingerprint_profile batch UUID
 -> input_batch_file (batch UUID, ordinal)
 -> ALL original upload_session UUIDs sorted
 -> ALL file_object UUIDs sorted
 -> ALL durable_task UUIDs sorted
 -> task_attempt (task_id, attempt) sorted
 -> external execution if applicable -> audit/outbox -> one flush -> commit
```

Apply this to full-spec queue unions and complete generic/current/history/worker scope unions. Complete parent/task discovery occurs before its lock phase and is bounded; under root reject changed edges instead of acquiring new late domain roots. Every task in a multi-task set must be held before the first attempt. Queue ordinary acquisition must not let a per-spec callback acquire attempts before all tasks in its scope. Transaction-created intake/output rows are the sole post-source new-domain exception; deliberate source locks precede input_batch_file FK INSERT. No new existing roots after sources/tasks, no SQL after final flush.

Current/history producer finalization proves actual RAW.producer_task_id -> actual production durable task, its immutable full TaskSpec/input/batch/fingerprint and actual successful typed output equal to that RAW. WORKING inherits the exact original RAW chain. It does not require the old producer to be RUNNING, have an unfinished lease/attempt, or equal today's businessVersion. Never rebase old task/base/fingerprint to current values. Generic current reads perform these producer checks too. Original B04 completed-upload epoch still controls byte eligibility, including history; metadata visibility does not revive cancelled source bytes.

Worker source finalization is distinct: actual RUNNING task, actual matching unfinished attempt, exact full spec/owner/attempt, DB expires_at > UTC_TIMESTAMP(6), strict current business/cancel/lifecycle/never-published fence and original fixed input. Renewed DB expiry works with older DTO expiresAt; future DTO expiry cannot revive an expired DB row. History producer success is not worker lease permission. Never call queue.validLease after sources and restart identity/root acquisition. Ordinary completion independently reacquires fresh current authority after parsing. Neither producer-proof mode authorizes initial external send or stale-domain retention.

## Revised full sequences

First binding/intake (accepted original behavior with explicit R1 checks):

```text
standalone bounded draft metadata + original route/session/fence observations
archive closure/manifest/BatchFingerprint/ConfigBindings/producer hashes outside roots
final transaction:
 idempotency -> one registry status-only B03 EDIT gate -> retain subject handle
 reserve new IDs and complete existing graph; contextual first-bind proofs incomplete
 sourceReceipt = prelockSources(gate,complete files,false)
 actual = sources(gate,sourceReceipt,complete files)
 B06 validates ordered graph + exact observations
 INSERT only reserved new batch/profile/members/input; read actual uncommitted rows
 seal private held processing facts; no standalone-reader or DTO minting
 bindAll(sourceReceipt,all exact bindings) once; receipt CONSUMED
 verify actual BOUND refs; advance meeting/subject once r+1/e; clear transcript/final confirmations; SET needs_generation=1; preserve the saved transcript pointer
 adopt same genuine B03+held graph into registered production preparation
 enqueue exact TRANSCRIPT_IMPORT_V1 / TRANSCRIPT_IMPORT spec
 idempotency result + audit/outbox; one final flush; commit; only then ACK
```

No fake current transcript/confirmation, default profile, current-input column or placeholder output. Draft and actual new-row held proofs remain different. Full rollback includes new profile/graph, protective upload handoff/referenceEpoch, task/fences/pointers/debt/idempotency/outbox. Exact canonical producer remains profile ama.transcript-import-producer.v1/kind TASK_PRODUCER/data taskType,meetingId,inputId,inputFingerprint,businessVersion,cancelEpoch, with priority 0/maxAttempts 3 and target r+1/e. Compute before roots; no obsolete IMPORT_TXT/TRANSCRIPT/TEST names.

Standalone planned source read:

```text
reject ambient transaction; choose exact CompleteReadPolicy or fail unavailable
bounded discovery + expected producer-key computation before gate
pinned = tx.required {
 gate = registry.lock(actor,meeting,READ) // status-only capture; one B03 issuer
 plan = policy.prelockRead(gate,meeting,request) // EDIT on same held gate if required
 prelock complete member references/domain; no DTO authority
 src = registry.prelockSources(gate,completeFileIds,false)
 actual = registry.sources(gate,src,completeFileIds)
 validate complete actual references/original source proofs
 policy.prelockReadTasks(allAuthenticScopes) // all task UUIDs, no attempts
 for each distinct authentic scope: policy.finishRead(scope,actual)
 return registry PRIVATE local pin only after complete successful authorization
}
// successful transaction return only; no transferable public pin/opener
backend = backends.require(exact historical backend,false)
open exact immutable pinned key/range outside DB transaction; close stream reliably
```

Genuine B03 gate, private B06 contexts and same DataSource/actual holder remain required at every consumption. No new synchronization, arbitrary authority callback or post-flush lock. Failed finalizer/commit never opens storage. Preserve B04 one-shot stream semantics (D06 periodic revocation remains separate), original route/epoch/current file checks, unknown-profile fail-closed, and standalone B06.2 ambient rejection. Worker byte size/SHA/parser/aggregate-budget checks happen outside locks and completion rechecks afterward.

## Closure and future evidence boundary

ADR-01..03 are addressed in this design revision, pending independent re-review; not claimed implementation PASS. Retain all original eleven test groups and independent review evidence matrix, adding exact name-projection/skip/roster preservation, unchanged legacy versus mandatory finalizer dispatch, expected-gate/full-set/consumption negatives and task-union-before-any-attempt traces. SQL traces must choose task UUID order different from input/parent/file order. Current/history producer success and live worker lease checks need separate positives and negatives.

Task 3 ownership adds only the narrow B03 capture/coverage refactor and new complete-read extension to prior files. Task 4/6 still own exact no-store bounded discovery/current/history HTTP contracts before UI. Sidecar 013.1 remains separately reviewed/applied deployment dependency. Root may freeze/dispatch Task 3 only after bounded R1 design review; no source/DB/tests were run here and no Phase B completion claim is made.
