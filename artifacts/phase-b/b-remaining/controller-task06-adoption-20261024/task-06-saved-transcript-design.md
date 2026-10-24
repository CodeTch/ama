# Task 6 — saved transcript, complete facts and confirmation design

2026-10-23. **DESIGN ONLY; pending independent review and coordinator adoption.** This report specifies proposed Task 6 implementation; no source implementation, compilation, tests, runtime qualification, database installation or Phase B completion is claimed. Only this report was written. No source edits, tests, Git mutations, DB/Docker operations, private environment reads or subagents. Task 3 author was active and its implementation is not reviewed here. Task 4 R1 became design-adopted during this work; final Java names and Task 5 output composition remain alignment dependencies.

## 1. Authority, inputs and actual sources

W is `/home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining`. Established changing-module behavior was inspected with read-only git show at **3e393dbc9485b187b89bfc03abd88821a0aabddb**, specifically TranscriptConfirmationHash, JdbcProcessingFactsReader and JdbcMeetingRepository. Other cited pure/schema/parser/controller files were read from the coordination checkout; this is not a seal over concurrently changing source. All proposed signatures below are design, not existing APIs.

Mandatory input SHA256 observations:

| Input | SHA256 |
|---|---|
| W/task-06-design-brief.md | 047670704f4142b5c176d6b93ffd721306b6a8248b701b9c975a58c962352184 |
| W/task-06-hash-preflight.md | 36390cf55f774e717aa2f79cf45951fe3a0d1d1170501d87be7765c8c531417a |
| docs/superpowers/specs/2026-10-23-ama-phase-b-controlled-runtime.md including Normative R1 | 8bec16ce2935cef50aee5771c4be3ea899db11fed28e4255b2feeb2f7a016b54 |
| W/task-03-authority-design.md | 121b58e04c0077d8a7e46b5efbae49d5d0b2955308d2ad486fdb0c017a0155ba |
| W/task-03-authority-design-r1.md | 6fcb1f1d816cd96d389f83e67b0ceaba3298c4657cda3ed8bdcc27f3ab6537e9 |
| W/task-03-pre-gate-read-addendum-r1.md | 58e085bc64a85f89e9ff3c9bdabf8dce31d5feb2205ebed46b56dfa12101387a |
| W/task-04-intake-design-r1.md | fcbde3fa7885d0fe3553646c2fbec28e9ad6da78aba4678f8e29461c3f35666b |
| docs/superpowers/specs/2026-10-23-ama-phase-b-processing-intake.md | b631f1d874c956af6f35cc4f632a83783271ffdad393127584565326f78bb536 |

Also read task-06-plan-extract, remaining plan, Task 3 R1 review/closure and minutes UX decisions. Task 4 wrapper references preserved full R1/reviews under `artifacts/phase-b/b-remaining/controller-intake-design/`; adoption is design approval only. This report's own SHA is delivered externally after writing, not embedded self-referentially.

| Actual source anchor | Constraint |
|---|---|
| backend/adapters/src/main/resources/db/migration/V011__transcript_schema.sql: transcript_history, transcript_confirm_once, segment_order | Unique meeting+history revision, unique meeting+confirmed revision. Segment PK is revision_id+segment_id, permitting unchanged segment identity across immutable versions. Same-meeting/immutable history are application obligations. |
| backend/adapters/src/main/resources/db/migration/V008__meeting_schema.sql: meeting, idempotency_record | Saved/confirmation/final pointers and needs_generation already exist. Idempotency has result UUID/status, no accepted revision or response JSON. |
| backend/adapters/src/main/resources/db/migration/V010__reliable_execution.sql: durable_task/task_attempt | Typed output_kind/output_id and actual terminal task must be verified; StoredTask DTO has no success proof. |
| backend/core/src/main/java/ama/minutes/contract/TranscriptConfirmationHash.java: data/hash/hashBound/segment/minimum | Current entry requires actual confirmation. Confirmation identity/time/actor/hash are excluded from canonical data. data alone bypasses archive prerequisites and is projection only. |
| backend/core/src/main/java/ama/minutes/contract/StoredSourceGraph.java: Session.transcript/graph/walkInputs | Transcript validation takes confirmation to create CatalogFact. General source graph derives represented minutes evidence, not database completeness/import authenticity. |
| backend/core/src/main/java/ama/minutes/contract/StoredFactsIndex.java: constructor/matching/validateMembershipRows | Bounds/uniqueness only cover supplied facts. It cannot detect a mark row omitted by a DB reader. |
| backend/contracts/src/main/java/ama/contract/MinutesContracts.java: Table/FullRow/StoredProvenanceFacts/ConfirmationFacts/StoredTask/GraphBudget | Exact positional complete projections. Caller must bound before defensive copies. No source/session/sidecar/terminal authority in these facts. |
| backend/core/src/main/java/ama/config/ConfigBindings.java: Context.processingStored/processing/confirmation/finish; ArchivedProfileResolver.java: open/confirmation/processingBudget/Scope.finish | Real input/manifests/files/tasks, archive bytes/refs/closure and original processing fingerprint remain prerequisites. Pure ConfigBindings does not prove success/current ACL/storage authenticity. |
| backend/adapters/src/main/java/ama/transcript/JdbcProcessingFactsReader.java: readStored/rows/manifest/tasks, frozen baseline | Standalone processing-only reader lacks segments/marks/current-pointer/terminal proof; separate issuer/ambient rejection cannot compose into write authority. |
| backend/adapters/src/main/java/ama/transcript/TextTranscriptParser.java: stableId/Lines.finishLine; core TranscriptDocumentParser.Segment | Stable UUID derives from parser/file/hash/paragraph; TXT preserves paragraph numbering, omits blank lines, yields null speaker/times. Human edits must not reparse IDs. |
| backend/adapters/src/main/java/ama/meeting/JdbcMeetingRepository.java: claim/checkReplay/restore/finish, frozen baseline; MeetingDigests.java | Existing actor/resource/operation/key/body digest, replay reauthorization, final flush. Do not copy unbounded SELECT * readers. |
| backend/core/src/main/java/ama/access/MeetingAccess.java; api MeetingController/SessionSecurityFilter | Genuine READ/EDIT, actual session/Origin/CSRF; no admin content bypass. Task 4 owns exact revision-token helper. |
| backend/adapters/src/main/resources/db/migration/V013__publications.sql: publication/final_confirmation | Existing publication unique meeting root and READY/RUNNING/RECONCILING/FAILED/SUCCEEDED vocabulary; Task 11 owns installed freeze policy. |

## 2. Supported saves, exact copies and lineage

Active scope is IMPORT/TXT/EMPTY only. ASR/ENHANCE, CANDIDATE/ENHANCED restoration, arbitrary source rebinding, segment split/merge/add/delete/reorder, speaker reassignment and timestamp editing are unavailable. Schema enums do not grant support. Capabilities/OpenAPI expose this narrow matrix; supported edits change bodies and complete marks only.

Every save allocates a new revision UUID; preserves selected source's entire ordered segment set, `segment_id`, `ordinal`, `source_file_id`, `source_paragraph`, nullable `start_ms/end_ms/speaker_id`, and exact batch/processing_input/input_fingerprint. New revision is WORKING, parent_id=selected source revision, producer_task_id=NULL, with real actor/time. Copy all segments and chosen complete marks to new revision; never edit historical rows. TXT original null speaker/time remain null. Non-null legacy or other pipeline data is not silently normalized into supported TXT; unsupported provenance fails.

Parent records copied-source derivation, not blindly previous current pointer. Restoring an older batch points to that older source, preserving its input/profile chain. Complete bounded acyclic parent traversal must end at actual RAW and its original successful producer. Each WORKING hop has null producer and same fixed input/batch/fingerprint as parent. No human save creates a processing input, manifest, task, file reference, artificial RAW or current-config substitution.

| Command | Semantics |
|---|---|
| PUT transcript from WORKING | Source must equal current saved pointer. Complete ordered `{segmentId,body,marks}` list matches inherited ID set/order exactly; omitted/extra/duplicate rows fail. |
| PUT transcript from RAW | Explicit owner save of exact successful RAW, including replacement of earlier saved version. Root CAS mandatory. Import/RAW alone never moves saved pointer. |
| PUT marks | Source=current WORKING. Complete `{segmentId,marks}` for every segment; copy bodies/metadata exactly. Empty array clears that segment; omission is invalid. |
| POST restoration | Explicit exact same-meeting RAW/WORKING history source. Copy whole bodies/marks into new WORKING. No old confirmation or review credit is copied. |
| POST confirmation | Only current real saved WORKING; no client bodies/marks/hash/actor. Complete graph/archive/readiness fresh recheck. |

Marks are actual KEY/CONCLUSION/DISPUTE/GENERATION_FOCUS set. Reject duplicate/unknown entries; normalize enum order before digest. Body valid scalar Unicode is stored exactly, without trim/NFC/newline normalization. Individual empty bodies permitted within fixed set; empty collection or all-blank transcript cannot save/restore/confirm. Use existing Java String.isBlank after scalar validation. Operational bounds do not introduce product content quotas.

Every new-key save, even identical content, creates the explicit new version, advances meeting+subject once r→r+1 with unchanged cancel epoch, clears transcript/final confirmation pointers, sets needs_generation=1 and updates search projection meeting_revision. Same-key semantic replay alone avoids repeat effects. Preserve existing saved minutes but expose stale/debt readiness; do not enqueue a dummy generation task. Do not invent stage transitions; preserve stage unless adopted shared lifecycle transition specifies otherwise, and project saved/confirmed/debt separately.

History allocation proposal accepted for alignment by coordinator: Task 5 RAW.revision = immutable producer TaskSpec.businessVersion, equal held meeting r at ordinary valid completion. Task 6 WORKING.revision=r+1. Under root require existing maximum transcript history revision≤r and target unused; query the meeting+revision index boundedly, never renumber or use MAX+1 to hide inconsistent history. Each intake advances before a distinct producer; stale ordinary completion cannot write. **Task 5 must adopt/prove this exact allocation and output kind before Task 6 source dispatch**. Schema allows it but does not itself enforce producer equality.

## 3. Proposed interfaces and complete facts

Use one Task 3 JdbcTranscriptAuthority issuer, same DataSource object/B03 instance/B04 gate; reuse accepted composition/preparer, no duplicate issuer/public mint/setter. Proposed data/adapter seams (names align after Task 3 adoption):

```java
record ConfirmationDraft(String meetingId,String revisionId,StoredProvenanceFacts stored) {}
interface TranscriptFactsReader {
  TranscriptSnapshot readCurrent(Actor actor,String meetingId,
      TranscriptReadBudget budget,BooleanSupplier cancelled);
  TranscriptSnapshot readOwnerRevision(Actor actor,String meetingId,String revisionId,
      TranscriptReadBudget budget,BooleanSupplier cancelled);
}
interface HeldTranscriptGraph {} // actual implementation private inside shared issuer
HeldTranscriptGraph lockSavedGraph(FileRegistry.LockedMeeting gate,
    PreparedTranscriptObservation prepared,TranscriptOperation operation);
void requireExact(HeldTranscriptGraph held,FileRegistry.LockedMeeting expectedGate,
    PreparedTranscriptObservation expected);
```

Snapshot/observation/draft are detached data, not authority. Held capability verifies private type/issuer, exact DataSource/active actual holder, exact B04 gate/B03 handle, actor/meeting/purpose, complete sets and acquisition phase. Same URL/equal DTO insufficient. Ended/suspended/foreign holder cannot consume. ConfigBindings bindings remain pure validation.

Complete selected closure:

* Actual selected revision + complete parent closure to RAW, complete segments and marks for every revision in that closure, including explicitly established zero marks. Charge depth/rows/edges cumulatively; supported chain has same fixed input.
* Actual processing input, original batch/ordered members and mandatory original fingerprint-profile sidecar. Preserve FullRow schema; sidecar separate observation. Exact IMPORT/EMPTY/null sourceRevision/material/model/prompt shape and original manifest/base/cancel values.
* All original files and exactly one original COMPLETED upload per file; literal original TRANSCRIPT_INPUT purpose, AVAILABLE/same meeting, exact UUID/hash/size/location/route and epoch through B04. Complete references valid. resolve/completed DTO/download is not write authority.
* Full declared RAW producer task union. Original full production spec TRANSCRIPT_IMPORT_V1/TRANSCRIPT_IMPORT, MEETING/TRANSCRIPTION_INPUT, original fingerprint/base/cancel, priority0/maxAttempts3 and actual canonical producer key. Actual task SUCCEEDED typed output must equal that RAW; Task 5 freezes the actual output kind. Human producer null never removes ancestor proof. StoredTask alone cannot attest success.
* Current pointer/actual confirmation when present, root r/e/lifecycle/governance/published/access and correction observations. Missing confirmation is absent, never fabricated.

Pure facts include complete relevant FullRows, ContentFiles and StoredTasks. Source-route/session/sidecar/root/terminal completeness observations are separate, not new S1 fields. Irrelevant minutes/material/review/configuration lists explicitly empty. StoredSourceGraph.derive is not repurposed as DB completeness proof. Task discovery uses declared PKs/unique producer key, not scanning all tasks by unindexed input_id.

## 4. Acquisition, bounds and exact fresh recheck

1. Standalone reads/services reject ambient transactions. Discover bounded immutable IDs/edges before locks: current or explicitly selected source, full parents, inputs/batches/members/source/task unions, relevant confirmation and maximum history guard. Indexed exact predicates and remaining+1 sentinels; no recursive table scan or clipping. Distinguish absence from corrupt/unavailable discovery.
2. Calculate needed producer keys outside roots. Follow Task 3 pre-gate addendum's private Prepared/Ineligible/Fatal routing: one READ gate, same-handle EDIT for owner history; known-meeting authorization denial precedes graph/profile/budget failure. Cancellation stops. Failed preparation mints no scope. Generic genuine ineligibility only permits an independently authorized unchanged legacy alternative per Task 3; arbitrary failures never fallback.
3. Final one-gate domain rank: identities unsigned UTF8 → subject+meeting/effective READ grant provenance → all revision IDs sorted → complete segment/mark children in fixed table/PK order and relevant confirmations → input UUIDs → batch UUIDs → profile sidecars → ordered members → all original sessions sorted → all files sorted → **all tasks sorted before any attempt** → declared attempts if needed → later external domain → audit/outbox. Children/confirmation are an explicit transcript-domain extension to align with Task 3 before implementation, not locks added after its source phase. All discovery rechecked; changed/new parents/tasks abort without late expansion.
4. Use B04 sources(expectedGate,receipt,exactFileIds), full registered current/history finalization. Historical producer proof requires success/original full spec/output, not live RUNNING lease or today's businessVersion. Original source epoch/route eligibility still applies. No queue.validLease call after sources and no source open in facts acquisition.
5. Before content transfer measure complete count/PK metadata and every variable-text UTF8 byte, scalar and UTF16 length. For LONGTEXT use OCTET_LENGTH, CHAR_LENGTH, OCTET_LENGTH(CONVERT(... USING utf16))/2; reserve cumulative budgets before the content SELECT. Bounded character streams validate scalar text and exact measured lengths. Fixed-width strings also reserve known schema upper bounds/actual lengths before allocation. Check complete PK-set/count equality across measurement and transfer. No getString(LONGTEXT)/SELECT * followed by size validation, no partial complete=true response.
6. Release read transaction with detached bounded facts. Load original archives, strictly parse, canonicalize/hash outside all roots. Before mutation or a read response asserting fresh authority, use the final independent gate and compare *entire actual graph* typed value/byte equality, all complete sets including zero marks, current pointer/r/e, source routes/counters and actual producer output. CAS alone cannot detect corruption. No archive/storage I/O, JSON parsing, canonical hashing or whole-body digest under locks. Failed final commit suppresses response/opening.

Current content reads still require complete declared provenance/source checks; hash/readiness additionally verifies original archives. Small authorized metadata can report unavailable capability but cannot substitute current configuration for a missing stored profile. Source download uses Task 3 private pin and postcommit open, never a reusable public pin/opener. Range does not reduce complete source authorization.

Proposed positive deployment defaults for review: full request8MiB, full JSON response32MiB; total100000 fact rows/500000 edges; parent depth128/revision nodes256; batch files64/tasks64; per-text4MiB UTF8/4Mi UTF16; aggregate16MiB UTF8/16Mi scalars/16Mi UTF16; manifest64KiB; metadata history default20/max50. Effective minima include actual ArchiveReadBudget, GraphBudget/CanonicalBudget, installed Task 3/parser limits. These are qualified deployment capabilities, not product quotas. Bound SQL count/deadline/cancellation, list/copy growth, JSON escaping overhead and archives cumulatively. Checked subtraction before addition/remaining+1; no preallocation from enormous budget. Oversized full graph fails safely; history pagination does not paginate evidence completeness.

## 5. Same-algorithm prospective confirmation hash

Add ConfirmationDraft to MinutesContracts and `TranscriptConfirmationHash.hashDraft(ConfirmationDraft, Artifact, GraphBudget, BooleanSupplier)`. It operates on an actual persisted saved revision, not unsaved client text, and grants no write permission. Preserve all existing public confirmed entry signatures/profile/golden vectors.

Extract confirmation-independent validation from StoredSourceGraph.Session.transcript into an internal `validatedTranscriptRevision(meeting,revisionId)` helper returning validated row/member data. Preserve existing checks: unique positive ordinals; some nonblank body; paired ordered times; positive nullable paragraph; marks belong to segment IDs; complete batch memberships/file hashes; each selected source is a batch member. Existing transcript(actualConfirmation,segmentId,meeting) delegates, then constructs CatalogFact/TranscriptVersion using *real* confirmation ID/content_hash. Draft invokes validation directly, never constructs a CatalogFact requiring imaginary confirmation ID/hash.

Extract `savedData(meetingId,revisionId,index)` and `hashSaved(...)`. Existing data(confirmationId,index)/hashBound first authenticate actual confirmation+meeting/revision and delegate. Draft authenticates actual revision+meeting, explicitly requires WORKING and delegates. Do not silently add new producer/RAW restrictions to historical generic pure APIs during refactor: preserve their current supplied-fact validation and byte results; production saved-only/terminal proof belongs to the real reader/command. Any extra pure validation tightening needs separate review.

Unchanged canonical projection:

```
meetingId, revisionId, batchId, segments (ordinal ascending)
segment: segmentId, ordinal, body, sourceFileId, sourceParagraph,
         startMs, endMs, speakerId, marks
marks: enum order KEY, CONCLUSION, DISPUTE, GENERATION_FOCUS
```

Actual nullable metadata stays null; empty marks stay []. Envelope profile remains ama.transcript-confirmation.v1/kind TRANSCRIPT_CONFIRMATION. No confirmation identity/hash/time/actor, parent, sidecar or task added. Identical text with different saved revision IDs intentionally differs; equality is for the same real saved revision before/after actual confirmation insertion.

Both hash paths: resolver.open(same supplied facts) → StoredFactsIndex under GraphBudget → actual processing_input_id and same meeting/batch → resolver.confirmation through ConfigBindings stored processing verification → supplied Artifact equals bound original artifact → minimum(runtime canonical, original processing canonical budget) → DomainHash.hash → operation.finish exact closure → final cancellation check. data() stays projection-only. Unconfigured constructor remains PROFILE_UNAVAILABLE. Original raw bytes/schema/refs/edges/four-artifact closure/manifest/fingerprint and genuine supplied task prerequisites remain. No computeProcessing draft bypass, current-config fallback or placeholder confirmation/hash. Runtime adapter separately proves actual source/task success.

## 6. Commands, confirmation identity and mandatory replay

Proposed service methods save/replaceMarks/restore/confirm own standalone orchestration; JDBC repository owns atomic final effects with shared Task 3 authority/B03 subject/audit/outbox. Versioned bounded length-prefixed semantic digests run outside roots and cover actor+meeting+operation+key scope; normalized expected revision; explicit source/saved revision; ordered segment IDs/bodies; complete normalized marks. Confirmation digest contains no current hash/config. Legacy integer/token-only/equal-dual forms normalize identically.

MANDATORY committed-result lookup occurs after safe wire parsing/normalization/digest and before current source/profile/readiness/CAS/exhaustion. Standalone transaction locks existing idempotency row, freshly authorizes B03 EDIT, checks exact actor/meeting/operation/key/body, validates state/status/result and actual same-meeting result row. Miss inserts no placeholder and ends its transaction. Mismatch409; malformed/missing result503, never MISS. DB wait bounded; never return uncommitted result. Current source/config/operational limits cannot preempt exact historical acknowledgment. Replay/controller construction must work independently of explicit unavailable new-command branch. Published historical acknowledgment is not a write; fresh B03 permission still applies.

After MISS, prepare complete authorized graph/request/original archives/fingerprint, actual qualification and server IDs outside roots. Save does not need a prospective confirmation: it persists WORKING, then user explicitly confirms in a later command. Confirm computes hashDraft over actual saved graph. Named ordinary prerequisite failure allows exactly one fresh committed-result lookup to recover concurrent same-key winner after preparation transaction ends. No catch-all recovery for malformed input, prior authorization denial, cancellation, corruption or programming exceptions. Final serialized claim also reauthorizes/matches/replays before new-write predicates.

Final new mutation sequence:

* Serialized idempotency claim; one fresh B03 EDIT/B04 gate; replay/mismatch before current write guards.
* Exact expected meeting r and subject business r, unchanged prepared cancel epoch, ACTIVE lifecycle/governance, not deleting/deleted, neither PUBLISHED nor ever_published; integer exhaustion guard before r+1. Task 11 installed in-progress publication policy adds mandatory guard; ever_published=false alone insufficient.
* Complete predeclared graph acquisition in section4 rank; private held capability authentication; exact current pointer and all prepared facts/sets/source/producer/correction recheck. No archive/parser/hash under locks. Accept preparer hash only when all actual original graph/profile observations match; its detached DTO alone is insufficient.
* Save/marks/restore inserts new WORKING+all children, validates actual new rows/affected counts, sets pointer, clears transcript/final confirmations, debt=1, advances meeting/subject once and search projection revision. New UUID collision aborts, never adopts existing row. Old graph untouched. Task10 disposition integration gate applies to these version writes.
* Confirmation branch below. Persist result reference/idempotency, actual typed event/audit and one final outbox flush; no SQL/locks after flush. Any row/readback/audit/flush/commit failure rolls back all graph/pointers/fences/debt/idempotency/events. No external I/O or partial success.

| Actual final condition | Confirmation effect |
|---|---|
| No current WORKING, requested ID not current, RAW/unsaved ID, blank graph or unavailable correction/profile/source/provenance | Reject with no confirmation/task/pointer effect. |
| Current WORKING has no real confirmation | Insert real UUID/hash/owner/time; attach pointer; r→r+1, clear final, set/retain needs_generation=1. No generation task. |
| Pointer already equals unique confirmation for same WORKING; recomputed hash agrees | Same ID/hash; no new row/version/debt mutation. New key records acknowledgment; fresh CAS/readiness still required after replay miss. |
| Pointer cleared but current WORKING unchanged and original unique confirmation exists | Explicit new request may reattach same immutable ID only after full fresh CAS/graph/profile/correction checks; r→r+1, clear final, debt=1. Do not overwrite confirmer/time/hash. This explicitly confirms displayed saved version, never adopts pending RAW. |
| Existing hash/provenance mismatch or pointer targets another revision | Unavailable/corruption, no overwrite/latest substitution. |
| Exact old idempotency result after later save/intake/publication | Historical acknowledgment only; never move pointer/reconfirm/clear new final or debt. |

Same root serializes first-confirm/save/restore; unique(meeting,revision) is backstop, not a duplicate-key bypass. Different-key concurrent confirms at same old r: first effect wins, second409; subsequent deliberate request at fresh r for already-current unchanged confirmation returns same ID without increment. Same-key exact replay returns prior actual identity regardless of newer current pointer after reauthorization.

Response avoids invented storage: stable `result={kind:SAVED_REVISION|TRANSCRIPT_CONFIRMATION,meetingId,revisionId,confirmationId?,contentHash?}` derives from real immutable row. Separately `observation={meetingRevision,revisionToken,accessRevision,accessRevisionToken,currentSavedRevisionId,currentConfirmationId}` is freshly read, not historical accepted revision. `replayed` identifies old acknowledgment; `resultIsCurrent` is derived under same fresh root. Saved result may include actual transcript history number/token. No invented confirmation.meeting_revision, infer-from-time accepted version or DDL just for response. Browser GET refreshes current state and never replaces local draft from historical result.

Confirm body allowlist has only expected revision and savedRevisionId; bodies/marks/hash extras reject. Server checks actual saved/current ID, not browser dirty flag. It cannot observe unsent buffer. Task12 disables confirm while dirty and preserves buffer on conflict/unavailable; this is not server attestation that no browser edits exist.

## 7. Qualified correction-domain capability

Composition provides explicit qualified `DISABLED_NOT_INSTALLED` for local B, with version/installation qualification identifier. It attests actual selected domain configuration and absence of installed correction capability, not a caught missing-table error. No C03/PENDING storage queried or claimed implemented; no synthetic pending count zero.

Proposed CorrectionDomain has private qualified-disabled implementation and explicit unavailable branch. Projection `{mode:DISABLED_NOT_INSTALLED,readiness:NOT_APPLICABLE,qualificationId}` has no pendingCount. Unknown, enabled/installed without authoritative reader or contradictory installation yields UNAVAILABLE and denies confirmation. Later C03 must provide complete real pending-correction read under common root/freshness; caller zero/boolean cannot satisfy it. Disabled qualification immutable for process; restart/replacement changes issuer/qualification and invalidates preparations; final checks current same component. Hot reload requires separately reviewed synchronized domain-generation protocol.

Owner saves remain possible independent of correction readiness; canConfirm/final readiness blocked when unavailable. UI differentiates not-installed/disabled from enabled-zero. Tests exercise actual qualification/configuration branches, not fabricated real correction rows.

## 8. Task 6 HTTP/OpenAPI ownership

Task6 owns TranscriptController/TranscriptErrors, safe projections and `contracts/openapi/transcripts.yaml` integration into api.yaml. Task12 owns generated browser types/client/UI. Reuse Task4 HttpRevisionTokens on every mutation: integer expectedRevision or expectedRevisionToken, equal dual allowed; grammar `0|[1-9][0-9]*`, supported0..9223372036854775806; no sign/space/leading zero/exponent/decimal. Normalize before digest, no double. Browser sends original opaque strings; all revision/access/history counters in projections carry tokens. No 2^53 DB cap.

Exact proposed routes, prefix `/api/v1/meetings/{meetingId}`:

| Method/path | Authorization and shape |
|---|---|
| GET `/transcript` | Fresh READ, current saved WORKING complete detail or saved:null. Owner and effective confirmed member; RAW never substituted. Current confirmation/debt/correction/viewer capabilities/safe reasons/fresh tokens. |
| GET `/transcript/history?limit=&beforeRevisionToken=` | Owner EDIT, RAW/WORKING metadata summaries descending history revision, exclusive cursor, default20/max50. Explicit nextCursor/hasMore, no bodies/marks. |
| GET `/transcript/revisions/{revisionId}` | Owner EDIT exact same-meeting supported RAW/WORKING complete content/marks/provenance. No latest substitution or truncation. |
| GET `/transcript/marks` | READ current saved only; `{revisionId,complete:true,segments:[{segmentId,marks:[]}],observation}`; all empty sets, no pagination. No saved is explicit null. |
| GET `/transcript/revisions/{revisionId}/marks` | Owner EDIT exact history complete marks. |
| GET `/transcript/confirmation` | READ actual current confirmation or null; identity/hash/profile/current relation, never newest historical confirmation. |
| GET `/transcript/confirmations/{confirmationId}` | Owner EDIT exact same-meeting immutable confirmation plus its saved graph; member UUID possession insufficient. |
| GET `/transcript/sources/{fileId}` | READ + Task3 CURRENT_EFFECTIVE full source protocol; only current WORKING member source. Original bytes, Range handled by B04. |
| GET `/transcript/revisions/{revisionId}/sources/{fileId}` | Owner EDIT + exact Task3 OWNER_HISTORY source protocol; no worker permission via browser download. |
| PUT `/transcript` | Owner EDIT; `{expectedRevisionToken or expectedRevision,sourceRevisionId,segments:[{segmentId,body,marks}]}` complete ordered list; receipt+fresh observation200. |
| PUT `/transcript/marks` | Owner EDIT; `{expectedRevision...,sourceRevisionId,segments:[{segmentId,marks}]}` complete current mark replacement creates WORKING200. |
| POST `/transcript/restorations` | Owner EDIT; `{expectedRevision...,sourceRevisionId}` exact whole-history copy into WORKING200. |
| POST `/transcript/confirmations` | Owner EDIT; `{expectedRevision...,savedRevisionId}` real confirmation receipt200, same status matching replay. |

Detail includes meetingId, observation, selected/saved `{id,kind,revision,revisionToken,parentId,batchId,processingInputId,inputFingerprint,createdBy,createdAt,segments}`; exact segment body/source metadata/marks; public ordered sources `{fileId,sha256,sizeBytes,sizeBytesToken,ordinal}`; original parser/config/profile version identifiers. Owner sees original RAW/task provenance and history navigation. Member projection includes only effective saved graph/public current source locators and omits owner-private task/RAW/history navigation and chain IDs unnecessary for current source access; server hash verification still uses complete internal facts. The member projection must not reuse the owner DTO and merely hide fields in UI. Current content may have null confirmation; RAW detail clearly labeled unsaved.

No raw config manifest, upload/provider token, backend/namespace/object key/path, credential, private archive location or raw SQL/cause in responses. canSave/canRestore/canConfirm/canReadHistory/canReadSources are fresh server observations, not posted authority. Read capability failure gives safe authorized reason; inaccessible actors see no graph-specific failure.

Bound streaming request before byte[]/JSON tree/list allocation, including unknown Content-Length; strict duplicate keys/unknown fields/trailing JSON, exact UUID grammar, one valid Unicode nonblank bounded Idempotency-Key. Stable protocol envelope parsing limits remain separate from mutable operational limits so replay is not preempted by lowered new-command limits. Bound response including JSON escape expansion. Source bytes use existing safe B04 range/length/content headers and deployment source bound; no inline HTML/sniffing. Each history page reauthorizes; pages do not claim a consistent whole-graph snapshot. Full detail/marks cannot paginate away evidence.

All success/error responses `Cache-Control: private, no-store`, including unauthorized/error paths; no cached304 bypass. Existing session filter, Secure HttpOnly host-only cookie, Origin/CSRF contracts apply. Missing session401; ACL403; authorized exact missing entity404; malformed400; stale CAS/digest mismatch409 with safe current tokens only after authorization; transport excess413; blank/unsupported business input422 (new explicit TranscriptErrors mapping, not claimed current behavior); missing/corrupt profile/provenance/correction/capability503. Internal failure codes remain safe; no raw cause. Admin-only role has no implicit permission.

## 9. Dependencies, write seams and source ownership

Task3 supplies genuine authority/source protocol; Task4 actual intake/policy/config/HTTP token parser; reuse its supplied preparer rather than duplicate. Task5 actual successful RAW producer/output kind/revision allocation. Task6 complete saved facts/confirmation feeds9–11; Task12 consumes exact producer routes after review.

Task10 owns shared **PENDING-candidate disposition on business version writes**. Before candidate runtime activates, every Task6 save/marks/restore and confirmation transition advancing r must invoke that same adopted helper under common root, with complete candidate domain declared/acquired before sources/tasks. Do not leave candidates silently PENDING at superseded business fence. No early no-op helper may claim Task10 installed. New-key unchanged confirmation/no-version acknowledgment and true replay do not dispose candidates. Candidate activation must fail until these paths are integrated/tested.

Task11 installs publication write freeze on all these owner mutations, including same-content new-key saves. Actual READY/RUNNING/RECONCILING and uncertain executions use its shared policy; failed-publication behavior is decided there. Existing ever_published/stage checks remain mandatory, never bypassed by absent helper. Compose installed policy and predeclared publication domain before publication runtime activation. Caller flags/stage alone/stale final ID grant no bypass. Authorized history and true acknowledgment replay remain reads, distinct from state change.

Proposed focused files/signatures:

| Files | Change responsibility |
|---|---|
| contracts MinutesContracts.java | Add data-only ConfirmationDraft, preserve old FullRow/profile shapes. |
| contracts TranscriptContracts.java | New exact request/read/receipt types and bounded budget types. |
| core minutes/contract TranscriptConfirmationHash.java, StoredSourceGraph.java | Shared confirmation-independent validation/projection, additive hashDraft, old golden compatibility. Minimal StoredFactsIndex/ArchivedProfileResolver internal reuse only if needed. |
| core transcript TranscriptFactsReader.java, TranscriptService.java, TranscriptRepository.java, CorrectionDomain.java | ReadCurrent/readOwnerRevision; save/replaceMarks/restore/confirm; explicit unavailable/qualified correction composition. |
| adapters transcript JdbcTranscriptFactsReader.java, JdbcTranscriptRepository.java, TranscriptPreparer.java, TranscriptDigests.java, TranscriptEventReferences.java | Bounded actual facts/preparation, independent lookupAcceptedResult, atomic write/replay, safe events; consume final Task3 issuer. |
| adapters transcript JdbcTranscriptAuthority.java | Narrow shared issuer extension for complete saved graph/rank. Exact names after adopted source; no duplicate authority. |
| api TranscriptController.java, TranscriptErrors.java, TranscriptConfiguration.java | Routes, safe errors, one actual source/access/outbox composition, unavailable new-command branch independent of replay. |
| contracts/openapi/transcripts.yaml and api.yaml | Complete producer API before Task12 client regeneration. |

Event names proposed TRANSCRIPT_SAVED→TRANSCRIPT_REVISION and TRANSCRIPT_CONFIRMED→TRANSCRIPT_CONFIRMATION must align with actual registry grammar and be registered with actual same-meeting row validators before use. Reference UUID alone insufficient. Audit/event payloads omit body/source path. Repeated unchanged confirmation creates no duplicate business-change event; optional acknowledgment audit must not imply a version change. No SQL migration assigned or proposed here.

## 10. Future evidence matrix — nothing executed

| Group | Required proof |
|---|---|
| Pure compatibility | Preserve HashProfilesTest independent hex/SHA, StoredSourceGraphTest, ConfigBindingsTest. Draft vs actual confirmed hash same saved ID for empty/all/subset marks, Unicode/supplementary/null metadata/row permutations. Each mark add/remove changes hash. Draft facts have no fake confirmation row. |
| Original archive chain | Actual four processing archives, strict bytes/schema/refs/closure/manifest; missing/corrupt/unknown/extra archive, forged Artifact, wrong input/task/fingerprint, sidecar missing/unknown. Changed current default never repairs old provenance. data-only helper cannot authorize. |
| Real pipeline | Native and S3 real uploads/controllers→Task4 intake→Task5 registered queue/WorkerLoop→actual bytes/parser→SUCCEEDED typed RAW→Task6 explicit save/confirm. Hand-inserted controlled structural fixtures never labeled actual pipeline evidence. |
| Immutable save/restore | Preserve stable IDs/ordinals/paragraph/speaker/time/input/batch/profiles, null human producer plus genuine RAW ancestry. Correct new ID/parent/history/r+1. Member never sees RAW before save. Older restore copies exact complete marks, no confirmation credit. |
| Complete DB acquisition | Missing/extra/cross-meeting parent/segment/mark/member/file/task, cycles/depth/node/edge bounds, queried zero marks vs omitted query, wrong producer state/output, duplicate original session. SQL trace detects incomplete query/late root/table scan. |
| Bounds | Huge body rejected before transfer; UTF8/scalar/UTF16 supplementary differences, limit/+1, cumulative ancestors/files/tasks, character stream exact lengths/EOF, JSON escaping, cancellation/overflow. No partial successful graph. |
| Genuine authority | Owner/member/outsider/admin-only; roster revoke/identity inactive/governance/delete/cancel/publish between prep/final. Wrong issuer/source/holder/gate/ended/suspended token. All tasks before any attempt, one gate per final transaction, no archive/parse/hash/storage under roots. |
| Atomicity/races | Content-only/marks-only/save/restore/confirm with injected row/readback/audit/flush/commit failures roll back graph/pointers/debt/fence/idempotency/events. Concurrent same-r one effect/loser409. Exhaustion after replay; no fake task from confirmation. |
| Replay | Mandatory first lookup, same-key body/token equivalence, changed body409/revoked owner denial; old acknowledgment never reattaches. Ordinary preflight failure concurrent winner second lookup, uncommitted wait/timeout/rollback, final-claim replay, corrupt result503. Unavailable new branch cannot remove replay route. |
| Confirmation | Real first insert/no placeholder. Different-key unchanged fresh confirm one ID/no increment; pointer-cleared explicit reattach same ID with r+1; old replay never reattaches. RAW/noncurrent/blank reject. Actual unique race and stable receipt versus fresh observation tokens. |
| Corrections | Real qualified disabled-not-installed wiring; unknown/installed-without-reader/contradictory qualification unavailable. No claim of actual C03 PENDING test. Dirty browser does not submit confirm; server rejects bodies/hash extras/noncurrent ID. |
| HTTP and UI | Actual HTTPS/session/Origin/CSRF four sessions; member current positive, RAW/history/by-ID negative; no-store all results, range full authorization. Values9007199254740991/2/3 and9223372036854775806, invalid grammar/range/dual mismatch/replay. Task12 separately proves browser opaque tokens and draft retention without route.fulfill successes. |
| Restart/downstream | Fresh JVM finds actual saved graph/marks/confirmation/original archives/hash after defaults change, lost ACK discovery. Task10 candidate disposition and Task11 freeze races verified before runtime activation. |

Preserve relevant MeetingRevisionIT and existing meeting concurrency regression (implementation author resolves exact actual class name), B04 legacy/source/receipt, B06 standalone-reader/queue/archive regressions. These are future gates, not test pass claims. No B01 IMM/B04 Q401/company/HA/capacity qualification implied.

## 11. Unresolved technical alignment and delivery status

No new product approval requested. Concrete gates before implementation:

1. Reconcile saved graph methods and child/confirmation rank with final adopted Task3 private issuer/CompleteReadPolicy/pre-gate APIs. If impossible, report exact source contradiction for reviewed extension, never mint alternate authority. Active author patch is not adopted source.
2. Task5 must freeze real RAW typed output and revision=original TaskSpec.businessVersion, prove unique history allocation. Human r+1 requires actual history≤r. Unsupported/inconsistent history fails, no renumbering/DDL inference.
3. Root pins qualified positive effective acquisition/request/response/archive/canonical budgets and actual disabled-correction installation qualification. Proposed defaults are not qualification evidence.
4. Task10 candidate-disposition and Task11 publication-in-progress guards must be adopted on actual Task6 version writes before those runtimes activate; no premature installed/no-op claims.
5. Independent review must check byte-compatible factoring, complete locking/acquisition, replay/current-ack distinction, current/member/owner projections and narrow copy semantics before dispatch. Any proved schema need goes to root; no migration allocated here.

Status: detailed design proposal ready for review; no source/runtime/test implementation claims. Artifact SHA delivered separately.
