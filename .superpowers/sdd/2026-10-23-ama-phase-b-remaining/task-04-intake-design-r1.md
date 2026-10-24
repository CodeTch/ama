# Task 4 R1 — processing intake, registered policy and authoritative HTTP design

2026-10-23. DESIGN ONLY R1; complete replacement design for bounded re-review. Original design SHA256 3626aad723401de14d633d28a200138aa2cdeb233fc44ef961c348164296a574 and independent review SHA256 37b9a2c422a2af686f12511e39414260dc1741a7a5f910f42a70dac72df52279 were read and independently hashed; both remain unchanged. R1 changes only mandatory accepted replay, upload action/retry discovery, exact manifest profile-ref spelling and their required tests. This R1 and its separate closure record are the only new artifacts. No implementation, tests, Git, DB, Docker, private environment mutations or new DDL. Reviewed Task 3 original + R1 contracts and R1 review remain binding. Coordinator reports Task 3 author active at frozen base `3e393db`; changing implementation has not been inspected. Names below are provisional until aligned with adopted Task 3 source.

## 1. Sources and boundaries

Read controlled-runtime spec including all Normative R1; remaining-plan Task 4/global/shared constraints; task-03-authority-design.md, task-03-authority-design-r1.md and its review. R1 replaces original sources(held) and optional protocol defaults: use sources(expectedGate,receipt,exactFileIds) and mandatory CompleteReadPolicy. All tasks precede any attempt. No contradiction found. Root compatibility ruling: unchanged generic legacy registries/tests may retain old two-method INPUT_BATCH_FILE providers; reject legacy bypass in NEW production assembly and NEW planned-read paths, verifying exact complete provider instance.

Actual source anchors (checkout-relative paths and methods):

| Source | Existing behavior |
|---|---|
| backend/api/src/main/java/ama/api/MeetingController.java: save/confirm/revision; backend/contracts/src/main/java/ama/contract/MeetingContracts.java | Integer-only HTTP CAS; supported request domain 0..9223372036854775806; Saved revision/accessRevision. |
| backend/adapters/src/main/java/ama/meeting/MeetingDigests.java; JdbcMeetingRepository.java: claim/checkReplay/restore/complete/finish, existing save/confirm | Versioned length-prefixed semantic digest, actor/resource/operation/key serialization, reauthorized replay, result UUID, final flush. Creator/schema work excluded. |
| backend/api/src/main/java/ama/api/FileController.java; backend/adapters/src/main/java/ama/storage/JdbcFileUploads.java: begin/acceptedReplay/complete/manifest | Real uploads/parts/completion/abort; UPLOAD_BEGIN result_id persists accepted session. Completion session-idempotent. partSize is CURRENT suggestion, not persisted receipt data. No discovery APIs yet. |
| backend/adapters/src/main/java/ama/storage/JdbcFileRegistry.java; backend/core/src/main/java/ama/access/MeetingAccess.java | B04 source/reference/route authority and B03 identity/root/ACL, extended only through reviewed Task 3 contracts. |
| backend/core/src/main/java/ama/task/TaskDomainPolicy.java; backend/adapters/src/main/java/ama/task/JdbcTaskPreparations.java, JdbcTaskQueue.java: prepareDomain/lockDomain/enqueueLocked/work | Authentic full-spec preparation/adoption, common-policy/common-subject batching, full producer collision check; nested queue leaves flush to outer transaction. |
| backend/core/src/main/java/ama/transcript/BatchFingerprint.java, ProcessingFingerprint.java; backend/core/src/main/java/ama/config/ConfigBindings.java: computeProcessing/verifyProcessing | Explicit batch v1 and distinct draft/stored verification, pure facts never authority. |
| backend/core/src/main/java/ama/config/ConfigSchema.java, ConfigArchiveSource.java; backend/adapters/src/main/java/ama/config/file/ControlledConfigArchiveSource.java; backend/adapters/src/main/java/ama/config/json/StrictConfigArchiveReader.java, StrictProcessingManifestReader.java | Exact pinned immutable archives, known IMPORT/TXT/EMPTY manifest, no latest fallback. |
| backend/adapters/src/main/java/ama/transcript/TextTranscriptParser.java; backend/core/src/main/java/ama/transcript/TranscriptDocumentParser.java | Actual ama.strict-utf8-lines.v1, strict UTF8/hash/EOF/lines with byte/UTF16/segment limits. Task 5 parsing. |
| backend/api/src/main/java/ama/api/MeetingConfiguration.java, FileConfiguration.java; backend/adapters/src/main/java/ama/meeting/MeetingEventReferences.java; backend/adapters/src/main/java/ama/task/JdbcOutbox.java | One B03 issuer; registry requires policy map; outbox presently registers only MEETING_INFO/MEETING_ROSTER. |
| backend/api/src/main/java/ama/api/SessionSecurityFilter.java, BusinessErrors.java, FileErrors.java, MeetingErrors.java | Fresh sessions, unsafe-method Origin/CSRF, no-store, safe error envelope. |
| backend/adapters/src/main/resources/db/migration/V009__file_schema.sql, V010__reliable_execution.sql, V011__transcript_schema.sql | Existing upload/part/file/input/typed task persistence; no current-input or accepted-part-size column. |
| contracts/openapi/api.yaml, meetings.yaml, files.yaml; web/src/shared/api/generated.ts, client.ts | Existing split schemas and browser contract location. |

Task 3 owns private reserved/held issuance, original-source receipt checks, actual new-row insert/read/seal and byte-read eligibility. Task 4 owns preparation, intake/CAS/idempotency/enqueue/audit/events, policy/config registration, HTTP CAS helper + actual info/roster token extension, these discovery endpoints and OpenAPI. Task 5 owns worker/parser/RAW writer; Task 6 saved transcript/confirmation/source APIs; Task 12 browser/resume. Accepted intake is actual input+task, never fake RAW/WORKING/confirmation.

## 2. Proposed signatures and exact task identity

```java
// Data-only contracts, never authority
record ImportRequest(long expectedRevision, List<String> orderedFileIds) {}
record ImportAccepted(String meetingId, String batchId, String inputId,
                      String taskId, long revision, String revisionToken,
                      String inputFingerprint) {}
interface ProcessingIntake {
  ImportAccepted accept(Actor actor, String meetingId, String idempotencyKey,
                        ImportRequest request, String traceId);
}
// Standalone/outside transaction; uses Task 3 final/private-constructor shape.
PreparedIntake ProcessingIntakePreparer.prepare(
    Actor actor, String meetingId, ImportRequest request, BooleanSupplier cancelled);
// Production TaskDomainPolicy; verifies genuine Task 3 actual held capability.
PreparedDomain ImportTaskDomainPolicy.adoptCreated(
    MeetingAccess.Locked access, HeldProcessingFacts actual);
long HttpRevisionTokens.expected(JsonNode body); // integer/token, at least one
String HttpRevisionTokens.token(long value);
interface ProcessingDiscovery {
  ProcessingCapabilities capabilities(Actor actor, String meetingId);
  Page<UploadSummary> uploads(Actor actor, String meetingId, PageRequest page);
  UploadReceipt upload(Actor actor, String meetingId, String uploadId);
  Page<PartSummary> parts(Actor actor, String meetingId, String uploadId, PartPage page);
  Page<CompletedFileSummary> completedFiles(Actor actor, String meetingId, PageRequest page);
  Page<InputSummary> inputs(Actor actor, String meetingId, PageRequest page);
  InputDetail input(Actor actor, String meetingId, String inputId);
  Page<ImportTaskSummary> tasks(Actor actor, String meetingId, TaskPage page);
  ImportTaskSummary task(Actor actor, String meetingId, String taskId);
}
```

Register EXACTLY TRANSCRIPT_IMPORT_V1 / TRANSCRIPT_IMPORT, MEETING:meetingId, TRANSCRIPTION_INPUT:inputId, priority 0/maxAttempts 3, businessVersion r+1, unchanged cancelEpoch e. Fingerprint is ConfigBindings processing result, not batch hash. TranscriptImportProducer uses adopted CanonicalValueCodec SHA envelope profile ama.transcript-import-producer.v1, kind TASK_PRODUCER, data {taskType,meetingId,inputId,inputFingerprint,businessVersion,cancelEpoch}. Bound/cancel/hash before identities/roots; independent golden vector; full TaskSpec comparison even on producerKey match.

Keep separate existing wire identities: manifest step IMPORT_TXT, batch TRANSCRIPT, input/manifest IMPORT are required by ConfigSchema and are NOT queue type/pool substitutes. No TEST keys.

## 3. Bounded preparation outside final transaction

1. Stream-bound request before buffering using Content-Length and sentinel for unknown length; strict duplicate keys/trailing JSON/unknown fields. Bound depth/tokens/strings/array before list allocation. Require canonical lowercase UUIDs, unique nonempty orderedFileIds. Normalize revision before semantic digest, never via double. Exactly one nonblank valid-Unicode Idempotency-Key; proposed technical ceiling 256 UTF8 bytes.
2. Service rejects ambient transaction. Digest transcript-import-request-v1 covers normalized expected revision, file count and exact order/IDs. Scope actor+meeting+PROCESSING_IMPORT+key uses length-prefixed SHA semantics. No client actor/config/manifest/fingerprint/batch/task/epoch claims.
3. MANDATORY accepted-result lookup runs after safe wire parsing/normalization/digest and BEFORE any current-CAS, exhaustion, source, current-archive or intake-readiness prerequisite. Its standalone transaction locks an existing idempotency row FIRST, freshly authorizes the actor as current owner through B03 EDIT, checks exact actor/meeting/operation/key/body scope and restores only actual committed task/input/batch acceptance. No speculative success insert or client-memory result. A miss ends that transaction before preparation. A bounded mandatory second lookup after a recoverable preparation failure handles a same-key concurrent winner; section 12 defines exact outcomes, ordering and non-speculation rules. Final serialized claim still checks replay before all new-intake predicates.
4. Task 3 ProcessingIntakeFacts.readDraft(actor,meeting,orderedFileIds,budget,cancelled) standalone: genuine EDIT registry gate, bounded complete original sessions/files/routes and current r/e, no business byte read/durable accept. Require expected r, subject r, ACTIVE lifecycle/governance and never-published. Guard exhaustion before r+1. Allocate fresh server batch/input UUIDs and creation timestamp; no existing-transcript placeholder.
5. Prospective batch/member rows and ContentFile facts are explicit draft prerequisites for pure computation. BatchFingerprint.compute(meeting,batch,TRANSCRIPT,r+1,members,...) freezes ama.input-batch.v1 sidecar. Both batch.base_revision and input.base_revision are r+1, old r only CAS preparation. Members remain original ordinals 1..N/SHA/size. No sidecar inference.
6. Open configured exact root/index SHA through ControlledConfigArchiveSource with IndexBudget/ArchiveReadBudget. Select exact deployment PROCESSING_CONFIG Ref(type/version/SHA), never highest/latest. Load complete four-artifact closure with manifest-byte reservation: PROCESSING_CONFIG schema ama.import-txt-config.v1/contract ama.import-txt.v1; PARSER_CONFIG ama.strict-utf8-lines.v1; CANONICAL_CODEC ama.canonical-value.v1; CONFIRMATION_HASH ama.transcript-confirmation.v1. Existing readers/ConfigBindings validate raw SHA, canonical JSON, exact refs/edges/slots/closure. Fixture VERSION is not a production default.
7. Build exact canonical manifest: schemaVersion1/profile ama.transcription-input.v1; meetingId/inputId; kind IMPORT; batch {id,kind:TRANSCRIPT,files:[{ordinal,fileId,sha256,sizeBytes}]}; sourceRevisionId:null; materials {mode:EMPTY,items:[]}; configs {processing,parser,model:null,prompt:null,profiles:[{kind:CANONICAL_CODEC,ref:codecRef},{kind:CONFIRMATION_HASH,ref:confirmationRef}]}; base {meetingRevision:r+1,subjectBusinessVersion:r+1,cancelEpoch:e}; one step {ordinal:1,kind:IMPORT_TXT,parentInput:null,priorOutput:null,source:{kind:INPUT_BATCH,id:batchId},config:processing,parser,model:null,prompt:null}. Each Ref is an object with artifactType/version/sha256; profile entries are the ordered kind/ref objects above, never bare strings or bare refs. codecRef and confirmationRef are exact existing archive references, not literal serialized variable names. computeProcessing receives draft prerequisites and no fabricated own input/task/output rows. Stored verification requires real existing producer and is not draft computation.
8. Freeze exact bytes/refs/binding/fingerprint/intended rows/observations/spec+producer in PreparedIntake; still no authority. Archive I/O/strict parse/canonical hashes/digests all outside roots. Final lock work compares bounded exact values. Transcript bytes parse only in Task 5; completed metadata does not prove valid TXT content.

Proposed explicit technical defaults: request64KiB, max64 files, JSON depth8/tokens4096; manifest<=64KiB and existing archive hard bounds; discovery response<=1MiB; name<=64KiB UTF8 and64Ki UTF16 each/256KiB aggregate; page default20/max50, part page default100/max500. Publish effective minima with ArchiveReadBudget/CanonicalBudget/IndexBudget/Task3 bounds. Positive validated settings are deployment capabilities, not product quotas. Count/measure/checked sums before arrays/maps/strings, supplementary Unicode and complete graph rows/edges/depth; preserve cancellation/interruption. Per-file bytes obey archived parse.maxSourceBytes; explicit configured aggregate intake bytes and Task5 aggregate budget also apply. No truncation or first-N selection.

## 4. Atomic transaction/replay

```
claim idempotency FIRST
 -> one registry.lock(actor,meeting,EDIT), retain registry.access(gate)
 -> matching committed replay: reauthorize/restore; no new handoff
 -> retain fences.lock(MEETING:meeting,false) at initial root phase
 -> authority.reserveIntake(gate,prepared): exact owner/r/e/complete graph
 -> contextual prelock each INPUT_BATCH_FILE:batch / TRANSCRIPT_INPUT
 -> receipt=registry.prelockSources(gate,complete files,false)
 -> authority.insertAndReadNew(reservation,receipt):
      sources(gate,receipt,exact files); original observation/order equality;
      actual new batch/profile/member/input insert, same-holder read/seal
 -> registry.bindAll(receipt,complete bindings) ONCE; consumed
 -> authority verifies actual BOUND refs/phase
 -> meeting revision r+1, clear transcript_confirmation_id/final_confirmation_id,
      needs_generation=1; preserve transcript_revision_id/saved minutes pointers
 -> fences.advance(retainedSubject,r+1,e,false); search projection revision
 -> importPolicy.adoptCreated(same genuine B03,actual held graph)
 -> queue.enqueue(exact frozen spec,authentic preparation)
 -> idempotency result_id=taskId/http_status202; audit/domain event
 -> one shared outbox.flush LAST -> commit -> ACK
```

Task3 alone seals actual rows/BOUND phase; Task4 calls its genuine checks. Detached DTO, PreparedIntake or ValidatedProcessingBinding grants no authority. Queue re-locks only already-held subject; no second identity/gate/source pass; nested queue does not flush. No SQL after flush; no I/O/parse/hash under roots. Existing roots complete before sources; only reserved new intake rows insert/read afterward with explicit source FKs held. Source/owner/fence/route/location/reference changes abort, never adapt preparation. Exact DataSource actual transaction holder, not URL equality.

Rollback covers graph/profile, references/protective upload handoff/referenceEpoch, meeting/subject/search projection, confirmations/debt/pointers, task/idempotency/audit/outbox. Failed/consumed receipt cannot reset. Build result from verified actual IDs before flush, expose only after successful commit.

Replay follows existing mutation semantics: exact same key/body reauthorizes and returns original task/input/batch/accepted revision even after later valid meeting changes. No old-CAS check against today's revision after success, no archive reselection/bind/enqueue. Changed body/order/revision same key is409. Failed transaction leaves no accepted record; retry fresh preflight/CAS. New key means explicit new intake, not revival. First/replay ACK202; response revision is accepted original, GET reports current. Uncertain ACK retries same key/body; no automatic new key on400/403/409.

## 5. Registered production policy/events

Policy owns one private JdbcTaskPreparations issuer and exact authority/B03/registry/DataSource. adoptCreated verifies Task3 private issuer/actual holder/gate, complete graph/profile/BOUND refs, updated target fence and owner EDIT; then issues singleton TASK_MUTATION full-spec scope. Public interface implementations or effectsAllowed=true are insufficient; no ThreadLocal/global token map/caller boolean/DTO authority.

Ordinary prepare supports bounded fullSpecs for this type/common meeting and TASK_MUTATION only. Before identities, discover current owner/complete bounded associations and compute expected producer from full spec. Public status-only tryLockIdentities locks complete canonical union. Under root reject changed owner without late replacement identity. lockRoots uses one lockPrepared then registry.adopt, complete R1 domain union/table rank, all original sessions sorted, all files sorted, then queue tasks. Override per-spec looping; overlapping batches cannot interleave domains/sources. Skip-locked partial ends candidate transaction. Adopted scope rechecks already-held facts without another prepare/gate/acquisition. Context-free lockRoot/external request/observation methods fail closed.

validateInput checks actual immutable IMPORT/EMPTY columns, same-meeting batch/profile/complete members, exact fingerprint/base/cancel/spec/producer. Initial intake froze/authenticated bytes; ordinary queue hooks use bounded immutable persisted provenance and authentic acquisition, with no archive I/O/strict manifest parse. Task5 independently verifies historical actual manifest/archive bytes before processing. Interface validateInput has no preparation parameter: no cached authority workaround; acquisition checks capability, DB validation runs inside established scope.

Install real typed output validation: SUCCEEDED requires TRANSCRIPT_REVISION pointing to actual same-meeting RAW, exact processing_input_id/batch/fingerprint and actual producer_task_id of matching immutable production task. Validate bounded complete segments/ordinals/source membership/marks/provenance without new existing roots after tasks. Task5 completion-created RAW/segments are its new-row exception and supplies actual prepared writer/result-equality evidence. Non-success cannot carry unvalidated output. Import RECONCILING/external execution unavailable. Missing writer never yields placeholder success.

Add TranscriptEventReferences(DataSource) DB-only TRANSCRIPTION_INPUT validator for exact existing queue TASK_* vocabulary and TRANSCRIPT_IMPORT_ACCEPTED: actual subject/input/batch/base/fingerprint and task relation. Accepted event uses actual taskId and input reference. Validate at append time; do not require terminal state during enqueue. Merge with MeetingEventReferences using duplicate-key rejection, no wildcard/no-op. Audit safe actor/IDs/revision/outcome/trace only, no names/manifest/text. Task5 owns any future output-domain event/validator; no permissive unimplemented TRANSCRIPT_REVISION validator now.

## 6. Cycle-free configuration/readiness

Task3 owns assembly seam, Task4 actual Spring production wiring. One factory with PRIVATE PRE-EXPOSURE attachment (final names aligned to Task3): construct authority+complete policy; construct registry with full immutable map; attach exact registry to authority exactly once via package-private construction mechanism; then publish frozen components. Every operation before attachment fails closed. Null/repeat/foreign attachment rejects. Nothing escapes before source/access/issuer links all validate; failure exposes nothing. No public setter, Lazy/ObjectProvider bean lookup, static registry or second issuer.

Actual FileConfiguration registry bean becomes completed assembly component. Production INPUT_BATCH_FILE is assembled internally, avoiding registry->policy->authority->registry Spring cycle. Preserve legacy kinds via explicit map input/duplicate rejection. NEW production assembly asserts exact CompleteReadPolicy instance for INPUT_BATCH_FILE; unchanged legacy registries/tests remain compatible per root ruling. NEW planned reads never fallback to old policy. Uninstalled import may retain legacy-only registry but advertises no intake capability. Purpose policy is explicit Task3 TranscriptFilePurposePolicy; missing provider still unavailable, not wildcard.

Outbox construction independent: validators depend only on DataSource/fixed DB logic, never queue/service/authority. Merge meeting+transcript validators, construct single JdbcOutbox. Then policy using completed authority/registry/B03; JdbcTaskQueue(source,fences,outbox,Map.of(TRANSCRIPT_IMPORT_V1,policy)); finally service/controller. No authority/reference-policy dependency back to queue/service/outbox. Source reader checks never call queue.validLease. All share exact configured DataSource/B03 objects; equal URLs/wrappers insufficient. Immutable assembly getters expose configured services, not user-supplied authority DTOs.

Explicit readiness: enabled deployment setting, applied Task2 sidecar support, known batch/parser profiles, exact complete registration, verified pinned archive/ref closure/budgets and usable storage metadata route. Missing/unknown unavailable503; no DDL/profile inference/archive publication in requests. Authorized metadata discovery stays useful when archives/storage unavailable, with separate byte/intake eligibility. Task5 supplies actual worker health: execution NOT_INSTALLED until installed. Qualified queue-only tests can expose intake acceptance separately; combined business readiness remains false until actual worker evidence. No worker-installed inference from task count.

Restart opens explicitly configured immutable index/snapshot. Stored manifest exact Ref(version,SHA) resolves historical retained artifacts via append-only releases, never new processing defaults. No new index column. Missing historical Ref fails operation closed. Existing archive restart tests are regression dependencies, not claim of Task5 worker restart.

## 7. Frozen HTTP/discovery contract

Existing session/filter/Origin/CSRF applies. Every success/error Cache-Control: private, no-store. No backend IDs/storage keys/routes/provider versions/tokens/credentials/lease-owner identifiers. Fresh actor/meeting authorization per page/detail; UUID/cursor possession grants nothing. Owner discovery requires actual B03 owner EDIT; published owner metadata remains inspectable under current B03 because never-published is separate write predicate. Inactive/governed follows existing denial. Member/outsider/admin-only denied owner routes. Capabilities READ-authorized, members receive only safe denied write capabilities. Authorized meeting + absent/wrong-meeting item404, denied meeting403.

| Method/path | Result |
|---|---|
| GET /api/v1/meetings/{id}/processing-capabilities |200 actual revision/accessRevision+tokens, canSubmit, readOnlyReason, intake AVAILABLE/UNAVAILABLE, execution INSTALLED/NOT_INSTALLED/UNAVAILABLE, formats[TXT], purpose TRANSCRIPT_INPUT, materialMode EMPTY, finite effective budgets, safe reason codes.|
| POST /api/v1/meetings/{id}/transcript-imports |Exact body {expectedRevision?,expectedRevisionToken?,orderedFileIds:[uuid...]}, Idempotency-Key;202 ImportAccepted, Location input-detail route.|
| GET /api/v1/meetings/{id}/uploads?limit=&after= |Owner persisted accepted upload summaries/all states/bounded UUID page; recover lost begin ACK without remembered IDs.|
| GET /api/v1/meetings/{id}/uploads/{uploadId} |Actual accepted receipt/session, optional completion, current allowed upload actions.|
| GET /api/v1/meetings/{id}/uploads/{uploadId}/parts?limit=&afterPart= |Actual persisted part receipts ascending part number, metadata only.|
| GET /api/v1/meetings/{id}/completed-files?limit=&after= |Actual file metadata tied to unique original COMPLETED session; completion distinct from readability.|
| GET /api/v1/meetings/{id}/processing-inputs?limit=&after= |Immutable accepted input summaries, no invented current-input pointer.|
| GET /api/v1/meetings/{id}/processing-inputs/{inputId} |Complete bounded original ordered association and actual producer task relation.|
| GET /api/v1/meetings/{id}/processing-tasks?scope=active\|all&limit=&after= |Production import tasks; active default=actual READY/RUNNING, all includes terminal.|
| GET /api/v1/meetings/{id}/processing-tasks/{taskId} |Actual typed task state/input/output, no client inference.|

Page shape {items:[...],nextCursor:string|null}; default/max20/50; parts100/500. after last canonical UUID; afterPart canonical nonnegative decimal string. Unknown/invalid query/limit/cursor400. SQL strict greater-than/ascending key/LIMIT limit+1, no OFFSET/count/unbounded Java materialization. Scope change restarts pages. UUID order not snapshot: new smaller IDs appear on refresh, so restart first page after write/restart. Detail graphs complete within explicit bound; no partial page pretending complete association. Configure query timeout; no new index/DDL work assumed for slow filters.

Exact field sets (timestamps UTC ISO8601):

* UploadSummary: uploadId,purpose,displayName,state OPEN|FINALIZING|COMPLETED|ABORTED|FAILED,expectedSize,expectedSizeToken,expectedSha256 nullable,createdAt,updatedAt,completedFileId nullable. Only committed accepted rows; provider orphans not receipts.
* UploadReceipt: summary plus accepted {uploadId,partSize,partSizeToken}, allowedActions[UPLOAD_PARTS|COMPLETE|ABORT],idempotentRetries[COMPLETE|ABORT],blockedReason nullable,completed nullable CompletedFileSummary. Both action arrays are explicit, possibly empty; idempotentRetries describes terminal request replay/cleanup only, not permission to change business/session state. partSize CURRENT suggestion identical to existing begin replay, NOT immutable original chunk size. PartSummary {part,sizeBytes,sizeBytesToken,sha256} comes from actual rows, excludes provider token. Resume uses actual receipt numbers/sizes/hashes. OPEN evaluates each operation separately: parts/new completion require the exact historical WRITABLE route; abort cleanup uses the historical recovery route, which may be READ_ONLY. FINALIZING advertises COMPLETE recovery only, subject to fresh owner/purpose/epoch/lifecycle/publication and historical-route checks; never UPLOAD_PARTS or ABORT. It may complete on a valid historical READ_ONLY route without a writable default. Terminal states have no allowedActions; separately enumerated idempotentRetries follow existing endpoint early-return/cleanup semantics in section 13. No new upload mutation.
* CompletedFileSummary: fileId,uploadId,displayName,sizeBytes,sizeBytesToken,sha256,fileState,originalPurpose,intakeEligibility {state:ELIGIBLE|INELIGIBLE|UNAVAILABLE,reason:null|SOURCE_STATE|SOURCE_EPOCH|SOURCE_PURPOSE|SOURCE_ROUTE|SOURCE_PROVENANCE|READ_ONLY},businessReadEligibility:NOT_EVALUATED. No content URL. ELIGIBLE is fresh metadata assessment, never capability; intake repeats exact checks. Task6/registry solely determines actual current/history bytes. Completed receipt remains discoverable separately from cancelled-byte eligibility.
* InputSummary: inputId,batchId,kind IMPORT,materialMode EMPTY,baseRevision,revisionToken,inputFingerprint,createdAt. InputDetail adds batchProfile,batchFingerprint,cancelEpochToken,files[{ordinal,fileId,uploadId,sha256,sizeBytes,sizeBytesToken}],taskId. Producer/type/input/subject relation identifies actual task; missing/multiple producers unavailable, never latest-task heuristic. Verify complete unique ordinal graph and exact saved provenance.
* ImportTaskSummary: taskId,type,pool,meetingId,inputId,batchId,state,businessVersion,businessVersionToken,cancelEpochToken,attempt,attemptToken,maxAttempts,availableAt,createdAt,errorCode nullable,output nullable {kind:TRANSCRIPT_REVISION,id}. Actual states READY|RUNNING|SUCCEEDED|FAILED|CANCELLED|STALE; import RECONCILING unsupported/corrupt, not mapped READY. ErrorCode allowlisted domain/queue diagnostic or PROCESSING_UNAVAILABLE, never raw exceptions. SUCCEEDED output validates actual RAW relation; does not imply WORKING/save/confirmation. No manual retry route: existing RETRYABLE becomes READY with availableAt; terminal re-import is explicit new CAS intake/input. Same key only recovers ambiguous acceptance.

Discovery is standalone DB-only under fresh B03. Bound candidate IDs/lengths before LONGTEXT; no JdbcFileUploads SELECT * or token-bearing receipt helpers. Explicit safe scalar columns, then pre-transfer UTF8/UTF16 name gates and bounded character stream verification. File eligibility uses genuine Task3 source verification over complete selected bounded union; public CompletedSource list cannot grant permission. Predeclare relevant input domains before sources, tasks after sources where graph validation requires. Metadata-only completed receipt does not require byte permission. Input history preserves original base/config/task, never rebases to current revision; obsolete original epoch may deny bytes while metadata remains visible. Missing/unknown profile/ambiguous originals never ELIGIBLE.

Oversized historical name/page/complete graph503 PROCESSING_READ_BUDGET_EXCEEDED before transfer. Smaller page may reduce aggregate bounds, but single oversized row needs larger qualified deployment budget. No product name cap or silently partial field. Explicit authoritative bounded discovery is required before UI, not deferred into browser memory.

## 8. Lossless HTTP CAS and old endpoint ownership

Task4 owns HttpRevisionTokens and actual MeetingController.save/confirm accepted-field extension expectedRevisionToken. Normalize into existing SaveInfo/ConfirmRoster before digest, preserving MeetingDigests version/order/semantic representation. Integer-only/token-only/equal dual forms replay same old command. Add revisionToken serialized property to Created/Mutation/Saved and accessRevisionToken to Saved using server Long.toString; preserve exact integer fields and constructors via additive accessor/API projection. Update meetings.yaml/new processing/files/root refs now. Task12 regenerates generated.ts and browser token-only requests. Server info/roster extension is NOT deferred to Task12.

Grammar 0|[1-9][0-9]*, ASCII, <=19 chars, range0..9223372036854775806. Token JSON string, legacy integral JSON number exactly convertible to long. Present null invalid; both absent invalid; dual must equal before digest/SQL. Reject signs/leading zeros/Unicode digits/whitespace/fraction/exponent/float/overflow/negative/MAX_VALUE. Check length/lexical bound before conversion. No double or 2^53 DB cap.

Max remains parse-valid/readable; new increment at max409 REVISION_EXHAUSTED/no effects, accepted request replay still succeeds. Task4 owns narrow existing repository writable exhaustion guard AFTER authorized replay handling for info/roster, plus intake guard; no creator edit. Do not reject before replay. max-1 proves successful resulting max and exact replay. New browser-used longs get opaque strings: size/expectedSize/partSize/businessVersion/cancelEpoch/attempt. Bounded ordinal/part/maxAttempts safe integers. Existing upload numeric sizes remain compatible within qualified practical limits; no unrelated upload mutation redesign. expectedAccessRevisionToken is not invented: accessRevision read token supports change detection while actual ACL always freshly authorizes.

## 9. Status/error/retry ownership

Existing BusinessErrors shape {error:{code,message,traceId}}. Processing advice400 INVALID_ARGUMENT wire/grammar/dual/duplicates; existing401 UNAUTHENTICATED/403 MEETING_FORBIDDEN and Origin/CSRF;404 PROCESSING_NOT_FOUND;409 PROCESSING_CONFLICT CAS/idempotency/source-change or REVISION_EXHAUSTED;413 REQUEST_TOO_LARGE transport/collection ceiling;422 PROCESSING_INPUT_INVALID well-formed unsupported domain input;503 PROCESSING_UNAVAILABLE/PROFILE_UNAVAILABLE/PROCESSING_READ_BUDGET_EXCEEDED. Missing sidecar/profile never creates/defaults it. SQL/provider/config details hidden. Unknown failures never success or license to resend with new key.

409 preserves draft/file order; refresh separate authorized projections rather than post-rollback unsafe current-state response. Lost ACK same key/body. Existing file endpoint semantics/statuses stay compatible; discovery adds no backend send. All success/error private/no-store. Task6 owns content/current/history/confirmation; Task12 owned/joined list/unified resume/browser polling. They consume actual producer APIs, not memory. Task5 alone actual worker readiness.

## 10. Required future verification (not executed here)

New owned fixture/harness only, actual reviewed/applied schema under root permission; no reset/shared historical fixture/private env reuse. Distinguish unit, real HTTP/MySQL, storage, restart, browser evidence.

1. Real B04 local/S3 completion -> real HTTP intake -> actual batch/profile/member/input -> one bindAll/protective handoff -> exact production spec/task -> r+1/e/debt/confirmation invalidation, saved transcript/body preserved. No initial transcript needed, no fake output.
2. Same normalized key/body, concurrent requests and lost ACK produce one task. Changed file/order/revision conflicts. Revoked actor replay denied. Actual result survives restart/current archive-selection change. Acceptance revision original; discovery current.
3. Inject profile/member/input/second-bind/enqueue/audit/flush failure: complete rollback including refs/epochs/fences/search/debt/pointers/idempotency/task/outbox. Draft-to-final owner/epoch/route/reference change fails. Failed receipt never reused.
4. SQL/holder instrumentation: one gate/exact issuer/source/actual holder; identities->subject->R1 complete domain->all sessions->all files->all tasks->attempts; new FK inserts after explicit sources; no late roots/parse/archive/hash and no SQL after flush. Adopted enqueue skips ordinary prepare. Missing/repeat/foreign attachment/partial publication fail closed; actual Spring context starts without cycle. Legacy generic policy compatibility positive; exact production complete provider negative/positive.
5. REAL HTTP for EACH info/roster/intake route at9007199254740991/9007199254740992/9007199254740993/9223372036854775806. Owned fixtures exact counters; inspect raw JSON without JS Number conversion. First three: successful mutation/exact response token/stale CAS/legacy-token-equaldual replay. Max: exact read/parse plus exhausted new mutation/no writes. max-1: successful resulting max and exact replay. Invalid grammar/null/float/exponent/dual/range fail before digest/SQL. Task12 separately proves browser JSON.stringify sends original opaque token strings and preserves draft on409; type generation/client code is a separate evidence layer.
6. Real HTTPS/session/CSRF/Origin four sessions owner/member/outsider/admin: owner discovery/intake; safe member capabilities; no member RAW/input/task/upload discovery; fresh revocation/lifecycle; published owner metadata while writes denied. Every success/error private/no-store, no client permission substitute.
7. Page/cursor/query/UTF8+UTF16/aggregate/huge-name bounds; no SELECT * secret transfer. Multiple original COMPLETED sessions, unavailable/mismatched/READ_ONLY route, obsolete epoch, wrong purpose/profile/cross-meeting graph/task/output/producer mismatch. READ_ONLY historical source remains usable under Task3 rules; disabled route does not. Completed receipt discoverable while pending/generic bytes denied. Active excludes terminal; all/detail recovers persisted input/task after refresh/lost response/restart. Immutable ordinal order deliberately differs from UUID order.
8. Independent manifest/producer golden vectors, archive raw/schema/ref/edge/profile damage, aggregate/cancellation negatives; canonical work outside roots. Historical config restart ignores new default, missing original fails closed. Preserve standalone ProcessingFactsReader ambient rejection and affected B04 legacy/download/receipt, B06QueueSeamsIT, BatchFingerprintTest, ConfigArchiveRestartIT regressions.

Task5 separately proves actual WorkerLoop/local+S3 bytes/parser/atomic RAW/fresh-JVM restart. Task6 RAW/WORKING/member/history/confirmation/correction capability. Task12 real browser request generation/refresh/lost ACK/resume with actual business APIs, no route.fulfill successes. Task4 acceptance alone cannot claim those done.

## 11. Focused future file ownership/alignment gates

| Task4 proposed files | Scope |
|---|---|
| contracts ProcessingContracts; core ProcessingIntake/ProcessingDiscovery/TranscriptImportProducer | Data contracts, orchestration, bounded exact producer hash. |
| adapters ProcessingIntakePreparer/JdbcProcessingIntake/ImportTaskDomainPolicy/JdbcProcessingDiscovery/TranscriptEventReferences | Preparation, atomic transaction, policy, DB discovery, events consuming Task3 issuer. |
| API ProcessingController/ProcessingErrors/HttpRevisionTokens/ProcessingConfiguration | Routes/errors/helper/actual registration. |
| Narrow MeetingController/MeetingContracts, repository writable exhaustion guard, MeetingConfiguration/FileConfiguration | Existing info/roster tokens/exhaustion; shared registry/outbox wiring. No creator/schema/ACL redesign. |
| contracts/openapi/processing.yaml plus api/meetings/files | Producer-owned schema before UI; Task12 generated.ts/client/browser work. |
| New focused unit/HTTP/IT owned fixtures | Above gates, no DDL authored/applied by Task4. |

No unresolved product choice. Concrete alignment dependencies: adopt Task3 final names for genuine bound-phase checks/adoption and private pre-exposure assembly seam; root supplies qualified pinned archive Ref/index/explicit technical budgets; Task5 installs actual worker health/output writer before combined-loop readiness. These do not authorize fake capabilities. If adopted Task3 source cannot express an invariant, report exact source contradiction rather than add public setter/second issuer. Task4 implementation starts only on coordinator's frozen post-Task3 base.


## 12. T4DR-01 — mandatory preflight-independent accepted replay

This section is normative where earlier shorthand is ambiguous. Existing-result replay is a correctness path, not an optimization. The section 3 replacement and this flow supersede all interpretations that allow current capability/CAS/source/exhaustion/archive checks first. Keep ordinary request authentication, transport safety, semantic normalization and exact scope/body matching; none is bypassed.

Proposed private adapter operation (data-only return; not locked authority):

```java
// Standalone transaction, no insertion or business mutation.
Optional<ImportAccepted> lookupAccepted(
    Actor actor, String meetingId, NormalizedImport request,
    String scopeHash, String requestHash);
```

NormalizedImport contains the exact normalized expected revision and original ordered file IDs/key scope. Its constructor/parse validation uses the versioned supported HTTP grammar and finite protocol envelope, independently of current per-intake/profile/source capability limits. Legacy/token/equal-dual forms normalize identically before this lookup. Do not move a newly lowered current max-files/profile limit ahead of replay; transport safety remains bounded by the accepted wire protocol. New intake checks the current effective operational limits after a miss.

Lookup transaction sequence:

1. Read/lock the existing idempotency row by precomputed scopeHash; use a bounded current locking read, never uncommitted state. Do not INSERT a miss to create a placeholder. If a competing transaction holds this row, bounded DB waiting is allowed; a timeout/unavailable DB is a safe503, never treated as MISS or accepted success.
2. Fresh B03 lock+EDIT authorizes current actor/meeting/ACTIVE identity/lifecycle/governance. This is the normal B03 identity->subject->meeting path, not a second ACL implementation. Missing idempotency row does not skip fresh authorization. A missing/foreign actor cannot learn stored result by key possession. Preserve request session/Origin/CSRF checks. Never silently reuse the earlier request's authorization after the preparation boundary.
3. When a row exists, require exact actor, meeting/resource, operation PROCESSING_IMPORT, key hash and request hash equality. Mismatch is409, not MISS. Check terminal persisted acceptance marker/result_id/http_status202. A malformed, impossible placeholder or incomplete record returns safe503; it cannot become a successful acceptance and must not be overwritten by preparation.
4. Restore only actual persisted task/input/batch IDs, immutable accepted business/base revision and input fingerprint, with bounded exact same-meeting production type/pool/typed-input/full fixed spec association checks. These are DB metadata checks under the held root, not source-byte/route/archive reads. Required missing/corrupt result rows fail safe503. Current task may have progressed/failed/staled; replay returns original acceptance IDs, not a fabricated task state or output.
5. No current expected-revision equality, increment/exhaustion, current cancel/source-byte eligibility, never-published/new-write check, current archive selection, current worker installation or intake-enabled/readiness prerequisite applies to historical acceptance. Fresh B03 permission and exact semantic match DO apply. Commit the read transaction before returning the restored202; fresh authorization or commit failure suppresses the response. A row absent after authorization yields MISS and closes the transaction.

The application/controller/replay lookup must remain constructible independently of new-intake readiness: common DataSource/TransactionRunner/B03 and persisted-result reader suffice. Missing current archive or failed new-intake assembly cannot cause dependency construction, controller removal or an eager request-level canSubmit check to preempt lookup. Keep the private authority assembly failure-before-exposure invariant: unavailable new-intake components are represented by an explicit unavailable branch passed at construction; no public setter, Lazy bean lookup, second issuer or fake policy. An available intake branch is selected only AFTER MISS. The capabilities endpoint can legitimately say canSubmit=false while exact historical acceptance replay returns202. Truly unavailable DB/identity/result metadata is503/denial; replay cannot conjure authority or rows.

Complete request flow, including concurrent winner:

```
safe parse -> normalize -> digest
accepted = lookupAccepted in fresh standalone transaction
if accepted: return committed original202
if explicit MISS:
    try new-intake readiness/CAS/exhaustion/source/archive preparation
    catch expected prerequisite failure:
        ensure preparation transaction is ended/rolled back and no holder retained
        recovered = lookupAccepted in ONE fresh standalone transaction
        if committed exact accepted: return original202
        if explicit MISS: return original safely mapped preparation error
        if fresh authorization denied: return denial
        if exact scope/body mismatch: return409
        if lookup/result/commit unavailable: return safe503
    if preparation succeeds:
        final existing serialized claim transaction
        -> fresh authorization/exact scope/body/replay BEFORE new-intake predicates
        -> only unmatched new command proceeds through original atomic intake sequence
```

The recovery catch is restricted to named ordinary prerequisites (CAS/exhaustion/source/current archive/intake readiness/configured operational budget). It does not turn malformed requests, prior authorization denial, programming exceptions, corruption, cancellation or arbitrary faults into successful accepts. Those remain fail-closed; the same-key retry remains available later. Any recovery attempt reauthorizes and rechecks exact semantic match; it does not return an earlier cached result.

Concurrent cases are explicit:

* A misses; B accepts same key/body and commits; A then fails source/CAS/archive/readiness preflight. A's fresh recovery lookup returns B's actual committed original result. A neither inserts/binds/enqueues nor rekeys.
* B is uncommitted when A recovers: locking lookup may wait within configured bounds. Only B's committed row/result can be returned. B rollback gives MISS; timeout gives503. No promise/result from B's memory or idempotency placeholder is acceptable.
* Recovery lookup misses before B has inserted/committed: A returns the original mapped preflight error. B may commit later. This is not a claim that acceptance can never occur; client preserves key/body and a later retry discovers it. No unbounded polling, automatic key rotation, speculative202 or implicit new intake.
* B commits different semantic body at the same key: A's recovery finds409 after fresh authorization. A cannot adopt B's IDs merely because actor/meeting/key match.
* Actor permission is revoked between A's first lookup and recovery: fresh denial wins even if B committed. An unreadable/malformed persisted result is unavailable, not MISS.
* A prepares successfully while B wins: existing final claim serialization returns B's exact acceptance after authorization/match; CAS/exhaustion/source handoff/new-write checks are not evaluated first. A's uncommitted prepared UUIDs and bytes are discarded. No bind and no extra task.

Preparation and recovery never share a holder. Final intake failure still rolls back the whole transaction and expires its capabilities; this correction adds no in-place transaction retry. Uncertain final HTTP/commit outcome retains same key/body and uses this mandatory lookup on retry. Existing info/roster replay-before-writable behavior and token normalization remain unchanged; no subject/meeting lock inversion is alleged or introduced.

Required additional future tests (actual HTTP plus controlled race barriers, no tests run here):

* Accepted command immediate retry at already incremented revision; retry after later business revision/publication/source epoch change; retry when current archive unavailable/selection changed/intake disabled/worker absent. Authorized exact legacy/token/equal-dual forms all return original task/input/batch/revision with zero readDraft/source/archive/bind/enqueue calls. Published acceptance replay is historical acknowledgment, not permission to mutate a published meeting.
* Accepted at resulting max from max-minus-one; exact retry before exhaustion guard. Unaccepted new max request fails409 after mandatory MISS. Do the corresponding existing info/roster tests.
* Barrier A after mandatory MISS, B same-key commit, A forced prerequisite failure: recovery returns B's actual row. Repeat with B rollback, uncommitted bounded timeout, B commit only after recovery MISS, differing semantic body and actor revocation. Assert precise safe response and one-or-zero actual accepted task; preserve key, never allocate a substitute acceptance.
* Successful A preparation racing B commit: final claim takes replay branch before all fresh-intake checks, with no A handoff. Inject recovery result corruption/DB failure/commit failure: safe503, no speculative response. Verify a current unavailable intake branch does not prevent replay route/controller construction.

## 13. T4DR-02 — upload-state-specific allowed actions and terminal retries

Task4 only projects existing B04 operations; it does not broaden them. Relevant unchanged source checked: JdbcFileUploads.complete/abort and FileBackendCatalog.require. The review's frozen source shows complete admits OPEN/FINALIZING, OPEN uses require(historicalBackend,true), FINALIZING uses require(historicalBackend,false); abort admits OPEN only after terminal early-return cases. B03/Task3 changing authority implementation is not part of this fix.

Common authorization for every listed operation: real current owner B03 authorization, installed original-purpose policy and unchanged upload cancel epoch. Nonterminal state changes/recovery also require writable meeting (not published) and normal ACTIVE lifecycle/governance. Backend lookup validates the exact historical session backend/managed identity/config revision/status; never current default or generic available flag. Read projections are observations, so mutation rechecks after discovery. COMPLETE additionally requires the caller's exact valid persisted part/hash manifest; listing it does not attest an arbitrary future body.

| Actual session state | allowedActions on WRITABLE historical route | allowedActions on valid READ_ONLY historical route | Terminal idempotentRetries, separate from state change |
|---|---|---|---|
| OPEN | UPLOAD_PARTS, COMPLETE, ABORT, subject to common checks | ABORT only; parts/new completion forbidden | [] |
| FINALIZING | COMPLETE only | COMPLETE only; recovery does not require writable default | [] |
| COMPLETED | [] | [] | COMPLETE and ABORT when fresh common owner/purpose/epoch checks pass; COMPLETE also validates exact saved manifest. Neither performs new storage I/O on this terminal early-return path. |
| ABORTED | [] | [] | ABORT when common checks and exact historical recovery route pass; repeats storage cleanup without reopening/changing the persisted terminal state. |
| FAILED | [] | [] | []; preserve existing state-conflict behavior, no invented repair/resume operation. |

FINALIZING never advertises UPLOAD_PARTS or ABORT. For DISABLED/missing/mismatched historical route it advertises no action and a safe route-unavailable reason. READ_ONLY does not hide its COMPLETE recovery; default backend disabled/unset/changed is irrelevant to an already accepted historical session if its route is valid. Publication/epoch/purpose/owner changes suppress FINALIZING recovery according to existing checks. FINALIZING is still a potentially state-changing completion operation, so its COMPLETE belongs in allowedActions, not a terminal retry list.

For OPEN, a READ_ONLY historical route denies parts/new completion, but ABORT cleanup uses require(...,false) and may still be offered if common mutation checks pass. A disabled/mismatched route means no advertised action: do not claim a successful abort is available. Existing abort writes ABORTED before its post-transaction cleanup; if cleanup then fails, persisted metadata may show ABORTED and subsequent cleanup retry remains route-dependent. This design does not change or falsely promise atomicity across that existing DB/storage boundary.

Terminal early returns differ from new state permission and retain existing behavior:

* COMPLETED completion returns the existing fileId after fresh owner/purpose/epoch and manifest validation, before new-write/publication/backend checks. COMPLETED abort is a no-op acknowledgment after owner/purpose/epoch, with no storage call. Therefore a published meeting or disabled historical route alone does not hide these terminal idempotentRetries, provided current B03 authorization and other early-return checks still pass. Neither revives source-byte permission or changes published business state.
* ABORTED abort repeats cleanup using the historical recovery route after owner/purpose/epoch checks. It does not require a never-published check after terminal detection, but route disabled/unavailable suppresses advertisement and fails the actual endpoint. It is not permission for OPEN writes or FINALIZING abort.
* Terminal retries never include UPLOAD_PARTS; ABORTED cannot COMPLETE. A changed epoch or revoked identity/owner still denies terminal retry even though owner-authorized receipt metadata may remain visible under the separate discovery contract.

The new required UploadReceipt.idempotentRetries array and existing allowedActions array are emitted from this matrix using actual state and individual checks, not one canWrite/uploadAvailable boolean. OpenAPI explains the distinction; UI may label terminal entries as retry acknowledgment/cleanup. Both arrays may be empty; blockedReason is safe metadata, not a capability. No new B04 mutation or route is introduced.

Required additional future evidence:

* Actual discovery plus real B04 requests for OPEN/FINALIZING/COMPLETED/ABORTED across historical WRITABLE/READ_ONLY/DISABLED/missing/mismatched routes, absent/changed default, same/changed epoch, current/revoked owner and published/unpublished meeting. Compare advertised action and terminal-retry meaning with actual safe response and persisted effects; never infer from a mocked success.
* OPEN READ_ONLY parts/complete denied, OPEN abort cleanup allowed when its actual recovery checks pass. FINALIZING READ_ONLY COMPLETE recovery positive with unrelated writable default unavailable; FINALIZING ABORT/parts never advertised and actual endpoint rejects without enabling them. Disabled FINALIZING recovery unavailable.
* COMPLETED same-manifest replay returns original file/no new rows/storage even if publication/default/route changed as permitted by terminal early return; wrong manifest fails. COMPLETED abort acknowledgment and ABORTED cleanup replay tested separately; ABORTED cleanup must fail on unavailable historical route. All retries still fail for owner/epoch/purpose revocation.
* All success/error/discovery responses private,no-store and safe bounded projections. Verify terminal metadata/receipt visibility never implies source content readability or first-binding authority.

## 14. R1 scope, precision and adoption gate

Manifest profiles now explicitly serialize ordered objects {kind:"CANONICAL_CODEC",ref:codecRef} and {kind:"CONFIRMATION_HASH",ref:confirmationRef}, each ref {artifactType,version,sha256}; no bare strings/refs. This is the existing ConfigSchema grammar, not a new profile/protocol.

T4DR-01 and T4DR-02 are addressed in this proposed R1 at design level, pending independent bounded re-review. All accepted original authority/rank/CAS/token/producer/assembly/bounds/rollback/discovery requirements and future evidence remain binding. No source/runtime/test/schema approval is inferred. Task3 facade names remain provisional; do not inspect/overwrite its active implementation. Root owns exact-hash adoption and subsequent Task4 dispatch.
