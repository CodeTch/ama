# B06.1 shared identity preparation correction report

Status: **READY_FOR_INDEPENDENT_REVIEW**. This completes the ACKed shared queue/access seam correction and its local evidence, not full B06 or independent adoption.

Owned branch/tree: `codex/phase-b-transcript-runtime`, `/home/tanren/ama/.worktrees/phase-b-transcript-runtime`. Exact BASE `6b48b2f855bbf890c3ccad68325e7fd74d434489`. Preserved prerequisite/RED commit `26bf77d`; tested implementation/consumer commit `553196cacbf27f2fd4dfc3b65d70c535f10f9958`. Review the complete BASE..final HEAD, including the subsequent evidence/report seal commit; the coordinator copy and final delivery message record that final immutable HEAD. No merge/rebase/peer import/main modification/push occurred.

Read the [consumer contract](task-B-06-seams-integration.md), original [technical proposal](task-B-06-seams-proposal.md) and [prerequisite proof](task-B-06-seams-proof-report.md). Public coordination authority is copied into `artifacts/phase-b/b06-queue-seams-fix/coordination/task-B-06-seams-root-ack.md` (including exact FAILED_ZERO_EFFECT factual-expiry addendum) and `task-B-06-regression-map.md`. They contain no private configuration values.

## Result and implementation

The queue now prepares the complete identity set before subject/root/task acquisition. TaskDomainPolicy exposes explicit prepare/requirePrepared/permitsDomainEffects and one complete lockRoots batch; its preparation defaults fail closed. Every legacy identity-free test registration opts in explicitly. Unknown production domains remain unavailable.

JdbcTaskPreparations uses a private Held type tied to its unique issuing factory, exact DataSource object, actual Spring transaction holder, complete immutable spec set and acquisition purpose. The queue independently verifies its own DataSource/holder at consumption and scope reuse. Another factory, fabricated token, expired/foreign holder, wrong source/spec/purpose cannot satisfy the contract. There is no unmanaged ThreadLocal, global transaction cache or extra synchronization.

A local DomainScope supports one subject and one registered policy object (which may explicitly own multiple registered types). The complete old/current/retry spec set is discovered before locks, checked for one subject/common issuer, and canonically sorted. The first acquisition prelocks all domain rows in one batch before tasks/operations; repeated retry/domain calls reuse the same local locked scope and never discover identities/roots late. Unsupported mixed subject/policy families throw DomainPreparationUnavailableException, an actionable non-acknowledgment for authoritative facts.

MeetingAccess adds genuine identity-phase preparation, skip-aware root acquisition, and exact actor/meeting/identity-coverage checks for adoption. Original public lock/require and repository package paths retain ordinary current-identity/roster behavior. API/file callers can explicitly adopt their authentic already-held B03 capability into their registered policy and pass queue.enqueue(spec,prepared). Re-entering identity preparation after roots remains rejected.

All queue and external consumers use the corrected acquisition. SKIP LOCKED applies to complete identities as well as subjects/roots/tasks; a blocked/inactive candidate exits before subject and partial identities end with its owned short transaction. Retry discovers both old/new specs before locks and uses a fresh locking producer lookup after discovery may have established an RR snapshot. Observation retains four complete rollback/rediscovery attempts and same-fact resubmission. Claim-only reconciliation skips unregistered task domains without granting authority; observe for unavailable families stays explicit non-ack, without swallowing unrelated SQL/protocol failures.

## Terminal facts versus business authority

EXTERNAL_FACT_OBSERVATION can carry current locked identity effectsAllowed=false without creating a MeetingAccess mutation grant. Terminal fact receipt requires exact operation/subject and current execution number/UUID, operation/execution MARKED or UNKNOWN, and SUCCEEDED or FAILED_ZERO_EFFECT. The unchanged independent authoritative observation verifier runs before updates; invalid facts roll back.

Only this factual receipt may ignore the token's lease owner, reconciliation epoch and expiry. It uses existing execution/operation terminal states, terminal task STALE and RECORDED_FENCED. It does not change owner/epoch, renew the lease, restore local initial-call authority, admit a model/send again, validate/apply domain output, or invoke callbacks. Business canApply still requires ordinary valid() plus effectsAllowed plus the current fence.

UNKNOWN/MANUAL_REQUIRED do not receive that exception. An invalid token follows its original STALE_TOKEN rejection with marker intact; a normally valid UNKNOWN keeps prior semantics. Wrong/non-current execution, fake/unverified facts, and old zero-effect contradictions retain rejection/quarantine behavior. ACTIVE owner/epoch/expiry authorization is unchanged. These exact boundaries have real JDBC regression assertions, including old owner/epoch token factual retention only after identity disable.

The old three-argument completeWithEffects semantics remain output validation before effects. No Supplier overload or WorkerLoop/ServiceLoader change was made.

## Genuine RED and final verification

All commands used the same new owned fixture, public Java21 and exact selectors. No broad IT/package/schema/refusal/recovery/native/S3 cohort ran. Raw log/XML/test-source versions are retained per run; setup errors are not called RED.

Original prerequisite evidence remains in `artifacts/phase-b/b06-queue-seams/`:

| Run | Actual IT counts (tests/failures/errors/skips) | Meaning |
|---|---|---|
| original B06QueueSeamsIT | 3/2/0/0 | identity-after-subject and ambient repeated-identity assertion RED; disable control GREEN |
| original output-order method | 1/0/0/0 | existing validateOutput-before-effects control |

Correction evidence is separately sealed under `artifacts/phase-b/b06-queue-seams-fix/`:

| Label | Actual IT counts | Meaning |
|---|---|---|
| initial-green | 4/0/0/0 | original assertions adapted to explicit genuine capability protocol and GREEN |
| expanded-red | 12/1/0/0 | inactive identity under skip=true threw instead of skipping before subject |
| focused-green | 13/0/0/0 | fixed skip; explicit unprepared identity-free registration also denied |
| expired-fact-red | 1/1/0/0 | actual STALE_TOKEN versus desired RECORDED_FENCED for disabled/expired current terminal fact |
| final-seams | 15/0/0/0 | preliminary terminal receipt/control expansion |
| unregistered-red | 1/1/0/0 | claim-only scan threw for retained unregistered domain instead of skipping |
| final-seams-16 | 16/0/0/0 | preliminary unknown-domain claim/observe boundaries |
| final-seams-17 | 17/0/0/0 | terminal-only exception; owner/epoch/expiry/UNKNOWN/wrong-execution boundaries |
| inherited | 28/0/0/0 | preliminary exact inherited selection before final queue DS guard |
| queue-crossds-red | 1/1/0/0 | two real DataSource transactions allowed a foreign-source factory token; required IllegalArgumentException was not thrown |
| **final-seams-18** | **18/0/0/0** | final source, including independent queue DataSource/holder guard |
| **inherited-final** | **28/0/0/0** | final source;26 exact methods, including3 held-row parameter invocations |
| **units-final** | **0 IT;179 Java units,0 failures/errors/skips** | one final current full Java unit command |

The cross-DS negative run did not reject the controlled misbound enqueue; its negative history was retained, with no reset/repair cleanup. The corrected run rejects before subject/domain locks and asserts no task persisted for its new UUID.

Final-source test snapshots/hashes match all12 changed/new Java files across final-seams-18, inherited-final and units-final. Earlier passing rounds are preliminary and do not substitute for these final-source runs. Final IT coverage is18 new methods plus26 inherited methods:44 distinct methods,46 actual invocations (the heldRow method has3 parameters). The prior rounds overlap these methods. Each normal Maven verify also reran the same179 units; those repetitions are recorded per-run, not counted as additional unique coverage. The direct inherited Maven commands intentionally ran zero units, never claimed as a unit pass.

Final inherited selectors are exactly the approved map's25 methods plus ModelPermitIT#copiedAndReconciliationTokensCannotAdmitAndDuplicateCannotResend. They cover queue concurrency/fences/rollback/skip, marker commit/unknown/success/reconciliation/retry, four RR observation/fix1 cases, final outbox flush, valid external heartbeat renewal, B03's three exact compatibility methods, and copied/reconciliation token admission. Exact argv and XML method-set equality are in inherited-final.command.json and inherited-final-method-coverage.json. No worker/process-kill/model-capacity whole class was selected.

The selected B03 BeforeEach genuinely invoked latest SchemaMigrator. All12 scripts validated, deployed history remained013 and each invocation performed zero migrations. This is not described as a no-migrate helper. Original B03 repository/CAS/discovery code was not changed.

## Fixture, schema and evidence preservation

Exactly one container remains owned/preserved: `ama-test-b01-ff63b53162a3`, loopback127.0.0.1:58060, pinned MySQL8.4.6 image,768MiB memory/swap,1CPU,pids256. Its container/image IDs, mounts, exact resources/port/process flags and final running status are selected-field records, not a full inspect or Config.Env dump. nativeAIO OFF is only this already-authorized new process, not host/shared-helper changes or native/capacity qualification.

The initial0-table proof precedes the one actual ordered application through013. Final history still has all12 successful original script/checksum rows and no new migration. lower-sql-final.json verifies each SHA against both pre-run list and exact BASE bytes. original-proof-preservation.json verifies all143 hashed original prerequisite artifacts unchanged; original parser/schema/B03/B05/B04/S1 evidence was not modified.

Per-run source copies, raw logs/XML, commands/statuses, all-runs.json, final fixture/history, public ACK/map, full delivery allowlist and SHA256SUMS are under the two new B06 evidence roots. Private .tools fixture env files are excluded; explicit own-secret scanning found no fixture password in the new public package. The original ephemeral-port setup correction and read-only inspection mistakes remain honestly disclosed in original commands.md, not relabeled as RED. No new command setup/compile error occurred in correction runs.

## Remaining scope and review gate

Independent scoped SPEC/quality review is still required. Complete B06 remains open: durable archive/index/restart implementation, new batch/producer fingerprint code, real INPUT_BATCH_FILE production policy, import HTTP/local/S3 workflow, full immutable facts/worker/output atomicity, saved edit/marks/confirmation and company/production qualification. The accepted design vector remains proposal-only. No new SQL, unrestricted policy, runtime provider, model/company calls, production deployment, or push was introduced.

Reviewer should assess complete BASE..final HEAD, including original proof commit, tested source commit and the final report/evidence seal. New evidence and negative history remain preserved on the owned fixture.
