# B06.1 prerequisite proof report

Status: **NEEDS_CONTEXT (root ACK)**. This delivery is the required proposal and real MySQL prerequisite proof; it is not full B06, a production meeting policy, or a deployed B05 failure/fix.

Owned tree: `/home/tanren/ama/.worktrees/phase-b-transcript-runtime`; branch `codex/phase-b-transcript-runtime`. BASE = HEAD = `6b48b2f855bbf890c3ccad68325e7fd74d434489`; BASE..HEAD is empty. New files remain uncommitted TDD/proposal evidence pending ACK. No shared production source changed, no SQL changed, no merge/rebase/import/push occurred.

Read the complete [technical proposal](task-B-06-seams-proposal.md), including exact batch profile/vector, controlled immutable archive source/retention/restart design, canonical IMPORT/EMPTY closure and finite budgets, actual complete DB/TaskSpec facts, first-batch reference eligibility, explicit transaction capability protocol, all external lockDomain consumers, and prepared atomic worker completion.

## Actual commands and counts

Only the new owned fixture's private env was sourced. Public Java21 path was used. Exact selectors, no wildcard/reset/refusal/recovery/native/S3 suites:

| Command | Actual IT result | Classification |
|---|---|---|
| `python3 tools/checks/run.py it B06QueueSeamsIT` | 3 tests / 2 failures / 0 errors / 0 skips; exit1 | desired-order and capability-reuse assertion RED; disable-control GREEN |
| `python3 tools/checks/run.py it B06QueueSeamsIT#currentThreeArgumentCompletionValidatesOutputBeforeEffects` | 1 test / 0 failures/errors/skips; exit0 | existing completion-order control GREEN |

Four distinct new test methods executed; two expectation REDs and two controls. Both Maven verify commands also executed the same179 Java unit tests successfully (0 failures/errors/skips), separately preserved per-run; repeated unit executions are not counted as358 unique coverage. No original B05 concurrency regression was rerun because no shared fix is implemented/approved.

The RED's real locking SQL was subject index0 → identity index1 → subject reentry index2 → meeting index3/4 → task index5; desired canonical identity-before-subject assertion failed. Ambient authentic B03 gate reuse failed with `Identity prelock cannot follow a meeting root lock`; real task/outbox counts0 after rollback. This narrow TEST_INFO domain uses actual enterprise_identity, task_subject, meeting, immutable meeting_info_revision, task/attempt, actual RR JDBC transaction and outbox. It does not claim a valid production transcription input or use fake manifest bytes.

Disable control coordinates actual SQL with bounded10-second latches: uncommitted DISABLED identity, completion before-execute identity boundary, then committed disable and fresh authorization denial. Actor.status remains ACTIVE while shared row DISABLED; effects0/current ENTRY/task RUNNING/outputNULL. It proves current shared-row denial and the reverse trace. It does not claim a measured lock-wait duration, opposing deadlock reproduction, or production fault.

Output control uses a real new meeting_info_revision UUID as intended output; current validateOutput queries before callback, finds no real row and rejects. Callback0/outputrows0/taskRUNNING. Existing three-argument semantics are retained. The proposed supplier overload would execute bounded prelocked writes, validate their actual stored output, then terminalize in one transaction; rollback boundaries are in the proposal.

## New fixture and lower migration provenance

Exactly one newly created container: `ama-test-b01-ff63b53162a3`, owner label `queue-seams-20261023`. Pinned MySQL reference `mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`; actual image ID, container ID, mounts, process flags, selected port/resources/state are preserved in selected logs. Actual allocation is127.0.0.1:58060; memory768MiB/swap768MiB,1CPU,pids256. MySQL8.4.6, nativeAIO OFF, UTC+00:00. NativeAIO OFF is only this new process's allowed narrow host-quota workaround, not native/capacity qualification or a global/helper change.

Before any migration, `fixture-initial-zero.log` records0 schema tables. The creation helper initially read requested HostConfig.PortBindings, whose ephemeral HostPort was empty; a second selected-field read of NetworkSettings.Ports obtained the actual allocation before JDBC. Same owned container, no restart/reset/new instance. This preparation correction is not RED. Every startup readiness poll status/stdout/stderr is retained and secret-scrubbed; setup polling does not coordinate tests.

All12 formal SQL files were SHA256-recorded before creation/application and compared afterward both to that list and exact BASE Git blobs. Real Flyway applied ordered001,002,005,006,007,008,009,010,011,011.1,012,013 successfully, ending013; every installed_rank/script/checksum/success row is in `history-after.log`. All12 remain byte-identical. No reset/repair/outOfOrder, old fixture change/stop/removal, Config.Env/env dump, PKI/S3/company call or guessed migration occurred. Existing containers/libraries/data are untouched. New fixture remains running and preserved.

## New evidence and source custody

Evidence root: `artifacts/phase-b/b06-queue-seams/`. Allowlist/SHA files contain only new public evidence and hash-listed source; private `.tools/b06-queue-seams/mysql.env` / `env.sh` are excluded and not printed.

- `commands.md` records exact selectors, toolchain/private-env location, setup correction and read-only inspection path mistakes. Initial guessed filenames/permission-denied inspections were not raw-log-captured; this is disclosed as reconstructed notes, not execution proof or RED.
- `red-run.log/.exit`, `run-1-counts.json` and `run-1/**` preserve original raw XML/text and exact original test source before the next runner deleted stale failsafe XML.
- `output-control-run.log/.exit`, `run-2-counts.json`, `run-2/**` preserve second raw reports and source. Between runs only the new test was extended; its never-reached negative effect stage was corrected to valid TRANSCRIPT_PENDING. Original RED source stays preserved.
- `fixture-*.log/.command.json`, `fixture-ready-attempts.json`, `history-after.log/.command.json`, `lower-sql-before.json`, `lower-sql-after.json` retain actual selected fixture/application provenance. Fixture launcher, port capture and evidence scripts are source-controlled candidates under tools.
- `source-sha256.json` records exact inspected production seam hashes and current test SHA; each production file was checked against exact BASE and matches.
- `batch-v1.literal.json/.bytes/.hex/.sha256`, `batch-vector.log`, `batch-vector-openssl.log` hold the independent734-byte design vector, matching Python/OpenSSL SHA `c4a558e4dc8aff8289b4ed60193efdfbc7f64f73ae642e2eec6398c473458053`. This is a proposal vector, no new runtime hash implementation.
- `allowlist.txt` and `SHA256SUMS` explicitly seal new evidence; artifacts are marked -text. Original parser/schema/B03/B05/B04/S1 evidence and production code remain unchanged.

## Decision and limits

Root ACK is requested for the explicit fresh pre-domain identity phase plus authentic ambient capability reuse; current review proposal also spells out the later separate supplier completion overload while preserving existing3-arg behavior. Primary risks are complete identity sets/cross-policy observation, skipLocked partial acquisition, repeated external domain reuse, observation fact retention for DISABLED owners without domain effects, and atomic output verification/rollback.

No durable archive implementation/restart proof, production INPUT_BATCH_FILE policy, new fingerprint implementation, production producer/provider/handler, import HTTP/local/S3 pipeline, saved edits/marks/confirmation loop or full B06 qualification exists yet. No full independent review was dispatched. Later shared correction must run affected B05 concurrency regressions and be independently reviewed before adoption.
