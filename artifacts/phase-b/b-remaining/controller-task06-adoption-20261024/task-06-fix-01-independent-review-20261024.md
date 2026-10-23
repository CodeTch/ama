# Task 6 fix01 independent scoped re-review

Date: 2026-10-24. Range: `aa490afc2690e9517a691cc8d1ae9487a025806d` → `d54b2e8841c664e691d03192507e7a192be38430`.

**Scoped SPEC: PASS. Scoped code quality: Approved.** T6SR-01 and T6SR-02 are **ADDRESSED**. Mandatory acceptance-evidence items T6CV-01 through T6CV-04 are **ADDRESSED** for the current server/runtime scope. **New findings: 0 Critical, 0 Important, 0 Minor. Remaining in-scope CannotVerify: 0.** Original **T6SR-03 remains one open Minor**, explicitly carried to Task12; it is not fixed or silently dropped by this verdict. Task8 model synchronization remains an explicit downstream obligation.

This is the requested same-author fix re-review, not a fresh review of all Task6, historical Tasks1–5, or the entire branch. It does not confer whole-Phase-B adoption. The original review remains immutable; the dispositions below supersede only its two Important findings and four specified evidence gaps. No tests, services, network calls, databases, Docker operations, private environments, Git mutations, or subagents were used. Only this new report was written.

## Inputs and method

Worktree root for relative paths below: `/home/tanren/ama/.worktrees/phase-b-completion`. `W` denotes `.superpowers/sdd/2026-10-23-ama-phase-b-remaining`; `E` denotes `artifacts/phase-b/b-remaining/task-06/fix-01`; `IT` denotes `backend/integration-tests/src/test/java`; `AD` denotes `backend/adapters/src/main/java`.

Read the scoped review brief, fix brief with verbatim original findings/CannotVerify wording, original review context, appended author report, public fix report, typed-input index supplement design and plan, PIN03 and candidate qualification proof. Rechecked the relevant saved-transcript R1 acquisition/bounds and acceptance-matrix sections, without reopening the whole plan/history. Reviewed every changed production hunk and new focused test/probe; inherited copies were assessed through their supplied exact original-to-copy patches, inventory, affected assertions and actual XML, rather than treating copied code as newly invented product behavior. Tool-output truncation was recovered where it cut relevant patch sections. Focused unchanged source checks are listed below.

Independently calculated input hashes:

- Scoped review brief: `09963dd90d7a08cc409cce2310d826e28fb0e4b6a29800d8da3c9d247b168999`.
- Complete non-artifact review package, 248719 bytes: `a1767f0689d5096a2692a9822485b82fe0f77a3cc9883a54681e0766c55d58dc`.
- Appended W/task-06-report.md: `c759eeca32f342e86cf801a150f8947c17a75e7e7914e5428b0ef2468f422c50`.
- E/report.md: `6ffac0811ddba318b203fb424f8d0c0d821981be9311ac8c68f6ee381caacfc9`.

The full raw audit/manifest and independent controller custody are available separately. I did not repeat exhaustive protected-file, private-value, resource/process, migration-history/data-preservation, or all-source hash audits. Those controller checks do not substitute for the source and evidence judgments here.

## Original findings

### T6SR-01 — ADDRESSED: prospective append ancestry

`AD/ama/transcript/JdbcSavedTranscripts.java:101` applies `canAppend` to every non-CONFIRM operation; lines103–105 count the complete admitted revision chain and require room below both depth and node bounds. Its callers at lines53 and62 apply the predicate to snapshot preparation and fresh F facts before insertion. The same predicate governs canSave/canRestore at line222. CONFIRM remains available at the ancestry ceiling because it appends no revision. No ancestry clipping, reparenting, new quota or mutable-history workaround was introduced.

`IT/ama/storage/Task06FixIntegrityIT.java:5` exercises SAVE/MARKS/RESTORE separately against depth and node ceilings: the final allowed child is readable, the next append fails with unchanged aggregate state, and capabilities refuse further appends while retaining confirmation capability. This is six boundary combinations, not six separately counted JUnit cases. The final XML records this test at `E/final-integration-01/integration-tests-TEST-ama.storage.Task06FixIntegrityIT.xml:76`. Inspected retained observations: `E/pipeline-47e62c3a-1046-4763-8170-06aa80686340/prospective-ancestry-boundaries.json:1`.

### T6SR-02 — ADDRESSED: CURRENT confirmation-pointer integrity

`AD/ama/transcript/SavedTranscriptGraph.java:37` refuses a non-null current confirmation with no selected revision. Lines56–62 acquire the union of the pointed confirmation and the selected revision's unique confirmation relation, require exactly one actual row, require WORKING, and compare ID, meeting and revision. These checks run in the shared S/F acquisition. Owner historical selection retains its separate branch at line63, so a legitimate old historical confirmation need not equal the current root pointer.

`IT/ama/storage/Task06FixIntegrityIT.java:11` tests wrong-revision, foreign-meeting and null-saved/non-null-confirmation combinations for owner and effective member, restores each controlled change, and retains valid old-owner-history and current owner/member positives. Its second final XML case is in the same retained integrity suite. `E/current-confirmation-query-plan.json:1` shows the actual PK/unique-index union plan. Its estimated two rows and filesort are not promoted into measured contention/scan claims; the relation's logical cardinality is at most two through the PK and unique meeting/revision key.

### T6SR-03 — NOT ADDRESSED, explicit nonblocking carry

The original generated request-type collapse to unknown remains open at `web/src/shared/api/generated.ts:1206` and the other original anchors. No request-schema or generated-client fix is present in this range. The root explicitly assigned correction and malformed-payload/type-use proof to Task12 before client activation. This report preserves the Minor and does not call the current generated request types sound.

## Mandatory evidence gaps

### T6CV-01 — ADDRESSED: actual public HTTPS, roles, sessions and revision wire forms

`IT/ama/storage/Task06FixHttpsIT.java:7` configures the real Spring Boot API for loopback HTTPS using retained PKI; lines10–11 use a CookieManager with the actual cookie formatter, verify Secure/HttpOnly/SameSite attributes and exclusion from plain HTTP. Lines16–23 exercise owner/member/outsider/admin HTTP authorization plus foreign Origin, missing CSRF and absent session. Lines25–34 exercise all four named large revision values, token acceptance, legacy replay, equal dual form, mismatched form and MAX-1 mutation refusal; lines35–40 check server logout clearing and private/no-store results. Both LOCAL and S3 cases pass in `E/final-integration-01/integration-tests-TEST-ama.storage.Task06FixHttpsIT.xml:75` and its next case. Inspected `E/pipeline-e0190096-90e2-4430-87dc-a746a26c994e/https-evidence.json:1` corroborates the exact values and status outcomes.

This closes the original plain-HTTP/admin-service-only/large-value evidence gap. Session identities are controlled test sessions, cookie issuance is supplied through the production formatter to a Java cookie jar, and browser SameSite enforcement or enterprise login was not exercised. Those are accurately bounded claims, not missing current-server evidence. Existing final-covered wire/access cases continue to supply malformed-body, range, history/RAW and private projection checks; this is not a claim that every old request was rerun over a browser.

### T6CV-02 — ADDRESSED: actual structural provenance negative closure

`IT/ama/storage/Task06FixProvenanceIT.java:15` starts from fresh actual pipeline RAW/saved/confirmed graphs, then makes and restores named structural changes. The final test at `E/final-integration-01/integration-tests-TEST-ama.storage.Task06FixProvenanceIT.xml:75` passes. Inspected `E/pipeline-f107fd87-3acd-47f4-966e-483447b53b1a/provenance-negative-matrix.json:1` records 28 named observations: missing/foreign/cyclic parent; missing/extra/foreign-source segment; extra/missing confirmed marks and impossible mark linkage rejected by composite FK; missing/extra/foreign batch member/file; missing/foreign/wrong-state/wrong-output/wrong-input/wrong-subject producer; same-subject and cross-subject surplus input tasks; duplicate original session; missing sidecar; depth/node refusal; missing original confirmation archive; published-history/read versus write-refusal; and disabled-route stored-history versus byte-refusal.

For structurally impossible missing referenced files/foreign mark edges, the test observes actual FK rejection rather than disabling constraints and claiming a reader accepted an impossible state. The original actual-pipeline claim is confined to the positive graph creation; the subsequent mutations are explicitly controlled structural probes. The new indexed membership code resolves the real surplus-task RED instead of hiding foreign-subject rows or substituting unrelated subject history. The additional task/index evidence is assessed below.

### T6CV-03 — ADDRESSED: fault/phase, current authority, handle and isolation evidence

`IT/ama/storage/Task06FixAtomicityIT.java:6` runs row, readback, audit, flush and commit faults for SAVE, MARKS, RESTORE and CONFIRM: 20 injected fault scenarios with pre/post aggregate state equality, followed by four lost-ack/replay scenarios. The SQL proxy injection verifies its targeted point actually fired; a generic unrelated failure alone does not pass these scenarios. Lines13–17 track metadata admission by exact connection/revision before body transfer, including command F and inserted-row readback. The passing test is at `E/final-integration-01/integration-tests-TEST-ama.storage.Task06FixAtomicityIT.xml:77`; inspected raw matrix `E/pipeline-f63426a1-70c8-4c70-a3e4-9b76065aa1d3/atomic-fault-matrix.json:1` records all20 plus4, with 10/10/10/8 body transfers in the respective lost-ack scenarios.

The same class at lines21–36 covers between-phase cancel epoch, lifecycle, governance, owner and current identity changes; effective-member revocation during A; and interruption before S, in archive preparation and during final readback. Retained `E/pipeline-d387641d-744b-40ec-8fa6-0bebb7fe5380/between-phases-authority.json:1` records the five mutation authority changes; the additional member/cancellation tests appear separately in the passing XML. Static published-write refusal is in the provenance test; no actual future Task11 publication protocol/race is claimed.

`IT/ama/transcript/Task06SavedHandleProbe.java:6` obtains a genuine complete saved handle and rejects foreign issuer, fabricated handle, wrong meeting, suspended REQUIRES_NEW holder, ended holder and later holder; the original holder is accepted again after resumption. Its final caller case passes at `E/final-integration-01/integration-tests-TEST-ama.storage.Task06FixHandlesIT.xml:75`.

`IT/ama/storage/Task06FixLocksIT.java:7` exercises five competing writes at readonly S/F, command S/F and inserted readback under default isolation and READ COMMITTED. Direct VALUES avoids the earlier INSERT SELECT read-lock confound. Inspected `E/pipeline-b941e424-d03c-4aaa-8d6f-3a6376fbe75f/all-phase-contention.json:1` and `E/pipeline-89302282-48a7-43ab-9964-6c6efeac65b4/all-phase-contention.json:1` each contain25 blocked observations, across all five phases, for body update, segment insertion, previously empty mark insertion, mark update and mark deletion. Both tests pass in the final locks XML, including the RC case at line72. This is50 actual attempts, not a Cartesian claim covering every operation, isolation, cancellation point and future writer.

These meaningful table-driven cases close the original coverage gap under the fix brief's concise representative-matrix requirement. The source still relies on registered root-ordered writers and actual retained parent/row/FK protection; unsupported concurrent direct maintenance is not advertised as safe.

### T6CV-04 — ADDRESSED: named inherited integration regressions

`E/inherited-copy-final/inventory.json:1` identifies ten forward copies and exact patches. Reviewed adaptations preserve the relevant assertions while changing names, new artifact paths, fixture identities, resource bounds and current14-history guards. Six copied classes run: meeting revision29, meeting concurrency5, authority22, prepared completion18, standalone facts reader12 and archive restart1: **87 distinct cases**. Actual final XML contains these named tests. Controlled metadata/queue/archive fixtures are not misrepresented as intake→Worker qualification.

The original global outbox-sequence overflow method deliberately lacks @Test in the new MeetingRevision copy (`IT/ama/meeting/Task06InheritedMeetingRevisionIT.java:192`). It is excluded rather than falsely counted as full original-class execution; the actual Task6 flush-fault cases supply the stated scoped atomicity equivalent without mutating the shared global allocator. The archive copy retains five subprocess launches and immutable original-reference/changed-default/negative recovery assertions. Added terminalization is limited to explicitly owned controlled fixture tasks after assertions; it is not evidence that actual pipeline completion was repaired by cleanup.

The first final integration batch's single failure was the copied sort-identity namespace expectation, not production ordering. `E/inherited-namespace-fix/exact.patch:1` changes only the fresh sort prefix to match the new owner/member prefix, preserving BMP/non-BMP ordering assertions. `E/inherited-concurrency-green-01/receipt.json:1` and its five-case XML establish the affected-class rerun. This is an explicitly adapted forward regression lane, not a claim that every original historical class was run unchanged.

## Introduced index/source changes

No introduced blocking or Minor defect was confirmed.

- `backend/adapters/src/main/resources/db/migration/V013_2__task_input_lookup.sql:1` contains exactly the approved nonunique `(input_kind,input_id,id)` lookup index. The independent design/freeze/separate ACK/application-preservation gates are binding controller inputs; this reviewed supplement supersedes the earlier no-new-DDL shorthand, not the rule against changing old migrations. No new business uniqueness, quota, backfill or authority is introduced. Task8 model synchronization is pending as declared.
- `AD/ama/transcript/SavedTranscriptRows.java:31` reserves the sentinel's row, ID and copy capacity before executing the forced-index equality query. It rejects overflow rather than clipping and has no missing-index/global-scan fallback. The fixed36 ID reservation is justified by the final frozen V010 schema's ASCII CHAR(36), not merely an assumption that all data is a UUID.
- `AD/ama/transcript/SavedTranscriptGraph.java:92` enumerates all tasks for the typed input, unions the actual RAW producer, preserves the existing task bound, refuses surplus before unnecessary foreign task metadata/body locks, and validates exact producer subject/input/state/output. Lines97–102 use sorted exact task PK acquisition and repeat complete membership under the same held gate. There is no same-subject filter that hides a foreign-subject anomaly.
- The consistent-read freshness premise was checked through actual call sites: mutation receipt reads are FOR UPDATE; B03 locks identities/subject/root before the first graph consistent read. The original root-ordered registered writer contract matters here. Under REPEATABLE READ a second consistent query alone is not evidence against arbitrary concurrent cross-subject maintenance; the implementation and qualification correctly do not claim that protection.
- `IT/ama/storage/Task06IndexedTasksIT.java:5,21,29,42` and its four passing final cases establish four S/F membership lookups, exact producer locks,65 unrelated held same-subject tasks still permitting the valid read,65 relevant tasks rejected before body/task metadata transfer, registered replay-writer waiting, and new membership between S/F causing atomic refusal in both isolation levels. Inspected raw files: `E/pipeline-29aba0fa-843b-4d6a-94c8-116aedbe1abe/indexed-membership-phases.json:1`, `E/pipeline-c0b8174c-de52-4b31-9893-5b6494e9c8a7/membership-budget-boundaries.json:1`, and `E/pipeline-bd96ca9d-bd1c-4996-9a32-e2e5bea78c93/between-phases-membership.json:1`.
- `E/indexed-query-plans.json:1` records actual ref/covering-index lookup without filesort and const PRIMARY producer access. Planner estimates are not measured scan/lock/capacity bounds. PIN03 preserves the local functional scope and original numeric/archive/declaration minima; it is not independent source approval or a new per-boot raw tuple observation.

## Evidence totals and limitations

I parsed the retained XML, inspected named cases/failure text and command receipts; no tests were rerun.

| Window | Observed result | Use in verdict |
|---|---|---|
| E/final-integration-01 |24 XML,121 distinct cases,1 failure,0 errors,0 skipped; exit1|All current Task6 cases plus inherited lane, with explicit namespace failure retained|
| E/inherited-concurrency-green-01 |1 XML,5 cases,0 failures/errors/skips; exit0|Replace the affected five-case class's earlier result, do not add five new cases|
| E/final-units-01 |41 XML,254 distinct cases,0 failures/errors/skips; exit0|Final unit coverage, including inherited hash/config/security/Worker units|

The composed integration result is **121 distinct =34 Task6 +87 inherited**, not126 and not a pristine green121-case batch. IT receipts retain the explicit integration-only MAVEN_CONFIG selector; the all-unit receipt has null. No repeated historical runs are counted as new independent coverage. Intermediate setup failures, REDs, the rejected global-OR intermediate approach and earlier qualification windows remain historical evidence rather than being overwritten or presented as the final source.

No in-scope CannotVerify item remains after this source/evidence review. This is not a statement that every possible cross-product or external environment was verified. No browser/company-login, production/capacity/HA, model-alignment, all-Phase-B or installed future Task10/11/correction claim is made. Their explicit later activation gates remain in force. Exhaustive custody verification belongs to the controller's parallel review.

## Focused unchanged-callsite checks

These checks were necessary to resolve risks introduced or newly exercised by the fix; no new finding reopens old adopted work:

1. `AD/ama/transcript/JdbcSavedTranscripts.java:45–105`: the fix hunk did not show both callers of writable; checked S and F placement, replay ordering and insertion/readback boundary.
2. `AD/ama/transcript/SavedTranscriptGraph.java:64–107` and `SavedTranscriptRows.java:41–80`: checked the surrounding retained metadata/deferred-body protocol and exact-key transfers, since the membership helper and confirmation branch join those functions.
3. `AD/ama/transcript/JdbcSavedTranscriptReceipts.java:24–32`, `AD/ama/meeting/JdbcMeetingAccess.java:50–97`: resolved whether an earlier plain read establishes a stale RR snapshot before the genuine root. Receipt/identity/root acquisition uses current locking reads.
4. `AD/ama/task/JdbcTaskQueue.java:77–90` and `AD/ama/transcript/ImportTaskDomainPolicy.java:30–57,81–108`: checked registered enqueue/adopted-root/expected-spec relationship relied on by the new nonlocking indexed membership proof. No public arbitrary-task authority was added.
5. `backend/adapters/src/main/resources/db/migration/V010__reliable_execution.sql:69–79`: resolved a tentative concern that V005's original utf8mb4 ID definition made36-byte reservations insufficient. V010 explicitly converts task ID and typed input ID to ASCII CHAR(36); therefore that concern is not a finding.
6. `IT/ama/storage/Task06GuardsIT.java:16` and `Task06AccessIT.java:7`: checked how unchanged final-covered cases complement the new between-phase/role matrix, without representing them as new HTTPS browser coverage.

**Disposition:** accept this scoped fix for controller adoption checks, retaining T6SR-03 as the explicit Task12 Minor and the index/model synchronization obligation as Task8 pending work.
