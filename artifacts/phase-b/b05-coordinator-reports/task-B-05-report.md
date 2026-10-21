Final delivery HEAD: `682a7bfaef45f6ed3fec38df99e85a7b78d450d7`. Full owned review BASE: `0daecad85d348305c11982b5f0a7e2536a47434d`. Main implementation commit: `b3b6e80`; historical test maintenance: `682a7bf`. Worktree was clean after final commit. Independent SPEC + quality review pending; no consumer or production activation.

# B-05 formal implementation delivery report

Date: 2026-10-22 Asia/Shanghai. Owner: `/root/b05_concurrency_implement`. Worktree: `/home/tanren/ama/.worktrees/phase-b-tasks`, branch `codex/phase-b-tasks`.

Status: full owned implementation and local functional verification delivered for independent SPEC + quality review. No production handlers, company calls, main merge or push. This is not whole-B, actual company quota/cutoff, production failover or performance qualification. The exact delivery commit is recorded in the coordinator's full task report after committing this source/evidence bundle; the tracked report is part of that commit.

**Full owned review BASE must be `0daecad85d348305c11982b5f0a7e2536a47434d` → delivery HEAD**, not the merge commit, because previous prepared source was not committed. All previous preparation work is included in this final owned diff.

## Handoff and preservation

Initial HEAD was `7a5d5844e2047c2befa1acc8184d8d32ff64f5d8`; mechanical reviewed-root merge produced `c5ace21`. Original modified POM, untracked preparation source, report and logs were preserved before merging; original source was restored after the merge. The stash is retained by exact OID `bc12e825246c60c12752129c4d512ef9c545758c`; no pop/drop and no reliance on stash@{0}. `artifacts/phase-b/b05-formal-handoff/` contains original status, HEAD, tracked patch, source-only archive, original preparation report and hashes. The archive's 18 files were compared byte-for-byte with that exact stash's untracked tree; all matched. Root's earlier 28-file preparation checkpoint and original historical reports remain separate preserved evidence.

Read and applied the original B05 plan, full latest resume brief (last FORMAL resumption overrides preparation-only waiting), B00-1.2 protocols/dictionary, actual 10-table metadata, registry and earlier review constraints. No archived noncompliant old queue/permit implementation was revived. TDD, systematic debugging and verification workflows were used; no child agents were dispatched. The original owner remained idle and this owner was the only writer of the task worktree.

## Delivered code and actual contracts

- Contracts/core: immutable TaskSpec/TaskLease/typed references; stable ExternalRequest/operation/execution token and four-valued ObservationResult; SubjectFencePort, OutboxPort, TaskDomainPolicy, ExternalOperationPort, explicit ModelConfiguration/DeadlineVerifier, durable OutboxConsumerPort/policy.
- Adapters: JdbcSubjectFences, JdbcTaskQueue, JdbcExternalOperations, JdbcModelPermitPool, JdbcOutbox, JdbcOutboxConsumer and transaction-local flush guard. Queue-owned transactions are short; caller-owned transactions retain explicit flush-last responsibility. No queue lazy subject creation or locking JOIN; registered domain root locks precede task locks. Producer replay compares complete immutable spec; crash attempts count toward finite cap; unknown results never return to ordinary retry.
- External execution: committed marker before returning initial authority; ambient transaction rejected; commit-ACK uncertainty returns no token; independent reconciliation number/epoch/owner/DB expiry; terminal fact retained even when old task/business version is fenced. Verified zero-effect retry appends new task/execution under the stable key and increments epoch. Contradictory late success enters MANUAL_REQUIRED. Weak identity references retain no abandoned token payloads and value-equal reconstructed records do not inherit initial-call authority.
- Quota: no constructor writes/default quota; explicit revision/enabled policies, sorted configuration and operation→execution→model→reservation locking, NULL conservative retention, finite deadline only through registered proof policy, UTC microseconds, exact historical-execution settlement. Unknown/disabled/local-unconfigured models fail closed; duplicate admission cannot authorize another send.
- Outbox: nullable task IDs for domain events, registered immutable references verified before sequence lock, sequence locked last, explicit flush/transaction identity/foreign and late synchronization guard, rollback of rows and sequence. Durable consumer lease/epoch/cursor accepts only continuous stored prefixes; local effects/cursor commit atomically after prelocking domains. There is no remote-delivery exactly-once claim.
- `backend/worker`: separate WorkerMain/WorkerRuntimeProvider process bootstrap, explicit per-pool bounded WorkerLoop, independent five-second heartbeats and polls, twenty-second leases, separate bounded ReconciliationLoop/ExternalOperationHeartbeat. No API autostart and no production provider/handler registration. Integration-test service provider lives only in test resources.

Full consumer handbook: `docs/acceptance/b/task-execution-integration.md`. It states actual signatures, DataSource/transaction capability identity, root-lock responsibilities, typed immutable registration, B03 create/edit ordering (idempotency→sorted identities→subject→meeting/roster/ACL/projection→audit→finalflush), actual TaskFixture/OutboxFenceIT examples, and limits of trusted prelocked callbacks. It does not edit or pre-enable B03 business code.

The guard cannot intercept arbitrary caller-issued JDBC SQL after flush. Such SQL/reverse-lock callbacks/network under locks are outside the supported contract. The supplied callbacks/policies are explicit trusted domain adapters, not an arbitrary user execution API. Domain owners must register their real immutable types and authoritative proof verifiers; TEST_INFO/TEST_TASK/TEST_CALL/test-v1/TEST_VERIFIED_ZERO are test-only actual-meeting fixtures, not invented company formats.

## Frozen V010 and schema evidence

`V010__reliable_execution.sql`: 35,136 bytes, SHA-256 **03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db**, Flyway checksum **-1187397378**. Root explicitly ACKed this SHA before the first formal apply; central freeze record commit `2f621d65b17ba1b22987c36b47f8e2ec35d6dfbf`. No byte changed after sharing/applying. Root maintains the central migration registry; this owner did not edit it. Lower V001/002/005/006/007/008/009 remain unchanged; full ordered successful history has 8 entries after V010. No repair, reset, outOfOrder or placeholder migration. B05SchemaIT now pins only its private historical migration helper to010 (same reviewed pattern as IdentityMigrationIT007); it does not reset/downgrade a newer fixture. Production SchemaMigrator remains latest; behavior tests are unchanged.

Migration performs conservative preflight before permanent DDL: exact lower table/column/index/check/FK metadata; all legacy task/outbox/reservation/capacity data empty; singleton sequence exactly (1,1); retained subjects only exact binary MEETING roots matching active lifecycle/revision/epoch. Five hex-encoded metadata groups were independently decoded and verified by root against the reviewed lower set. Migration uses minimal ALTERs for six legacy tables and creates four additions; it does not drop/recreate entire existing tables.

Independent expected source: `docs/acceptance/b/database-upload-addendum-metadata-evidence.json`, full 85 tables / 674 columns, SHA **3de8041db8860f1547ff53a94b4ddd79470c4881340111fdafbc3ce5322399ad**. Filtered actual target metadata matches exactly: **10 tables, 91 columns, 43 index columns, 16 CHECKs, 10 FK columns**. Both B05SchemaIT and final capture comparison verify this; no legacy B00-1.1 metadata was mislabeled.

Raw review-only sandbox candidate(s) on port45009 were never Flyway V010 history and are retained with their original bytes/provenance. Formal positive on45979 was first applied only after ACK, with source-empty log, lower history, before/full target metadata and successful history captured. Formal negative46509 seeded nonempty model_capacity: V010 refused, success=0/checksum history remains, all lower DDL/metadata/business rows are unchanged. Separate raw refusals for capacity, invalid sequence, unknown subject and missing meeting were retained and rolled back as their explicit raw test controls; failed formal history was not repaired.

## Actual fixture ownership and recovery

All NEW functional fixtures used pinned MySQL image and private process-local wrapper with `--innodb-use-native-aio=OFF`, after observed host AIO usage 61455/65536 and root authorization. Actual @@innodb_use_native_aio=0 is captured. Shared helper and defaults/global sysctl were unchanged. Root later reviewed its shared helper preservation fix independently; it was not merged during these runs.

| Own fixture | Endpoint/history/purpose | Final preservation |
|---|---|---|
| ama-test-b01-5833aa5fe461 |45009, lower formal001..009 + explicitly raw review-only target|running, data/history retained|
| ama-test-b01-3ee46f689f9a |formal positive originally45979; dynamic Docker restart changed endpoint to45951|same exact ID 59f3de25efa7156e89329880ccfbd4d51bd2f52d735ead9fca64745c28eb3dcb, original env retained, derived env for remaining functionality, running|
| ama-test-b01-5a929c100b88 |46509, formal failed010 guard with preserved capacity row|running, success=0 retained, no repair/reset/remove|
| ama-test-b01-4a85d5a1df1a |explicit fixed44847, source 0 tables→all frozen migrations, one recovery restart|same ID e89aea35e05b2534d81091812bb2ef9e721acdc5e537fac44add6d209430b090, port/args/volume/history/DDL retained, running|

Original46037 and all other-owner fixtures were untouched. Final inventory records exact IDs, labels, AutoRemove=false, mounts, ports, args and state. Credential env files remain local and are not committed; original/derived positive env hashes are recorded.

Dynamic positive restart: one authorized graceful exact-ID restart retained volume/args but Docker reassigned the random published port45979→45951. The old endpoint readiness assertion failed after30s. Failure log and before/after rows/history/metadata were preserved; no second restart or endpoint relabeling claimed a pass. This is an environment/fixture deviation, not a product RED.

Fixed-loopback recovery: root authorized a new fixed publish wrapper. DatabaseRecoveryIT verified owned ID/label/AutoRemove=false/persistent volume, captured before state, restarted that exact ID once, observed the old JDBC connection fail, then measured from **actual DB connectability** to separate worker task completion: **1476 ms**, below30s. It rejected the stale task completion. Before/after ID, args, mounts, host bindings, loopback port, metadata, DDL, migration SHA and successful history are equal; no table row count decreased. ReconciliationWorkerIT separately demonstrated due-operation ownership and independent heartbeat while its handler was blocked, then retained authoritative success after old task expiry. This is local functional DB recovery only, not real VIP/failover/native-I/O/load qualification.

## Verification and genuine failures

Final evidence contains **47 distinct B05 IT cases** across final passing invocations: the 44-case full owned group passed; final authority-lifecycle follow-up ran ExternalExecutionIT10 + ModelPermitIT7 (one added case), giving45 unique core cases; fixed DatabaseRecoveryIT1 and ReconciliationWorkerIT1 passed separately. No actual restart was repeated to manufacture this count. Latest affected Java units33, Python74 and Web8 passed with zero failures/errors/skips. Final source follow-up ran only the changed authority/admission groups plus reactor units; unrelated passing groups were not broadened again.

| Final selected class | Cases | Evidence |
|---|---:|---|
|TaskLeaseIT|13|final-owned-green.log/XML|
|ExternalExecutionIT|10|final-authority-lifecycle.log/XML (also full group)|
|ModelPermitIT|7|final-authority-lifecycle.log/XML|
|OutboxFenceIT|7|final-owned-green.log/XML|
|OutboxConsumerIT|2|final-owned-green.log/XML|
|WorkerProcessIT|3|final-owned-green.log/XML|
|B05SchemaIT|3|historical-target010-green.log/XML (also full group)|
|DatabaseRecoveryIT|1|fixed-recovery-and-reconciler.log/XML|
|ReconciliationWorkerIT|1|fixed-recovery-and-reconciler.log/XML|

Authority-lifecycle follow-up changed only weak-identity authority storage and a copied/reconciliation/duplicate token admission test; External10/Model7 and all Java33 passed. A subsequent coordinator-requested historical-oracle maintenance pinned B05SchemaIT to a private target010 helper; its canonical3 cases and incidental Java33 passed without altering production migration behavior. Initial 44-case full-group pass remains the evidence for unchanged queue/outbox/process code; claims are not based on stale XML from other invocations.

All actual earlier IT invocation counts are preserved below as tests/failures/errors/skips, with incidental unit executions separately listed by `execution-manifest.json`:

| Invocation log | T/F/E/S | Meaning |
|---|---|---|
|schema-red|1/1/0/0|target10 tables missing before target migration|
|task-red|9/8/0/0|missing queue behavior; rollback case passing a stub is not feature RED|
|task-green1|9/0/0/0|initial real queue on raw sandbox|
|external-red|5/5/0/0|missing external implementation|
|external-green1|5/1/0/0|UNKNOWN retry threw duplicate-key before state rejection; state check corrected|
|permit-red|4/4/0/0|missing model permit behavior|
|consumer-red|2/2/0/0|missing consumer behavior|
|formal-behavior1|27/0/2/0|MySQL reserved cursor identifier unquoted; quoted and reran|
|worker-process-red|2/2/0/0|missing process bootstrap / operation heartbeat|
|formal-behavior2|33/1/0/0|commit-ACK test accidentally mixed original/wrapped DataSource; real guard rejected before commit. Fixed test to same wrapped source, then proved committed marker with lost ACK|
|retry-cap-red|1/0/1/0|retry replay threw rather than returning accepted task; error retained candidly|
|retry-cap-red2|3/2/0/0|asserted replay + PREPARED crash cap missing; commit-ACK test passed|
|retry-cap-green|3/0/0/0|replay and cap terminal proof fixed|
|retry-race-and-recovery|2/2/0/0|real retry RR snapshot race duplicate insert; separate dynamic-port recovery environmental failure|
|concurrent-config|2/1/0/0|retry race passed after subject-before-snapshot fix; real missing-key configuration gap-lock deadlock remained|
|final-owned-and-units|44/1/0/0|config fixed by materializing explicit sorted config before SELECT; UNKNOWN test reused original producer, causing legitimate immutable replay conflict. Updated to a new valid retry producer to exercise UNKNOWN rejection|
|reconciliation-worker-red|1/1/0/0|no-op reconciliation loop could not enter due handler|
|fixed-recovery-and-reconciler|2/0/0/0|actual stable-endpoint restart and independent reconciliation GREEN|
|final-owned-green|44/0/0/0|entire core owned class selection passed|
|final-authority-lifecycle|17/0/0/0|affected external/admission cases, including added copied/duplicate/reconciliation authority test|
|historical-target010-green|3/0/0/0|canonical historical schema test with private target010, production latest unaffected|

Web first launch failed before execution because this tree lacked node_modules; failure retained. Pinned `npm ci --ignore-scripts` installed exact lockfile dependencies without source changes, then8 actual tests passed. Python74 passed. Java33 comprises metadata2, storage8, API deployment9/identity3/security6, worker3, contract-client2. No skipped test is counted as passing. Early wildcard XML archives may include stale unselected classes; only per-class counts in each invocation log are counted, and final selected XML directories were copied by explicit class names.

## B00 difference coverage and remaining gates

| Difference | Implemented/tested B05 scope | Downstream gate |
|---|---|---|
|DIFF04|real marker commit/ambient rejection/commit-ACK unknown, killed marker process, independent reconciling authority, epoch/late-success/UNKNOWN no resend|real company status/proof/fencing adapter|
|DIFF05|subject→root→task locking, actual held subject/root/task SKIP LOCKED, concurrent cancel/complete, model independent tx, explicit final sequence|each domain owner must follow same prelock order; trusted callbacks cannot issue reverse raw JDBC|
|DIFF06|producer full spec/typed immutable references, old attempt and version/epoch/tombstone gates, crash cap including PREPARED execution, age/backoff behavior|domain-specific immutable provenance policies|
|DIFF07|delayed low sequence commit blocks later producer, rollback sequence and effects,20 concurrent producers, consumer continuous prefix/duplicate/epoch/atomic rollback|real remote delivery semantics and downstream authorization|
|DIFF08|actual competing capacity admissions, NULL timeout retention, verified cutoff timing/microseconds, disabled/shrink/settlement|actual company capacity and server-enforced cutoff proof|
|DIFF12|no lazy subject or locking JOIN, domain policy roots prelocked, external fact independent of origin lease, same-tx capability/flush guards|B03/B04/B08 business adapters reviewed before enablement|
|DIFF13|constructor no writes, unknown local policy denied, sorted concurrent config fixed/tested, one-shot execution permit, no unproven release|explicit reviewed deployment config; no default company quota|

Central difference registry remains root-owned and is not automatically closed by this report. Independent full-range SPEC + quality review is required before integration/consumer enablement. No real company IDs, roles, typed manifest formats, quotas, cutoff semantics or credentials were guessed; no real provider/mail/publish/network effect was invoked. No production worker provider was installed. No full-B/performance/platform qualification is claimed. Applied V010 is frozen; a discovered SQL defect requires root registration of a new version.

Evidence directory: `artifacts/phase-b/b05-functional/`; preservation directory: `artifacts/phase-b/b05-formal-handoff/`. `execution-manifest.json`, `final-verification.json`, `file-sha256.json` and `source-sha256.json` support review. All retained failures, raw versus formal provenance and bounded qualification limits above are intentional parts of delivery.
