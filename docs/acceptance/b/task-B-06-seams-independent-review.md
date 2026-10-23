# Independent B06.1 shared identity preparation review

Date: 2026-10-23. Reviewer: `b06_seams_review`, independent of the author. This is the bounded SPEC and quality review requested by the coordinator. It is not complete B06 or stage B acceptance.

## Exact reviewed range and verdict

- Author tree: `/home/tanren/ama/.worktrees/phase-b-transcript-runtime`.
- BASE: `6b48b2f855bbf890c3ccad68325e7fd74d434489`.
- HEAD: `4dcdc6b5102c7d99b043385349b4a87c98b52e80`.
- All three commits are included: original proposal/real RED `26bf77d`, tested implementation `553196cacbf27f2fd4dfc3b65d70c535f10f9958`, and final report/evidence seal `4dcdc6b`. The review did not narrow to HEAD~1.
- Full package: `/home/tanren/ama/.superpowers/sdd/2026-10-23-resume/task-B-06-seams-review-package.diff`, 10,692,954 bytes, SHA256 `b283e5639f222a66a850b8b3e81c20f057b6ccf2394de459bb281391c5c8b0fd`; independently checked.
- Full changed-path set: 1,104 paths, comprising 1,094 additions and 10 modifications. All final working bytes equal the exact committed HEAD and the author tree is clean.

**SPEC verdict: PASS. Quality verdict: PASS. Actionable findings: 0.** P0: 0; P1: 0; P2: 0; P3: 0. Deferred findings: none. Parked findings: none.

The range is suitable for exact adoption into the coordinator branch within the acknowledged B06.1 scope. This verdict grants no main merge, push, production registration or full runtime acceptance.

## Requirements and complete source coverage

Read the activated review brief, original prepared B06 brief, coordinator technical ACK including the factual-expiry and unknown-domain addenda, full regression map, current coordinator resume/handoff state, complete original proposal/proof, final author report and consumer contract. The author tree's old handoff entry is inherited BASE history; the coordinator's current resume record is the current coordination authority. The later Supplier/WorkerLoop/archive/fingerprint/import proposals remain future work.

Read the complete final contents and BASE..HEAD changes of all 12 changed/new Java files:

- Production: `ama/access/MeetingAccess.java`; `ama/meeting/JdbcMeetingAccess.java`; `ama/task/TaskDomainPolicy.java`, `TaskQueue.java`, `DomainPreparationUnavailableException.java`, `JdbcTaskPreparations.java`, `JdbcTaskQueue.java`, and `JdbcExternalOperations.java` in their respective core/adapters modules.
- Tests: `B06QueueSeamsIT.java`, `TaskFixture.java`, `ExternalExecutionIT.java`, and `ReviewConcurrencyIT.java` in the integration-tests module. All 18 new seam methods and their real JDBC instrumentation were read, rather than inferred from method names.

Read the 11 new `tools/b06_*.py` fixture/capture/selection/sealing utilities, final `.gitattributes` append, all four new tracked B06 documents, and selected raw/XML/provenance evidence. Relevant unchanged contracts/callers inspected include TaskContracts/ExternalContracts, TransactionRunner, TaskTransactionContext, JdbcSubjectFences, JdbcModelPermitPool, MysqlTestDatabase, the selected B03 service concurrency paths, selected TaskLease/model/heartbeat tests, WorkerLoop and ExternalOperationHeartbeat. A repository-wide Java callsite search confirmed that all registered TaskDomainPolicy implementations are the explicit test registrations covered by the correction; no production meeting policy was activated.

## Protocol and behavioral assessment

1. `JdbcTaskPreparations.Held` is private, immutable for issuer/source/actual holder/full TaskSpec set/purpose/effects permission, and branded by the exact factory instance. Public marker implementations and a second same-source factory cannot satisfy the issuing policy. Queue consumption separately checks the exact queue DataSource object and current Spring holder before subject/domain locks; this also rejects a foreign-source factory while both real source transactions are bound. The policy then checks its issuer, complete set and purpose. This division is sound: issue() remains a trusted registered-policy operation, not an identity authorization endpoint.
2. `prepareDomain` rejects unsupported subject/policy combinations before acquisition, de-duplicates and canonically orders the complete discovered specs, and requires the explicit preparation protocol. Unknown/unprepared policies have no permissive fallback. The first local DomainScope acquisition locks subject and calls one complete `lockRoots` batch before tasks/operations. Repeated retry checks validate and reuse that scope, rather than reacquiring identities or roots after task locks. Default identity-free test registration remains explicit; production domain owners must implement complete identity/root discovery and canonical batching as documented.
3. Queue claim keeps an independent short transaction per candidate and carries SKIP LOCKED through identity/subject/root/task. Partial identity acquisition ends with that candidate transaction; missing/inactive/held identities skip before subject. The exact current immutable task spec is compared to discovery before mutation. Ordinary renew/complete still use fresh preparation and original lease/attempt/owner/epoch/current-fence checks.
4. B03 ordinary lock/require/package paths retain their behavior. New identity and meeting capabilities enforce issuing adapter and actual transaction; adoption checks exact actor, meeting and complete already-held identity coverage and reuses ordinary authorization. Identity reentry after roots still fails. The correction adds no transaction cache, unmanaged ThreadLocal or extra synchronization. The pre-existing weak holder guard and B05 sole final-flush synchronization are preserved.
5. External observation prepares the complete current/old spec union and prelocks domain rows before both task locks, then operation/executions. Immutable-spec and execution-generation changes lead to complete rollback/rediscovery, with the existing four-attempt limit and explicit same-fact resubmission exception. Incompatible policy families raise the actionable non-acknowledgment exception before locks; they do not acknowledge the fact as STALE_TOKEN. Global reconciliation skips only unregistered domains through an explicit lookup and does not catch unrelated SQL/protocol exceptions.
6. The terminal facts-only exception is limited to EXTERNAL_FACT_OBSERVATION with authentic effectsAllowed=false, the exact current operation/subject/execution number and UUID, MARKED/UNKNOWN operation and execution states, and SUCCEEDED or FAILED_ZERO_EFFECT. The unchanged authoritative verifier runs before updates. Only this receipt can ignore lease owner/epoch/expiry. Business effects still require effectsAllowed plus the unchanged ordinary valid() predicate and current subject fence. Receipt writes existing terminal fact states, STALE task and RECORDED_FENCED, without renewal, owner/epoch changes, output validation/application or callback. UNKNOWN/MANUAL_REQUIRED do not gain the expired-token exception; wrong/current-generation mismatch, unverified facts and old zero-effect contradictions retain their explicit rejection/quarantine paths. Original initial-call authority behavior and model admission checks remain unchanged.
7. Retry discovers old/new specs before preparation and uses a current locking producer lookup after the preliminary RR snapshot. Existing immutable replay/proof checks, execution append, epoch growth, task order and operation/execution order remain intact. Existing three-argument completeWithEffects still validates stored output before effects. No Supplier overload, handler/provider or WorkerLoop behavior change is hidden in this range.

The implementation is bounded to the required shared seam; it does not introduce a generic multi-subject framework or new schema. The consumer document states the obligations and unsupported compositions clearly.

## Independently checked execution evidence

No test command was rerun for this review. Neither reviewer accessed a database/container/private environment, started a service, altered fixtures, repaired source or rewrote evidence. Existing fresh author evidence was inspected and mechanically checked; this avoids repeating the heavy or incompatible historical cohorts.

The original raw MySQL log and XML show 3 tests with 2 assertion failures, 0 errors/skips: actual identity/subject/root/task indexes `[1,0,3,5]`, and authentic ambient enqueue failing on repeated identity acquisition with task/outbox both zero. The separate original output-order control is 1 pass, not a new production fault. The disable test gates real identity SQL using bounded latches, preserves stale Actor.status=ACTIVE versus current DB DISABLED, and leaves effects=0/task RUNNING/output null. Its timing claims are appropriately limited.

Later retained REDs are genuine assertion failures: expanded skip 12/1/0/0; expired disabled fact 1/1/0/0 with actual STALE_TOKEN versus RECORDED_FENCED; unregistered scan 1/1/0/0; and queue cross-source 1/1/0/0 when the expected exception was absent. They are distinct from the disclosed ephemeral-port setup/read-only inspection mistakes. Raw final locking SQL shows identity before subject/meeting/input/task, and ambient final task/outbox counts are one each.

Final XML and testcase nodes confirm:

| Final source run | Suites / methods / actual invocations | Failures / errors / skips |
|---|---|---|
| final-seams-18 | 1 IT suite, 18 methods, 18 invocations | 0 / 0 / 0 |
| inherited-final | 7 IT suites, 26 distinct methods, 28 invocations | 0 / 0 / 0 |
| units-final | 26 Java unit suites, 179 tests, zero IT | 0 / 0 / 0 |

The final inherited XML method set exactly equals the 25 approved mapped methods plus the single copied/reconciliation-token ModelPermit method. The held-row method has three parameters, explaining the two extra invocations. Thus final IT coverage is 44 distinct methods and 46 invocations. Preliminary rounds and incidental repeated 179-unit executions overlap; they are not additional unique coverage. The direct inherited command intentionally runs zero units, and this is stated honestly. Exact final commands exit 0 and raw logs show reactor BUILD SUCCESS.

Every final snapshot has the same exact 12 Java file hashes, independently equal to tested commit `553196c`, final HEAD and working bytes across final-seams-18, inherited-final and units-final. The final seal/report commit does not replace source-test association with an earlier pre-guard green run. The selected B03 BeforeEach truly called latest SchemaMigrator: raw logs validate all 12 unchanged scripts, report V013 current and zero migrations for each of the three methods. No schema qualification, reset/refusal/recovery, whole worker/model cohort, native/S3, process-kill or company calls were executed by these selectors.

## Custody and scope limitations

Mechanical full-path, manifest/XML/source/preservation audit is recorded separately in `task-B-06-seams-evidence-audit.md`. Original 143 sealed payload hashes and its 144-path allowlist match; the final 1,103 payload hashes and 1,104-path delivery allowlist match the complete Git range exactly. All 1,104 final working payloads match HEAD. The 1,740 pre-existing artifact files, 7 approved plan files and 12 SQL files (1,759 protected files) remain byte-identical to BASE. Existing attributes are retained with only the two new raw-evidence-root rules appended.

Selected fixture provenance supports exactly the one new owned loopback MySQL8.4.6 container, initial zero tables, ordered successful 12-script application through V013, fixed loopback port58060, 768MiB memory/swap, 1 CPU, 256 pids and process-only nativeAIO OFF. Final selected history retains the same scripts/checksums/success rows; the fixture remains preserved. Public delivery excludes private .tools/env files and Config.Env dumps. Review did not read private secrets; the author's exact own-password scan is recorded by its sealing utilities, while independent inspection checks public paths/content rather than claiming access to those secrets.

B04-Q401 stays UNKNOWN and B01-IMM-01 permanent PARTIAL. D01 remains adopted design without a new formal migration; pure S1 remains its independently adopted component scope. `main` was independently observed unchanged at `0676a21311f2b2193f73a5e096f4a5605ee9b41f`.

Full B06 remains open: durable configuration archive/index/restart, actual batch/producer fingerprints, real INPUT_BATCH_FILE production policy, import HTTP/local/S3 workflow, immutable stored facts, worker/output atomicity, saved edits/marks/confirmation and company/production qualification. These are explicitly excluded future deliveries, not deferred findings against this B06.1 patch. No additional executable check is required for this scoped adoption.
