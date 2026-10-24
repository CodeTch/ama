# Task 5 import Worker — independent design review

2026-10-23. Design review only. No implementation, executed-test, runtime, source-stream, schema or Phase B qualification is conferred.

- **SPEC verdict: CHANGES REQUIRED.**
- **Design-quality verdict: CHANGES REQUIRED.**
- **Findings: 2 Important / P2; 0 Critical / P0–P1; 0 Minor / P3.**
- Finding IDs: **T5DR-01**, **T5DR-02**.

The measured parser, immutable-input processing, explicit runtime composition, atomic output ordering and proposed evidence custody are sound directions. Before implementation freeze, the design must make fresh completion authority reach the private RAW writer through an executable confined path and remove the completion/renewal monitor inversion. These are design gaps, not allegations about Task 3's changing implementation.

## Reviewed inputs and method

Read the complete task-05-import-worker-design.md and task-05-design-brief.md. Independently verified the design's raw SHA256 as **26a5b94befb57b5802b37b7dcd5e0679fc42add7f6d0e9a69602da871556d06f**. Also read Task5 plan extract/parser-fixture preflight; Task1 actual completion report and review; adopted Task3 authority R1 and pre-gate R1/R2, R2 review and relevant original authority sections; Task4 R1 policy/assembly/intake and normative replay refinements with its review; and Task7's ordinary/marker/operation monitor and close contract. The latest pre-gate R2 remains binding even though the Task5 input table lists only the earlier R1.

Named source checks used read-only git show at **3e393dbc9485b187b89bfc03abd88821a0aabddb**: JdbcTaskQueue, TaskQueue, TaskDomainPolicy, JdbcTaskPreparations, WorkerLoop/WorkerMain, TextTranscriptParser, ConfigBindings, LocalFileClient, S3 RangeReader, worker/adapters POMs, V011 schema and the controlled-runtime/authority-adoption specifications. The invocation-local safe.directory option did not alter Git configuration. No live Task3 source was used to decide this review. An initial WSL invocation failed with access denied; subsequent reads used the explicit UNC checkout and Windows Git. A wrong LocalFileClient path was corrected to ama/storage; no command ran tests or changed source.

Only this requested report was written. No source/test/index/HEAD mutation, DB, Docker, private environment access, fixture creation or subagents occurred. All tests below are prospective requirements.

## Important findings

### T5DR-01 — Specify the private queue-to-writer authority handoff (P2)

**Location:** design section 7, especially line 114; related section 11 gate 2 and final lease clarification at line 191.

The proposed internal writer receives prepared data only and requires an active configured-source transaction. That proves neither that this invocation is inside the queue's authentic acquisition nor that the exact complete domain, source, task and unfinished attempt are held. A TaskLease, immutable PreparedRaw and an arbitrary transaction on the correct DataSource remain data plus a transaction. A private method name or absence of a controller route does not establish the required call path when preparation returns an executable Supplier.

Frozen JdbcTaskQueue.prepareDomain creates a genuine DomainScope containing the exact policy and PreparedDomain. However, lockDomain(TaskSpec,...) returns only a LockedSubject; validLease returns only Row; Supplier.get receives no scope. The authority is checked in the queue, but the proposed writer has no specified way to authenticate or be confined to that checked scope. Existing Task1 intentionally trusts bounded callbacks as composition; it does not turn a returned callback into independently usable authority. The adopted Task3 allowance for deterministic DB validation inside an already-established queue scope does not authorize invoking the new RAW writer in another ambient transaction.

Consequently the detailed design currently leaves the implementer to choose between an unproved convention, a forbidden holder/ThreadLocal cache, reacquiring roots late, or an unreviewed capability/API amendment. Section 11's provisional facade gate correctly recognizes the dependency but does not yet close this writer entry contract.

**Required design closure:** freeze the exact private queue-owned after-task path. Retain or explicitly convey the authentic registered scope on the call stack, or provide an equally explicit confinement mechanism making the writer callable successfully only by that path. State its signature/owner and how it binds the exact configured issuer/DataSource/actual holder, complete preparation/full spec, current task/attempt, prelocked graph and one-use completion phase. Detached preparation may carry intended rows only. No public authority setter, new issuer, ThreadLocal/global cache, recursive validLease/source-read call or late existing-root acquisition. Preserve the old Runnable API and Task1 Supplier ordering/sole completion ownership; an additive internal hook can be designed without making ordinary callbacks a new public permission API.

The same closure must explain how exact prepared-result comparison and registered actual-output validation share the confined invocation without storing the last preparation globally. Raw rows can be read as the permitted transaction-created-row exception after insertion; that exception does not manufacture permission to insert them.

**Prospective proof:** invoke the returned preparation/callback outside queue completion in a same-source transaction, with another holder/issuer, after scope termination, with a different task/spec/attempt, and a second time. Every unsupported invocation must fail before RAW INSERT. The authentic queue path must write once, expose actual new output to the registered validator and roll all effects back on validation/flush failure. These are targeted authority tests, not a demand to sandbox arbitrary trusted application code.

### T5DR-02 — Remove the renewal-monitor / completion-DB lock inversion (P2)

**Location:** section 3 line 52, together with section 7's callback-entry guard after queue locks.

The design permits renewal to hold the execution monitor through bounded JDBC renew, while completion first acquires database roots/tasks and then acquires that monitor to admit the Supplier callback. This permits the following real schedule:

1. Completion acquires the meeting/subject/domain locks in validLease and is about to enter its Supplier.
2. A heartbeat owns the execution monitor and calls queue.renew; renewal waits for those same database locks.
3. Completion waits for the execution monitor before its writer may start.

This is monitor -> DB versus DB -> monitor ordering. A finite JDBC timeout prevents an infinite wait only if it is correctly enforced; it still makes renewal fail, sets sticky loss, and rolls back an otherwise valid completion. Repeated timing overlap can consume the import's three attempts. Close/cancellation selection also waits behind the blocked renewal. Therefore merely choosing shorter statement/lock bounds does not solve the ordering defect.

Task7's marker path is not a justification for this inversion: its documented selection/monitor entry precedes its independent marker transaction. Task5 adds a different entry point inside an already-locking queue Supplier. The frozen WorkerLoop uses AtomicBoolean at that point and therefore does not already exhibit the proposed monitor cycle.

**Required design closure:** define one executable ordering for ordinary renewal, completion dispatch, callback entry and close. No renewal may hold the monitor while waiting for database locks held by a completion that needs that same monitor. For example, explicitly phase and drain/suspend ordinary renewal before database acquisition, or publish bounded renewal results through a state/generation protocol without holding the monitor over conflicting JDBC work. Choose and specify the concrete protocol; do not introduce a second lease scheduler or hold a monitor over parse/network/archive work. Preserve loss/close winning before callback entry => zero writer, fresh queue authority after lock waiting, no second ordinary completion after dispatch/unknown ACK, and Task7's later MARKING/OPERATION ownership handoff.

**Prospective proof:** deterministic barriers with completion holding its first root, heartbeat attempting renewal, callback admission, and concurrent close. Assert progress without relying on a database timeout to break a self-created cycle; false renewal from genuine loss must still suppress effects when it wins before admission. Include already-running renewal at dispatch, queued tick, successful terminal completion followed by a late tick, and close before callback admission. Keep the 20s/5s/5s lease/heartbeat/poll contract and bounded shutdown.

## Accepted design decisions and retained implementation gates

### Exact parsing and original input

The separate MeasuredTranscriptDocumentParser port is appropriately additive. Existing Result/Segment/profile/stable IDs and old parse exception behavior remain unchanged. The frozen parser actually increments Lines.units before BOM removal, CRLF handling and blank-line omission, and BoundedSource.used counts original bytes through EOF. Returning these exact internal counters on successful completion solves the aggregate-undermeasurement problem. Checked remaining budgets, no partial usage on failure, positive-budget exhaustion handling and append-after-admission are explicit. Preserve independent golden IDs and split UTF8/supplementary, BOM/CRLF/blank, exact/plus-one and last-file rollback cases.

ConfigBindings already obtains ConfigSchema.parseBudget from the verified processing definition. Adding the retained data-only accessor is preferable to a second parser or caller-attested limits; propagate it through complete() as well as initial construction. Verify actual stored manifest/producer/profile/sidecar/complete archive closure and original ordered files, never current defaults. Original intake and live source acquisition remain separate from final completion authority. No archive/hash/parse work belongs under root locks.

### Genuine one-shot source and deadlines

The plan correctly requires the adopted single B03 gate, complete registered policy, all tasks before attempts, actual DB lease expiry/unfinished attempt, commit before private historical pin/open, complete range and actual EOF/size/SHA. Pre-gate R2's recognized-selector permission floor and deferred safe failures must be inherited through the source reader; Task5 must not introduce a separate convenience source path.

LocalFileClient's frozen send uses ofInputStream and reads error bodies inside send, before returning a body to its caller. Therefore the handler's post-open registration alone cannot bound all local reads. Section 6 explicitly requires a transport-boundary deadline/abort path, bounded error bodies, finite open/header wait, verified unblock and unavailable status if that cannot be demonstrated. This is accepted as a mandatory prospective gate, not as an existing implementation proof. At source adoption, choose the concrete cancellation mechanism and register its bounded lifetime before any success/error/metadata body read can block; state how concurrent normal close/watchdog close, late EOF and timeout arbitrate exactly once. Header timeout or thread interruption alone cannot close this gate. A watchdog whose close itself blocks indefinitely or an abandoned reader consuming a worker slot fails it. S3 also needs concurrent abort/release and real stalled-body qualification; RangeReader's present boolean close guard is not evidence of thread-safe exactly-once release for the new watchdog use.

The design forbids accepting a parse result until close and final cancellation checks succeed and rejects trickle traffic through whole-file/batch deadlines. That is the correct behavior. No actual local/S3 deadline or permit-lifetime claim is made here.

### Queue attempt check and atomic output

The final clarification correctly identifies a real baseline omission: validLease validates the durable_task row but does not SELECT the current task_attempt before effects. Adding an exact owner/unfinished-attempt check after the complete task-lock set and before Supplier is appropriate. Do not move it into lockRoots. The frozen current completion/renew path is singleton; if a future scope contains additional tasks, explicitly acquire their complete sorted union before the first attempt. Avoid recursion and any new identity/domain pass.

Because renew and both completion overloads share validLease, document and test that they all reject missing/ended/foreign attempts, while a renewed DB expiry with an older lease DTO remains valid. Keep Runnable validation-before-effects and Supplier effects-before-actual-output-validation. endAttempt currently ignores its update count; with the row authenticated and held in the same transaction, require the expected one-row terminal transition rather than silently claiming atomic attempt completion after a zero-row update. This is a prospective integrity check consistent with the proposed attempt gate, not an additional finding.

The output model correctly uses actual transcript_revision.revision and its (meeting_id,revision) uniqueness, fixes RAW to original producer businessVersion, advances no meeting/pointer again, and distinguishes historical producer version from today's version. There is no producer_task_id uniqueness constraint to rely on. Exact new-row readback, source paragraph/stable ID/global ordinal preservation, empty marks/time/speaker, actual typed-output validation, flush-last and rollback matrix are appropriate. T5DR-01 must close before these private writer requirements are executable.

### Bootstrap, health and narrow lifecycle changes

Worker -> adapters -> core introduces no cycle; API may use the same adapters assembly without Worker dependency. Explicit TranscriptWorkerMain avoids colliding with the integration-test SPI provider and actually starts the existing poll/claim loop. Pre-exposure attach-once and exact per-JVM DataSource/B03/registry/issuer identities preserve the adopted assembly contract. Separate JVMs acquire fresh DB authority rather than sharing in-memory tokens.

The authenticated private health probe serves the necessary installed-loop evidence: actual polling/progress, bounded stale age, exact handler/pool/profile, fail-closed identity, and no sensitive storage/DB detail. It is internal management plumbing, not a new company protocol. Keep fixed bounded decoding/timeouts and configured exact mTLS peer identity; saturation must not be reported unhealthy merely because no empty claim occurs. Existing accepted replay remains constructible when new intake/execution is unavailable. NOT_INSTALLED/UNAVAILABLE/INSTALLED must follow actual installation, not task count or class presence.

The read-only contextual cancellation seam and registration-after-cancel rule are narrow and preserve old handler construction. Guard unexpected-error quarantine against closed/cancelled/lost as required, and keep a finite total shutdown bound rather than summing unbounded waits. T5DR-02 is the necessary concurrency correction before this can be adopted alongside Task7.

### Actual evidence and custody

The proposed positive path uses genuine local HTTPS and S3 uploads/intake, actual queue/WorkerLoop, real parser and MySQL; SQL-seeded RAW is explicitly excluded as primary proof. Fresh JVM B consumes A's existing READY task and retained original archive/input/route after default change, with distinct PID/command/exit evidence and no re-enqueue or direct harness claim. This is process restart, not DB restart.

The prospective rollback, stale/expired/revoked, stream corruption, aggregate and close/loss cases cover the principal failures. Current-run unique S3/local/TLS custody, absent-target guard, selected-field inspection, preserved MySQL/history, secret-path-only receipts and fresh XML/source binding are adequate boundaries. The old fixture helper's unsafe defaults/full inspect are explicitly not treated as permission to run it. No new DDL or company/HA/native-I/O/capacity/B04 Q401 qualification is implied.

## Disposition

Revise only the affected design contracts for T5DR-01 and T5DR-02, then perform bounded re-review of those changes and their compatibility consequences. Retain the accepted measured parser/archive/source/atomicity/bootstrap/evidence requirements and the prospective local/S3 abort qualification gate. This report does not authorize implementation or runtime activation and does not assess live Task3 code.
