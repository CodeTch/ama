# Task 7 external prepared-output and immutable stale-candidate retention

Status: DESIGN CANDIDATE ONLY. Independent review is required before implementation or activation.

Date: 2026-10-23. Assigned tree `/home/tanren/ama/.worktrees/phase-b-completion`; coordinator-supplied HEAD `46a1adad4dd4afda3272e34669235d4126c480d6`, remaining-work BASE `34cb226aefe99c67d3681af68d748b0aeea4e073`. These were supplied, not independently Git-verified. Task 1 code is under separate review; this document neither reviews nor adopts it.

## 1. Authorities and inspected source

Read complete controlled-runtime spec including Normative R1; remaining plan including Tasks 7/9/10/11; ignored-workspace b07-b08-preflight.md and design-r1-review.md. R1 supersedes preflight READ authority and positive-epoch suggestions: retention requires ACTIVE current owner EDIT, and automatic first save requires actual persisted zero intent. R1 approval leaves this detailed protocol unapproved.

Read actual ExternalOperationPort, TaskDomainPolicy, JdbcTaskPreparations, JdbcTaskQueue, JdbcExternalOperations, JdbcModelPermitPool, WorkerLoop, ReconciliationLoop, ExternalOperationHeartbeat, B03 JdbcMeetingAccess/MeetingAccess, ExternalContracts, TaskTransactionContext, JdbcOutbox, relevant V012/V013 definitions and external/permit/B06 seam tests. No tests run, source/test/DB/Git/network/private environment/migration/historical artifact changes; only this document is written.

Observed implementation constraints:

* beginExternal independently commits marker/execution/task RECONCILING, then grants initial-call authority by token object identity. Copied/reconciliation tokens get no initial authority.
* observe discovers old/current specs and executions, acquires identity/domain before tasks/operations, retries changed discovery in a fresh transaction at most four times, preserves proof contradictions and finishes fenced tasks STALE/BUSINESS_FENCED/null output. Both validateExternalObservation and validateOutput currently precede callback. Prepared handling must split immutable remote fact/proof verification BEFORE writer from actual typed-output/provenance verification AFTER writer.
* JdbcTaskPreparations privately binds issuer, exact DataSource, actual Spring holder, full specs and purpose; queue independently checks source/holder.
* B06QueueSeamsIT.factOnlyReceiptSeparatesExpiredOwnerEpochFromActiveAuthorityAndUnknown requires ACTIVE plus invalid owner/epoch/expiry to remain STALE_TOKEN. Existing effects-denied preparation admits exact-current verified terminal fact without restoring effects. Keep this rule.
* B03 EDIT checks ACTIVE owner/lifecycle/governance, but not ever_published itself. Domain policy additionally checks publication/deletion/cancel/input and stage; detached Actor/DTO is no authority.
* Model acquire locks operation/execution then capacity/reservation and rejects duplicate admission. Existing settled(Permit) is independent and validates the actual execution terminal fact. Worker ordinary renew naturally fails after committed marker; current WorkerLoop would wrongly suppress receipt or second-complete unless ownership is explicit.

## 2. Decisions and transaction boundary

ExternalOperationPort remains sole durable external-state owner. Add prepared request/observation methods there, not TaskQueue. A registered policy owns DB-only marker/result writers; caller provides opaque prepared data, never Supplier/Runnable authority or caller-selected writer. Legacy methods retain behavior and gain no retention.

Marker transaction commits independently before any network. Terminal domain/output/task/operation/execution/audit/outbox writes share one independent observation transaction. Prefer preserving existing verified independent permit settlement AFTER that transaction, with bounded durable recovery defined below. This explicitly refines compact Task 7 wording; do not claim marker or permit settlement is in the output transaction. A crash between verified terminal commit and permit settlement conservatively holds capacity, never permits resend or premature release. Root must explicitly adopt this refinement before implementation.

No Task 7 DDL is proposed. Existing external rows, unique generation_candidate.task_id, candidate/task states, model_reservation and publication attempt execution relations suffice subject to replay-equivalence gate. Editor guard/profile DDL belongs to Task 2. Any additional durable receipt identity requirement needs independent review and root allocation, never edits to old SQL or author-assigned migration numbers.

Only dedicated generation retention may relax original businessVersion equality. It cannot grant enqueue/claim/renew/ordinary complete/initial send/retry/adoption/publication authority. Retained success leaves task STALE/BUSINESS_FENCED/null output and verified operation/execution success. No new Outcome enum. PENDING blocks final; STALE does not clear generation debt/current-input blockers. Candidate/final/review/publication serialize on the same meeting root.

## 3. Proposed additive APIs and exact capability boundary

These are proposed names/signatures requiring independent freeze, not existing APIs.

```java
// ExternalOperationPort; defaults fail closed on other implementations.
default Optional<OperationToken> beginPrepared(TaskLease lease,
                                               PreparedExternalRequest request);
default ObservationResult observePrepared(OperationToken token,
                                           PreparedExternalObservation observation);
default ObservationResult observePreparedFact(OperationToken token,
                                               PreparedExternalObservation observation);
interface PreparedExternalRequest {}
interface PreparedExternalObservation {}

// Chosen only from actual task's registered policy instance.
interface PreparedExternalTaskPolicy extends TaskDomainPolicy {
    ExternalRequest requireRequest(TaskSpec actual, PreparedExternalRequest request);
    void writeMarked(PreparedDomain held, ExternalMarkerContext marker,
                     PreparedExternalRequest request);
    ExternalObservation verifyRemoteFact(TaskSpec actual,
        ExternalExecutionContext execution, PreparedExternalObservation observation);
    TaskResult writeCurrent(PreparedDomain held, ExternalExecutionContext execution,
                            PreparedExternalObservation observation);
    void validateStoredResult(PreparedDomain held, ExternalExecutionContext execution,
        PreparedExternalObservation prepared, TaskResult actualResult);
}
interface GenerationRetentionPolicy extends PreparedExternalTaskPolicy {
    interface Retention {}
    Optional<Retention> prepareRetention(PreparedDomain held,
        ExternalExecutionContext execution, PreparedExternalObservation prepared);
    TypedRef retainStale(Retention retention);
    void validateRetained(Retention retention, TypedRef actualCandidate);
}
```

ExternalMarkerContext/ExternalExecutionContext are opaque interfaces, accepted only as private JDBC-held implementations binding exact issuer/DataSource/actual holder and actual locked task/spec/operation/execution/request. Public records or reconstructed interface implementations are rejected. Context grants no method to change ownership/fences. verifyRemoteFact verifies remote receipt/proof and request/execution before writer, without requiring nonexistent output. validateStoredResult verifies actual post-write graph/provenance. Legacy validateExternalObservation behavior remains untouched; prepared path uses explicit split, never skipping the remote verifier.

Outside transactions, MinutesObservationPreparer produces private issuer-bound PreparedMinutesObservation: bounded strict parsing/schema/source verification, exact archive/fixed input binding, authenticated normalized provider receipt, complete immutable row plan and UUIDs. A structural output failure produces a private rejected-result preparation preserving remote receipt rather than losing remote truth. Payload is data, not authority, and may survive same-fact retry; each transaction reacquires authentic access/full graph and compares actual immutable input/task/execution. Private type/issuer verification rejects foreign implementations.

No new external API accepts Supplier, Runnable, arbitrary writer or policy argument. Registered writeCurrent may return a TaskResult as data, but fresh policy checks decide every effect and local outcome. Its SUCCEEDED output requires actual complete persisted graph; its FAILED/INVALID_MODEL_OUTPUT is allowed only for the exact registered normalized-response rejection. Caller-selected failure strings cannot bypass provenance or change remote state.

Registry binds retention explicitly to reviewed generation family only. Proposed Task 9 type MINUTES_GENERATE_V1, input GENERATION_INPUT, output GENERATION_CANDIDATE; Task 9 must freeze full TaskSpec/producer/request/archive dispatch before registration. Publication never implements retention. Interface membership alone does not automatically enable retention on arbitrary task types. Unknown/mixed policy families fail before roots.

Private JdbcMinutesTaskPolicy.HeldRetention captures issuer; exact source; actual holder; private purpose IMMUTABLE_GENERATION_RETENTION; parent authentic EXTERNAL_FACT_OBSERVATION preparation; full discovered specs; fresh current identity/root/prelocked graph; actual task/input/producer; operation/execution ID and number; exact request hash/manifest; authoritative receipt identity; exact staged row plan/candidate identity; original business/cancel/fingerprint. Constructor is private and only invoked after every predicate. Consume with issuer/source/holder/purpose/full-coverage/current-execution checks. It cannot be converted into TASK_MUTATION, enqueue/send/current-write capability. Keep the two generic Acquisition values unchanged; dedicated retention purpose is private, not a generic relaxed acquisition.

## 4. Lock order and complete fresh preparation

Bounded discovery captures complete immutable plan and all affected identity IDs, including actual request creator/current owner as relevant. Recheck after locks; owner/spec/execution change releases all locks and rediscovers. Never acquire a new identity after roots. Original requester must remain actual authorized owner unless separately reviewed transfer semantics exist; no silent request reattribution is proposed.

1. Complete identity set in B03 unsigned-UTF8 order, including inactive identity evidence needed for receipt-only handling.
2. task_subject then shared meeting root; check subject/root revision/cancel consistency. Genuine active access uses B03 issuer/holder. Disabled/deleting/governed fact-only handling may inspect and deny effects; it must not make receipt impossible by requiring EDIT first.
3. Complete union of domain rows in documented table/PK order: editor guard, current pointers/confirmations/final/publication root, original generation input/frozen transcript/config/source/result closure, candidate/existing output. All original sessions sorted, then files sorted. Missing rows/ranges are protected by shared root protocol or explicit range locks. No new identities.
4. All old/current durable tasks and required immutable producer task rows in one complete sorted task-ID phase; compare full discovered specs.
5. External operation then involved executions sorted by execution number. Verify actual task/execution/request linkage; generation changes restart entire transaction and RR snapshot, at most four attempts.
6. Domain/task audit and buffered outbox; existing sequence flush exactly once LAST. No new synchronization or after-commit domain writer.

Independent permit acquire/settled preserve existing operation/execution -> model capacity -> reservation order. Configuration locks complete sorted capacities and never operation/task/domain roots; retain its RR counting discipline. Observation does not take capacity locks in the selected design.

New result insert parents/IDs belong to prelocked domain plan; no new FK-parent lock discovery after task/operation phase. Actual output validation uses current/locking reads or already-held rows, not old consistent snapshot. Compare complete fixed facts, not caller hash only. No large parse/hash/archive/file/network operations under locks; bound counts and cumulative sizes before allocation, never truncate.

Verify actual immutable producer/input/task relation: full type/pool/producer/subject/input/fingerprint/businessVersion/cancelEpoch/priority/attempt limits; original base revision/archive profile; true original producers; actual request manifest/hash and exact execution-to-task association; complete original graph. Captured Spec equals caller Spec is insufficient. Original history verification never substitutes latest config/current input.

## 5. Marker and initial-send protocol

beginPrepared follows existing independent strict validLease, validates actual registered request, creates/adopts only legitimate RETRY_QUEUED/PREPARED execution, sets RECONCILING, invokes registered writeMarked, verifies actual marker relations, audits/flushes. Task 11 hook writes real publication_attempt referencing actual operation/execution/confirmation and publication runtime state. No fabricated IDs or provider I/O. Failure rolls back domain marker, task/execution/operation and outbox.

Only actual commit ACK grants existing token-object call authority. Marker ACK unknown returns no authority and sends nothing. Context becomes MARKER_UNKNOWN and uses discovery/query; no repeat begin/send on that stack or inference of rollback from connection error. Existing marker means query only. Truly rolled-back marker can leave an expired RUNNING task recoverable by a fresh strict ordinary claim; no abandoned context authority carries over. Ambiguous ACK itself never authorizes send.

Model transport needs token-bound committed global reservation after handoff and before send. Duplicate acquire, no capacity or acquisition ACK unknown means no send. Runtime consumes local send permission once. A reviewed verifier may certify NOT_SENT only from authentic local execution evidence; caller labels are not proof. Otherwise remain UNKNOWN/manual-required with conservative capacity. Company adapter absent qualification fails closed; controlled loopback is explicitly selected normalized test provider.

## 6. Prepared observation transaction and separate remote/local outcomes

```text
observePrepared(token, staged):
  reject ambient transaction; revoke initial-call authority
  up to four NEW q.work transactions:
    discover operation, old/current execution and complete task specs
    prepare EXTERNAL_FACT_OBSERVATION and full domain graph
    lock tasks, operation, executions; verify actual discovery
    preserve existing token/fact-only/old-proof-contradiction gates
    verify exact remote fact/proof and original input/request/execution
    UNKNOWN/MANUAL_REQUIRED: existing authorized control branch only;
                            no candidate/result writer; no capacity release
    canApply = genuine effectsAllowed && live op token && ordinary q.current
    if canApply:
      localResult = registeredPolicy.writeCurrent(held, execution, staged)
      policy.validateOutput(actualSpec, localResult)
      policy.validateStoredResult(held, execution, staged, localResult)
    else if remote SUCCEEDED && locally valid result && live token
            && genuine effectsAllowed && dedicated retention capability:
      candidateRef = retentionPolicy.retainStale(capability)
      retentionPolicy.validateRetained(capability, candidateRef)
    else: no domain writer, no fake output
    persist independently verified remote operation/execution terminal fact/proof
    finish task: canApply ? exact policy-verified local terminal result
                         : STALE/BUSINESS_FENCED/null output
    audit/candidate/terminal events; flush once; commit
    return canApply ? APPLIED : RECORDED_FENCED
  throw ObservationRetryRequiredException; same fact unacknowledged
outside observation transaction:
  attempt existing verified independent permit settlement
  on failure leave durable terminal rows for bounded settlement recovery
```

The prewriter remote verifier never requires output-row existence. Postwriter verifier independently reads actual complete row graph and matches staged payload/original provenance. Retention uses dedicated postinsert verification, not a fake current fence or generic callback. Callback throw/null, wrong output/type, missing/extra/cross-input graph, invalid result mapping, audit/task/flush/commit rollback fails the whole observation transaction. No unrelated precommit output creation.

Remote versus local outcome mapping is explicit:

| Verified remote fact | Registered local result under current authority | Durable remote state | Durable task |
|---|---|---|---|
| SUCCEEDED, exact valid normalized output | SUCCEEDED with actual output | SUCCEEDED | SUCCEEDED/output |
| SUCCEEDED, strict output/schema/source/encoding/budget invalid | FAILED/INVALID_MODEL_OUTPUT/null | SUCCEEDED | FAILED/INVALID_MODEL_OUTPUT/null; terminal, never retryable |
| SUCCEEDED, valid output, only business version advanced and retention allowed | full immutable STALE candidate retained | SUCCEEDED | STALE/BUSINESS_FENCED/null |
| SUCCEEDED or FAILED_ZERO_EFFECT, effects forbidden | no domain result | respective verified terminal state | STALE/BUSINESS_FENCED/null |
| FAILED_ZERO_EFFECT with complete authoritative proof | registered local FAILED mapping | FAILED with proof | FAILED with registered error/null |
| UNKNOWN or MANUAL_REQUIRED | no terminal output | existing UNKNOWN/manual control state | existing RECONCILING handling |

A model may consume capacity/charge and return invalid JSON: that is never FAILED_ZERO_EFFECT. Preserve its authoritative success receipt even if bounded parsing fails, with local rejection reason issued by the registered parser/preparer and revalidated under exact original input/schema. If remote success itself is unverifiable, remain UNKNOWN/manual-required; local parse failure alone cannot establish remote terminal fact. Unknown HTTP errors/not-found/timeouts never manufacture zero-effect proof. No invalid-output path enqueues resend. Old observe API compatibility remains unchanged.

Retention predicates: authoritative SUCCEEDED plus locally valid result; exact CURRENT execution and live op token; genuine current ACTIVE owner EDIT; same cancel epoch; consistent nondeleted live root; lifecycle/governance ACTIVE; ever_published false; supported complete original input/producer/graph; original businessVersion OLDER than current. Only original equality is relaxed. Future/corrupt versions fail. Historical input need not remain current confirmed input; it is verified as original, never rebased.

Retain complete immutable candidate/body/set/elements/locators/evidence/directories/joins/suggestions with original task/input/base/fingerprint and STALE. No current pointer/title/meeting or subject revision/debt/confirmation/saved target/review mutation. New minutes/element history ordinal allocation under shared root is not meeting/subject business-version advancement; bounds/overflow reject atomically. Verify protected root fields unchanged after narrow writer. No general SQL callback surface. STALE is owner-private/non-adoptable and not PENDING readiness blocker.

Current valid result: actual zero edit guard + no effective body + original current input/business/cancel fences + fresh owner/lifecycle can first-save; positive/missing guard or existing body yields PENDING. Task 10 supplies actual current writer and HUMAN title/provenance rules. Intent alone does not make result STALE. First-save's intentional version mutation follows fresh entry check; postoutput checks bind original task/input and explicitly allowed mutation, not an accidental q.current comparison against the writer's own version advance.

## 7. Receipt-only, token loss and same-fact retry

observePreparedFact restricts effects only: same verifier/preparation/token gates, no writeCurrent or retainStale, even if local heartbeat authority later recovers. With live token it records terminal fact RECORDED_FENCED. With invalid token and ACTIVE identity it remains STALE_TOKEN. Genuine effects-denied preparation retains existing exact-current-terminal exception; UNKNOWN/nonterminal never gets it. Caller choosing receipt-only does not mint effectsAllowed=false as authority.

Local heartbeat loss/exception is sticky for that context but does not prove DB takeover. Eventual result goes through receipt-only. If token still valid, record fact without effects; if stale under active owner, preserve SAME fact for a fresh reconciler or lookup exact stable result after fresh claim. Current reconciler independently reauthorizes; old worker does not regain business permission. Existing old-owner/epoch rules remain exact.

Each in-flight context holds one bounded immutable pending observation; scheduled bounded retries submit that same fact, never another external request. DomainPreparationUnavailableException, four-attempt ObservationRetryRequiredException, rollback or uncertain terminal ACK is no acknowledgement. Keep original receipt/result/row plan, inspect durable state, resubmit. Fresh process recovery relies on committed marker plus provider lookup by original stable key returning exact immutable result; memory retention alone is not restart evidence. Controlled provider persists results/receipts across application restarts. Production without needed recovery contract fails closed.

Prepared-only terminal replay adds no enum: same locks verify already terminal exact execution/fact/result/proof and actual candidate relation; return original APPLIED or RECORDED_FENCED without rerunning writer, changing disposition, terminal task or capacity authority. Actual task local state/error/output identifies successful-versus-invalid/fenced mapping. Private result exposure still reauthorizes. Mere equal result ID is insufficient unless provider immutable result ID binds exact normalized payload. Full proof values match stored proof. If equivalence cannot be proven, return conflict/stale and retain fact; propose separate reviewed durable digest if required, never fabricate proof.

Old-generation success contradicting previous verified zero-effect FAILED takes existing MANUAL_REQUIRED quarantine before replay, no domain output/new execution. Do not settle a newer reservation. Only explicit existing retryAfterZeroEffect appends next PREPARED execution under complete proof/current authorization/same fixed input+request/producer replay checks. Preparation/reconciliation/fact retry never creates a remote effect.

## 8. Verified independent permit settlement and recovery

Selected decision: preserve existing ModelPermitPool.settled(Permit) independent transaction. It discovers actual execution operation/no by permit.executionId, locks operation/execution, capacity then actual reservation, compares execution/model/config, returns true if already settled, and permits release only for SUCCEEDED or FAILED with stored complete verified no-effect proof (or existing separately qualified passed safe deadline). Existing code provides this proof; a local task FAILED due to invalid output still settles because remote execution is verified SUCCEEDED.

After acknowledged terminal observation, load the actual reservation bound to exact execution and call settled outside ambient transaction. Do not infer settlement from ObservationResult alone, caller Permit, task status, timeout, UNKNOWN, expired lease or close. On any exception/unknown observation ACK first inspect actual durable execution; settled itself rechecks. No reservation is normal for nonmodel/not-admitted operation; a successful registered model execution without authentic admission is integrity failure and requires investigation, not retroactive permit insertion.

Add default fail-closed ModelPermitPool.recoverSettlements(int scanLimit) implemented by JdbcModelPermitPool, called by existing ReconciliationLoop's bounded maintenance cadence, not another lease/provider loop. It performs bounded keyset discovery of unsettled reservations whose own execution is terminal (SUCCEEDED or FAILED with all proof fields), constructs an internal Permit from actual reservation row, then calls existing independent settled for each candidate. Discovery grants nothing; settled fresh-locks/revalidates. Explicit limit and cross-call cursor make progress past failed/locked candidates; process restart safely begins a new scan. Do not hold discovery locks across settlement transactions. Future implementation should use bounded lock waits or SKIP LOCKED discovery and finite per-pass budget; never allow one unavailable reservation to block all recovery indefinitely. Any cursor reset or exhaustion behavior must be tested, not fake next-check mutation.

No schema/work item is necessary: durable external terminal rows and unsettled reservation already encode debt. Crash after observation commit before settlement conservatively holds capacity; later scan releases exact verified execution. Crash after settlement before ACK is idempotent. UNKNOWN/MANUAL_REQUIRED without qualified execution terminal proof remain held. Preserve existing proven-deadline verifier/microsecond cutoff policy, capacity config ordering and duplicate/copy/reconcile-token admission denial. No newer execution permit release by old proof/contradiction. Root should record this explicit technical ruling rather than claiming all settlement is atomic.

## 9. Worker single completion owner and handoff synchronization

Add WorkerLoop.ExternalHandler plus explicit mixed factory/configuration rejecting duplicate task registration across ordinary/prepared/external maps. Existing constructors/factory remain compatible. ExternalHandler.handle(ExternalTaskExecution) returns void and never ordinary-completes. Context owns begin, token-bound model admission/one-shot send, observe and receipt retry. Handler receives no TaskQueue completion reference. Context coordinates, but actual JDBC policy still authorizes every durable effect.

Per-execution state is guarded by one monitor:

```text
ORDINARY -> MARKING -> OPERATION -> SETTLING -> FINISHED
                    -> MARKER_UNKNOWN
ORDINARY -> LOST/CLOSED
OPERATION -> RECEIPT_ONLY (sticky) -> SETTLING or deferred reconciliation
```

Ordinary renewal holds monitor through bounded JDBC renew and changes loss only in ORDINARY. begin holds same monitor, checks close/loss, changes MARKING, cancels ordinary future, performs independent bounded marker transaction, registers exactly one operation tracker, then changes OPERATION before releasing or transport admission. A tick already inside finishes before marker; queued tick sees non-ORDINARY and neither renews nor flags ordinary loss. Monitor never covers provider/network wait, parse/archive/hash or unbounded blocking. Durable lease/token check still rejects expiry while waiting for monitor/DB.

Empty begin result sends nothing and exits without a second ordinary completion. Marker exception/commit ACK uncertainty becomes MARKER_UNKNOWN and forbids ordinary complete/quarantine. Operation tracker registration failure after commit sends nothing and leaves marker recoverable. Ordinary renewal false after RECONCILING is expected and must not suppress received authoritative fact.

One operation tracker per context. Extend ExternalOperationHeartbeat with non-owning bounded-scheduler construction for WorkerLoop reuse; reconciler may retain its existing owned scheduler. No second handler/provider lease loop. Tracker loss/close is sticky, including scheduler rejection and runtime shutdown. Add reconciliation factory whose handler receives ExternalExecutionSession with tracker state; preserve old token-only constructor compatibility. New handler dispatch routes pending observations before making another lookup of the same execution; lookup is allowed, resend is never allowed.

Operation tick and settlement selection hold same context monitor through short DB operation. If renewal fails first, settlement goes receipt-only. If verified settlement starts first, fresh DB token/domain gates decide; a later false renewal because operation is terminal is not retroactive loss. A bare read of tracked.lost() outside transaction followed by later writer is insufficient. The tracker must use this shared monitor, not an unrelated internal loss lock.

Close first atomically disables new claims/send admissions, then signals contexts/cancels trackers/workers and joins boundedly, preserving interrupt. One-shot send wrapper checks open/operation/admission under context monitor and starts bounded transport admission; network waiting happens outside. A send admitted before close is in flight, and close cannot retract already admitted bytes. No admission after close. A settlement already linearized before close can complete under its DB authority; future settlement selection after close is receipt-only. Late response is retained for receipt/reconciliation even if scheduler/worker interrupted. No ordinary quarantine after MARKING, no automatic resend, no permit release in finally. Releasing local worker semaphore is not global model capacity settlement.

## 10. Candidate/final and Task 9-11 contracts

Task 9 supplies full actual graph/preparer/normalized authenticated provider. Task 10 supplies guarded current writer/adoption. No permissive production placeholder registration while absent. Task 11 supplies publication-specific prepared request/result policy with actual final/publication/attempt references and no retention. Fenced publication success records remote truth without forging local published pointers; read projection exposes controlled reconciliation/manual status, never an UNKNOWN resend action.

Candidate discovery is through actual candidate.task_id -> immutable task/input relation even when task STALE/null output; UI never equates SUCCEEDED with adoption or absence of output with absence of candidate. Reads are fresh owner authorized, bounded, private/no-store; member/admin/task-ID possession does not confer access. STALE allows inspect/dispose/regenerate but never old-fence adoption. Explicit adoption uses current owner CAS/current input and original provenance, not original editor-zero/equality. Positive intent does not deny deliberate current adoption.

PENDING later made obsolete by a saved revision must be explicitly STALE-dispositioned under shared root or excluded using an equivalent deterministic stale filter shared by readiness/adoption; Task 10 must freeze the choice. No hidden full graph mutation; only allowed disposition/decided_at changes. Final and candidate lock same root: current PENDING first blocks final; final first must advance businessVersion or implement an explicit final-state gate preventing unchanged-fence PENDING from silently invalidating frozen readiness. Task 11 must freeze exact version/gate semantics before activation, not leave this as inferred behavior. Late STALE may then be retained without changing final graph/reviews/pointers/debt. Published/governed/deleting always denies retention.

## 11. Required future selectors and deterministic barriers

Proposed tests below are not passing evidence. Use new owned MySQL fixtures, actual B03 and real generation policy for domain acceptance; a permissive TEST policy is only a queue seam test. Barriers must intercept actual transaction/transport stages with finite JDBC/HTTP deadlines and bounded waits, not hopeful sleeps. Controlled output is fabricated model data, never company quality evidence.

| Selector/family | Barrier or adversary | Required assertion |
|---|---|---|
| ExternalPreparedObservationIT.createsBeforeValidation | Actual insertion before validator | New full output exists at validator; graph/task/operation/execution/outbox commit together |
| ExternalPreparedObservationIT.rollbackAtEachStage | Graph insert, postvalidation, task finish, audit, flush, commit rollback | No partial output/state/events; same-fact retry writes once |
| ExternalPreparedObservationIT.foreignCapabilities | Wrong issuer/source with both bound, old holder, wrong purpose/full specs, fake context/staging type | No writer/send/effects; independent source check |
| ExternalPreparedObservationIT.remoteSuccessInvalidLocalOutput | Actual remote success plus bad JSON/schema/source/Unicode/budget | Remote SUCCEEDED, task FAILED/INVALID_MODEL_OUTPUT/null, no candidate, terminal no retry; exact permit subsequently settles |
| GenerationRetentionIT.onlyBusinessStale | Response blocked while business version advances | Full STALE graph original provenance; task STALE/BUSINESS_FENCED/null; all protected root fields unchanged |
| GenerationRetentionIT.ordinaryFencesUnchanged | Stale spec through enqueue/claim/renew/complete/begin/legacy observe | Strict existing rejection/no callback/no new send |
| GenerationRetentionIT.intentAndExistingBody | Intent before/during generation, zero/positive/missing guard, existing body | Only zero/no-body/current can autosave; current suppression PENDING; no draft overwrite |
| GenerationRetentionIT.otherFencesDeny | Disabled/revoked/foreign owner, member/admin, cancel, delete/governance/publication, bad input/execution | No candidate/pointer effects; existing eligible authoritative fact branch still works |
| GenerationRetentionIT.finalRaceBothOrders | Each operation paused before shared root; publish after final | PENDING blocks final, STALE does not mutate frozen final, published denies retention |
| ExternalPreparedObservationIT.sameFactRetryLostAck | Prepare unavailable, four rediscovery changes, rollback, terminal commit ACK lost | Same original receipt, one candidate/state/event; zero extra sends |
| ExternalPreparedObservationIT.oldTokenProofBoundaries | ACTIVE stale epoch, disabled fact-only, wrong execution/subject, old UNKNOWN, proof contradiction | Inherited STALE_TOKEN/RECORDED_FENCED/QUARANTINED; no output/new execution |
| ExternalWorkerLoopTest.tickHandoffRace | Tick inside monitor before marker; queued tick after commit | Single owner; no posthandoff ordinary renew/quarantine; fact not dropped |
| ExternalWorkerLoopTest.operationLossSettlementRace | Loss before response; loss versus final tx; transient renew failure | Sticky receipt-only when loss wins; stale active token awaits new reconciler; terminal false renewal harmless |
| ExternalWorkerLoopTest.closeAndTrackerReject | Close before/during/after marker, before transport/inflight/after receipt, scheduler rejection | Bounded close, no later send admission, durable marked state/permits preserved |
| ExternalPreparedMarkerIT.realAttemptAtomicity | Domain marker writer throw; commit ACK loss | Real attempt execution IDs, all-or-nothing marker/domain state, unknown ACK sends zero |
| ExternalPermitRecoveryIT.afterTerminalCrash | Kill after terminal commit before settled; after settle before ACK; local invalid output | Conservative held capacity then verified exact settlement; no resend, repeat settlement idempotent |
| ExternalPermitRecoveryIT.boundsAndOldGeneration | More than one recovery page; blocked candidate; old proof versus new inflight; UNKNOWN | Bounded progress/cursor, exact execution-only release, UNKNOWN/new execution capacity retained |
| ExternalRecoveryProcessIT | Kill before marker; commit before ACK; handoff before permit; permit before send; remote durable effect before response; response before commit; commit before ACK | Restart query original key/result; one remote effect/candidate; durable fixture store not reset; capacity correct |
| PreparedReconciliationIT.sameFactAfterPreparationFailure | Terminal lookup then preparation unavailable; recovery/takeover | Same verified execution fact reoffered with fresh authority, no initial send |

Run inherited ExternalExecutionIT, ModelPermitIT, B06QueueSeamsIT, ordinary queue/transaction/outbox tests, WorkerLoopTest and PreparedWorkerLoopTest. Keep exact ACTIVE stale-token assertion. Task 8 fairness >64 held/unregistered candidates remains separate activation gate; finite-window Task 7 passing does not close it. Tasks 9-11 additionally require actual saved graph/archive/API/provider and fresh-JVM restart tests.

## 12. Review decisions and activation limits

Independent review must approve registered opaque APIs, split remote/local verification, exact generation retention capability, strict token/fact semantics, terminal replay equivalence, Worker monitor/close linearization, bounded durable permit recovery, and final/candidate version gate. Root must explicitly adopt the independent-permit-settlement refinement; this document does not call it atomic.

No new DDL works only if controlled immutable result identity/proof plus actual candidate/task/execution rows prove terminal replay equivalence. If not, propose a precise durable observation digest/identity design for independent review/root migration allocation; do not insert arbitrary raw remote JSON/fake output or invent provider proof. Company endpoint/wire/credentials/idempotency/lookup/cutoff contracts remain absent and production fails closed.

No runtime implementation completion, tests passed, company qualification, fairness closure or Phase B completion is claimed. Preserve B01 IMM PARTIAL, B04 Q401 UNKNOWN, all historical artifacts/hashes, and company/HA/native-I/O/capacity limitations.
