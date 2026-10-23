# Task 4 implementation report

Task4 controlled LOCAL implementation is complete for independent review. This commit adds real authenticated HTTP transcript-import acceptance, persisted discovery, registered production task policy, shared Spring assembly and lossless HTTP tokens. Execution remains `NOT_INSTALLED` and `businessReady=false`; no parser Worker, RAW writer, save workflow, S3 runtime, browser main flow, company-service runtime, HA or capacity qualification is claimed.

## Source and custody window

Original Task4 BASE: `2ec38c0350f466fc4292d588051c5694cc099cb5`. Branch: `codex/phase-b-completion-20261023`. Frozen precommit HEAD: `875047cdbcd6ddd17f5c267f8998b7a5a4378b6c`. Parent-only Task9/10 design commits intervened; they changed no backend/contracts source. The report and 32 explicitly owned source paths are included in this Task4 commit; the commit SHA is supplied by the commit result. `owned-paths.txt`, `owned-source-manifest.json`, `self-review-checks.json` and command-bound `*.sources.json` are the exact review inventory. No root index, unrelated author source, formal SQL, Worker or UI source was modified. The existing Task2 minutes-editor guard and FileReferencePolicy remain intact.

The final production source seal was exercised by `final-focused` (19 HTTP cases). The only later Java source change is the integration-test fixture: an explicit lost-commit-acknowledgement case and accepted READY/fullspec custody guard, recorded in `final-test-custody-change.json`. It passed separately, then all 20 Task4 HTTP methods and the inherited regression ran on the final exact 28 Java file hashes. `verification-source-deltas.json` records every earlier run's differences. No final Java bytes changed after that regression snapshot. OpenAPI is sealed independently in the same 32-path manifest and linted/bundled from those bytes.

## Implemented behavior

`POST /api/v1/meetings/{id}/transcript-imports` accepts one canonical ordered source list with one Idempotency-Key. Safe request normalization and semantic digest precede the mandatory independent DB-only accepted-result lookup. Historical replay requires fresh owner EDIT authorization and actual stored task/input/batch/profile/ordinal/fullspec/producer agreement. It is independent of current archive/default-write/intake availability and current publication/source epochs. Exactly one new independent lookup follows the named ordinary preparation failures, including current operational budget failure. The serialized final claim checks replay before new-intake CAS/exhaustion/source prerequisites. Mismatched, revoked, uncommitted, corrupt, absent and database-failed winners never create speculative acceptance. Actual uncertain commit acknowledgement is recovered by the same key/body and exact persisted task.

New acceptance freezes archive closure, canonical manifest, batch fingerprint and canonical `ama.transcript-import-producer.v1` identity before held roots. Genuine Task3 draft/reservation/row-seal/source receipts and one issuer/DS/B03 gate insert the real batch/profile/members/input, bind all sources once, advance meeting/search/subject r→r+1, clear confirmation pointers, create generation debt and enqueue one `TRANSCRIPT_IMPORT_V1`/`TRANSCRIPT_IMPORT` task. Idempotency result, audit and typed events are in that transaction; outbox flush is last. Each injected stage and refused commit rolls the complete transaction back. No fake transcript/RAW/output row exists before Task5.

The same authority privately creates the production `ImportTaskDomainPolicy`. Its enqueue adoption proves the exact owned transition and authentic holder/fullspec. Ordinary queue prepare discovers immutable complete input/source metadata and canonical proofs before roots, locks identities/meeting/input/batch/profile/members/sources in the existing phases, and lets the queue own actual task/attempt/lease checks. SKIP LOCKED input contention returns no lease promptly. Stale-task retirement uses actual metadata and queue fence/state decisions without granting Worker source bytes. Success output validation requires real RAW/input/batch/fingerprint/producer provenance and valid bounded segments; failure cannot carry output, external/RECONCILING paths fail closed. Task5's private AfterTask/writer-validator extension is still future work.

Spring serves the accepted-replay branch even when processing is enabled with missing positive deployment bounds. Enabled new intake requires explicit positive archive/canonical/index/aggregate bounds, protocol max64 files and 64KiB manifest/request envelope. Outbox reference validation is DB-only and independent from queue/authority construction. Default processing file purpose is the actual transcript purpose policy; the shared registry/authority/queue use one assembly. Application contexts start in enabled and unavailable configurations.

Nine GET routes expose processing-capabilities; upload list/receipt/part pages; completed files; input list/detail; task list/detail. Owner discovery uses a fresh EDIT gate; capabilities permit existing READ access. Data comes from stored receipts, complete bounded metadata, genuine source eligibility and persisted tasks. UUID and numeric keyset pages have explicit limits and sentinel rows; Unicode/name checks happen in bounded SQL projections before text transfer. No provider handle, storage key/backend path, manifest body or authority DTO escapes. Unknown/ambiguous route/provenance is unavailable. A known ineligible neighbor does not relabel a genuinely valid source. READ_ONLY historical sources and the exact B04 OPEN/FINALIZING/COMPLETED/ABORTED/FAILED action/terminal retry matrix are preserved and checked against real operations. All new routes, including authentication failures, use `private, no-store`.

Shared expectedRevision parsing accepts legacy integer, canonical ASCII decimal token, or equal dual forms, normalizing before the existing info/roster digest. No numeric 2^53 cap was introduced. Mutation/read DTOs add revision/access tokens and original upload begin adds `partSizeToken` while retaining numeric representations and original part-size semantics. Every actual info/roster/intake route exercises 2^53−1, 2^53, 2^53+1, Long.MAX_VALUE−2 and Long.MAX_VALUE−1, equal legacy/token/dual replay and stale/new mutation exhaustion. New mutation at revision9223372036854775806 returns409 REVISION_EXHAUSTED; accepted replay precedes that rejection.

## Final verification

| Command label | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| final-focused | 21 | 0 | 0 | 0 |
| lost-ack-custody-final | 1 | 0 | 0 | 0 |
| final-regressions | 150 | 0 | 0 | 0 |
| config-restart-final | 1 | 0 | 0 | 0 |
| all-unit-final-resumed | 223 | 0 | 0 | 0 |

The final distinct set is **223 unit tests + 151 integration tests = 374**, all passing in the applicable final invocations. `final-focused` and `lost-ack-custody-final` overlap the final regression and are not added again. The inherited integration suite counts are:

- ama.meeting.MeetingConcurrencyIT: 5
- ama.meeting.MeetingRevisionIT: 30
- ama.storage.FileRegistryUploadIT: 39
- ama.storage.ProcessingIntakeHttpIT: 20
- ama.storage.TranscriptAuthorityIT: 22
- ama.storage.TranscriptAuthorityReviewIT: 4
- ama.task.B06QueueSeamsIT: 18
- ama.transcript.ProcessingFactsReaderIT: 12

ConfigArchiveRestartIT is a separate genuine five-child JVM config/archive recovery test: A commits a controlled B06 fixture, fresh B recovers the old immutable input after append/default change, and three fresh damaged-copy probes return typed unavailable without DB mutation. This proves archive recovery, not an installed importer/Worker restart. It has its own guarded new UUID directory, full expanded argv/process receipts and retained archive copies in `config-restart-final-retained/`.

OpenAPI lint and external-reference bundle passed; `openapi-final-bundle.yaml` is the final Task4 evidence. The earlier bundle remains preserved. Self-review corrected only the original upload partSizeToken schema to its unchanged positive int64 range, independently of revision tokens; openapi-size-range-correction.json records this OpenAPI-only source delta, with fresh lint/bundle and no Java change. The protected accumulated bundle was not overwritten. Full commands, outputs, actual process arguments, source snapshots and copied fresh JUnit XML are retained per label. The bundle retained the existing duplicate component-name rename warnings (Conflict/Unavailable) and exited0; no warning is hidden. The canonical `python3 tools/checks/run.py unit` discovered actual *Test classes; no historical exact-schema/reset suite ran.

## RED and failure history

All failed/setup invocations are retained with their original source snapshots and fresh XML; `verification-summary.json` includes their real counts/exit status. Meaningful REDs include missing token acceptance, missing intake/discovery endpoints, revision exhaustion overflow, corrupt accepted producer still returning202/discovery200, duplicate JSON returning503, stale queue acquiring obsolete source permission, locked-input blocking, a committed winner missed after operational413, and a valid file mislabeled beside DELETE_PENDING. Each has a covering green on the final source. Independent `producer-golden.json` derives the fixed canonical producer hash in Python (tags/length framing/order), and the Java golden test proves it without deriving the expected value from the implementation.

Earlier `intake-red`/`intake-red-2` were setup mistakes (part response is204, completion path is `/completion`), `intake-green` was an ambiguous Role import, and the first manifest-matrix assertion expected400 where inherited B04 correctly returns409. These are not product RED proof. The initial focused race/bounds run retained one duplicate-JSON failure; the later action/malformed suite fixed it and the final full regression covers it. One model-capacity interruption lost a preparatory boundaries command session without a command receipt; process absence was checked before the sole retained `boundaries-green` invocation. No missing invocation is called passing evidence.

Focused cases cover ten write/commit fault stages, immediate/historical/restarted-unavailable replay, seven concurrent recovery outcomes, current fresh owner/member/outsider/admin/session-generation/CSRF/Origin/governance rejection, actual queue claim/renew/failed completion and rejected fake output, source/action matrix, invalid JSON/token/cursor/unknown fields, oversized Unicode names, complete bounded graph admission and no duplicate task/bind. A genuine JCA SHA-256 delegate observer records hashes outside roots and zero while actual meeting roots are held, with source lock traces. The observer changes no hash bytes and grants no authority.

The user/host interruption occurred after config restart completed and while the first all-unit-final invocation had only written its guard/source snapshot. No completion receipt/log/XML exists for that interrupted attempt. No process survived the interruption. Only that incomplete unit gate resumed under all-unit-final-resumed, followed by the pending OpenAPI gates, using a pure runner that does not load runtime credentials or claim database access. interruption-recovery.json retains this distinction. Root subsequently recovered the exact original container (stopped with exit255/OOMfalse after Docker Desktop stopped), verified its fullID/owner/pinned image/resource limits, and started only that same container. Its original dynamic port mapping resumed at127.0.0.1:45157; all374 completed runtime/unit receipts retain their historical58062 window. The authorized final-audit-restored invocation performed only read-only history/custody SQL on that restored original fullID/schema, proving13 migration rows and100 Task4 task/attempt rows, all tasks terminal and attempts ended. No tests were rerun, no fixture was replaced, and this operational restoration is not database-server restart qualification. Root custody receipts are W/post-user-interruption-mysql-selected.json and W/post-user-interruption-mysql-start.json; final-migration-history.tsv and final-task04-custody.tsv retain the refreshed facts.

## Resources and fixture retirement

Only exact this-run MySQL container `ama-test-b01-ad9f1af29eff`, ID `eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502`, owner `remaining-20261023`, loopback58062/schemaama_b01_fixture was used after selected-field identity/running/port guards. Only `.tools/phase-b-remaining/env.sh` supplied private credentials; Config.Env and password values were never printed. Public JDK21, pinned ./mvnw3.10.0 and public Node/Redocly paths are recorded. The 13 formal migrations through immutable V013.1 validate unchanged; final read-only history is retained. No DDL/reset/drop/repair/outOfOrder/new container/S3/old fixture recreation occurred.

Preparatory runs before `discovery-spec-red` set Maven512MiB/2CPU but did not explicitly constrain the test fork heap; those historical runs are preparatory evidence only. They are not retroactively qualified. From that label onward the runner records inherited JAVA_TOOL_OPTIONS512MiB/2CPU. Final native HTTP, inherited regression and units use Maven/test512MiB/2CPU. Task4's own native service is in-process under that JVM; it has no service child. No native-service child or Registry API child was launched in the final selected regression; their approved64MiB/192MiB limits are patterns, not claimed execution evidence. The separate config-restart run uses Maven/test explicit512MiB/2CPU and only its five public child probes inherit64MiB/2CPU. `*.children.json`, commands, fixture paths and task04-custody retain actual observations, not inferred extra execution.

Task4 native/TLS/archive roots use fresh guarded task04 UUID paths with resolved target containment and nonexistence checks; preparatory fixtures' fresh random PKI paths are retained separately. No old PKI/private env was sourced. Accepted READY task IDs/full immutable specs are recorded by the final fixture before claim/output exercises; cleanup rechecks exactly those specs under actual task lock and ends only their actual unfinished attempts, then terminalizes only the recorded READY/RUNNING tasks. This is parent-approved deny-only fixture retirement, not business cancellation or Worker completion. Earlier preparatory cleanup checked exact tracked IDs/type/state; the later fullspec guard is explicitly dated by its new source seal. Fixture teardown retained database rows, and accepted READY receipts remain in the evidence; current database access is qualified below. Final restored-original-container read-only bounded custody finds100 Task4 task/attempt rows, all tasks terminal and attempts ended; no broad prefix update was performed. The read-only prefix selects at most1001 and asserts completeness below that sentinel.

## Self-review and remaining gates

The self-review checked genuine private issuer/transaction/holder/transition authority; replay order and fresh authorization; complete task/input/source provenance and lock phases; strict request/budget/token parsing; no archive/parse/hash under roots; typed outbox/audit atomicity; safe discovery fields/actions; exact final source/test seals; no unrelated paths/formal SQL changes; preserved Task2 guard; empty index before staging; and zero exact owned-password hits across new evidence/source/report. `self-review-checks.json`, source deltas and evidence manifest provide the receipts. Root independently reviews this complete original-BASE-to-final-commit range after commit.

Task5 parser Worker/RAW writer, Task6 save/confirmation, browser main pipeline, S3 bytes, external/company-service qualification and broader Phase B remain open. Capability output states that execution is not installed. This slice does not claim those later gates passed.


# Task 4 fix round 1 appended continuation

Status: DONE WITH RETAINED VERIFICATION CONCERNS, pending scoped independent review. Explicit replacement continuation after the original author handle was unavailable. BASE and frozen precommit HEAD: `f176382618924d9a00fd5fbabf93bbc30c78dd71`; branch `codex/phase-b-completion-20261023`. Only T4SR-01/02/03 and their necessary test/evidence consistency are addressed. The commit result supplies the final commit SHA. No subagents, unrelated product work, SQL migration, Worker, save workflow, browser implementation, main merge or push was performed.

## Corrections and exact source scope

Four changed source paths are sealed in `owned-source-manifest.json` and `source-scope.diff`:

- `backend/adapters/src/main/java/ama/transcript/JdbcProcessingIntake.java`
- `backend/adapters/src/main/java/ama/transcript/JdbcProcessingDiscovery.java`
- `backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java`
- `contracts/openapi/processing.yaml`

T4SR-01: both independent accepted lookup (including the single recovery lookup) and final serialized replay now carry the original normalized ImportRequest. The accepted task business/base revision must equal the original expectedRevision+1; the complete bounded ordinal/file-ID sequence must equal the original orderedFileIds. Fixed production task/input/IMPORT/EMPTY/null/schema/config/parser/batch/profile associations are validated. The bounded stored canonical manifest supplies original file hashes/sizes and exact input/batch/config/base associations, allowing batch fingerprint, input fingerprint and producer recomputation before genuine roots. No current source table, route, eligibility, epoch, current archive or deployment-readiness observation is needed. After fresh owner EDIT authorization, the same transaction rechecks the exact input, manifest text, batch, profile, members and full task with current locked reads. No hash, JSON parse or archive I/O runs under held roots. The result remains a historical acknowledgment, never a new source permission or handoff. Missing/corrupt metadata is safe503; mismatched normalized request remains409. New-intake logic and supported revision range remain unchanged.

Regression cases redirect A's result_id to a later genuine same-meeting acceptance B; replace member ID/hash; swap continuous ordinals; remove/restore the profile; corrupt the batch fingerprint; corrupt model/prompt/schema/material/parser/processing/manifest fields; and place a corrupt genuine winner in the final serialized claim branch. Every new corruption is confined to newly owned fixture rows and restores exact original values in finally. Safe503 creates no extra input/batch/task/idempotency/audit/event rows. Historical unavailable/published/later-revision replay, lost acknowledgment, preparation races, legacy/token/equal-dual forms, queue behavior, bounds and root hash observations remain covered.

T4SR-02: the exact eligibility-reason enum includes JSON null. Draft 2020-12 checks cover seven representative ELIGIBLE/ineligible/unavailable objects plus invalid numeric/unknown reasons. Four actual HTTP completed-file and completed-upload receipt objects from the final source validate against the exact full published schemas, including ELIGIBLE/null and INELIGIBLE/SOURCE_STATE. Repository commands `npm --prefix web run generate:api` and `npm --prefix web run check:api` passed. Regeneration is byte-identical to the existing browser `generated.ts`, so no generated file change is staged. A transient local symlink reused the existing pinned phase-a-web web/node_modules and was removed after the gates; no package/lockfile was changed or installed. Redocly lint and external-reference bundle passed. Existing duplicate Conflict/Unavailable rename warnings and CLI update banners are retained and are not described as pristine output.

T4SR-03: fresh capability also requires a revision that can increment. At supported maximum9223372036854775806 an otherwise writable, available owner meeting returns canSubmit=false and REVISION_EXHAUSTED. Existing max-minus-one/max HTTP fixtures assert the transition, and accepted max-minus-one requests still replay their resulting maximum through all lossless forms. Execution remains NOT_INSTALLED and businessReady=false.

T4SR-04/05/06 remain explicitly deferred outside this fix loop.

## Genuine RED and retained attempts

`integrity-capability-red` ran four HTTP regressions plus two unit cases: four HTTP failures, no errors/skips. Each failed on the intended missing behavior: redirected result, changed member and corrupt fixed IMPORT state returned202, and exhausted capability returnedtrue. `final-claim-red` ran the final-claim regression plus two unit cases: that HTTP case also incorrectly returned202. `schema-red-2` failed specifically because null was absent from the exact enum. These are genuine behavioral REDs.

All other failed/setup/interrupted attempts remain under new evidence, with logs, command receipts, applicable source snapshots and available fresh XML:

- The first guard guessed different MySQL resource bounds and stopped before tests. Selected-field inspection established the retained original768MiB/1CPU bounds; the guard was corrected without resource mutation (`guard-setup-attempt.json`).
- `schema-red` used the wrong schema component name and raised KeyError; it is setup failure, not product RED.
- `integrity-capability-green` logged five passing HTTP and two passing unit cases. Its retained fresh XML contains only six cases because the preliminary producer XML was not copied by the mtime freshness filter. This preparatory invocation is not used for the final composed count; no missing XML is fabricated. Final invocations retain all needed fresh XML.
- `covering-final` ran24HTTP+2unit with two HTTP failures: the preliminary implementation's file_object reads violated the inherited strict zero-source-query sentinel, and a new upload returned401. The replay reader was corrected to use only the original manifest, preserving the sentinel.
- `replay-independence-green` ran5HTTP+2unit with one401 failure in a new positive integrity fixture; the historical/source-zero and genuine-root hash cases passed. `replay-auth-diagnostic` ran2HTTP+2unit and retained another401.
- The latter diagnostic records an ACTIVE, generation-matched, unrevoked session, canonical token, one exact Cookie, issued_at11:45:51.503154 and immediate database_now11:45:48.434167; direct real SessionPort resolution was false. The precise observation is a session issue time about3seconds ahead of the later database timestamp. It is an environment/test-precondition clock observation, not a replay product RED or production clock qualification. Diagnostic chronology is retained; a host-clock-step cause is not independently qualified.
- Only the four new positive integrity methods use a real session issued one minute before fixture creation, with unchanged expiry and real SessionPort predicates. Inherited and negative authorization fixtures retain their original issuance. No runtime authentication/time-skew policy was weakened.
- `covering-final-2` was explicitly stopped after the parent narrowed the fixture correction. Exit143 and two completed unit XML cases remain. It is INCOMPLETE, never a clean HTTP run. Only exact owned Maven/test descendants were terminated; the Python runner retained its result. Read-only `interrupted-custody-2` then confirmed no owned READY/RUNNING tasks. Its first predecessor had incorrectly expected a header for an empty mysql result and is another retained setup assertion error.

## Final exact-source composed qualification

No source bytes changed between these final invocations:

| Label | HTTP | Unit | Failures | Errors | Skips | Exit |
|---|---:|---:|---:|---:|---:|---:|
| covering-final-3 |24|2|1|1|0|1|
| covering-missing-2 |2|2|0|0|0|0|

The full HTTP run has22passing cases. It retained an inherited `ownerMemberOutsiderAdminSessionAndCsrfGuardsAreFresh` expected403/actual401 failure and a setup error in `ordinaryQueueSkipsLockedInputRootsWithoutWaitingOrLeasing` (`FileBackendCatalog` native/TLS startup list returned typed STORAGE_UNAVAILABLE). The auth diagnostic again records issued_at11:49:44.971618 versus immediate database_now11:49:41.697378, with active/generation-matched/unrevoked session and exact canonical Cookie. Only these two missing inherited methods were rerun unchanged; both passed. Later read-only public certificate validity dates are retained in `fresh-tls-validity-readonly.json`; they do not independently establish the original typed startup failure's TLS cause.

The composed final set is **24 distinct HTTP methods +2 unit methods =26 distinct passing covering cases**, all on the same four final source hashes. Repeated unit cases are not added twice. **No single clean24HTTP invocation is claimed.** `covering-exact-seal.json` verifies exact source seals and method-level passing union; both full-run nonpassing cases remain in that seal. `verification-summary.json` retains every completed label's real exit and copied XML counts. This is composed source qualification with disclosed environment/setup concerns, not blanket suite success.

The full command was:

```sh
./mvnw -pl backend/integration-tests -am verify -Dtest=HttpRevisionTokensTest,TranscriptImportProducerTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ProcessingIntakeHttpIT -Dfailsafe.failIfNoSpecifiedTests=false
```

The only final recovery changed the selector to:

```sh
-Dit.test=ProcessingIntakeHttpIT#ordinaryQueueSkipsLockedInputRootsWithoutWaitingOrLeasing+ownerMemberOutsiderAdminSessionAndCsrfGuardsAreFresh
```

Exact expanded argv/cwd/JDK/resource settings, actual logs and fresh XML are in each label's command/children/log/XML files. `http-schema-final` passed4actual response objects; `schema-green` passed the representative matrix and two invalid reasons. Lint/bundle/generated consistency cover the final exact contract bytes. The historical374-suite result was not rerun or relabeled as this fix's verification.

## Custody, preservation and resources

Each test invocation guards the same retained container fullID `eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502`, owner remaining-20261023, pinned mysql image digest869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9, actual768MiB/1CPU, loopback46653/schemaama_b01_fixture. Only current private `.tools/phase-b-remaining/env.sh` supplies credentials, never printed. Every command's bounded read-only history proves the same13 successful migrations and V013.1 checksum. No container reset/recreate/new container, DDL, repair, history change, old fixture/PKI reuse or S3 operation occurred.

Maven and tests inherit explicit512MiB/2CPU via MAVEN_OPTS/JAVA_TOOL_OPTIONS. Actual process arguments and fresh fixture receipts retain those limits; native storage is in-process under that JVM, with no service/API child JVM claimed. Fresh guarded UUID native/TLS/archive roots are within the approved target ownership. Final read-only custody finds189 task/attempt rows (all terminal/attempts ended);89 newly evidenced accepted task IDs are covered. The interrupted run left no unfinished task. No prefix update was used.

`original-task04-proof.json` and final audit prove all590 original Task4 raw files byte-for-byte unchanged. `original-report-proof.json` pins the original report prefix; the fix report is appended to that same W/task-04-report.md, never replacing its history. New evidence is only this task-04-fix-01 directory with nested byte protection. Final source, command, evidence hashes and exact known SQL/native TLS password scans are retained. The pre-staging scan includes the final appended/public reports and finds zero exact owned secret hits. The index was empty before explicit scoped staging. W and separately owned root documentation are not staged.

Independent scoped review remains required. Task5/6/7, browser/S3 main pipeline, company service, HA/capacity and production-clock qualification remain open. This report does not adopt Task4 or declare remaining Phase B complete.
