# Task 6 saved-transcript design — independent review

Date: 2026-10-23. DESIGN REVIEW ONLY.

**SPEC verdict: CHANGES REQUIRED.** T6DR-01 conflicts with the adopted separation of stored historical transcript visibility and original-file byte eligibility.

**Design/quality verdict: CHANGES REQUIRED.** Resolve T6DR-01 and make the acquisition phases in T6DR-02 executable before source dispatch.

**Findings: 2 — P0: 0; P1: 1; P2: 1; P3: 0.** No implementation, test, runtime, company integration or Phase B qualification is asserted.

## Reviewed artifact and evidence boundary

Read the complete task-06-saved-transcript-design.md, independently SHA256-verified as `1734217a8d6954a8d408b30a3f5912c1e93f1c9592b2313375d80af2f6389a3e`, plus its brief, extracted plan and hash preflight. Binding comparisons include controlled-runtime Normative R1, approved Task 3 original/R1, pre-gate R1 and independently passing R2/review (R2 coordinator adoption pending), and adopted Task 4 full R1 preserved under artifacts/phase-b/b-remaining/controller-intake-design/.

Named source checks used frozen commit `3e393dbc9485b187b89bfc03abd88821a0aabddb` for TranscriptConfirmationHash, JdbcProcessingFactsReader and JdbcMeetingRepository. Other focused reads covered StoredSourceGraph.Session.transcript, ConfigBindings stored-processing/confirmation paths, ArchivedProfileResolver, MinutesContracts fact/table shapes, TransactionRunner and V011 schema. Adopted B06 seams/archive integration guides, facts/fix1 reports and remaining-preflight/b06-preflight supplied the history/locking distinctions. No active Task 3 implementation was used as adopted authority. Task 5 remains a prerequisite, not an approved implementation.

No tests, DB/Docker operations, private environment reads, source edits, index/HEAD mutations or subagents were used. This report is the only authored artifact. WSL execution was unavailable; filesystem reads and read-only git show with command-local safe-directory configuration were used instead. No persistent Git configuration changed. An initial report write had an invalid PowerShell provider-prefixed path and failed before creating a file; this write uses the explicit UNC path.

## T6DR-01 — P1 — Separate stored-history content from original-file byte eligibility

Affected design: section 3 complete closure, especially line 87; section 4 steps 4–6 and line 102; section 8 owner revision/marks/confirmation-detail routes.

The common closure requires every source to be AVAILABLE with current B04 route/epoch eligibility. Section 4 requires full registered current/history source finalization and says original epoch/route eligibility still applies. No operation-specific exception exists for readOwnerRevision or historical marks/confirmation graph retrieval. The natural implementation consequently denies an authorized owner's already-stored historical body/marks when an original upload epoch becomes obsolete or its storage route is disabled, although no source bytes are opened.

This is an unsupported denial, not a request to weaken source authorization. `docs/acceptance/b/remaining-preflight/b06-preflight.md:85` distinguishes stored historical transcript visibility from historical file-byte access. Task 3 original/R1 keeps original epoch mandatory **for bytes**. Frozen JdbcProcessingFactsReader.readStored/files preserves original input/task fences and reads four ContentFile facts (id,meeting_id,sha256,size_bytes) without making current upload epoch/storage route a stored-metadata permission requirement. Its historical root-versus-input evidence does not mean the future saved reader already exists.

Required correction: freeze an operation matrix separating (a) authorized stored history/body/marks/provenance inspection, (b) original-file download, and (c) save/restore/confirm/new business authority. Stored inspection still needs fresh owner EDIT, exact same-meeting supported RAW/WORKING identity, complete bounded stored graph and original metadata/profile/producer integrity. It must not require a current byte-open grant solely to return retained DB text. Expose source-byte eligibility separately; fail the explicit source route on B04 epoch/route ineligibility. This does not authorize restore/confirmation/current-member source access, CANDIDATE support or any old upload bytes. Keep each operation's adopted predicates explicit. Corrupt/missing provenance or required archives are not excused by this correction.

Prospective closure evidence: owner can inspect exact retained historical text and all marks after a byte-only epoch/route eligibility change; the paired explicit file-byte route fails with zero opens. Member/by-ID/outsider/admin-only negatives remain. Distinguish stored-graph corruption from byte unavailability and preserve original input/config. Include published immutable owner history under fresh B03 permission. These tests have not run.

## T6DR-02 — P2 — Freeze transaction phases and measurement-to-transfer stability

Affected design: section 4 steps 1–6, especially lines 96–100, and section 6 final mutation sequence.

The ingredients are sound, but section 4 combines a pre-gate outcome, a “Final one-gate” acquisition, a released read transaction, and a later “final independent gate” in one sequence. It does not clearly assign initial body/manifest transfer and producer preparation to each phase for each read/command. The implementer must choose whether the first gate is the detached snapshot transaction, whether a DB-only read needs another gate, and how the final mutation consumes its already acquired gate. This is an implementation-readiness gap, not a claim that two independent transactions violate the one-gate rule.

Lengths/PK counts are promised before transfer and equality afterward, but the SQL/holder condition making measured lengths valid through the content SELECT is not frozen. With the actual MySQL JDBC dependency, getCharacterStream alone is not proof that oversized text was never transferred/buffered before application checks. Frozen JdbcProcessingFactsReader.manifestSize/manifest explicitly measures FOR UPDATE and retains that row lock through transfer. TransactionRunner selects no special isolation level. V011 has revision/segment parent FKs and indexed child keys that can support stable acquisition without DDL; the new reader must specify its proof.

Required correction: write separate short sequences for detached snapshot acquisition, archive-dependent preparation plus final mutation/readiness, DB-only content reads, and Task 3 source opening. Each transaction gets one genuine gate; source opening preserves Task 3's single final gate/private postcommit pin. Archive I/O, parsing and canonical/producer/body hashing occur outside retained roots. Reuse the final gate within its transaction; do not add a third authorization transaction merely to rename an already authorized read.

Every LONGTEXT path must reserve bounded metadata/lengths before any content-bearing SELECT and retain actual row/child locks plus complete-set protection through transfer, or use a same-query server-side size gate that cannot send newly oversized content. Cover inserted children and zero-mark membership explicitly. Locked FK parents with mandatory writer discipline may be the proof; an appropriate indexed locking range is another option. Do not assume gap locks at an unspecified isolation level. Final exact comparison uses the same bounded discipline, not an unbounded second read. Post-transfer mismatch detects failure but is not the pre-transfer bound.

On root adoption, carry R2's provider-independent minimum authorization/error precedence into source-read alignment. Missing/legacy/ambiguous support requires no fabricated provider or extra gate.

Prospective closure evidence: actual-JDBC SQL instrumentation demonstrates metadata-only pre-transfer gates, no oversized body returned to the driver, retained measurement/transfer protection, explicit empty marks and one gate per transaction. Separate traces prove snapshot commit → outside-root archives/hash → final exact recheck, and zero source opens after failed finalization/commit. No such evidence was executed here.

## Accepted design decisions and regression constraints

- One Task 3 private issuer with exact DataSource/actual holder/B03/B04 gate checks is appropriate. Detached observations remain data. Complete parent/child/input/batch/profile/source/task sets are declared before sources; all tasks precede any attempt. The child/confirmation rank extension must align with adopted source, not a concurrent patch.
- RAW never substitutes for a current save. WORKING preserves fixed segment identities/order/source paragraph/null speaker/time/input/batch/fingerprint, complete marks and genuine RAW ancestry; human producer is null. Body/mark edits and restoration produce immutable versions, and restoration copies no confirmation credit. The supported edit matrix is explicit.
- hashDraft can avoid fake confirmation rows by extracting confirmation-independent revision/member validation, preserving per-segment validation, and keeping CatalogFact/TranscriptVersion construction tied to real confirmations. Existing projection fields/order/marks/profile and confirmed hash bytes remain unchanged. Resolver.open, original processing/config/profile binding, exact Artifact equality, minimum canonical budget and operation.finish remain mandatory. Pure facts do not prove DB completeness, ACL, upload authority or actual producer success.
- Replay-first committed lookup, bounded concurrent-winner recovery and final serialized replay before new-write guards match Task 4. Replay construction remains independent of the unavailable-new-command branch. Immutable historical result identity and fresh observation are separated. No accepted-version column or receipt-only DDL is invented.
- Confirmation unique identity, same-ID/no-version unchanged acknowledgment, explicit cleared-pointer reattachment with fresh increment, and obsolete replay without reattachment are coherent. Current WORKING, fresh CAS/readiness and exact graph/hash consistency remain mandatory; no dummy task. Exhaustion applies only when an increment is needed.
- Current/member and owner/history routes use distinct safe projections. UUID possession and admin role grant no extra permission. No-store, safe errors and opaque lossless revision tokens are explicit; T6DR-01 must preserve these boundaries.
- Correction readiness is a qualified disabled/not-installed component. Unknown or installed-without-reader is unavailable; no synthetic PENDING count or missing-table fallback. Actual qualification/configuration remains a deployment gate.
- Task 10 disposition and Task 11 publication freeze are explicit activation gates on Task 6 version writes, not installed no-op helpers. Historical acknowledgment remains a read, distinct from a new state transition.

## Disposition and remaining dispatch gates

Return for a narrow design revision closing T6DR-01/T6DR-02, followed by independent review of closure and introduced regressions. Preserve the reviewed input. These findings request no new product approval or schema allocation.

Even after closure, Task 6 source dispatch requires frozen/adopted Task 5 output kind, successful RAW producer proof and RAW.revision = immutable producer TaskSpec.businessVersion allocation/evidence. WORKING uses r+1 with history≤r/unused target verified under root. Root must pin positive effective budgets, correction qualification and final shared issuer/rank APIs. Task 10/11 integrations gate their later runtime activation. Existing hash/graph/config/meeting/source regressions and the full prospective matrix remain required future implementation evidence, not passed tests.