# Task 11 independent SPEC and design-quality review

## Spec Compliance

**SPEC: ISSUES FOUND. Design quality: NEEDS FIXES.** Findings: 0 Critical, 2 Important, 0 Minor. Design-only verdict; no source/runtime/provider/Phase B qualification follows.

Reviewed D = `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-final-publication-design.md`, raw SHA256 `a8ec962bfee7ed1025a8d0dc113750447d9a8cc95b006b758cc544de900981e8`, 69,377 bytes / 316 lines. Primary full-addition diff `task-11-design-review-diff.txt`: raw SHA256 `d9d41e1312097172c20ed8f3873a2d255d6750bba4ff810b0ba1c4d95c469c64`, 69,815 bytes / 319 lines. The entire addition was read once in consecutive segments without an omitted/truncated design section; later searches were targeted named-risk checks. W below denotes D's directory.

Read the exact Task11 brief/extract first, full controlled-runtime including Normative R1, zero-effect root ruling, shared Task9/10 rank ruling, adoption wrappers for Tasks5/6/7/8/9/10, Task7 root/attempt clarifications, minutes UX/config-wire/contract authorities, and actual original product requirements `designDocs/智能会议助手一期需求说明文档.md:869`, `:1033` through `:1036`. Task4's changing implementation was not treated as adopted.

## Strengths

- D:110, D:114, D:116, D:120, D:122 and D:128 separately define current readiness, deterministic WHOLE SOURCE_SUPPORT plus nullable accepted ACTION_FIELDS human reviews, exact frozen memberships, a genuine prospective draft and readiness before the single revision advance. They preserve the existing final algorithm rather than fabricating stored success or adding a blanket BODY/OPEN_QUESTION gate.
- D:79 through D:106 declares accepted generation without candidates, all publication task/execution history, actual original producers, complete S/A/F equality, the binding full rank and protected pretransfer LONGTEXT measurement. Original archives/hash/parse/network work stays outside roots.
- D:199 through D:240 treats supersession as a new publication-only confined extension: genuine issuer/DataSource/holder/purpose/full-old-and-new-domain proof, unchanged generic retry semantics, immutable historical manifests, no restored old-token authority, one publication UUID and real marker timing for new-key operations. D:218 correctly respects V010 marked_at NOT NULL.
- D:171 and D:226 through D:230 define the provider's durable publication-wide sole-document slot, immutable execution history and proof-CAS replacement across key changes/restarts. UNKNOWN cannot advance the slot or authorize resend.
- D:244 through D:258 uses the Task5 drain, Task7 committed marker and single external owner, separately verified postcommit settlement, no fact-only domain writer, and honest remote SUCCESS/raw PENDING/manualLocked projection without claiming that terminal operations remain reconcilable.
- D:262 through D:288 separates immutable acknowledgments and fresh observations, keeps actual ACL/no-store/token/OpenAPI responsibilities explicit, and preserves replay-first independent construction. D:292 through D:313 honestly labels implementation and evidence gates as future work.

## Issues

### Critical

None.

### Important

**T11DR-01 — The controlled KB caller-authentication and actor-delegation contract is missing.**

Location: D:169, D:173, D:179 through D:183; evidence gate D:297.

The protocol pins the provider's TLS origin/identity, stores actor grants and issues signed destination assertions, but never selects how the provider authenticates the calling adapter or establishes that its supplied actorId is an authorized delegated identity. The assertion route accepts Actor/provider/library/folder; publish accepts actorId plus that assertion; discovery and exact execution/result reads likewise have no selected caller-authentication rule. A signed assertion authenticates the issuer's statement, not the caller's actor claim that caused issuance. Server TLS and loopback binding do not provide client authentication. Application session/Origin/CSRF at D:262 covers `/api/v1/meetings`, not the separate controlled provider.

Following only this contract could therefore permit a caller to request an assertion for another granted actor, invoke publication, or inspect results outside the authenticated application path. This is a concrete gap in the promised real destination-ACL protocol, not a request for company authentication or hostile-host qualification. The adopted Task9 provider explicitly selects a per-run bearer secret and authenticated routes (W/task-09-minutes-generation-design.md:194, :196, :197); Task11 neither adopts that mechanism nor defines another one.

Fix: select the controlled adapter-to-provider authentication mechanism, credential custody and trusted delegation of the server-authorized original actor. Apply it to destination issuance, publish, discovery and execution/result reads. Pin signed-assertion verification to the configured provider/store and define direct document-view authorization or its deliberate unavailability. Add actual HTTP negatives for missing/wrong credentials and actor/delegation mismatch, proving denial before effect and no unauthorized result read. No company protocol or application DDL is required merely to specify this boundary.

**T11DR-02 — A genuinely unmarked terminal task can leave publication permanently READY.**

Location: D:139, D:161, D:163, D:217 through D:222, D:254 and D:284.

Initial acceptance creates publication READY and an ordinary task, with no operation/execution/publication_attempt (D:163). Ordinary pre-marker retries and maxAttempts3 are explicit (D:161). Repeated preparation/destination-authorization failures or worker death before marker can exhaust that task. The only defined publication FAILED transition is a marked execution's verified provider-zero-effect observation (D:254). READY freezes all mutations (D:139), while retry/replacement requires prior execution proof plus locally applied failure (D:217, D:222). No defined transition or recovery releases this genuinely unmarked failed task. D:284's assertion that all accepted failed rounds have marked execution manifests does not cover this allowed lifecycle.

Focused source evidence: `backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java:108` terminally fails exhausted ordinary tasks; :136 through :143 also turns exhausted RETRYABLE into task FAILED. `terminateUnmarkedExecution` at :145 through :147 returns when the initial task has no execution; none of these paths writes publication state. `backend/worker/src/main/java/ama/worker/WorkerLoop.java:128` and :148 ordinary failure handling is not a publication-domain writer. Adopted Task5 attempt-state repairs add no Task11 publication failure transition. The root highlighted this boundary during review; this finding was independently checked against the actual code.

Fix: design a confined fresh-authority local failure/abandonment and explicit recovery protocol for work proven never to have crossed a committed marker. Acquire the complete publication/task/attempt/operation/execution inventory in the shared order; atomically record or reconcile the true local failure; preserve original accepted task/request acknowledgment; allow authorized configuration correction/retry/edit without inventing provider proof or a publication_attempt. Cover initial no-operation work and replacement PREPARED work explicitly. Any MARKED execution, ambiguous marker commit, unresolved older round or possible send remains frozen/query-only. Add exhaustion-versus-marker barriers, restart/commit-ACK ambiguity cases, and old-ack replay after the recovered submission changes destination/final. Existing local PREPARED termination is not a substitute for this missing publication lifecycle.

### Minor

None.

## Named source checks and limits

- **Historical replay/current-hash risk:** Read actual `JdbcExternalOperations.java:30` through :53 and :94 through :175, plus Task7 prepared API/authority/replay contracts. Legacy retry compares the current operation hash at :161 and fixed input/logical identity at :165/:167. D:234 through D:240 and D:288 provide a viable proposed separation of immutable publication history and command acknowledgment. Source review must prove that historical publication replay after supersession never accidentally reaches the legacy current-hash comparison. This is an implementation gate, not a finding requiring broad weakening of generic retry.
- **Schema feasibility:** Read the original whole V013 and focused V010 external operation/execution definitions. Unique meeting publication, exact attempt/execution links, nullable material reference, existing state vocabulary, request_manifest capacity and marked_at NOT NULL support the marked-round proposal. T11DR-02 must be resolved before concluding that the pre-marker accepted-binding/receipt design is complete; no missing column is invented here.
- **Pure final compatibility:** Read actual `ResultHashProfiles.java:106` through :156 and the whole `ReviewCoverageEvaluator.java`. The real projection does not directly hash every final header/body field, correctly acknowledged at D:122. Draft/stored byte equivalence and separate frozen payload checks remain future executable evidence.
- **Controlled authentication:** Read the adopted Task9 exact transport/authentication contract at W/task-09-minutes-generation-design.md:192 through :204. Its separate model-provider authentication does not implicitly implement the new KB provider.
- **Unmarked terminal failure:** Inspected only relevant existing Worker/queue exhaustion/completion/PREPARED termination and adopted Task5/7 failure contracts. An attempted core queue-interface path did not exist; the risk was resolved from actual Worker/JDBC source without a broad crawl.
- Tool truncation affected an initial V010 grep's large embedded preflight hex, not the design. The actual external table definitions were recovered in full bounded ranges. No tests were rerun to validate prose.

## Cannot verify from design / activation gates

- **T11G1:** Pin actual adopted upstream source/raw hashes and common genuine issuer/DataSource/holder/gate; prove complete declaration/rank/effective budgets and real MySQL protected pretransfer bounds in S/F/readback (D:69, D:104, D:294, D:296).
- **T11G2:** Independently review the publication-only replacement source, exact historical manifest verification, all old/new tasks, old tokens, new-key marker timing, same-key PREPARED ownership, rollback and post-supersession replay while preserving generic retry semantics (D:199 through D:240, D:299).
- **T11G3:** Supply the actual all-mutator inventory and demonstrate content/intent/reference/current-and-retained-generation freeze through real APIs/policies, separately preserving lifecycle fences and fact-only receipts (D:134 through D:149, D:294).
- **T11G4:** Execute unchanged old final goldens and draft/stored exact bytes, frozen required memberships/nullable tuples/original archives, accepted-generation-without-candidate/debt/PENDING blockers, full rollback and concurrent-root barriers (D:295, D:296).
- **T11G5:** After findings close, qualify real authenticated controlled HTTP, durable sole-document slot/proof-CAS/immutable journal, strict bounded abort, destination revocation, retained-store fresh-process lost-response recovery, actual happy completion and honest terminal-manual projection (D:297 through D:300).
- **T11G6:** Install actual Task5 drain, Task7 marker ACK/single-owner/postcommit permit recovery, Task8 fair sweep, producer OpenAPI and four-session native+S3/browser chain (D:244 through D:258, D:301). None is proved by this design review.

## Assessment

**Task quality: NEEDS FIXES.** Marked-execution supersession, final-review freezing and remote/local reconciliation are carefully constrained. The missing controlled caller-authentication choice and genuinely unmarked terminal-failure lifecycle are concrete design defects requiring correction and independent rereview before adoption. The source/evidence gates remain separate even after both findings close.

Only this requested report was written. No source, old plan, migration, Git, database, Docker, private environment, network, test or resource mutation; no subagents. Review-artifact raw hashes/lengths were measured as ordinary files outside any DB transaction. The first report-write command failed because PowerShell supplied a provider-qualified path to System.IO; it created no report and was corrected using a literal filesystem path.
