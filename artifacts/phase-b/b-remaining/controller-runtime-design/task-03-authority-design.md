# Task 3 / 4 file authority integration design

2026-10-23. Design against coordinator-declared HEAD `46a1adad4dd4afda3272e34669235d4126c480d6`. Proposal for independent root review, not implementation approval or runtime evidence. Only this document is written. No source, Git, DB, Docker, tests, private environment or historical artifacts changed. Ordinary prepared completion is under separate review and is not reviewed here.

## Sources and actual constraints

Read: full controlled-runtime spec including Normative R1; remaining plan and Tasks 3–6; full remaining-preflight/b06-preflight; B06 seams/archive and B04 file-registry integration guides; actual FileRegistry/FileReferencePolicy/FilePurposePolicy/JdbcFileRegistry/FileBackendCatalog; ProcessingFactsReader/JdbcProcessingFactsReader; MeetingAccess/JdbcMeetingAccess; JdbcTaskPreparations/TaskDomainPolicy/JdbcTaskQueue/JdbcSubjectFences/TransactionRunner/TaskContracts; B06QueueSeamsIT; actual V008/V009/V011 schemas. Root's allocated 013.1 sidecar remains a dependency, not assumed applied. New normative task names supersede the older preflight proposal.

* `resolve` only supplies internal same-meeting metadata. It does not establish original upload, readable content or business membership.
* Original completed_file_id has no uniqueness constraint; exactly one COMPLETED original must be checked, never first-row selection.
* `MeetingAccess.require(EDIT)` proves current ownership/ACTIVE identity/lifecycle/governance, but not never-published. Writers/workers separately reject ever_published and PUBLISHED. Authorized owner history may inspect published immutable history without gaining writes.
* All adapters share the exact DataSource object and MeetingAccess instance. Same connection URL is insufficient.
* `input_batch_file` INSERT takes file FK locks. All original sessions/files must be deliberately acquired before member INSERT. New rows may then be inserted/read after source locks; existing domain discovery may not happen late.
* Current registry SELECT * materializes LONGTEXT display_name. New bounded paths must use bounded projections/pre-transfer length gates, not reuse that helper unmodified.
* Queue compares actual full spec/state/attempt/owner/current fence and actual DB expiry. Do not require lease.expiresAt equality after renewal; that DTO timestamp is merely an observation.

## Ownership and minimal structure

One production `JdbcTranscriptAuthority` is B06's private capability issuer and bounded domain acquisition owner. InputBatchFileReferencePolicy, JdbcProcessingIntakeFacts, JdbcProcessingSourceReader, ImportTaskDomainPolicy and later JdbcTranscriptRepository share it. Its actual capability implementations and constructors are private. Public selectors/DTOs/operation enums grant no authority.

B04 continues owning original session/file/route validation, sorted source acquisition, references and stream opening. B06 owns operation eligibility, actual batch/input/revision provenance and task checks. No public callback is accepted as authority to pin bytes.

Task 3 owns additive FileRegistry/FileReferencePolicy/JdbcFileRegistry seams, bounded projections, authority/source/intake-facts adapters, controlled TranscriptFilePurposePolicy, tests. Task 4 owns actual intake/production policy/producer helper/config/HTTP/discovery. Task 5 consumes source reader and prepared completion for RAW. Task 6 consumes current/history authority for saved graph/confirmation. Do not copy private issuance into each consumer.

Preserve existing FileRegistry APIs and defaults for old implementations (new APIs default fail closed). Preserve standalone ProcessingFactsReader ambient rejection and existing algorithms/FullRow shapes. Optional shared bounded acquisition extraction is not required to land this slice.

## Proposed API signatures

These are proposed Java signatures, not existing APIs. Keep policy records nested where practical.

```java
// Additive FileRegistry
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
List<CompletedSource> sources(LockedSources held);
void bindAll(LockedSources sources, List<Binding> bindings);
Download read(Actor actor, String meetingId, ReadRequest request, String range);

// Additive FileReferencePolicy, fail-closed defaults except readMembers singleton
Object prelock(FileRegistry.LockedMeeting gate, String meetingId,
               FileRegistry.Reference ref, String fileId,
               FileRegistry.ReferenceContext context);
ReadPlan prelockRead(FileRegistry.LockedMeeting gate, String meetingId,
                    FileRegistry.ReadRequest request);
void finishRead(Object domain, List<FileRegistry.CompletedSource> sources);
List<ReadMember> readMembers(Object domain, FileRegistry.Reference reference,
                            String requestedFileId);
record ReadPlan(FileRegistry.ReferenceContext context,
                List<ReadMember> completeMembers, String requestedFileId) {}
record ReadMember(FileRegistry.Reference reference, String fileId) {}
```

`ReadPlan` is composition, not authority: registry obtains it from the exact registered policy, invokes contextual prelock for every member, checks all resulting private registry Proofs and calls that same policy's finishRead. A caller cannot inject a plan. Every contextual prelock in a B06 plan returns the same authentic private complete-domain scope; finishRead receives that scope, not an unrelated newly constructed object. B06's context verifier checks private class/issuer, exact DataSource/actual holder, exact registry gate/B03 access, operation purpose and complete graph before use.

`adopt` calls configured access.requirePrepared on the supplied authentic B03 handle, exact actor/meeting/required identity coverage. It creates one registry Gate without access.lock. For production import the complete required identity is the current owner/actor; no segment author or DTO identity is relevant. It rejects an existing gate on the holder. This is for queue composition after its identity/subject preparation; no late identities are acquired.

`prelockSources` supplies DB-only metadata, no business byte/binding authority. Its private receipt stores registry issuer, source/holder, gate identity, exact complete immutable file set, original sessions, files and phase. Empty/duplicate/oversized lists fail. No set expansion after issuance. Discover originals unlocked, lock all original session UUIDs sorted, recheck unique matching COMPLETED association and equality, then lock all file UUIDs sorted. Each uniqueness query has LIMIT 2 (reject multiple); do not materialize an unbounded corrupt association set. Source/session skip-locked applies to every row; partial acquisition returns empty and caller ends the short transaction without effects.

Track sources-acquired separately from binding-consumed. Existing bindAll(bindings) obtains a source receipt then delegates to the shared private binding implementation. New bindAll(sources,bindings) consumes the same-holder complete receipt after new FK rows have been inserted. A second handoff/expanded set/different gate fails. Existing repeated single-bind rejection remains.

Validate AVAILABLE, same non-null meeting, unique original COMPLETED session, exact original UUID/purpose/backend/namespace/key/size/non-null SHA, upload epoch == current meeting cancel_epoch, supported location/reference counters, valid configured managed backend identity and actual backend type/status/revision. `backends.require(id,false)` performs metadata checking only. READ_ONLY preserves completed immutable objects; DISABLED/missing/mismatched historical route fails, no default fallback. First binding also calls installed purpose provider and requires literal TRANSCRIPT_INPUT. Optional client upload UUID must equal the derived one.

Before handoff compare exact draft observations including locationRevision/referenceEpoch/session/route. After handoff referenceEpoch legitimately changes. Keep original-epoch checks for current and history bytes. Route/provider tokens/keys remain internal.

## Genuine held/uncommitted processing facts

```java
// Opaque adapter interfaces; actual implementing classes private inside authority
interface ReservedIntake extends FileRegistry.ReferenceContext {}
interface HeldProcessingFacts {}
ReservedIntake reserveIntake(FileRegistry.LockedMeeting gate, PreparedIntake prepared);
HeldProcessingFacts insertAndReadNew(ReservedIntake reservation,
                                    FileRegistry.LockedSources sources);
void requireHeld(HeldProcessingFacts held, FileRegistry.LockedMeeting gate,
                 TaskSpec exactSpec);
StoredProvenanceFacts detached(HeldProcessingFacts held);
```

PreparedIntake is final/private-constructor, produced by the actual preparer from bounded detached draft facts and verified archive computation outside roots. It carries exact intended rows, fresh server IDs, original metadata observations, target fence, canonical manifest bytes and full spec. It is still preparation, not authority. The preparer consumes the existing ControlledConfigArchiveSource Snapshot/ConfigBindings APIs, not a new provider or substitute hash.

Private lifecycle: RESERVED -> SOURCES_HELD -> INSERTED_AND_READ -> BOUND -> TASK_ADOPTED. Store issuer/source/actual holder, registry gate, exact B03 handle/actor/meeting, old and target fences and complete ordered graph. Reject repeated/wrong-purpose use; no thread-local authority cache or global current-token map.

reserveIntake verifies genuine registry.access, fresh owner EDIT, writable state, exact r/e CAS, complete list and all existing domain locks. Server batch/input UUIDs must be absent. Contextual registry prelock can reserve proofs before those new owner rows exist, but validate/bind/read must reject until actual insertion and sealing. Reservation alone cannot authorize binding.

insertAndReadNew first authenticates the registry source receipt and full exact equality to prepared observations, then inserts new batch/profile/members/input with reserved IDs/values (one affected row each). Existing ID conflicts abort; never adopt old rows. It reads actual same-holder uncommitted batch/member/input/profile rows and checks full bounded equality, complete membership/order, and explicit complete-empty own producer/output sets. Only then issue HeldProcessingFacts and seal the first-bind context. This is real DB state, not readBatch composed into a transaction, not a DTO constructor and not a fake persisted confirmation.

All existing domain prelocking has ended before sources. Only these genuinely new rows are inserted/verified after sources, with their file FKs already held. No existing root/version/input may be discovered then. Tests distinguish transaction-created row access from forbidden late existing-domain locks.

Detached view is data only; authority remains in private holder-bound token. Sidecar profile is a separate domain observation: preserve existing INPUT_BATCH FullRow/hash shape. Missing/unknown profile fails closed; do not infer from hash/date/recomputation/historical receipts. Runtime depends on reviewed/applied 013.1; no DDL/backfill or default profile on missing rows/tables.

Apply file/row/edge/depth/string/aggregate/cancellation bounds before allocation. Measure UTF8/scalar/UTF16 lengths before LONGTEXT transfer; bounded character streaming verifies exact lengths. Source checks need not read upload/provider tokens, provider versions or names. Discovery names get separate bounded acquisition. Locked recheck uses bounded exact equality, not archive I/O/strict JSON parsing/canonical or whole-body hashing.

## Distinct reference operations

Register INPUT_BATCH_FILE only with the production policy. ownerId is batch UUID because members have no UUID. Purpose comes from original upload_session, never an invented member purpose column.

| Private context | Issue and use |
|---|---|
| First bind | Owner EDIT reserve sealed by actual insertAndReadNew; new batch/profile/members/input, exact target fence and complete originals; no existing transcript required; never grants byte reads |
| Current effective | Fresh READ; current transcript_revision_id -> same-meeting WORKING -> exact input/batch/profile and complete members; valid current roster grant for members; pending/RAW/CANDIDATE do not qualify |
| Owner revision history | Fresh owner EDIT; explicit exact same-meeting RAW/WORKING revision with complete original chain; never substitute current input; no business-version rebasing; original epoch still required for bytes |
| Worker fixed input | Fresh current owner EDIT; full actual input/batch/profile/members; real task lease validated only after all source locks; strict current business/cancel fence and writable lifecycle |

RAW history validates actual producer relation. WORKING history traverses bounded complete parent chain to original RAW, validates same meeting/fixed input/fingerprint and actual producer. Detect cycles/duplicate/ambiguous edges. Required producer tasks are locked after sources in a complete sorted set. Do not infer support for arbitrary CANDIDATE history from schema enum alone.

Existing context-free `prelock` remains CURRENT_EFFECTIVE only. Owner/file UUID possession cannot select history/worker/first bind. New contextual overload accepts only genuine private B06 contexts.

Generic download stays current-effective. To respect complete batch ordering, after existing current-reference domain prelocks collect `readMembers` from each valid policy domain: singleton default for old policies; all members of the actual current batch for B06. Prelock the complete domain union before sources, then lock the union of original sessions/files once and validate actual exact references. Requested file alone is opened. No source locks inside a per-reference loop. Current B06 policy rejects obsolete references before expansion. Unexpected exceptions are not swallowed; existing NotFound reference fallback is preserved. After source/reference validation, generic download also invokes finishRead once per distinct authentic private domain scope, including any previously discovered producer-task checks; it cannot skip those checks merely because it used the generic entry. The expansion cannot create history selectors or late domain discovery.

## Worker source read / internal post-commit pin

```java
interface ProcessingSourceReader {
  FileRegistry.Download open(TaskLease lease, String inputId, String fileId);
}
record WorkerSourceRequest(TaskLease lease, String inputId, String fileId)
    implements FileRegistry.ReadRequest { /* kind INPUT_BATCH_FILE */ }
record OwnerRevisionSourceRequest(String revisionId, String fileId)
    implements FileRegistry.ReadRequest { /* kind INPUT_BATCH_FILE */ }
```

Requests are selectors/claims only. JdbcProcessingSourceReader rejects ambient transactions; bounded unlocked actual task/subject/input/meeting-owner discovery chooses the actor for fresh gate acquisition. Actor.status/roles are untrusted. Owner/discovery changes are rejected under root, never followed by late identity acquisition. Compute the expected production producer key from the discovered full spec before acquiring the registry gate; bind that observation into the private read scope and compare the actual locked spec/key later. Never move canonical producer hashing into finishRead.

Registry owns the complete standalone read:

```text
reject ambient transaction; find exact registered policy, fail unknown
pinned = tx.required {
  gate = lock(actor, meeting, READ)                 // one B04/B03 gate
  plan = policy.prelockRead(gate, meeting, request) // checks EDIT for worker/history
  proofs = contextual prelock for every complete plan member
  src = prelockSources(gate, all file IDs, false)
  validate every exact reference, original purpose and file using private proofs
  policy.finishRead(plan.context, sources(src))    // task locks now; no roots
  verify requested file belongs to this exact set
  return PRIVATE local Pinned(file/key/range/route)
} // successful return means commit; exceptions cannot yield pinned
backend = backends.require(pinned.backendId, false)
body = backend.storage.read(pinned.key, validatedRange)
return Download(internal file record, body, offset, length)
```

Pinned type/opener remain private in JdbcFileRegistry. There is no public pin return/open method, no receipt opened before commit, no callback authorizing reads and no additional transaction synchronization/final-flush hook. Extra route check before open catches disabled/mismatched backend. Storage open occurs with no DB transaction. Immutable historical key is pinned, never current-default location. B04's one-shot stream semantics remain: revocation after commit is not periodic stream revocation (later D06). This slice adds no location move/deletion; pinned immutable locations remain retained. Completion independently rechecks after parsing.

finishRead for worker locks the exact durable_task, then actual task_attempt; any additional producer tasks are part of a complete sorted task set. Check actual full TaskSpec equality including type/pool/subject/key/input/fingerprint/businessVersion/priority/cancelEpoch/maxAttempts; task ID/input/meeting equals prelocked graph. Require RUNNING, matching attempt/owner, actual expires_at > UTC_TIMESTAMP(6), matching unfinished task_attempt. TaskLease.expiresAt cannot extend authority and need not equal renewed expiry. Require strict current subject/meeting revision and cancel epoch, not deleted, ACTIVE lifecycle/governance, never-published. Validate complete same-meeting input/batch/member/profile/reference provenance and exact production grammar. Forged DTOs fail; possession does not qualify.

Do not call queue.validLease here: it reacquires identity/domain locks. A small shared DB-only row predicate is possible; queue acquisition is not. No renew/complete/external side effects in source authorization, no business-stale retention relaxation or initial-send permission.

Worker first reads original stored metadata and archive closure/ConfigBindings outside roots, then acquires files by immutable ordinal. Each open checks complete batch metadata/reference set but opens only requested stream. Aggregate byte/text/segment budgets span files; hash actual bytes outside locks and compare actual size/SHA to pinned completed metadata; close every stream. Subsequent acquisitions fail after lease/revocation changes. Prepared ordinary completion rechecks actual authority after parsing.

## Atomic intake and production queue policy

Exact production spec:

```text
TRANSCRIPT_IMPORT_V1 / TRANSCRIPT_IMPORT
MEETING:meetingId / TRANSCRIPTION_INPUT:inputId
fingerprint = exact ConfigBindings processing result
businessVersion r+1, cancelEpoch e, priority 0, maxAttempts 3
producer = canonical SHA envelope
 profile ama.transcript-import-producer.v1, kind TASK_PRODUCER,
 data {taskType,meetingId,inputId,inputFingerprint,businessVersion,cancelEpoch}
```

Use adopted canonical codec and independent golden vector. Never older IMPORT_TXT/TRANSCRIPT or TEST names. Producer hashing runs before identity/root locks. Ordinary queue preparation can compute bounded expected producer from the full spec before identity acquisition; no archive I/O/manifest parsing in hooks. Queue checks persisted normalized immutable provenance; worker independently verifies actual original archive closure before bytes/output. No supplied S1 DTO replaces persistence authorization.

`ProcessingIntakeFacts.readDraft(actor,meetingId,orderedFileIds,budget,cancelled)` owns a standalone transaction: one EDIT registry gate, all original sessions/files, bounded metadata/routes/fence; no business-byte permission, new batch/task or durable accept. Outside transaction allocate server IDs/timestamp; compute v1 batch and exact IMPORT/TXT/EMPTY target r+1/e manifest/config closure/processing/producer digest. No own stored input/task/output placeholders are passed to draft computation.

```text
normalize exact revision/token and semantic request digest outside locks
transaction {
 idempotency serialization before identity
 gate = registry.lock(actor,meeting,EDIT)
 matching replay reauthorizes and returns stored task; no bind
 retain already-held subject handle at this initial root stage
 reserved = authority.reserveIntake(gate,prepared) // CAS r/e, existing domain complete
 proofs = registry.prelock(gate,each ref/file,reserved)
 sources = registry.prelockSources(gate,all files,false)
 held = authority.insertAndReadNew(reserved,sources) // brand-new FK graph only
 registry.bindAll(sources,all exact original upload/location bindings) // once
 verify actual references; mark private held BOUND
 advance meeting/subject once to r+1/e; clear transcript/final confirmations
 set needs_generation; preserve previous saved transcript pointer
 adopted = importPolicy.adoptCreated(registry.access(gate),held)
 taskId = queue.enqueue(exactSpec,adopted)
 idempotency result + audit/events; one shared outbox.flush LAST
}
ACK only after commit
```

For fences.advance retain a real subject handle by calling the existing fences.lock immediately after registry.lock while still in root stage (re-lock already-held subject is allowed). Do not add an invented B03 getter or discover subject after files. Queue's later subject re-lock is the same already-held row.

`ImportTaskDomainPolicy.adoptCreated(MeetingAccess.Locked,HeldProcessingFacts)` verifies its configured private issuer/source/holder, exact gate, actual newly inserted graph/profile, actual BOUND refs and updated target fence. requirePrepared checks exact owner/meeting/complete identity coverage. Only then issue private JdbcTaskPreparations capability for exact singleton full spec, TASK_MUTATION and private adopted scope. true effectsAllowed is a consequence, not caller evidence. lockRoots recognizes adopted scope and rechecks already-held facts; no second prepare/identity/root/source discovery.

Ordinary `prepare(fullSpecs,purpose,skip)` supports production/common meeting and TASK_MUTATION only; rejects unsupported external observation. Discover current owner before identity, compute expected canonical producer, lock complete current-owner identity union, issue preparation. lockRoots uses access.lockPrepared once, validates discovery/current owner, then registry.adopt of that exact B03 gate. Acquire complete union of existing input/batch/profile/member/revision domain then all sessions/files before queue task locks. Override default per-spec lockRoots: overlapping batches cannot interleave domain/files. Complete specs and unique issuer are checked on every preparation consumption.

validateInput/validateOutput use deterministic bounded DB-only checks of already-held immutable graph/new output rows. Current interface does not pass preparation to validateInput: do not add ThreadLocal authorization to compensate. All acquisition hooks separately validate authentic preparations; checking DB rows inside already-established queue scope is adequate. Output validation requires actual RAW/task/input/fingerprint and complete prepared segment provenance; exact writer/check belongs Task 5.

Any insert/bind/enqueue/audit/flush failure rolls back all new graph/profile rows, refs/referenceEpoch, fences/pointers/debt, task/idempotency/outbox. Completed upload receipt replay remains available independently of business-readable eligibility. No invented current-input column.

## Lock graph / ordering decision

```text
idempotency(write) -> identities(unsigned UTF8) -> subject + meeting + READ grants
 -> complete EXISTING domain graph (table rank, UUID/key order)
 -> ALL original upload sessions sorted UUID
 -> ALL file objects sorted UUID
 -> own NEW intake graph INSERT/read (FKs already held), if applicable
 -> durable tasks sorted UUID -> attempts
 -> external operation/execution (unused here)
 -> audit/outbox -> one flush -> commit -> storage/archive/parser/network I/O
```

Recommend one B06 table rank for every operation: complete transcript_revision/parent closure by UUID, transcription_input UUID, input_batch UUID, profile batch UUID, members(batch UUID,ordinal). Discover bounded parent closure before locks; under meeting root recheck full discovered set. Reject additions/ambiguous chains rather than acquire new locks late. Root serializes supported writers; immutable rows do not change behind it. Initial intake has no existing batch/input/revision rows; reserve phase is not counterfeit row locking. New rows after sources are explicitly permitted, existing late roots are forbidden. No new locks after tasks or flush; repeat access to held rows is distinguished from acquiring new old rows.

## Negative/positive tests required (not run)

1. First actual completed uploads, no transcript/current pointer: real new profile/input + one bindAll + production enqueue succeeds, no fake RAW/confirmation. Pending owner/member generic download denied.
2. Fake interfaces, different issuer/registry/DataSource wrapper, other/ended/suspended holder, wrong meeting/batch/input/order/purpose, reserve-before-insert, detached DTO, replayed seal/handoff all reject before effects.
3. Late domain prelock, second/expanded sources, repeated bind, subset/extra/duplicate members, multiple original COMPLETED sessions, wrong UUID/key/size/SHA/epoch/locationRevision/state fail. READ_ONLY positive; disabled/missing/mismatched route negative.
4. Pre-transfer huge text/manifest rejection, supplementary UTF16 accounting, rows/edges/files/parent depth/aggregate/cancellation failure; never truncate. Existing standalone ProcessingFactsReader ambient rejection unchanged.
5. Actual effective member WORKING read succeeds; member RAW/history/pending/foreign fails; owner same-epoch explicit RAW/WORKING history succeeds; generic obsolete-only reference fails. History bytes after cancel fail though metadata history may remain readable. Admin alone denied.
6. Forged task ID/full spec/type/pool/key/input/fingerprint/priority/maxAttempts/attempt/owner; READY/SUCCEEDED/STALE/expired rows fail. Future DTO expiry cannot rescue; actual renewal succeeds with old DTO expiry. Owner/status/governance/publication/cancel/business changes fail. No browser download or marker/renew invoked.
7. SQL trace reverse file/session order, multi-batch unions, all domains before sessions before files before tasks, exactly one gate, FK member INSERT after explicit sources. Skip-locked partial ends transaction. Adoption skips access.lock/prepare. Test fake late root acquisition rejected.
8. Storage spy: no DB transaction on open/read; failed task check/commit never opens. Exact historical route/key; default change irrelevant; disabled before open fails; range/disconnect closes stream. No public transferable pin.
9. Failure at profile/member/input/second bind/enqueue/audit/flush rolls back exact complete DB snapshot, protective upload refs/business refs/referenceEpoch, fences/pointers/debt, task/idempotency/outbox. Wrong later member purpose rolls earlier changes back.
10. Draft then fence/source/route/session/reference change: final recheck leaves no rows/handoff. Same semantic idempotency returns original task after reauthorization; reordered/changed request conflicts. Exact legacy/token/dual >2^53 normalization before digest.
11. Missing/unknown profile never inferred; new rows have sidecar; old unchanged. Fresh JVM resumes exact original input/config/task/route, never new defaults.

Inherited targets: FileRegistryUploadIT binding/foreign/late/location/effective-member cases; ProcessingFactsReaderIT; BatchFingerprintTest; B06QueueSeamsIT; affected FileRegistryRestartIT and ConfigArchiveRestartIT. Task 4 supplies real HTTP/no-store bounded discovery and intake; Task 5 real local/S3 bytes/WorkerLoop/parser/restart. No tests run by this design task.

## Remaining bounded review decisions

No new product decision. Root should independently review additive registry adopt/source/read-plan seams and fixed domain rank/new-row sealing before Task 3 authorship. These are bounded B04/B06 integration costs; they do not justify generic history download, callback authority, profile inference or fence relaxation. ReadPlan/readMembers may be nested adapter types but must preserve issuer/full-set semantics.

Task 4 must freeze actual owner upload-session/accepted receipt/completed-file/ordered input/active task endpoint paths, safe no-store shapes and pagination before UI. Task 6 freezes explicit current/history source routes and full saved graph. This design does not assert those APIs exist, qualify actual PENDING corrections, enable external workers, or claim Phase B complete.

