# Task7 source alignment preflight — 2026-10-23

Status: PREPARATION ONLY. This is an archaeology map for the adopted protocol, not a design reopening, implementation, independent source review, activation or passing runtime evidence.

## Boundary and source seal

Worktree: `/home/tanren/ama/.worktrees/phase-b-completion`. Every product/test/tool/schema statement and range below comes from **committed BASE `35b2325aee8e6c09aa1bdebc880c4f09bd37837b`**, retrieved with `git show BASE:path`. Concurrent Task5 working sources were not read. Ranges are one-based baseline lines. Appendix hashes identify exact baseline bytes, not future activation source.

Read first `task-07-brief-prepared.md`, then its plan extract, runtime-author-common, adopted authority-external-protocols, exact Task7 design/rulings/attempt clarification/review, controlled-runtime Normative R1 and Task5 import-worker adoption. Task8 prepared brief was read only to identify its pending ordinal/fairness boundary. Exact Task7 design SHA256 `35c1ef41e8764d973814b3cb54b3f25e4b205c19bcebdb420a3196182fdd664d`; rulings `5ee7b6e9ec9e11cc5d669daac67e8b9f649b8cb6874a4a181b17454d25d81b0c`; attempt clarification `2a5ea32975ef486375f8c23656ec2c5bf49f0f58b27f987793307329d4f7626a`. T7-G1..G8 remain future implementation/activation gates.

Only this report was written. No product/test/schema/index/Git mutation, runtime command, test, DB connection, resource operation, private-env/credential read or subagent. Read-only git/show/tree/search/document inspection supplies no runtime or clean-tree claim.

## Pending final-source repin

- **Task5 confined completion and ordinary-renew drain facade:** WorkerLoop, TaskQueue, JdbcTaskQueue, import policy and tests below are historical landmarks. The concurrent sole Task5 author owns their final facade. At activation repin its reviewed adopted commit and inspect actual one-use completion/attempt checks, lifecycle envelope and DRAINING_RENEWAL barrier. Ordinary renewal JDBC runs outside its monitor; bounded drain with no DB roots must complete before MARKING. Consume that same barrier; do not restore Task7 §9's superseded monitor-over-ordinary-JDBC shorthand. After MARKING, ordinary completion/quarantine is forbidden. This is a prerequisite, not a gap to redesign.
- **Task8 immutable ordinal/fairness:** baseline JdbcExternalOperations:66–75 is the old LIMIT64 selector. Task8 owns final reconcile_scan_seq, fixed highwater/raw bounded keyset traversal/cursor/exhaustion, >64-held/unregistered evidence and centrally allocated/reviewed/frozen/ACKed migration. Its prepared brief is DDL shape only. Repin actual adopted source/schema/evidence before external activation; do not allocate SQL, implement its selector here or count finite Task7 tests as fairness closure. Task7 terminal-reservation recovery cursor is distinct. Ordinary queue OFFSET at JdbcTaskQueue:86–98 is separate.
- Root supplies the final reviewed clean Task7 BASE, incorporating Task6 and notified commits; retain this report's original BASE. Task9 genuine generation/preparer/durable provider, Task10 save/adoption/PENDING disposition and Task11 final/publication freeze/joined projection are consumer contracts, not installed runtime evidence.

## External-operation and mapper sites

Paths shortened below are under `backend/`; full paths/hashes appear in the appendix.

| Baseline site | Existing behavior and adopted Task7 alignment |
|---|---|
| core/.../task/ExternalOperationPort:8–25 | Sole external state API: beginExternal/claimReconciliation/renew/old observe Runnable/explicit proof-only retry. Add fail-closed prepared request/observation/fact-only framework here; preserve old callers. Proposed design signatures are not existing APIs. |
| adapters/.../task/JdbcExternalOperations:14–29 | Private weak token-object identity initial-call authority 17–20. Operation/Execution records 21–22, concrete JDBC mappers op/ex 24–25, locking readers 26–27, exact current epoch/owner/expiry/state gate 28. Extend private contexts for actual request/execution/full-spec checks; reconstructed OperationToken values never mint initial-send authority. |
| JdbcExternalOperations:30–54 | Independent marker uses strict q.validLease, registered request verification, actual IDs, initial insert or legitimate RETRY_QUEUED/PREPARED adoption, task RECONCILING and event. Grant at53 occurs only after q.work commit ACK. Prepared marker writer/validation must join that transaction using actual IDs. Unknown ACK/exception sends nothing; no second begin/send/ordinary finish. |
| JdbcExternalOperations:55–80 | Domain→task→operation→execution claim/renew preparation. Task8 owns scanning. Operation renew is independent of ordinary task lease. Preserve genuine policy/token gates. |
| JdbcExternalOperations:81–116 | Four fresh observation transactions; rediscovery releases locks and RR snapshot. Complete old/current specs and EXTERNAL_FACT_OBSERVATION before domain, all sorted task IDs, operation and sorted execution numbers. Prepared observation preserves order; all output FK parents/producer tasks belong to prelocked plan, no late graph discovery. |
| JdbcExternalOperations:117–132 | Genuine effectsAllowed exact-current terminal fact exception. ACTIVE invalid owner/epoch/expiry remains STALE_TOKEN. Effects-denied terminal receipt does not restore authority; UNKNOWN has no exception. Old verified-zero-effect failure contradicted by success quarantines before replay. Caller receipt-only mode cannot manufacture effectsAllowed=false. |
| JdbcExternalOperations:133–145 | **Concrete remote/local mapper seam:** remote verifier133; UNKNOWN/manual134–136; canApply effects+valid token+strict current138; legacy validateOutput BEFORE effects139; remote state140; execution/proof142; operation/result identity143; task/error/output144. Prepared branch needs remote fact verification before writer, typed/full-graph validation after writer, then exact local TaskResult mapping. Keep legacy behavior. Remote success/invalid normalized output remains external SUCCEEDED but local FAILED/INVALID_MODEL_OUTPUT/null, no candidate/zero-proof/resend. Retention leaves task STALE/BUSINESS_FENCED/null despite stored immutable candidate. |
| JdbcExternalOperations:147–175 | Explicit zero-effect retry validates complete old/new specs, same fixed input/subject/business/cancel/fingerprint, immutable producer replay and complete proof, then appends PREPARED execution/epoch. Same-fact retry/reconciliation never uses this path. No old proof settles a newer reservation. |
| contracts/.../ExternalContracts:15–56 | Existing request/token records, ObservationResult33, external outcomes/proof35–42 and observation45–56. Keep existing enum vocabulary. Provider result-ID/payload equivalence is a Task9 eligibility gate, not implied by equal external_result_id. Execution mapper stores request/proof, not a normalized payload equivalence proof. |

No production generation candidate JDBC writer/mapper or JdbcMinutesTaskPolicy exists at this BASE. MinutesObservationPreparer and JdbcMinutesTaskPolicy are prospective contracts, not files to discover or permission to install placeholder production policy. T7-G3 must fail closed without genuine durable provider identity/payload equivalence; this report authorizes no speculative DDL.

## Queue, authority, transaction and retention anchors

| Baseline site | Alignment requirement |
|---|---|
| adapters/.../task/JdbcTaskQueue:20–38 | Exact source/transaction/outbox wiring; work26 flushes outermost once; independent27; registered policy28; full TaskSpec JDBC mapper read33–35; strict deleted/business/cancel current38. Preserve generic fence. |
| JdbcTaskQueue:39–68 | Private DomainScope retains actual policy/specs/purpose/preparation. Complete family/subject union rejects before roots45–54; independent queue source/holder52/58; subject then complete domain roots61–63. Prepared external authority must authenticate issuer/source/actual holder/private purpose/full set/execution, never DTO/interface/caller policy. |
| JdbcTaskPreparations:12–49; core/.../TaskDomainPolicy:8–43 | Private Held issuer/source/transaction/full-set/purpose12–14, consume21–38 and actual Spring holder45–49. Both generic Acquisition values stay unchanged. Independently bound cross-source queue checks remain required. Dedicated generation retention is private purpose/capability, not third generic relaxed acquisition or arbitrary callback. |
| JdbcTaskQueue:70–120 | Enqueue full input/spec/producer/current checks, claim attempt lease and strict validLease/renew. No retention relaxation for enqueue/claim/renew/ordinary completion/begin. Repin Task5's final attempt validation/DomainScope facade. |
| JdbcTaskQueue:121–143 | Legacy Runnable validates before effects122–125; Supplier writes before validation127–134. retry/endAttempt/finish136–143. External observe already calls finish to update durable_task, end task_attempt and append event. Atomic task+attempt means these queue rows, never publication_attempt. Task5 owns final exact-count/state-aware attempt behavior. |
| TaskTransactionContext:19–55; JdbcOutbox:25–48 | Same source/transaction buffer; no later locks/work after flush; foreign synchronization denied; explicit final flush and sequence lock/insert last. Output/domain/task/attempt/operation/execution/audit/outbox roll back together. No after-commit domain writer or observation capacity locks. |
| adapters/.../meeting/JdbcMeetingAccess:57–104,106–133 | Binary unsigned UTF8 complete identities first; issuer/holder; subject before meeting root; fence consistency; re-read held root/ACTIVE identity/lifecycle/governance; EDIT only owner. B03 alone does not forbid ever_published/in-progress publication. Retention additionally verifies actual immutable request/producer/input/execution, same cancel, live nondeleted root and no publication/governance/delete. Fact-only denied identity must still admit eligible terminal receipt without impossible EDIT acquisition. |
| V012__minutes_evidence.sql:39–46,71–106,121–135,283–291 | Existing set meeting/revision uniqueness; candidate task uniqueness and PENDING/ADOPTED/REJECTED/STALE; original input/base/fingerprint/body/set; original generation_input and candidate FKs. Full immutable STALE graph preserves provenance/no pointers/business revisions/confirmations/debt/reviews. Discover by candidate.task_id even task STALE/null output; prelock all parents. |
| V013_1__b_runtime_metadata.sql:19–34; V013__publications.sql:44–74,101–107 | Sticky editor guard with no backfill; publication/attempt actual op/execution relations. Auto-first-save needs actual0/no effective body/current fences. Task11 freezes publication lock representation. FACT-ONLY calls no domain writer, including publication_attempt; joined remote terminal/domain PENDING must be honest, never reconciliation/resend permission. |

Only older businessVersion equality may relax under genuine dedicated retention. Future/corrupt version, changed cancel, owner revocation, delete/governance, ever-published or publication in progress deny candidate effects. Full body/set/elements/locators/evidence/directories/joins/suggestions plus postinsert verification and unchanged protected roots are mandatory. Task10 owns complete bounded PENDING prelock/disposition for every version writer; Task11 owns readiness-before-final-revision. TEST framework policy success cannot close those consumer gates.

## Parser/result mapping consumer split

Existing pure pipeline: StrictMinutesJsonReader.read20–45 strict UTF8/schema/duplicates/budgets → schema-to-contract map119–171 → OutputValidation.validate11–34 exact fingerprint/source alias/locator/evidence membership → CreationSupport.classify6–21 initial PARTIAL/UNSUPPORTED and empty accepted ACTION tuple. Run pure parsing/hash/archive/transport work outside DB roots. These do not grant persistence authority. GenerationFingerprint100–123 verifies actual task/historical input with archive provenance; RowsetProjection13–47 and StoredFactsIndex53–77 support complete row/membership checks, not permissive JDBC output writes. Task9 binds genuine fixed graph/archive/provider/staged private result/rejection; Task7 supplies framework atomicity. Parser failure never establishes remote zero-effect proof.

## Permit settlement and maintenance

ModelPermitPool12–20 has token-bound acquire/verified independent settled; no recovery API at BASE. JdbcModelPermitPool19–41 preserves complete sorted model capacity locks before RR reservation counts/configuration. acquire42–59 checks token-object identity47, operation/execution before capacity/reservation, exact model/config/deadline and one admission/execution. Copied/reconciliation/duplicate token cannot authorize send.

settled60–73 discovers actual execution operation/no then independently locks operation/execution → capacity → actual reservation, compares stored execution/model/config, repeats idempotently and releases only remote SUCCEEDED or FAILED with complete proof (or existing qualified passed safe deadline). Local INVALID_MODEL_OUTPUT task failure can settle because remote execution is SUCCEEDED. Task status, ObservationResult, close, expiry, UNKNOWN or caller timeout are insufficient.

Add adopted bounded terminal-reservation discovery followed by fresh existing verified settlement AFTER observation commit. Construct internal Permit from actual reservation; discovery grants no authority. Freeze finite page/statement/lock/per-pass budgets; advance cursor past failed/locked debt; test exhaustion/wrap/restart; no discovery locks spanning settlement; exact old/new execution isolation and conservative unknown ACK handling. No capacity locks inside observation or global finally release.

ReconciliationLoop43–58 currently gates claims behind slots and returns on empty claim;60–74 tracks operation/releases only local semaphore;77–84 closes. Settlement maintenance must run on existing bounded cadence independent of slots/claims, even no due operations and saturated handlers. Only placing recovery inside a claimed handler fails T7-G5. Repin Task8's final scanning before integration.

## Worker ownership anchors

WorkerLoop16–18,44–71 provides ordinary/prepared handlers only. execute101–140 schedules ordinary renew, prepares/handles, ordinary-completes once, quarantines before completion on failure and releases local semaphore; renew142–146 ordinary renew; close156–165 stops executors. These are historical landmarks under concurrent Task5 ownership.

Task7 adds mixed external registration rejecting duplicate type, one external context and MARKING/OPERATION/SETTLING owner, one-shot transport admission and bounded pending SAME observation. After final Task5 drain before independent marker, ordinary completion/quarantine becomes impossible. Empty/uncertain marker, tracker rejection and capacity-empty/ambiguous admission send nothing. Honest UNKNOWN/manual state is allowed without authentic NOT_SENT proof. I/O outside DB/monitor; close stops admission while preserving safe later facts; sticky loss selects receipt-only, never effects/resend.

ExternalOperationHeartbeat8–17 currently owns scheduler/AtomicBoolean loss; ReconciliationLoop60–65 passes only token to legacy handler, without tracked loss. Add non-owning shared-scheduler support, actual shared context/tracker monitor arbitration and explicit tracked reconciliation session, preserving compatibility. Detached lost() then later observation is insufficient linearization. Ordinary drain follows Task5; operation tick versus observation selection uses actual shared tracker/context with bounded DB work.

## Required new test cohorts (prospective names, no passing evidence)

| Gate | Concrete test boundary |
|---|---|
| T7-G1 prepared authority/atomicity | Foreign issuer, both-bound cross-source, expired holder, wrong private purpose/full specs/execution/staged type; output exists at postvalidator; insert/postvalidation/task/attempt/audit/outbox/commit failures roll back all. Preserve legacy prevalidation. |
| T7-G2/G3 fact/replay | ACTIVE stale token, genuine disabled exact-current terminal, denied UNKNOWN, old proof contradiction, four rediscovery exhaustion, preparation unavailable/rollback/terminal ACK loss; SAME immutable fact/row plan reoffered; terminal replay invokes no writer/events and requires real immutable provider equivalence. |
| T7-G6/G7 retention/consumer | Only business stale succeeds; current zero/positive/missing intent/existing body; owner/cancel/lifecycle/publication denials; complete graph/provenance/protected-root assertions; final race both orders. Actual generation/Task10/11 acceptance remains consumer work. |
| T7-G4 Worker | Selected ordinary renewal inside JDBC and queued tick versus drain/marker; committed marker then queued tick; marker ACK loss; tracker register rejection; operation loss versus observation; close before/after admission/inflight/late fact; no second ordinary completion/no lost receipt/no new send. |
| T7-G5 permit | Terminal commit-before-settle, settle-before-ACK, invalid output, concurrent exact settlement, multiple pages/locked or failed head/restart/exhaustion, no due operation/full slots; UNKNOWN/new execution held; fresh independent verification/bounded waits. |
| T7-G3/G8 restart | Actual committed marker and durable provider original request key/result/payload across fresh JVM; no second remote effect/candidate. Memory fact retention is not restart proof. |

## Inherited cohorts and schema/reset exclusions

These source-identified selectors require later guarded execution on final reviewed source. None was run. No broad integration glob. Root checks retained exact resource identity/current endpoint/own tasks and resources. DataSource tests internally need the guarded private env; this preflight did not read it.

| Cohort | Baseline ranges and qualification |
|---|---|
| ExternalExecutionIT |10–44 TaskFixture: independent marker/ambient rejection, old task expiry, takeover, process kill, commit ACK loss, retry concurrency/replay/history. No reset/migration in TaskFixture. Process/file effects require later custody. |
| ModelPermitIT |7–24 concurrent admission/null deadline, no capacity, shrink/config order, exact terminal/cutoff/microseconds, copied/reconcile/duplicate denial. UUID rows/models; no reset. |
| B06QueueSeamsIT |128–199 and234–318 authority/order;320–448 fact/proof/retry/preparation;449–474 independent cross-source. **Preserve exact method429–447 factOnlyReceiptSeparatesExpiredOwnerEpochFromActiveAuthorityAndUnknown, especially ACTIVE STALE_TOKEN434.** Setup116–125 uses current migrator and UUID seed, no reset; root still owns any schema apply. |
| ReviewConcurrencyIT |18–77 model RR lock order;78–129 ambient/rediscovery;131–227 late success/zero-proof/retry generation barriers;233–270 JDBC/commit interception patterns. No reset. |
| TaskLeaseIT |12–29 duplicate/expiry/cancel/version/tombstone/full spec/lock skip/attempt cap/backoff/transaction-outbox rollback. TaskFixture14–64 UUID fixtures/no reset. Repin final Task5 attempt tests. |
| OutboxFenceIT; OutboxConsumerIT |Fence28–55 requires successful V010 presence, not exact total history;63–192 flush/synchronization/prefix/rollback. Consumer6–11 exact prefix/effect rollback/epoch. No reset. Shared outbox counts need controlled writer concurrency. |
| ReconciliationWorkerIT; WorkerProcessIT |Reconciliation5–14 independent tracker/fact after ordinary expiry. Process10–17 reclaim/heartbeat blocked handler. No reset through TaskFixture. Process/temp/resource custody later. DatabaseRecoveryIT excluded below. |
| PreparedCompletionIT |Behavior112–238 write-before-postvalidation, rollback/expiry/stale/owner/audit/flush/ambient/Worker. **BASE @BeforeAll75–80 migrates then asserts history.size()==12. Do not run unqualified on forward shared schema.** Repin Task5 approved final adaptation/successor and guard; preserve historical assertion/evidence. No fake history/reset/skip to claim PASS. |
| Worker/pure units |WorkerLoopTest16–115;PreparedWorkerLoopTest17–124 (repin Task5 additions). StrictMinutesJsonReaderTest27–80;OutputBudgetBoundaryTest8–42;GenerationFingerprintTest16–35;SourceAndSupportTest21–69;StoredSourceGraphTest62–117;HashProfilesTest34–88;FinalAndCoverageTest20–45;MinutesFactBudgetTest;TaskMetadataTest. Preserve parser/fixed graph/golden/metadata. Allunits once final source; pure units do not prove genuine generation runtime. |

**Do not select IdentityAccessIT for inherited identity regression:** setup29–43 calls destructive MysqlTestDatabase.reset and deletes shared identity/audit tables. Use B06 genuine B03 preparation/identity-bound receipt cohort; additional consumer authority cases need UUID-owned fixtures. MysqlTestDatabase.reset10–19 explicitly DROP DATABASEs.

Exclude from forward shared fixture: FoundationIT(reset34,target005/006), MeetingSchemaIT(reset30), IdentityAccessIT(reset30), IdentityMigrationIT(reset12,target007), MeetingSchemaLegacyAclIT(target007/008), IdentityMigrationRefusalIT(target006), B05SchemaIT(target010/exact history), TranscriptSchemaIT(target011.1), MinutesSchemaIT(target012), PublicationSchemaIT(target013). RuntimeMetadataSchemaIT targets013.1 and needs repin after Task8; it is not future-agnostic. Never weaken historical exact schema assertions/reset/repair/outOfOrder. DatabaseRecoveryIT6–22 performs Docker restart/inspect; not automatic Task7 regression or this no-resource preflight.

Canonical tools/checks/run.py52–75 expands class glob and optional #method, requires explicit IT DB URL, and counts fresh XML1–49. A comma-separated class list is not one accepted class glob. Use explicit class invocations or reviewed deliberate selection, no `it *`. At activation capture exact final commands/source seal/fresh XML counts; preserve failures. No counts claimed here.

## Activation handoff

Root pins reviewed Task5 facade and Task8 ordinal/fairness/schema/evidence, then supplies final clean Task7 BASE. Read actual touched source fully before implementation; this range map is not source review. Preserve generic strict fences and consumer gates; no placeholder production policy/company proof/new generic enum/unreviewed SQL. Verify T7-G1..G8 through real deterministic bounded barriers, remote/local mapping and conservative capacity recovery; inherited nonreset regressions after final schema/setup inspection, then allunits once. Root owns independent review/activation/central records. B01 IMM PARTIAL, B04 Q401 UNKNOWN, FUP-B05-CANDIDATE-01 and company/HA/native-I/O/capacity limits remain unchanged.

## Exact baseline retrieval appendix

All rows are `git show BASE:path` bytes. Git blob=SHA1 object identity; SHA256=whole file bytes. Paths below resolve shortened source locations above. Line counts use splitlines().

| Path | Lines | Git blob | SHA256 |
|---|---:|---|---|
| `backend/adapters/src/main/java/ama/meeting/JdbcMeetingAccess.java` | 135 | `80b3c184987e2b9783878c92b24ab648a00cb20a` | `75917561fcd0e91cdf097997189a3c28d7998bbaf28db62c16a8162f6bdb64ec` |
| `backend/adapters/src/main/java/ama/minutes/json/StrictMinutesJsonReader.java` | 175 | `a7b963ecff9f1099611e56f39192ea9e2329a17a` | `976954647aaa6b8fd5ad5168ac6f6d64532d1d34dfe199e8dd8635bfbaf08593` |
| `backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java` | 176 | `b858b8a9c6cff75f81024145ab01249a13888e96` | `63e84274395c74c8d5e5375fcfc898c5cc39f19447318a2093eebaff1496ad19` |
| `backend/adapters/src/main/java/ama/task/JdbcModelPermitPool.java` | 74 | `4a9011cf726ce75953ba953d4adc8361c0d4490f` | `229db724cc7eec612060eaed2b689258485dae861c3202d4cd665b902619281d` |
| `backend/adapters/src/main/java/ama/task/JdbcOutbox.java` | 60 | `2726f8f2305f2816291f428d5164772cd57f0d0c` | `a97cf78ab2585e33159027716f1c2e8078af276a97cfacfa3603ee59af272909` |
| `backend/adapters/src/main/java/ama/task/JdbcTaskPreparations.java` | 51 | `d117b442825d56ba356d2f4974758f427d0e12f3` | `43e93c567664bb916c03a52381ee4892aa153a58dff0cfa9537c11f4e68d6905` |
| `backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java` | 154 | `efd72ebc065d41fa982af5988cb3eae44d49f150` | `83c2ba70b1a80449e83e49b0e5bf6efbd4574c7597bc687d2c41721768794b14` |
| `backend/adapters/src/main/java/ama/task/TaskTransactionContext.java` | 57 | `cf22c115910832e339bc41044b93e411b8e5af62` | `111780076f9b7fd49fc8bc86df24bf25d8e499a4bcc0a2fae12ed811b4214b19` |
| `backend/adapters/src/main/resources/db/migration/V012__minutes_evidence.sql` | 335 | `2cb09667a39586fe53dcac11f4b062aea397c951` | `10742ca2075fa3430dedf692853e1c106bd4464eaed46d8c8c1aa07859878fb7` |
| `backend/adapters/src/main/resources/db/migration/V013_1__b_runtime_metadata.sql` | 34 | `8d3fa8f72a7519b1683d158d831deb4935c28fe1` | `9280baea017014f3322af901373a7f8d69dfaa396fdd49f3c5d872535c43a520` |
| `backend/adapters/src/main/resources/db/migration/V013__publications.sql` | 113 | `1d0e931bce61098eca4eadade89558bd6f6a8656` | `1de37066869617723a11f05d7423584143a64e0c9ddf8879c7b4fd107de08e3c` |
| `backend/adapters/src/test/java/ama/minutes/json/OutputBudgetBoundaryTest.java` | 42 | `a885a10b3c753150b28b013d780a733f38afa7a2` | `48842e2461c8a9cbf4925a93b85d8dc22181e721f59b9d39f3e7d5d544a40b92` |
| `backend/adapters/src/test/java/ama/minutes/json/StrictMinutesJsonReaderTest.java` | 94 | `0dc82d2b26861c901636f35c7d8e97899cfe16f8` | `fb5e79d53f56b5c55f692a8fedeb6feb0015168c27ef15e20ef773a536b206f6` |
| `backend/adapters/src/test/java/ama/task/TaskMetadataTest.java` | 21 | `3fa7a74c4fd5d27267f864978ee1805122053539` | `bd8d21bdaaed179e237adcbc46fe790482e473572883c446f7828c2da224eb31` |
| `backend/contracts/src/main/java/ama/contract/ExternalContracts.java` | 58 | `d03dc35a58e4cc009039e1bc82d44ab715233547` | `c8300a97bb7ef9fb02a67ac9068dfaac88177af84a6ff929daf8c68955c8d21d` |
| `backend/contracts/src/main/java/ama/contract/TaskContracts.java` | 64 | `23a0ddb414ac12fad8d576fe3cd5735d069b3349` | `6641098514d21f4cba9b2ab82b7a6eaea9894f162a563447c69859e9dbdb9880` |
| `backend/contracts/src/test/java/ama/contract/MinutesFactBudgetTest.java` | 35 | `c23ad6a78884bd6a931aee27c17efab8e5decd0c` | `3ad2c0a8585f348a0a244492a2816f207d9733776a61554b5c90efce67c03827` |
| `backend/core/src/main/java/ama/minutes/contract/CreationSupport.java` | 23 | `94affb5c43c5c9edfe57b13b39d45197d3828d60` | `003b2d0081938c011caaee11748a4b6de46d9388e47f5f9bd6d759b3c4148186` |
| `backend/core/src/main/java/ama/minutes/contract/GenerationFingerprint.java` | 139 | `6824d68b40e1bbcef88097d26a80d0b27c893f78` | `9897d025afebbc9d042b148362d6ca9474099ac0a666856d476cb4d0b83a20a3` |
| `backend/core/src/main/java/ama/minutes/contract/OutputValidation.java` | 57 | `2fb5f159a635078ece07d0a6506daff64e4718dc` | `98df9ea7b45ee5266327b1336adde5f01e82a701fae6f6e73c21b6f0f06e6c06` |
| `backend/core/src/main/java/ama/minutes/contract/RowsetProjection.java` | 47 | `fb81e8be644914c68bf4c53746e4fa22c4bbacad` | `c550bc224504152c6eee559a7a5ee99f2709ac4ea2ccb2cd8da1d16baf16f4ec` |
| `backend/core/src/main/java/ama/minutes/contract/StoredFactsIndex.java` | 79 | `f3725b521deb4234c544e528de9794c4a5e7bb2a` | `d3a1feaa336e7abef93de75ff708e3e0d0aa6de1fdaa60f03ac797db7a9a1f7d` |
| `backend/core/src/main/java/ama/task/ExternalOperationPort.java` | 25 | `1dee4cadd19bea389e8cbd1571f881c05e7159a0` | `6230e84a6294c5252d149db66d8ba1ef2eca50bb2e26eff5b4a25851eecdee08` |
| `backend/core/src/main/java/ama/task/ModelPermitPool.java` | 17 | `a5f944b3f678d4082b2411fb051bdeb830c29ea7` | `f6b9b10755583686d7e1984940e2bf8ecbdc5e7df29d45c4e9f0146f45c03f32` |
| `backend/core/src/main/java/ama/task/TaskDomainPolicy.java` | 43 | `b42cfb8914847818bb48656378064656260e6b1c` | `acea5e5690597cb03163ba82217ae3a390b7b279ed511d316b0890500f08e620` |
| `backend/core/src/main/java/ama/task/TaskQueue.java` | 27 | `c60b555910ba348ccd197c682f046ba853f294a7` | `756036a23b7c101b3a6bf90dd56617b87b7425da1e3b1b76ee72bed87351d4f4` |
| `backend/core/src/test/java/ama/minutes/contract/FinalAndCoverageTest.java` | 45 | `a3572b9f214382449e85115ebe60ce1f8a86ffef` | `a63d4dbbdf62209d132c59cffb2b42bc7c17cd65a84283d3fa1be9de719b48bf` |
| `backend/core/src/test/java/ama/minutes/contract/GenerationFingerprintTest.java` | 35 | `7109917e4ed3bc786678d85e54eb4b9173999810` | `542ec403f8b8dd835841ef37c2a0df3763af32d750f6118ba43029df423545cf` |
| `backend/core/src/test/java/ama/minutes/contract/HashProfilesTest.java` | 88 | `8ac9bc05a329a8a6fdd2cc3732e6bd5a0f26eb67` | `d3084a6dda0e1f910dc3adacc4b71b841a585145797d444ebf0341dfdcd56574` |
| `backend/core/src/test/java/ama/minutes/contract/SourceAndSupportTest.java` | 69 | `f032d64cb35580dc502f59c46e177f92de8af1aa` | `2e0cc389fb909d033bed176175b2097e30ead12bce3706b6e11430b6960f4bc0` |
| `backend/core/src/test/java/ama/minutes/contract/StoredSourceGraphTest.java` | 117 | `0f6d82d5607142d0907d82a53e4a4a0119fa8895` | `2da3c1eed8202db20afc2d736bd41f1d220556ff600f66b65729b14b363ce9c1` |
| `backend/integration-tests/src/test/java/ama/db/FoundationIT.java` | 378 | `b49ff1bced083f55979ec3cda325f759eaa1b014` | `a1b4cb4395973197e5f4eb694cf6e5f091aaef33a84b0890700e0d46514c1775` |
| `backend/integration-tests/src/test/java/ama/db/MeetingSchemaIT.java` | 218 | `a954f2b76b9bd53931d681fad4f017afc251b80c` | `945d7dbdfc4bfe8bdb4ec5add6503f99f9323fc2eb0732576e7d5f40c6b1a42d` |
| `backend/integration-tests/src/test/java/ama/db/MeetingSchemaLegacyAclIT.java` | 85 | `9c7896a43c12d350b2bd258ca20ff8d0a2a61c16` | `f2bc7b8755256dd911001d16709b076b07170320d43f52da01ac93b62eb7db3a` |
| `backend/integration-tests/src/test/java/ama/db/MinutesSchemaIT.java` | 481 | `ae82d6fddb484f59078a182925c326881707333f` | `414721c6d2d9e24ed5e5793676680051006fee581d1205bc68109445c1deda2b` |
| `backend/integration-tests/src/test/java/ama/db/PublicationSchemaIT.java` | 468 | `aa9817a3b46389c02509e90da98f67ddefa23695` | `496d0a3649f14668fc2bdf68422e39e71f04d39301a259d527a33d22407eca6b` |
| `backend/integration-tests/src/test/java/ama/db/TranscriptSchemaIT.java` | 546 | `693a5f866d61105d4a865baa23a5ae7d4e5b1bfe` | `5d5568fa3d740976ffa836829227cc65dc30f1adfc2e040b0cda3bbce70a8c99` |
| `backend/integration-tests/src/test/java/ama/identity/IdentityAccessIT.java` | 212 | `8d8eff8b4f7c9c80fa0c39cc91644d297dc42bf7` | `c11bb807711682d4cc5fb0d9ca61d45392acafbd48fff1d0dc5e3f9203610c45` |
| `backend/integration-tests/src/test/java/ama/identity/IdentityMigrationIT.java` | 41 | `c8dfc6e177d38a7723c1c8251b2df91255506cfd` | `4d7951261625135d3661f58203ca338410332436ac7371bd568466223ba5f8a8` |
| `backend/integration-tests/src/test/java/ama/identity/IdentityMigrationRefusalIT.java` | 42 | `c7f2329756f6a079101b7e69b98e680e16d24502` | `ec2f58e885729c48b025958d8f52b3ee231c52ce52b0f670d7bb341cd703af57` |
| `backend/integration-tests/src/test/java/ama/meeting/RuntimeMetadataSchemaIT.java` | 53 | `0434ac8d948668f61abd5731cbcfaadc370427cc` | `7c82585918a147f45d45f9c968c8b0cf563909ad51b9dde31dfeaf6acecf0786` |
| `backend/integration-tests/src/test/java/ama/support/MysqlTestDatabase.java` | 31 | `9bd21e7b164b7121554d45c75ea5e42943dc40fe` | `a734c9bc0c30e0e176d28fe1505c4d7d0a2a7dc188a09f31d4c38f79a96c878a` |
| `backend/integration-tests/src/test/java/ama/task/B05SchemaIT.java` | 33 | `cd146b177b55df561dbb7fb9cd55c1990ca6d256` | `2a5bfb7e4417f4108060553362382a47b6bca5dafb80518923d372c31b50ec04` |
| `backend/integration-tests/src/test/java/ama/task/B06QueueSeamsIT.java` | 500 | `2c9934e9882a8a80dcc9f9c8556e9c69a35b470b` | `f2fdf2f64ca33d112a8cb618748249b8454e631cc24a8090927f2963b5312a74` |
| `backend/integration-tests/src/test/java/ama/task/DatabaseRecoveryIT.java` | 23 | `8737286140bee618497cc74695610ae89ad0c7ce` | `186829397c21df401ea72e97d039a41698754db7edd61d787fd6689096e0cafd` |
| `backend/integration-tests/src/test/java/ama/task/ExternalExecutionIT.java` | 45 | `2b61d6a8f02520bdce11dda6c422a14c079339fc` | `1699ba7e6627ce9eb811aa8b816fa83464c0af26332b90279f8a9c4b5c37dd29` |
| `backend/integration-tests/src/test/java/ama/task/ModelPermitIT.java` | 25 | `59321dc8d0d148bbd7af4de21b0c72417cfa7158` | `415b930e8e76a02826eb8be5f29d415f95827d2a71f681f6d1f1fe54efbf0fc8` |
| `backend/integration-tests/src/test/java/ama/task/OutboxConsumerIT.java` | 12 | `1a4e5fdc993dfb43032eafe95ba13c251e512b17` | `b1e5f7cdc2780d55a23f5ede681913c806ce8f91796858ae089288e569cedaf6` |
| `backend/integration-tests/src/test/java/ama/task/OutboxFenceIT.java` | 193 | `c669c4ae45a642424042f0d984f1a606be7df0f3` | `6e198f342b5926300ed1249e1a2894e0a54c8aa6393e66367c06252a3456fff7` |
| `backend/integration-tests/src/test/java/ama/task/PreparedCompletionIT.java` | 239 | `32c75b585f96222973367510cf36f118ad211efe` | `d3296fd1bcbfbec7d97acfe77044a146535c0ea6384dc1531a42423d66f03c5b` |
| `backend/integration-tests/src/test/java/ama/task/ReconciliationWorkerIT.java` | 15 | `4a2ddd62c29d03e1b378e17054e878a4942ae784` | `5c8c6a15792155e86e8239974d545a208537f9210edf3989e59995760bb8bdd9` |
| `backend/integration-tests/src/test/java/ama/task/ReviewConcurrencyIT.java` | 271 | `3e8101105c3f990bc23ca900d17d9360fb311df6` | `28c95367173e005c45128b303b8fd9bfedc600a9d3be6dd79fd11da723e80be5` |
| `backend/integration-tests/src/test/java/ama/task/TaskFixture.java` | 65 | `f1ebc1a2fe86dff1ad9008099984f65f41540f54` | `032d7842bfaf1f0ff71fecc5a4674bed4dcd5057b555a9f9000e87d60f7dd22d` |
| `backend/integration-tests/src/test/java/ama/task/TaskLeaseIT.java` | 30 | `f1891882b413172aba36896972e8a92a47d9d639` | `14ea074807329c5546414bba9e2d0ba2acca41c67f1d06971e5919520c4e85bf` |
| `backend/integration-tests/src/test/java/ama/task/WorkerProcessIT.java` | 18 | `854694b7986c5c13387eeac4498d7638a772bdef` | `8bf2b112b48598d6ce5a8f0b499879b991295f67ac61fc30e99917056dcfcdaa` |
| `backend/worker/src/main/java/ama/worker/ExternalOperationHeartbeat.java` | 18 | `85199bb5d32fabf8b827491ae279391ba15f2774` | `2489a7c9080f3a9d5c4ff98d53b7919afe99d7012bae4fe87c115216e5487fad` |
| `backend/worker/src/main/java/ama/worker/ReconciliationLoop.java` | 85 | `b41113538b63ee6d8a3d9ba28f3e266b9f502d61` | `d2dc10a818c870e250fdfa265682fa897019668376e450ebf5b8abed50914149` |
| `backend/worker/src/main/java/ama/worker/WorkerLoop.java` | 171 | `54cb13d39a2252d11a4edfc3f64672fccb6b835f` | `4479f78fc37566a2bf6ebdb3118512e377bd0987808d4ba1e246f8aba05f4027` |
| `backend/worker/src/test/java/ama/worker/PreparedWorkerLoopTest.java` | 124 | `eb46785a8643d319e5294350fc33ad498d1943ce` | `7f2d15936ed4b8e2e181a62752b6e27dda5ffde94c540d018312fde9c3214c35` |
| `backend/worker/src/test/java/ama/worker/WorkerLoopTest.java` | 115 | `1b26131430d4723ac409736a5f1072fdf5031853` | `d0d83ab2144eceba8c9a1cdddbbc7cdf64a87e8a16c011e20d713a0fc7295155` |
| `tools/checks/run.py` | 176 | `16a750cee47604dcff7452b225a3e255352b63f4` | `a3ece9cd0c1249fcbe51982fe9b2f30d330b4418406e4d00b8dc39a6cb8b3217` |

## Read document custody

Adopted documents read from disk were checked byte-for-byte against the committed BASE before sealing. Working preparation briefs are not adopted source.

| Document | SHA256 | Same as BASE |
|---|---|---|
| `docs/superpowers/specs/2026-10-23-ama-phase-b-authority-external-protocols.md` | `8a5781937fd75f88b77154728933481f5009ac772ffcb3297d9eba55cf82130d` | yes |
| `docs/superpowers/specs/2026-10-23-ama-phase-b-import-worker.md` | `391be2a974eede3f80ae472a1a48c7b0169fc5d66577c5308eee3b484db98890` | yes |
| `docs/superpowers/specs/2026-10-23-ama-phase-b-controlled-runtime.md` | `8bec16ce2935cef50aee5771c4be3ea899db11fed28e4255b2feeb2f7a016b54` | yes |
| `artifacts/phase-b/b-remaining/controller-runtime-design/task-07-protocol-design.md` | `35c1ef41e8764d973814b3cb54b3f25e4b205c19bcebdb420a3196182fdd664d` | yes |
| `artifacts/phase-b/b-remaining/controller-runtime-design/task-07-root-review-rulings.md` | `5ee7b6e9ec9e11cc5d669daac67e8b9f649b8cb6874a4a181b17454d25d81b0c` | yes |
| `artifacts/phase-b/b-remaining/controller-runtime-design/task-07-attempt-receipt-clarification.md` | `2a5ea32975ef486375f8c23656ec2c5bf49f0f58b27f987793307329d4f7626a` | yes |
| `artifacts/phase-b/b-remaining/controller-runtime-design/task-07-protocol-design-review.md` | `95793e9417ae4b130ce5ca96d9d23743b535c8c05b6a43c7eab047d365b7b2fa` | yes |
| `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-07-brief-prepared.md` | `09c4463971d7fe0e618391dfee4d9fcebd0980560e04062829a973b4d159b126` | working preparation |
| `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-07-plan-extract.md` | `0fcf1c0d6ae3a33648e92ff43a6bbfe1873f79635d1bab104363f0e1c5e28cef` | working preparation |
| `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/runtime-author-common.md` | `cbb0bf1dc05ecf98b12786d7e49792f24c730ffe6a383412358c9aaaeeedcbac` | working preparation |
| `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-08-brief-prepared.md` | `ea64843ae75b2a80de2fad707438db4cc7af5264cbfeae942b30f92e6123cc82` | working preparation |
| `artifacts/phase-b/b-remaining/controller-import-worker-design/task-05-import-worker-design-r1.md` | `7abe66e4fb2ae304df29ad62d374455c86a928d41fa4ba03f2d78fd268a284f4` | yes; exact Task5 refinement read |
| `artifacts/phase-b/b-remaining/controller-import-worker-design/task-05-import-worker-design-r1-review.md` | `dfa0e697b58bf367141ad58a3295e578480b29ceaad49e035e0c9434f22f2d14` | yes; exact Task5 refinement read |
| `artifacts/phase-b/b-remaining/controller-import-worker-design/task-05-import-worker-design-r1-closure.md` | `b80abbcda376ded13cd5b4b9776e259722387190dad6df25d206f408623cd2e7` | yes; exact Task5 refinement read |
