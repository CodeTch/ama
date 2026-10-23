# Independent bounded design rereview R1

Date: 2026-10-23. Reviewer: b_remaining_design_review.

**SPEC: PASS. Design: APPROVED for the six reviewed design corrections. Remaining finding IDs: none.** DR-01 through DR-06 are ADDRESSED in the frozen R1 candidate. No new concrete finding arose from the changed scope. This approval is design-only and retains every task-specific implementation, schema and external-protocol activation gate.

## Exact reviewed inputs

Read both complete frozen candidate documents and independently computed their SHA256 with PowerShell Get-FileHash; both exactly match the coordinator's supplied values:

| Frozen candidate | Verified SHA256 |
| --- | --- |
| `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/design-r1-candidate/controlled-runtime.md` | `0f57b2d7441ac3c7342be0df96739c33b1024f74738ee0fb74cba052d62de7d5` |
| `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/design-r1-candidate/remaining-plan.md` | `e5236be1d141f5d643fc2d5baa28142d45835da6c302faf2be749e75ae2f1416` |

The review is bounded to original DR-01..06 from `design-review.md`. The spec's Normative refinement R1 explicitly supersedes ambiguous earlier shorthand; the plan's R1 resolutions make that refinement binding on Tasks 2..13. The original review remains preserved. No changing Task 1 source was read as a code review, and no code, SQL, Git, DB, tests, services, private environment or subagents were used. Only this rereview record is written.

## Closure by finding

### DR-01 — ADDRESSED

References: spec adopted decisions 2/5 and R1 Editor intent; plan R1 DR-01 resolution.

The unsafe equal-positive-epoch comparison is replaced with a real persisted zero guard created only for a genuinely new meeting. Legacy absence denies automatic first save. Any positive intent remains sticky; no expiry, reset, decrement or UI claim can restore zero. Same root locking serializes completion and intent; current owner, lifecycle, original input/business/cancel fences and absence of an effective body remain required. Intent-before-generation is therefore covered as well as intent-during-generation. Overflow rejects rather than wrapping.

Explicit adoption no longer depends on the original zero/equal epoch and still rechecks fresh owner CAS/current eligibility and immutable provenance. This closes the contradiction that otherwise prevented adoption after the very edit intent that suppressed autosave. Typed drafts remain preserved.

The operation-specific replay choice is coherent: the same key/body does not increment again, reauthorizes, and returns the current committed positive guard rather than claiming an immutable original epoch is stored. A stale expected meeting revision conflicts and preserves the draft. Actual zero is the only autosave authority; no consumer needs the original positive value. Existing mutation replay contracts stay unchanged. This is an explicit new-command contract, not silent relaxation of prior operations.

### DR-02 — ADDRESSED

References: spec R1 Retention and external completion, first two paragraphs; plan R1 DR-02 resolution.

Retention is generation-only, registered and authenticated by private issuer/same DataSource/actual holder/purpose/full-spec/prelocked graph. Actual producer/input/task and exact current external execution/authoritative observation are required. Current ACTIVE owner EDIT, same cancellation epoch and valid unpublished/ungoverned/nondeleting lifecycle remain mandatory. Only the original businessVersion equality can be relaxed for immutable result retention; ordinary initial send and generic queue authority stay strict. Fact-only receipt does not grant business permission.

Business-stale candidate state is explicitly STALE with unchanged original input/task/base/fingerprint and no pointer, revision, generation-debt or confirmation mutation. It is private, non-adoptable and not a pending-readiness blocker. Current-fence editor-suppressed results and existing-body regeneration become PENDING, which does block final readiness. Shared-root serialization and the separate generation-debt/current-input blockers resolve the late-retention/final race.

The durable task remains STALE/BUSINESS_FENCED with no output under the existing fenced observation vocabulary. The retained candidate is discovered through its authentic task relation; UI does not infer adoption or result existence solely from SUCCEEDED. Candidate graph plus terminal fact/task recording is atomic. No speculative enum or separate unsafe precommit was introduced.

The detailed Task 7 capability/transaction protocol still requires independent review before activation, as the original finding requested. This review approves the normative boundary, not an unseen implementation.

### DR-03 — ADDRESSED

References: spec R1 Retention and external completion, external execution state-machine paragraph; refinement evidence gates; plan R1 DR-03 resolution.

R1 separates ordinary task lease/heartbeat from committed external marker/token ownership and explicitly hands off to operation heartbeat before I/O. It requires synchronization for a renewal tick racing the handoff. After handoff, ordinary task complete/quarantine cannot run, and loss of ordinary RUNNING renewal cannot suppress authoritative receipt. Operation-token loss suppresses business application while retaining existing fact-only/quarantine handling.

Uncertain marker ACK, app death after marker, preparation/rediscovery retry, close/interruption and permit settlement are explicit. None authorizes a second send. Marker commit is an independent transaction before network; the final observation is its own atomic settlement. The normative refinement resolves any ambiguity in the plan's earlier compact phrase about completing marker/operation/task/result/permit/outbox atomically.

Task 1 remains ordinary DB-prepared completion only. Task 7 owns the external handoff state machine and its adversarial execution tests, preserving the intended dependency boundary.

### DR-04 — ADDRESSED

References: spec R1 Supported v2 graph operations and final required reviews; refinement evidence gates; plan R1 DR-04 resolution.

The R1 matrix specifies immutable complete candidate graphs, new saved graph remapping on adoption, inherited generation/profile chain for body saves, complete body-evidence reanchoring, immutable element/action edits and copied directory/suggestion joins. Alternative pointer adoption requires a separate explicit review rather than being assumed equivalent. Old candidate content stays immutable; only disposition changes. No old review credit transfers to new targets.

CONCLUSION/ACTION to OPEN_QUESTION is now an explicit saved operation retaining sources/history; new non-ACTION targets have no ACTION-only child rows. Complete categories, accepted tuples, suggestions, evidence, directories/joins, exclusions, inherited inputs/tasks/archives and review membership are enumerated. Unsupported rebind/mixed-context/v3 histories fail atomically and remain later D01/D03/D04 obligations. EMPTY materials is not treated as proof of arbitrary-edit provenance support.

Final readiness now computes the required review set independently of the hash helpers: every current CONCLUSION/ACTION requires latest exact whole-target SOURCE_SUPPORT=SUPPORTED over the complete nonempty relevant evidence set with MEETING support; every ACTION separately requires ACTION_FIELDS=CONFIRMED for its actual accepted tuple, including nulls. Partial reviews cannot combine into whole credit. OPEN_QUESTION has no blanket support gate, and B adds no blanket body review gate. Required targets/scopes and selected optional body reviews are explicit and frozen. Review writes advance subject/meeting and clear final pointer. The prospective final hash boundary is explicit and cannot fabricate a stored placeholder.

This closes the original under-specification without changing old v2 hash bytes or requiring the entire later D01 runtime in B. Later implementation review must verify the stated business readiness checks separately from ResultHashProfiles/ReviewCoverageEvaluator invocation.

### DR-05 — ADDRESSED

References: spec R1 Lossless browser version wire; read-output table and evidence gates; plan R1 DR-05 resolution.

Canonical decimal tokens are authoritative browser values, never derived from rounded numeric fields. Grammar/range and equality of dual integer/token forms are explicit. Every browser-used mutation, including existing info and roster commands, accepts the token alternative. Existing exact JSON integer APIs remain available; normalization to the same semantic integer happens before idempotency digesting. No 2^53 cap is imposed on the database domain.

The required real HTTP/browser vectors cover the safe boundary and adjacent unequal integers, the existing maximum, malformed/range/unequal dual forms, replay and draft-preserving conflicts. Generated schemas/types are required from these contracts. This is a backward-compatible design; implementation must preserve the existing integer normalization when adding token input rather than inventing a different historical request digest.

### DR-06 — ADDRESSED

References: spec R1 Mandatory authoritative read/discovery outputs and refinement evidence gates; plan R1 DR-06 resolution.

Producer ownership now covers upload receipts/completed files/ordered input and task discovery (Task 4), transcript/history/confirmation and source marks (Task 6), candidate/body/element/source/review/readiness (Tasks 9/10), final/publication/destinations and UNKNOWN actions (Task 11), and authorized meeting list/workspace/capability/task/session/category composition (Task 12). Exact endpoint shapes/OpenAPI must be frozen before each consuming UI slice and are task-review outputs.

Fresh actor/meeting authorization, bounded payload/pagination, private/no-store responses and safe errors are explicit. The spec disallows browser-memory-only resume, owner inference, storage-key leakage, task-success-as-adoption and invented company folder semantics. Actual same-origin HTTPS session/CSRF/Origin, four sessions, both storage backends and refresh/process-restart/lost-response recovery are required; mocked business success is excluded from end-to-end evidence.

## Continuing gates and limits

Approval applies only to the two verified frozen documents and these six corrections. The coordinator may adopt their exact reviewed content into the normative tracked spec/plan and continue dependent design/implementation work. It does not allocate any migration number, authorize SQL application, approve runtime activation by itself, close the fairness follow-up, certify company adapters, or complete Phase B.

The earlier accepted sidecar/stage-patch choices retain root allocation, pinned Flyway ordering, full SQL/SHA freeze and separate ACK requirements. The Task 7 detailed authentic retention/external protocol review remains required before its consumers activate. Actual graph/review, HTTP, MySQL, local/S3, browser and restart evidence plus exact implementation reviews remain task exits. B01 IMM PARTIAL, B04 Q401 UNKNOWN, company and HA/native I/O/capacity qualifications remain unchanged.
