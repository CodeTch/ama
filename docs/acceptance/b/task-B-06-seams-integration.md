# B06.1 shared queue identity preparation consumer contract

Scope: shared lock preparation and explicit capability adoption only. No production meeting task/reference policy, import API, archive provider, fingerprint, Supplier completion or WorkerLoop changes are enabled. This addendum replaces only B05's former subject-first queue composition instructions. Original handbooks and evidence remain immutable.

## Complete preparation and transaction binding

Use the exact same DataSource object for TransactionRunner, access, fences, queue, outbox, audit and every registered policy. JdbcTaskQueue requires a preparation issued by JdbcTaskPreparations, whose private Held type binds its unique factory instance, exact DataSource object, actual Spring transaction holder, complete immutable TaskSpec set, Acquisition purpose, private domain proof and effects permission. Constructing another factory with the same DataSource cannot mint the registered policy's brand. The queue independently checks the token's DataSource/holder; policy.requirePrepared additionally checks its issuer/full set/purpose.

There is no ThreadLocal, global transaction capability cache, second transaction synchronization or implicit flush. PreparedDomain is only a per-call opaque handle. After transaction completion, under another DataSource/holder, with another issuer/spec set/purpose, or when fabricated, it fails. issue() is a trusted policy factory operation after actual prelocking/adoption, never an authorization endpoint; its boolean does not prove identity or business permission by itself.

TaskDomainPolicy's new prepare/requirePrepared/permitsDomainEffects defaults throw DomainPreparationUnavailableException. Every previously identity-free test registration explicitly implements the protocol with its own JDBC factory. Unknown/unprepared domains have no fallback. Legacy lockRoot(spec,skip) remains for explicit identity-free registrations; identity-aware registrations consume lockRoot(spec,skip,prepared) or batch lockRoots.

The queue discovers complete old/current/retry TaskSpecs before any identity/subject/root/task/operation locks. It rejects different subject refs or different registered policy object instances before acquisition; one common policy object can explicitly own several registered task types. Unsupported combinations throw the actionable non-acknowledgment exception; they never acknowledge an authoritative fact as STALE_TOKEN. No generic multi-subject coordination is supplied.

Within one supported scope:

1. prepare(fullSpecs,purpose,skip) locks the complete discovered identity union in unsigned UTF8 order. It only acquires identities, no roots. Any discovery race must fail coverage/current-owner checks under the later root, never acquire identities late.
2. The queue validates the authentic preparation, then locks the one subject.
3. It calls lockRoots(fullSortedSpecs,skip,prepared) once before tasks/operations. Domain owners prelock all existing roots/inputs/children for the complete set in canonical order. The default batch visits input-kind/input-UUID/type/producer ordered specs; shared roots/children with different ordering needs must override the batch. No I/O or later root discovery is allowed.
4. A local DomainScope caches that real subject/root acquisition. Repeated retry/domain checks validate the same capability and return the already-held scope; they do not repeat identity/root hooks after task/operation locks.
5. Existing task, operation/execution, audit/events and final explicit outbox.flush() ordering follows. Partial skip acquisition ends at the owned candidate transaction boundary.

claim remains independent and uses skip=true for identity, subject, every root/input, and task. A blocked/missing/non-ACTIVE identity skips before subject; a partial identity union is retained only until that candidate's short transaction ends. renew/complete acquire fresh ordinary preparation and retain existing DB-time/spec/attempt/owner/epoch checks. Ordinary enqueue/renewal/completion never use a fact-only permission to write domain state.

## Authentic B03 capability adoption

Existing MeetingAccess.lock(actor,id), require(held,action), standalone authorization and B03 repository paths keep their behavior. Added ordinary-identity APIs:

- tryLockIdentities(actor,completeAffectedIds,skip): strict complete current ACTIVE identities before any root; no stale Actor.status authorization.
- lockPrepared(identities,meetingId,skip): authentic same-access-adapter/actual-holder identity capability into subject/meeting, honoring root skip; no new identities.
- requirePrepared(held,actor,meetingId,completeAffectedIds,action): verifies authentic issuer/holder, exact actor/meeting, complete already-held coverage and fresh ordinary authorization. It does not start another identity phase.

A file/API transaction first obtains FileRegistry.lock once and registry.access(held), then prelocks all its domain rows before session/file/task work. Its registered domain policy explicitly adopts that genuine B03 capability (including all input proof coverage) into its own PreparedDomain. Pass it to queue.enqueue(spec,alreadyHeld). Do not infer a gate from an Actor, call access.lock again after roots, or wrap independent claim in an outer transaction.

The B06 test policy demonstrates adoption against actual meeting/info rows. It is not production import permission or authoritative archive/input facts.

## External facts and business authority

All normal beginExternal/claimReconciliation/renew paths use TASK_MUTATION preparation before subject/root/task/operation. Valid external renewal is still independently tested. Claim-only global reconciliation scans skip unregistered task domains without granting authority; unrelated DB/protocol exceptions are not swallowed. observe remains independent; unsupported policy/spec families throw DomainPreparationUnavailableException so the caller retains the same fact and resolves configuration, without resending the request.

observe prepares the complete current+old task set with EXTERNAL_FACT_OBSERVATION and locks every required domain row before tasks, then operation/executions. Authoritative observation validation remains mandatory. When the locked current identity forbids domain effects, the policy issues a fact-only capability that cannot pass ordinary MeetingAccess authorization.

A narrow terminal-fact receipt path exists when all of the following hold:

- Acquisition is EXTERNAL_FACT_OBSERVATION and authentic preparation reports effectsAllowed=false.
- Token operation/subject and execution number/UUID bind the exact current execution. Operation and execution remain MARKED/UNKNOWN.
- Observation is terminal SUCCEEDED or FAILED_ZERO_EFFECT and its unchanged independent authoritative verifier accepts the fact/proof before updates.

For this factual receipt only, operation lease owner, reconciliation epoch and expiry may differ from the token. The adapter records the fact using existing SUCCEEDED/FAILED execution/operation state, terminal task STALE and RECORDED_FENCED. It never renews or changes the owner/epoch, restores local initial-call authority, authorizes model admission/resend, validates/applies business output, or runs domain callbacks. canApply still requires ordinary valid() plus effectsAllowed plus current business fence.

UNKNOWN/MANUAL_REQUIRED do not receive the terminal exception: an invalid token retains the existing STALE_TOKEN rejection and leaves the marker intact; a normally valid token keeps its prior UNKNOWN semantics. Wrong/non-current execution and forged/unverified facts do not enter factual receipt. Old verified-zero-effect versus late success still uses contradiction/quarantine/manual handling. ACTIVE identities keep all existing operation owner/epoch/expiry checks.

The four-attempt full rollback/rediscovery and same-fact resubmission rule from B05 fix1 is unchanged. DomainPreparationUnavailableException and ObservationRetryRequiredException are non-acknowledgments; neither permits another external request.

## Unchanged completion and qualification

The existing completeWithEffects(lease,result,Runnable) still validates output before running effects. This patch adds no Supplier overload or prepared WorkerLoop handler. The separate saved-output atomicity proposal remains later B06 work.

Consumer validation is the new B06QueueSeamsIT plus the exact inherited selection and final current Java units recorded in task-B-06-seams-report.md. The selected B03 BeforeEach calls latest SchemaMigrator; on this already-V013 fixture it validated all12 unchanged scripts and performed zero migrations. This is local real MySQL lock/transaction evidence, not company adapter, production runtime, HA/native I/O, archive durability or full B06 qualification.
