# Task 4 fix round 1 report

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
