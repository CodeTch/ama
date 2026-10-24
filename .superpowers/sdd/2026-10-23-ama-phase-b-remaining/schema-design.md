# Task 2 runtime metadata design — UNASSIGNED, review only

2026-10-23. Source baseline declared by coordinator: `34cb226aefe99c67d3681af68d748b0aeea4e073`; controlled-runtime documentation baseline `bd47aa0e1b24fa89fee5385860454f43dc0aecd7`. This is a design proposal and candidate DDL, not a formal migration, independent approval, test result, or runtime activation. Only this file and sibling `UNASSIGNED-runtime-metadata.sql` are authored. No DB, Git, test, source, old artifact, private environment, or formal migration operation was performed.

## Sources actually read

- `docs/superpowers/specs/2026-10-23-ama-phase-b-controlled-runtime.md` and `docs/superpowers/plans/2026-10-23-ama-phase-b-remaining.md`.
- `docs/acceptance/b/remaining-preflight/b06-preflight.md`, especially forward batch discrimination, and `b07-b08-preflight.md`, especially editor visibility, candidate retention, and atomic output.
- `docs/superpowers/specs/2026-10-22-ama-minutes-ux-decisions.md`.
- `docs/database/migration-register.md`, `ama-data-dictionary.md`, relevant complete-schema draft definitions, and actual V008/V011/V012 SQL. The complete-schema draft contains later proposed domains; it is not the deployed schema oracle.
- Actual `JdbcMeetingAccess`, `JdbcMeetingRepository`, and `GenerationFingerprint`: root/subject equality and identity-before-root locking, separate published write check, explicit creation path, fixed generation projection, and idempotency record storage.

## Adopted narrow ruling and minimal shape

Exactly two new sidecars, four columns, two primary indexes, two CHECKs, and two physical foreign keys. No changes to any existing table, canonical FullRow, hash profile, manifest encoding, archive dependency, or old migration. There is one per-meeting editor guard and no per-editor/session or generation snapshot table.

Coordinator explicitly adopted the conservative rule during this design: a positive editor epoch permanently suppresses only automatic first save for that meeting. Explicit adoption remains possible after current authorization/version/provenance checks. Abandoning an editor may therefore require manual adoption. There is no reset, close, timeout, heartbeat expiry, or trusted browser assertion that returns the epoch to zero. This does not promise that every later apparently idle state automatically saves.

Absent editor guard means UNKNOWN and never permits automatic save. New meeting creation atomically inserts zero. An existing meeting's first authorized editor intent may create one; neither reads nor generation may initialize an old meeting to zero. This distinction avoids treating pre-deployment unsaved typing as nonexistent.

## Exact data dictionary proposal

All tables use InnoDB and table default `utf8mb4_0900_ai_ci`, matching the existing adopted convention. UUID columns use `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin`; callers validate canonical server UUID syntax using the existing UUID contract. Physical foreign keys guarantee existence, not current authority or same-meeting facts beyond the keyed parent.

|Table / field|Exact SQL type|Null / default|Meaning|
|---|---|---|---|
|input_batch_fingerprint_profile.batch_id|char(36) CHARACTER SET ascii COLLATE ascii_bin|NOT NULL; no default|PK and FK to immutable input_batch.id|
|input_batch_fingerprint_profile.profile|varchar(64) CHARACTER SET ascii COLLATE ascii_bin|NOT NULL; no default|Exact supported batch algorithm identifier, currently only `ama.input-batch.v1`|
|meeting_minutes_editor_guard.meeting_id|char(36) CHARACTER SET ascii COLLATE ascii_bin|NOT NULL; no default|PK and FK to meeting.id; at most one guard per meeting|
|meeting_minutes_editor_guard.intent_epoch|bigint (signed)|NOT NULL; no default|Server-owned nonnegative monotonic counter; zero is proven untouched new meeting, positive is sticky first-save suppression|

- `ck_input_batch_fingerprint_profile_0`: `OCTET_LENGTH(profile) = 18 AND profile = 'ama.input-batch.v1'`. ASCII plus binary comparison rejects case changes; byte-length rejects trailing-space equality under ASCII PAD SPACE collation. Empty, unknown version, non-ASCII and whitespace variants must fail. Do not trim, lowercase, normalize, truncate or coerce a supplied value into the supported code. Application inserts a server constant only after authentic algorithm selection; strict SQL mode remains required for application writers.
- `ck_meeting_minutes_editor_guard_0`: `intent_epoch BETWEEN 0 AND 9223372036854775806`. Signed BIGINT intentionally matches Java long without unsigned overflow ambiguity; one value below Long.MAX_VALUE is reserved as an arithmetic safety margin. This representation bound is not a product quota. Increment only below 9223372036854775806; exhaustion fails closed without wrap/saturation/reset.
- Both PKs are the corresponding FK support indexes. No duplicate indexes, alternate UUIDs, identity FKs, timestamps, owner columns, or expiry columns are needed. Existing audit records carry actor/time/action; guard ownership always comes from the current meeting and current identity, never a cached creator.
- `fk_input_batch_fingerprint_profile_0`: batch_id -> input_batch(id); `fk_meeting_minutes_editor_guard_0`: meeting_id -> meeting(id). Both `ON UPDATE RESTRICT ON DELETE RESTRICT`; no deferred FKs.
- No defaults intentionally prevents omitted profile/epoch from becoming an apparently qualified row. SQL constraints do not prove monotonic transitions or authorized insertions; narrowly owned repositories and the genuine transaction gate enforce those invariants. A SQL administrator is not the application threat boundary.

## Batch profile lifecycle and compatibility

1. On authentic new intake, compute exactly the existing `ama.input-batch.v1` bytes and fingerprint outside DB root locks with explicit deployment resource budgets. Under the fresh complete domain gate, insert input_batch and its profile sidecar, ordered members, transcription_input, file-reference handoff, immutable task and audit/outbox atomically. Profile is server-selected, not client authority. Failure at any stage rolls back all DML and leaves no half-qualified batch.
2. Every new production batch created by this runtime must have the sidecar in that creating transaction. The FK alone cannot enforce this parent-to-child requirement; intake and writer tests must. Another creator lacking a sidecar produces an unsupported batch and is not silently repaired.
3. Missing sidecar means legacy/unknown for new and historical rows equally. No inference from 64-character hash shape, dates, kind, file membership, a recomputation match, defaults, earlier test receipts, or known original code. No migration backfill, lazy read backfill, or later attachment to a historical batch is allowed.
4. Unknown/missing profile fails closed for verification-dependent new processing/generation/adoption. Owner diagnostic/history reads may surface explicit unsupported-profile status under existing read authorization. They do not rewrite old rows or old digests. Future supported profiles require an explicit reviewed forward CHECK expansion and dispatch; never reinterpret v1.
5. Sidecar is immutable after creation. Parent and member identities and profile remain fixed while historical input is retained. Normal cancellation, deletion marking, governance changes, publication and ownership changes do not rewrite it. Permanent cleanup deletes this child only within the authorized parent cleanup sequence after retained provenance references are resolved; never delete it alone to reclassify history.

## Editor lifecycle, API contract and transactions

Proposed operation: authenticated owner POST editor-intent with meeting ID, expected meeting revision and the normal semantic idempotency key. Final endpoint spelling belongs to Tasks 10/12. The request contains neither an authoritative epoch nor `notEditing`, `editing=false`, session-expiry proof or an auto-save permission. Return committed `meetingId`, current `meetingRevision`, current `cancelEpoch`, server-issued decimal-string `editorIntentEpoch`, and current saved-body reference. Decimal string prevents browser precision loss above 2^53. The response is a successful intent acknowledgment, not a perpetual access capability.

Before acknowledging, the same genuine issuer/DataSource/transaction holder must lock/recheck complete active identities, task subject, meeting, and editor guard in that order (and any other domain rows in one registered canonical order). Verify active current owner and EDIT, matching subject/meeting revision and cancellation epoch, lifecycle ACTIVE, governance_state ACTIVE, ever_published=0, stage != PUBLISHED, expected revision, and any existing write gates. `JdbcMeetingAccess.require(EDIT)` alone is insufficient: its current implementation separately leaves the published check to writers. Roles/admin/member access never grant editor authority. No network/file/archive/hash work under these locks.

The meeting root is the serialization lock even if the guard row does not exist; do not rely on an absent-row/gap lock. A prelocked writer may use:

```sql
-- New meeting creation ONLY, in the original create transaction:
INSERT INTO meeting_minutes_editor_guard (meeting_id, intent_epoch) VALUES (?, 0);

-- First authenticated intent for an existing meeting with an absent guard:
INSERT INTO meeting_minutes_editor_guard (meeting_id, intent_epoch) VALUES (?, 1);

-- Subsequent non-replayed intent, while the same root and guard are held:
UPDATE meeting_minutes_editor_guard
SET intent_epoch = intent_epoch + 1
WHERE meeting_id = ? AND intent_epoch = ? AND intent_epoch < 9223372036854775806;
```

Exactly one affected row is required. Do not expose an unrestricted upsert that can set zero or accept a caller epoch. On duplicate creation/guard conflicts, re-read under the same root or abort; do not silently reset. No operation deletes the guard during the retained lifetime of the meeting.

Persist the intent transition, idempotency success, audit and any required outbox together; final flush last. Do not acknowledge until commit succeeds. Audit/flush/commit failure leaves the UI disabled, and rolls back the guard change. A lost response may leave a committed positive epoch and conservatively suppress automatic save. No compensating decrement is permitted. The intent operation changes neither meeting.revision nor task_subject.business_version, cancel_epoch, effective content, current pointers, title, generation fingerprint/debt or reviews. A content save/adoption is a different operation and follows normal version/pointer/review invalidation.

The existing idempotency row can retain `result_id=meetingId` for this new operation (do not reinterpret old operation results). Digest covers actor/resource/operation and expected revision. Same-key different semantics conflict. Replay reauthorizes against current identity/lifecycle and reads the already-positive guard under lock; it does not increment again and returns the CURRENT epoch/projection, not a purported frozen original epoch. If the original expected revision no longer holds, return a conflict/current projection and preserve the browser draft; do not mint a fresh acknowledgment through replay. Missing/nonpositive guard on successful intent replay is an invariant failure. Define this operation's current-state replay semantics explicitly in OpenAPI and test it; old meeting mutation replay behavior stays unchanged.

The browser keeps typing, IME composition, paste, drag/drop, dictation, restored drafts and programmatic editable-field changes disabled until this acknowledgment is received for the loaded meeting/version. Begin handshake when entering editing, not after the first input event. Existing local drafts are preserved while the handshake refreshes; a late generation or conflict cannot replace them. If first auto-save commits before the intent request, the response/CAS must reconcile the new saved-body revision before enabling a blank/stale editor. A timeout never enables editing by assumption. Further content writes always freshly authorize and CAS; possession of any epoch alone grants no writes. Stale acknowledged epoch can be rejected as a guard conflict, with draft retained and a fresh handshake available. Multiple editors can advance epochs; no exclusive editor lease is implied.

New meeting creation must insert guard zero in the same original transaction as meeting/subject/projections/audit. Creation replay never initializes a missing guard on an older meeting. Historical missing guard remains unknown until actual owner intent writes one. Deploy compatible creator and writer/worker code together; disable incompatible older writers during cutover. Runtime missing TABLE is a schema-unavailable failure, distinct from a missing ROW, and never triggers DDL or a default zero.

## Generation snapshot, first-save races and retention

At generation request preparation, read guard state separately from the adopted generation hash projection: `UNKNOWN`, `ZERO`, or positive epoch. Inside the actual enqueue transaction under the shared meeting root, freshly capture/recheck that state. Outside-lock observations and client state are advisory. Existing canonical generation bytes/15 archive profiles remain unchanged.

Automatic first save is permitted only for a newly qualified production generation input/task and complete valid result graph, under fresh active owner/domain authority, valid original business-version/cancel fences, current matching input, no effective saved body, existing guard row with epoch=0, and all other adopted first-save conditions. Both candidate output and adoption decision occur atomically with task/external completion. Ordinary retry/duplicate completion cannot create a second adoption. HUMAN title origin remains protected.

No durable per-generation epoch snapshot is needed for this intentionally stronger zero-only predicate. Proof: the only transition into zero is new meeting creation; thereafter a retained guard never decreases or disappears. If the final locked guard equals zero, its earlier generation snapshot must also have been zero, and no acknowledged intent could have intervened. Unknown/positive at enqueue cannot become zero later. This proof depends on the creation/no-delete/no-reset invariants and fresh qualified task policy; it does not authorize old/test tasks merely because an epoch happens to be zero. Positive snapshot == positive current is NEVER sufficient: a user may have started typing before generation and still be typing. A later design permitting that recovery would require a new durable draft/close protocol and independently reviewed metadata; this proposal does not include one.

|Interleaving|Required outcome|
|---|---|
|Generation snapshot 0 -> intent commits 1 -> model completion|Fresh guard 1; candidate retained, no automatic first save. Original businessVersion remains unchanged, so intent alone does not require stale retention.|
|Intent already positive -> generation -> no further intent|Candidate only even though captured/current epochs equal. Abandoned or active editor cannot be distinguished safely.|
|Auto-save locks root first, commits -> intent locks next|Auto-save preceded edit authorization. Old expected revision conflicts or client reloads current body before editing; blank/stale draft is not enabled or overwritten.|
|Intent locks root first, audit/commit fails -> completion|No acknowledgment and no enabled typing. Rolled-back epoch remains 0; auto-save may proceed if every other condition still holds.|
|Intent commits, response lost or browser closes -> restart/completion|Positive durable epoch survives; candidate only; retry/replay reauthorizes.|
|Two completions or generations race at epoch 0|Shared root/version/effective pointer/task uniqueness permits at most one first save; the other is candidate or fenced according to original input.|
|Saved edit, metadata edit, transcript/input change advances businessVersion|Generic B05 fences stay strict. Only the separately reviewed Task 7 retention protocol may preserve immutable candidate under fresh current identity/same cancel epoch/lifecycle; this schema does not grant it.|
|Revoked owner, identity generation/session invalidation, deletion/cancel, governed or published meeting|Fresh gate denies intent/adoption/effects; stale response or stored epoch grants no authority. External receipt-only handling remains separate.|

Explicit adoption requires current owner, current guard handshake/epoch as appropriate, fresh expected meeting revision and current input/provenance validation against the candidate's original frozen input. It cannot relabel an obsolete candidate as current, reset the guard, or infer access from retained rows. It is available with positive epochs when all actual adoption checks pass. Body saves/edits preserve typed drafts on conflict. Published, withdrawn/obsolete and pending-governance states never reset the guard or restore mutability; retained read authorization remains the existing independent policy.

Logical deletion retains the guard through cancellation and all late-worker fences. Physical parent cleanup may explicitly delete it only when the meeting itself is permanently being removed under the deletion protocol; the meeting UUID is never reused. No orphan guard, cascade, retention TTL or standalone reset endpoint.

## Exact migration and review gate proposal

The actual migration location contains twelve scripts: V001, V002, V005, V006, V007, V008, V009, V010, V011, V011_1, V012, V013. Preserve every byte, SHA and applied history. Parent V013 is frozen. V014..V027 retain their original reserved owners; next NEW INTEGER remains V028. No version is assigned here.

After independent design/DDL review, root alone may case-register the stage patch 013.1 if it proves, with the pinned resolved Flyway implementation, that `13 < 13.1 < 14` and that every target history has the complete successful twelve-script prefix and no applied next integer 14 or later. This conditional possibility is not allocation or ACK. A failed, unknown, divergent or high-version history is outside this path; stop and retain evidence. No outOfOrder, repair, reset, renumbering or rewriting existing SQL.

Root must read final complete SQL, assign a name centrally, freeze complete bytes/SHA in a committed register entry, separately ACK that exact SHA and authorized NEW fixture, and only then permit first apply. Candidate SQL is not in the formal location. The matching dictionary/design delta must be reviewed/adopted explicitly; do not edit old historical design copies. Stop writers during migration/cutover. Schema installation alone cannot activate profile issuance, auto-save, editor UI, or runtime external workers.

## Proposed independent test oracle (not executed)

|Gate|Concrete assertion/evidence required|
|---|---|
|Initial-zero and prefix|New owned MySQL fixture with initial zero tables; exact server/version/SQL mode and native-I/O qualification recorded. Apply all twelve old scripts in order; save raw history/checksums/source SHA/UTC epoch and all old rows/SHOW CREATE/five metadata groups before new DDL. Do not touch previous fixtures.|
|Real RED|Before candidate apply, required two tables are absent and new-schema assertion fails for that reason. Before/after this failed assertion, old rows/schema/history are equal. Setup failures are not RED.|
|Exact physical shape|After acknowledged apply, actual tables/columns/index columns/CHECKs/FK columns equal reviewed oracle: 2/4/2/2/2. Include engine/default collation, every column type/null/default/collation/order, signed BIGINT, PK order, CHECK expression/enforcement and RESTRICT actions. Compare actual-to-expected sets both ways; no extras. Counts alone do not pass.|
|Positive/negative constraints|Valid parent/profile and guard 0/1/9223372036854775806 insert; duplicate PK, orphan parent, NULLs, missing required fields, negative epoch or Long.MAX_VALUE, wrong case, empty/unknown/non-ASCII/leading or trailing whitespace profile reject. Verify CHECKs are enforced. Strict mode catches overlength/overflow; service must reject conversion/truncation and exhaustion independently. Parent key update/delete with live child rejects.|
|No historical reinterpretation|Seed a valid old batch with no sidecar whose hash happens to equal v1 recomputation, plus old meeting without guard. After apply and all read-only/restart paths both remain sidecar-absent. Profile verification unsupported, guard auto-save ineligible. A new authentic v1 batch is accepted; missing new sidecar remains unsupported. No default/template/date heuristic.|
|Old data/schema equality|After migration, all preexisting user-table rows, SHOW CREATE and five metadata groups compare exactly to before. Exclude only the two new tables from whole-schema comparison and separately compare them to the oracle. Existing target-parent metadata must not gain unwanted indexes. History retains identical twelve-row prefix; exactly one reviewed successful new row appended.|
|Rerun|Private target and latest Flyway reruns each apply zero migrations; existing/new metadata, old rows, source hashes and history remain equal. No IF NOT EXISTS is used to conceal drift.|
|Batch DML rollback|Force failure after batch/profile/members/input/file handoff/task/audit and at flush/commit boundaries. Entire transaction rolls back; no orphan/profile-only/batch-without-profile task and no fence/reference change. Successful transaction commits all together.|
|Creation and intent rollback|Create failure after guard insertion leaves no meeting/subject/guard/projection/idempotency/audit/outbox. Intent failure after increment leaves epoch and versions/rows unchanged and no acknowledgment. Retry after uncertain commit never decrements; same semantic replay does not increment again.|
|Concurrency and restart|Barrier-controlled two intents yield unique increasing committed epochs; exact request replay, changed-payload conflict, old revision conflict, multi-tab and above-2^53 decimal string round trip. Process restart retains positive epoch. All race-table cases above run with actual queue/Worker/DB transactions, plus browser IME/paste/restore-before-ACK blocking.|
|Authority and lifecycle|Current owner succeeds; member/outsider/admin-role-only, stale session/identity generation, revoked owner, foreign holder/issuer/DataSource, DELETING/DELETED, cancelled subject, every non-ACTIVE governance state, ever_published even when stage changed, and PUBLISHED all fail with no mutation. Replayed keys reauthorize.|
|No proof inflation|Owner epoch is not authority; caller-supplied booleans/epochs cannot enable auto-save or invent profile. No completion/retention shortcut after businessVersion change. Existing B06.2 hash vectors and fixed FullRow shapes remain byte-identical; malformed mixed-profile closure fails closed.|

DDL rollback is NOT asserted: MySQL multi-statement DDL may partially commit. If a CREATE or FK addition fails, retain actual partial schema and failed history, stop, and seek a centrally registered forward recovery. DML rollback assertions above are separate and mandatory. Failed schema tests do not authorize editing the frozen candidate, relaxing oracle, or erasing history.

## Remaining decisions and scope limits

No unresolved product question is required by this proposal: sticky suppression/unknown legacy behavior was explicitly adopted by the coordinator. Remaining gates are independent design/DDL review, root's actual version-order proof/allocation/freeze/ACK, actual schema evidence, and service/UI implementation review. Review should specifically challenge the zero-only monotonic proof, signed epoch representation, current-state idempotency replay semantics, and absence of any initialization/reset path on legacy/read APIs.

Tasks 3/4 implement authenticated batch creation; Tasks 9/10/12 implement guard-aware generation, editor acknowledgment and UI; Task 7 alone implements stale candidate retention. This schema proves neither full Phase B completion nor company protocol/HA/capacity qualification. No production quota, company contract, correction table, material provenance v3, generation snapshot hash change, or publication table is introduced.
