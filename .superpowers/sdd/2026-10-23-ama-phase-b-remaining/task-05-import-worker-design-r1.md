# Task 5 R1 — confined completion and renewal drain

2026-10-23. DESIGN REPAIR ONLY. Supersedes affected completion/cancellation contracts in task-05-import-worker-design.md; accepted remaining sections stay binding. Original raw SHA256 26a5b94befb57b5802b37b7dcd5e0679fc42add7f6d0e9a69602da871556d06f; independent review raw SHA256 90f4d86d43a0d0d87889d7a33c1e2e80696a389763853f298ffcdd5679feb6a7, read fully and independently rehashed. Findings T5DR-01 and T5DR-02 accepted. No original/review rewrite, source/test implementation, test execution, Git mutation, DB/Docker/privateenv operation or subagents. Only this R1 and its closure record are written. Frozen source reference 3e393dbc9485b187b89bfc03abd88821a0aabddb; changing Task3 implementation excluded.

Task3 pre-gate R2 is now explicitly binding: recognized selector minimum READ/EDIT authorization precedes deferred missing/legacy/ambiguous-provider failures on the one genuine handle. Read full R2 and Task7 Worker ownership/monitor/close section for this repair. Accepted measured parser, exact original archives, real source pipeline, RAW revision rules, assembly, health, fixture custody and fresh-JVM gates remain; no implementation or production activation approval follows.

## 1. T5DR-01: queue-confined production completion

### 1.1 Decision and package ownership

Use a queue-owned package-internal after-task protocol for NEW production import. Retain TaskQueue and its existing Supplier/Runnable signatures and Task1 ordinary trusted-callback behavior. Do not attempt to sandbox or retrofit arbitrary existing callbacks. For the production TRANSCRIPT_IMPORT_V1 registration, however, the returned Supplier is an inert private data carrier: get() ALWAYS throws, even in a same-DataSource transaction. Only the registered policy's confined queue path can consume its intended rows.

Concrete new internal interfaces/types live in adapters package ama.task alongside JdbcTaskQueue. The production registration implementation is a private nested class of the shared adapter runtime assembly in that same package; authority/data preparation collaborators still use the adopted Task3 issuer and full graph protocol. External API/worker code sees only the installed queue and a preparation function that returns Supplier<TaskResult>. It cannot construct the registration, after-task context, completion invocation or private writer. No new public authority interface, setter, second B03/JdbcTaskPreparations issuer, ThreadLocal/global prepared-state cache or late root acquisition.

Proposed package-internal signatures (names frozen for the completion protocol, Task3 domain-facade names aligned only after its source adoption):

```java
// adapters/ama/task/ConfinedTaskCompletionPolicy.java, package-private
interface ConfinedTaskCompletionPolicy extends TaskDomainPolicy {
    CompletionInvocation bindCompletion(
        JdbcTaskQueue.AfterTask scope, Supplier<TaskResult> preparedCarrier);
}
interface CompletionInvocation { // package-private, implemented privately by import policy
    TaskResult write();
    void validateActual(TaskResult returned);
}

// JdbcTaskQueue; nested package-private type, constructor PRIVATE to queue
static final class AfterTask {
    // private fields: exact queue, DataSource, actual holder, DomainScope,
    // complete specs, registered policy, locked task/attempt observations,
    // phase, exact carrier identity, exact invocation identity, returned result.
    TaskDomainPolicy.PreparedDomain preparedFor(
        JdbcTaskQueue expectedQueue, ConfinedTaskCompletionPolicy expectedPolicy,
        List<TaskSpec> exactSpecs); // verifies authenticity/holder and BINDING phase
    void requireInvocation(JdbcTaskQueue expectedQueue,
        ConfinedTaskCompletionPolicy expectedPolicy,
        CompletionInvocation exactInvocation, Supplier<TaskResult> exactCarrier,
        TaskLease expectedLease, CompletionPhase expectedPhase);
}
enum CompletionPhase { BINDING, BOUND, WRITING, WRITTEN, VALIDATING,
                       VALIDATED, SETTLED, CLOSED }
```

No outside caller gets AfterTask: bindCompletion is invoked only internally by the queue with its freshly minted private instance. No public mint/phase-transition methods. All phase mutations are private queue methods. The private constructor cannot be invoked by an interface implementation or detached record. Invocation methods are package-internal composition; they succeed only when queue has set the corresponding one-use phase and exact identity on this live context.

### 1.2 Exact queue/policy construction without a public setter

Bind the private production policy to the EXACT configured queue instance during a queue-controlled pre-exposure factory. Keep existing public JdbcTaskQueue(source,fences,outbox,policies) unchanged for old registrations. Add only a package-private installation path used by the shared assembly:

```java
static JdbcTaskQueue installConfined(DataSource source, JdbcSubjectFences fences,
    JdbcOutbox outbox,
    Function<JdbcTaskQueue, Map<String,TaskDomainPolicy>> trustedFactory);
```

Implementation uses a private constructor: initialize source/tx/fences/outbox; invoke the trusted factory with this not-yet-exposed queue; factory constructs private ImportPolicy(expectedQueue=this, same adopted domain collaborators), returns complete map; queue copies/validates immutable registrations and sets its initialized state only at successful construction end. No registered operation may run before initialization. The factory is private assembly code, cannot retain/publish incomplete components, and failure exposes nothing. This is construction confinement, not a public lifecycle setter or an alternative authority issuer. Existing input/ref/outbox assembly graph stays acyclic: policy holds queue identity only for comparison, never calls queue to acquire roots. API and Worker use this exact factory within each process.

The private ImportPolicy contains the same configured authentic domain-preparation issuer/authority from Task4 registration; it is not a permissive decorator/fallback around another policy. Foreign queue, policy, source, preparation issuer or actual transaction holder is rejected even if IDs/URLs/DTO values match. The carrier records its exact private ImportPolicy owner, so another installation cannot consume it.

### 1.3 Preserve and carry actual acquisition through lease validation

Refactor queue's private call stack only: validLease returns `Optional<ValidatedLease>` rather than discarding DomainScope and returning Row. No public contract change:

```java
private record ValidatedLease(DomainScope domain, Row task, LockedAttempt attempt) {}
private Optional<ValidatedLease> validLease(TaskLease lease);
```

DomainScope retains exact registered policy, complete immutable full-spec list, TASK_MUTATION purpose, genuine PreparedDomain and locked subject/domain. validLease performs original prepareDomain/lockDomain once; authentic issuer/source/actual holder/complete-spec checks stay in registered hooks. Then lock actual task set, compare full immutable spec/current owner/attempt/state/current business/cancel/lifecycle/expiry and input. Lock and verify exact unfinished attempt only AFTER all task locks. Current completion/renew path is singleton. A future expanded scope must declare/acquire complete sorted task union before any attempt; this R1 does not silently add multi-task completion support.

AfterTask is minted ONLY after this returns a genuine validated lease and only for the exact registered ConfinedTaskCompletionPolicy path. It stores the genuine DomainScope object by identity, not reconstructed facts, and actual holder captured using the exact source's active transaction resource. Before each use verify that resource is still identical, queue is initialized, queue's immutable registration at task type is expectedPolicy, source identity exact, domain policy/purpose/specs/prepared identity unchanged, and task/attempt/full lease expectations exactly equal validated rows. Renewal may have extended actual DB expiry; equality with old DTO expiresAt is not required. Expiry must be freshly validated by queue after lock waits, as existing validLease requires.

preparedFor returns the retained actual PreparedDomain only to exact expected queue/policy/fullspec during BINDING. ImportPolicy calls its already-configured JdbcTaskPreparations.require/value/permitsEffects and adopted authority's genuine held-graph verifier on that value, proving SAME issuer/source/holder/gate/full prelocked graph and operation purpose. These checks acquire no identities/roots/sources/tasks; they validate capabilities already on this call stack. A needed Task3 facade amendment must be private genuine-token verification, never DTO minting. Re-running source reader/validLease/preparation is forbidden.

### 1.4 Private carrier, invocation and one-use phases

Private carrier shape owned by ImportPolicy:

```java
private final class RawCarrier implements Supplier<TaskResult> {
    final ImportPolicy owner;
    final PreparedRaw rows; // bounded immutable intended rows only
    public TaskResult get() { throw new IllegalStateException("QUEUE_PATH_REQUIRED"); }
}
private final class RawInvocation implements CompletionInvocation {
    final AfterTask scope; final RawCarrier carrier;
    final PreparedDomain authenticPrepared; // retained same-call actual preparation
    // private constructor called only from bindCompletion after all checks
    public TaskResult write() { /* require WRITING; DB-only insert/readback */ }
    public void validateActual(TaskResult actual) { /* require VALIDATING */ }
}
```

PreparedRaw remains data: exact immutable lease fullspec/input/archive binding/ordered prepared segments/RAW ID/time/owner observations and effective bounds. No data-only constructor grants write permission. bindCompletion requires private RawCarrier class and carrier.owner == this; a public Supplier lambda or carrier from another installation fails before any write. It validates intended exact fullspec/task/attempt association and authentic prelocked graph while context phase BINDING. No hash/archive/parse/read stream or additional row acquisition. It constructs one private RawInvocation retaining this context+carrier+authentic preparation. Queue verifies invocation nonnull, binds exact carrier/invocation identities and advances BOUND. It never calls the carrier's get.

Queue dispatch algorithm for production confined path:

```text
work transaction -> validLease retains DomainScope + actual task + unfinished attempt
 -> mint AfterTask BINDING
 -> registeredPolicy.bindCompletion(context, carrier)
 -> bind exact invocation and carrier; phase BOUND
 -> WorkerLoop's queue-owned callback admission guard, after DB locks
 -> phase WRITING; invoke write once
 -> store exact returned result; phase WRITTEN
 -> phase VALIDATING; invoke same invocation.validateActual(result) once
 -> phase VALIDATED
 -> completeValidated(actual task,result), exact attempt update, audit/outbox
 -> phase SETTLED; outer queue work flushes last and commits
finally context phase CLOSED, on success or any exception; never reusable
```

The admission guard must remain executable even though carrier.get is inert. See section2's additive WorkerLoop/queue composition. Authority and write are still solely queue-owned, and effects precede actual output validation. validateActual is the registered output-validation implementation for this confined path: it shares SAME invocation/carrier/prepared graph, performs bounded actual newly-created RAW+segments readback and exact prepared comparison, then existing production output-provenance checks. It cannot read a global last-plan. Old TaskDomainPolicy.validateOutput remains for old completion paths; confined production registration rejects SUCCEEDED via ordinary complete or old Runnable (and arbitrary Supplier) because those paths cannot provide this invocation. Named no-write FAILED/RETRYABLE carriers use a private sibling invocation with exact spec/lease binding and no output; no unvalidated success shortcut.

RawInvocation.write first requires exact context/queue/policy/carrier/invocation/holder/fullspec/task/attempt and WRITING phase, plus local invocation writeEntered CAS false->true. It then calls private writer under same context; no direct public writer exists. validateActual similarly requires VALIDATING plus local validationEntered false->true and exact returned TaskResult identity/value captured by queue. Unexpected phase/use rejects BEFORE any relevant effect. Duplicate invocation, old context, other holder, suspended holder and after-return invocation fail. All phase guards remain true even if trusted test code retains references for negative probes. Queue closes context in finally before it leaves transaction work; any flush/commit failure rolls all DB output/task/attempt/audit/outbox back. CLOSED context cannot be reopened after rollback. Old callbacks retain Task1 semantics; NEW production success cannot bypass confinement.

### 1.5 Preserve WorkerLoop Supplier compatibility with a private guarded carrier

Original WorkerLoop wraps Supplier in a lambda to check lost before invoking get; that would erase private carrier type and attempt its inert get. Therefore add an adapters-independent, data-only wrapper protocol in core for the contextual prepared factory, with no authority methods:

```java
interface PreparedCompletionEnvelope extends Supplier<TaskResult> {
    Supplier<TaskResult> payload(); // inert private carrier for configured production policy
    void admit();                  // deny-only WorkerLoop lifecycle guard; no DB authority
}
```

WorkerLoop privately implements this interface; get() throws for this contextual confined use. Payload is the exact private carrier returned by preparation. Queue unwraps one envelope ONLY on the registered confined production path, captures exact wrapper/payload identity in context, calls envelope.admit once after bind/DB checks and before WRITING, then invokes the private invocation. Any caller-created envelope still grants nothing: policy requires authentic carrier/issuer/full scope, and actual queue lease/domain revalidation remains mandatory. Returning a fake admit-success cannot create a carrier or change fresh DB authority; never treat envelope as an authentic authorization token. Raw carrier exposure remains harmless because get always denies and every attempted queue completion must acquire genuine authority. Wrong wrapper/payload/recursive envelope or null rejects.

Old WorkerLoop.PreparedHandler/factory continues returning ordinary Supplier wrapped as today and using original Task1 branch. New contextual factory requires the production confined path and wraps its returned carrier in its private envelope; no reflection, marker boolean, ThreadLocal or global invocation state. Default TaskQueue implementations reject this unsupported prepared mode before running effects. A supplied generic wrapper cannot route a production carrier through a legacy policy; registration is queue-fixed by actual task type.

## 2. T5DR-02: drain ordinary renewal BEFORE completion DB entry

Choose a phase-and-drain protocol, not monitor-held JDBC during completion. One existing heartbeat scheduler; no second renew timer. Lease20s/heartbeat5s/poll5s unchanged. One per-execution monitor protects phase/renewInFlight/generation/lost/closed/dispatched/admission state. NO ordinary renewal holds it over JDBC. NO completion holds DB locks while waiting for an in-flight renew to finish.

States for ordinary/prepared completion:

```text
ORDINARY -> DRAINING_RENEWAL -> DISPATCHED -> WRITER_ADMITTED -> FINISHED
any pre-admission state -> LOST or CLOSED (sticky deny)
DISPATCHED exception/unknown ACK -> FINISHED_UNKNOWN (no second completion)
```

State names are local runtime ownership, not new durable task Outcome values. Task7 external MARKING/OPERATION remain distinct branch; never mix ordinary completion with that branch.

### 2.1 Tick and publication

Tick enters monitor briefly. Only ORDINARY, open, not lost, no renewalInFlight permits a tick. Set renewalInFlight=true, capture monotonically increasing local generation/tick ID; release monitor. Call bounded queue.renew(lease) outside monitor. Reenter monitor to publish once: clear that exact in-flight ID, mark sticky loss on false/exception if phase still ORDINARY or DRAINING_RENEWAL, notify waiters. A closed/lost context remains denied; success cannot revive it. Result from a stale/nonmatching generation cannot change authority/phase. No tick can start in DRAINING_RENEWAL/DISPATCHED/WRITER_ADMITTED/FINISHED or future Task7 MARKING/OPERATION. Tick never holds monitor while DB waits; close is responsive.

### 2.2 Dispatch barrier before DB acquisition

Preparation finishes outside DB. Dispatch under monitor changes ORDINARY -> DRAINING_RENEWAL, disables new ticks/cancels future schedule(false), records generation. Release monitor. Await currently in-flight tick completion using condition wait that releases monitor, bounded by configured drain timeout and with NO DB transaction/locks. Close/loss wakes promptly; timeout cancels/denies the attempt without dispatch or second completion. The actual queue later expires/reclaims it. Existing blocked JDBC renewal remains under finite DB timeout and must finish for bounded resource closure; it cannot block a callback because no completion has begun.

Only after in-flight=false and fresh monitor check !lost && !closed may dispatch set DISPATCHED and completing=true. Completing flag is set before any queue call; from now no ordinary quarantine/retry completion regardless of false return/exception/unknown commit. Release monitor, call queue Supplier completion once. A close after this selection but before actual queue entry is harmless: envelope.admit checks closed later; queue may acquire locks and then roll back without writer.

No heartbeat renew runs concurrently with this completion DB transaction. This deliberate short finalization pause is safe because validLease freshly verifies actual DB expiry after lock waiting; if lease expires while waiting, callback is not invoked. Do not extend lease from memory, renew after taking completion locks, or move parser/I/O into the pause. Bound final completion work with configured DB/transaction limits below lease profile; timeout is an operational failure, not a deadlock-resolution mechanism.

### 2.3 Callback admission, close, terminal handling

At the queue-owned envelope.admit after genuine DB checks, take monitor briefly; require exact DISPATCHED generation, !closed/!lost/!cancelled, and not admitted. Set WRITER_ADMITTED; release. No monitor held during writer/validator/flush/DB waits. Renewal cannot need the monitor while holding/waiting those locks because dispatch already drained it and excluded later ticks. Thus no monitor->DB / DB->monitor cycle exists.

Close disables claims globally, marks context closed/cancelled and notifies drain waiters under monitor, then aborts streams/interrupts/joins outside monitor. If close/loss wins before admit, it throws and no RAW INSERT. If admission wins first, fresh DB-authorized atomic writer may finish or cancellation checks roll it back; never claim to retract committed effects. Watchdog/close registration and parser checks remain deny-only and independent of DB permission. After queue return/throw, publish FINISHED/FINISHED_UNKNOWN locally; late queued ticks do no JDBC and cannot convert successful completion into lost. No ordinary quarantine after DISPATCHED. Preparation errors before dispatch use the same drain/dispatch path for a confined no-write FAILED/RETRYABLE result; closed/lost/cancelled paths do not quarantine.

### 2.4 Task7 ownership compatibility

This R1 refines ordinary tick mechanics only. It preserves Task7 guarantee that an already-selected ordinary renewal finishes before marker selection and a queued tick cannot run after handoff. Future ExternalTaskExecution.begin must use the SAME DRAINING_RENEWAL barrier before changing to MARKING, then perform adopted independent marker/operation-tracker handoff. MARKING selection occurs only after no ordinary renew is in flight; it must not ordinary-complete afterward even on unknown marker ACK. Task5 adds no marker/external send/permit settlement. Task7 operation tracking/receipt-only behavior remains its adopted protocol; no blanket permit release on Task5 close. This explicit refinement supersedes only Task7 shorthand that ordinary renew holds monitor over JDBC; it preserves its ordering semantics and prevents shared runtime reintroducing the inversion.

## 3. Exact attempt validation and terminal transition

Shared validLease used by renew, old Runnable and Supplier completion must lock/check task_attempt(task_id,attempt), exact owner, ended_at IS NULL after actual task set. Missing/ended/foreign row denies all three; current renewed DB expiry plus older TaskLease timestamp remains valid. Keep full actual task state/spec/owner/current fences and DB time check.

When closing an attempt, endAttempt's existing UPDATE ... WHERE task_id=? AND attempt=? AND ended_at IS NULL must affect exactly1 for a path that claims to end a validated/current positive attempt. Add owner predicate when the verified owner is available; require equality to that locked observation. Zero or >1 throws integrity failure and rolls back entire task/output/audit/outbox transaction. READY attempt0 legitimately emits no attempt transition; do not demand an invented row. Existing expired RUNNING reclaim path must authenticate/lock its exact old unfinished attempt after task lock before it ends EXPIRED; it then requires1. Attempt-limit handling must not attempt to end the same attempt twice: distinguish whether reclaim already ended it, and perform exactly one transition before terminal task state. Do not silently tolerate a second zero-row UPDATE merely to preserve old call arrangement. This is narrow internal queue state accounting, not a new scheduler/schema/outcome.

Required tests cover missing/ended/foreign attempts on renew/Runnable/Supplier, actual expiry renewal, expected1 transition on success/retry/failure/expired reclaim, injected0 transition rollback, and expired-at-max-attempt exactly one attempt end plus FAILED task. The task/attempt existing vocabulary remains unchanged.

## 4. Deterministic prospective closure proofs

No tests ran. Implement barriers in owned test instrumentation around the real queue/Worker path; do not use sleeps as the ordering oracle or let DB timeout break a self-created cycle.

### T5DR-01 authority/confinement

1. Actual preparation returns RawCarrier. Call get directly outside transaction and inside arbitrary same-source transaction: QUEUE_PATH_REQUIRED before RAW INSERT. Ordinary complete/Runnable/arbitrary Supplier cannot produce production SUCCEEDED; old unrelated Task1 callbacks retain their original behavior.
2. Retain test-observed invocation/context from authentic call and attempt use with foreign queue/registered policy/preparation issuer/DataSource wrapper/another or suspended holder, mismatched full spec/task/owner/attempt/prelocked graph, after transaction/context termination, during wrong phase, and twice. Every negative fails before first or additional RAW INSERT. Public envelope/no-op admit/DTO equality cannot manufacture the private carrier or AfterTask; a retained carrier alone is not permission.
3. Authentic same-issuer/DS/holder/fullspec/domain/task/unfinished-attempt invocation writes exactly once; validator observes actual new RAW/complete segments and compares exact SAME immutable prepared rows. Interleave two tasks/holders with different plans and reversed segment values; neither can validate using the other's plan. No static/ThreadLocal/cache query supplies the plan.
4. Inject wrong/null return, corrupt actual new body/source/order/count/RAW producer, validator failure, attempt update0, audit/event/flush failure: all domain/task/attempt/outbox effects rollback. Every context ends CLOSED including failure; retry reacquires fresh scope, never revives old context. New-row read exception grants no insert permission by itself.
5. Trace full identity/domain/session/file/task/attempt order and zero late root acquisition. bind/write/validate checks use same genuine PreparedDomain/actual holder. Parsing/archive/canonical hashing/source I/O complete before queue entry. All task UUIDs precede any attempt if future batch support is explicitly added.

### T5DR-02 renewal/admission/close

A. Start a real renew tick, pause its JDBC call before return, finish preparation and request dispatch. Observe DRAINING_RENEWAL and no completion transaction/root acquired. Close concurrently: close selects immediately under free monitor and wakes drain; release renewal; no callback/ordinary quarantine. Separate positive releases successful renewal without close, then exactly one completion begins.

B. Complete drain and let completion acquire first subject/meeting root; pause before Supplier admission. Run a queued heartbeat tick while that root is held. Tick sees DISPATCHED and returns without JDBC or loss. Release callback barrier: completion progresses without any DB timeout. Assert no monitor holder is waiting for completion locks. This is the exact schedule that previously inverted.

C. Already-running tick returns false/throws before drain finishes. Sticky loss wins, dispatch never enters queue and no writer. No successful later tick can revive. Separately revoke/expire actual lease AFTER drain while completion waits for roots: fresh validLease denies despite prior successful renewal; no callback.

D. With completion holding first root but before admit, concurrent close obtains monitor and sets closed; release callback barrier, admit denies, zero RAW. Converse barrier permits admission first, then close: operation either fully commits under real authority or fully rolls back by cancellation; no partial rows or second complete. Do not assert an already committed effect can be retracted.

E. At DISPATCHED inject completion exception or unknown commit ACK; exactly one queue completion attempt, no ordinary quarantine. Successful task terminal state followed by queued/late tick causes no renew/JDBC/loss rewrite. Drain wait timeout before dispatch denies work without DB completion; actual lease recovery handles the task later.

F. Test no new tick after DRAINING_RENEWAL, only one in-flight tick ID, no stale generation publication, bounded shutdown of a truly blocked renew via its configured DB bound, actual streams aborted outside monitor, healthy saturated loop observation. Future Task7 handoff test uses identical drain barrier then MARKING and proves no ordinary renew/quarantine after marker; no Task7 behavior is implemented by Task5 tests.

## 5. Retained measured/source/runtime/evidence gates

All original accepted constraints continue, with these explicit precision points from review:

* Measured parser uses actual BoundedSource.used/Lines.units/segment count only after successful full EOF/hash/NO_TEXT/final cancellation checks. ALL BOM/separators/blank/supplementary units count. Old parse/Result/Segment/profile/hash/stable IDs/independent goldens unchanged. Remaining aggregate bounds before growth and final-file rollback remain required. ConfigBindings parseBudget retained through BOTH initial construction and complete(), not dropped when completed binding is returned.
* Stored original full manifest/producer/sidecar/ordered batch and four exact original archives stay authoritative after default changes. No current-default substitution and no archive/hash/parse under DB roots. Task3 pre-gate R2 applies to WorkerRequest even when optional provider resolution fails; no alternate convenience source path.
* Local transport must establish finite lifetime before any success/error/metadata body read, including LocalFileClient.send error-body parsing before Download returns. Parent handler registration alone is insufficient. Source adoption must choose and prove a concrete finite abort mechanism; request-header timeout/interruption alone is not qualification. Stream lifecycle arbitration must be exactly-once OPEN -> EOF_CLOSED or ABORTED/FAILED, with a sticky deadline/cancel winner and no late EOF accepted after abort. Normal-close/watchdog-close races cannot double-release permits; watchdog close itself cannot block unboundedly. S3 RangeReader's current unsynchronized boolean is not accepted as proof: qualify atomic/synchronized exactly-once transport abort/release with real stalled body. Preserve mTLS/HTTPS/private pin/whole-file and batch deadlines/trickle rejection and zero abandoned readers.
* RAW revision is actual producer businessVersion, original schema field revision, no additional meeting advance/current pointer/human save. Unique(meeting_id,revision) and lack of producer unique index interpreted exactly; complete immutable output validation and flush-last rollback remain mandatory.
* Shared adapters assembly uses one exact process-local DS/B03/registry/authority/policy; explicit production main actually starts queue consumption, no API module dependency or extra lease provider. Installed health comes from actual loop/probe, while Task4 accepted replay remains available without current execution readiness.
* Real local HTTPS and S3 uploads -> actual intake READY -> actual WorkerLoop/parser/queue/MySQL -> complete RAW, plus fresh JVM B consuming A's same READY/original archive/source after default change. No SQL-seeded RAW as main proof, mock successes, re-enqueue, direct harness claim or DB-server-restart relabel. Unique guarded current-run fixture targets/secret-free receipts/fresh XML/source SHA; no old fixtures or reset/newDDL.

## 6. Bounded ownership and disposition

R1's additional source proposal is limited to queue-private retained ValidatedLease/AfterTask/protocol and confined installation factory; private production carrier/invocation/writer registration; core data-only PreparedCompletionEnvelope and Worker contextual wrapping; ordinary renewal phase/drain/admission logic; exact unfinished attempt/one-row terminal transition. Existing public constructors, TaskQueue Supplier/Runnable contracts and unrelated trusted callbacks preserve compatibility. No generic authority issuance API or external retention/scheduler change.

The named internal completion protocol is now concrete rather than deferred to an unspecified private writer convention. Task3 facade alignment may still adjust calls to its authentic held-graph verifier, but cannot relax any identity/holder/purpose/fullscope/task/attempt/phase rule. R1 independently proposes a narrow refinement to Task7 ordinary renewal ordering preserving its handoff guarantees; independent re-review must accept this before implementation freeze.

Status: T5DR-01 and T5DR-02 addressed at DESIGN level, pending independent bounded re-review. No implemented/tested/runtime/production PASS. Original accepted transport abort/runtime/fixture settings/evidence gates remain future requirements. B01 IMM PARTIAL, B04 Q401 UNKNOWN and company/HA/native-I/O/capacity boundaries unchanged.
