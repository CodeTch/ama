# Task11 replacement independent source review — R1 — 2026-10-25

Gate-scope SPEC: **Fail**. Quality: **Needs fixes**.

Findings: **Critical 0 / Important 5 / Minor 0**. Independent replacement SEND decision: **DENY** for this frozen candidate, including accepted task 1a8dc6bb-2076-44d2-8ff0-aa1b5f630e27 / PREPARED execution 427e9751-2bca-483b-8d91-cb1711ae127c / operation ccad27fc-5cf1-432b-83f8-a71341efcff5. Acceptance, rollback and provider pure-function evidence do not qualify the subsequent marker/send/late-fact composition.

## Scope and evidence identity

This is the single independent SPEC + quality source gate requested by R1 §§8.1–8.4 and the coordinator's additive gate ruling. It is not a new design review, a whole-branch review, or full Task11 completion review.

Product root P = /home/tanren/ama/.worktrees/phase-b-completion.
Review records W = P/.superpowers/sdd/2026-10-23-ama-phase-b-remaining.
Frozen source root S = W/task-11-replacement-review-source-r1-20261025.
All source path:line citations below refer to the exact S copies unless explicitly labelled focused live dependency.

Fixed BASE: ba48f35e7ef97c1e162a2d373453bd1e9689529c. HEAD for this review is the uncommitted immutable source snapshot, not a commit.

Independently verified by reading and hashing files, without executing product code:
- 74/74 frozen source files match their manifest SHA256; zero mismatch.
- Manifest: b8012cc81f0e8de43f74e3cf6c4107da8f01029f8863c3cda52e02180af47109.
- Scope report: cad2f0aef778ea75903bddb7da5bd2d451c56edaa91a0d61e73132f3d4171cd4.
- Exact 534930-byte diff: 3312f9fe9b262d765ae32acc119d3cbff6ebaebdb8b2f096d9b4d32e4d643421.
- Adopted 102624-byte R1 design: f18ae4734603709284d91345b2909eca9687627c825444fd8cfa06dd49d36ffc.

Inputs read: independent-review brief, author activation brief, plan extract, author replacement scope, coordinator source-gate ruling, verbatim normative excerpts, and focused full-design clauses on original facts/current authority, pretransfer budgets, replay and the §12 matrix. The fixed diff was read in bounded passes. Focused source/context reads resolved original engine observation/control flow and transaction/lock/budget composition; these are itemized in the companion coverage JSON.

No DB/provider/network/test execution, source/index/HEAD/resource mutation, or subagent was used. Only the two specified review records are written. A quoting error in a read-only Python metadata command caused no write or product execution; the corrected read-only hash/line-reference command succeeded.

## Important findings

### I1 — [P1] Historical observation rejects the required old/new spec union

Source: backend/adapters/src/main/java/ama/transcript/PublicationTaskDomainPolicy.java:36; backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java:234, :262, :270; backend/adapters/src/main/java/ama/transcript/JdbcPublicationService.java:57.

Trigger: after same-key replacement, a verified observation or lost-ACK recovery for old no1 enters observeCurrent while the operation's current execution is no2. The engine intentionally supplies both actual full TaskSpecs. They are distinct even for unchanged content: submissionNo participates in the producer digest. prepare rejects any list with more than one distinct spec before a root can be acquired.

Consequence: the original immutable FAILED acknowledgement cannot reach acknowledgeOldFailure, and an authenticated contradictory old SUCCESS cannot reach the existing MANUAL_REQUIRED quarantine branch. The old token does not become valid, which is good, but losing these mandatory fact-only paths is not a safe implementation of R1 §8.4. The boundary test only tries reconstructed-token renew and fake carriers; it does not exercise this observation union.

Minimal remedy: prepare the complete same-publication old/current spec and identity union in one issuer-bound acquisition. Validate each original submission/manifest/final/attempt association separately, retain a distinct strict current-effect selection, and preserve the existing epoch/token rules. Do not narrow the supplied union to one spec or overwrite an old spec with current projection data.

Required focused verification: real same-key replacement followed by old definitive-failure terminal ACK recovery, and an exact authenticated contradictory old success; show original acknowledgement or actual old-operation quarantine with no domain writer and no successor execution rewrite. Include changed logical destination and same-logical/new-submission cases.

### I2 — [P1] Current marker/application does not block a quarantined older operation

Source: backend/adapters/src/main/java/ama/transcript/PublicationTaskDomainPolicy.java:46, :54–57, :60; backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java:105–110, :262–266; backend/adapters/src/main/java/ama/transcript/JdbcPublicationService.java:76–80.

Trigger: a new-final/new-key replacement is accepted while all old rounds are closed. Before its current effect, a genuine late SUCCESS contradicts an old zero-effect failure on the different, older operation. The existing engine can quarantine that old operation. The new worker still prepares from a fresh capture containing that MANUAL_REQUIRED state.

The policy captures the publication-wide rows but never evaluates the old-operation terminal safety predicate for writeMarked or writeCurrent. sources explicitly omits external_operation/external_execution/task fields from its equality comparison. current checks only current meeting r/final/lifecycle/governance/ever_published. The new-key engine branch consequently can create a new marked operation/execution and return initial-send authority; the ordinary current-result path also lacks the older-operation barrier.

Consequence: an already known publication-wide contradiction does not freeze later business effects. A healthy controlled provider's own no-document CAS may reject the request, but that is not a substitute for the required local freeze—especially when the contradiction itself is evidence of a provider violation. R1 §§5 and 8.4 expressly require an older-operation contradiction to block new/current effects while allowing original fact recording.

Minimal remedy: add a private current-effect predicate over the complete already locked publication submission/task/attempt/operation/execution/mapping union at marker and application. Permit only the exact selected current round and admitted immutable closed predecessors; deny older UNKNOWN/MANUAL_REQUIRED/success/unapplied/inconsistent histories. Keep receipt-only preparation available. Do not blindly call requireClosed on the entire graph, since the legitimate current round is itself live.

Required focused verification: new-key acceptance, actual older-operation contradiction, then attempted claim/marker/send/current application. Assert no new admitted POST or current domain effect, preserved newer uncertainty, and real old-operation quarantine. Cover a contradiction between outside preparation and the final locked marker.

### I3 — [P1] Publication LONGTEXT still transfers before protected bounded acquisition

Source: backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java:47–57, :130, :230–233; backend/adapters/src/main/java/ama/transcript/PublicationRecordedLookup.java:10–17.

execution first fetches only OCTET_LENGTH metadata, but its special size predicate applies exclusively to MINUTES_GENERATE_V1. KNOWLEDGE_PUBLISH_V1 falls through to SELECT * from external_execution, including request_manifest and no_effect_evidence, without publication byte/scalar/UTF16 reservation. operation also uses SELECT *, transferring external text even when its mapper ignores it. These helpers run during pre-domain discovery/lookup/renew/observation as well as protected phases.

Separately, PublicationRecordedLookup.captureAuthorized explicitly requires an independent call, performs short authorization transactions that finish, and then reads both execution and submission LONGTEXT in an ordinary nonlocking JOIN. Its exact length predicates and shared-ledger reservations are useful, but no root/parent/row X locks are retained over measurement and transfer. Later fresh authorization does not retroactively satisfy the required protected transfer.

Consequence: the new publication consumer bypasses the established pretransfer boundary. Oversized/corrupt publication external rows can be fetched before rejection; a post-transfer equality/length predicate does not establish the R1 §3 locked metadata/transfer invariant. A good bounded acceptance writer is not evidence that every historical/corrupt row is safe to fetch.

Minimal remedy: make preliminary external discovery genuinely metadata-only. At the publication-specific protected phase, acquire the complete declared root/parent/task/operation/execution union, reserve exact field byte/scalar/UTF16/copy budgets, and transfer only protected exact PKs. Carry detached immutable data outside roots for parsing/hashing/provider work. Keep generic nonpublication contracts unchanged; do not merely expand the existing one-dimensional 64KiB post-query guard.

Required focused verification: SQL/row trace for replacement original-receipt capture, worker PREPARED adoption, renewal and historical observation; oversized manifest/evidence/URL/result plus concurrent metadata/content change must abort before any disallowed LONGTEXT transfer. No provider call under locks.

### I4 — [P2] The effective cumulative ledger does not cover the complete replacement operation

Source: backend/adapters/src/main/java/ama/transcript/PublicationGraph.java:11–24; backend/adapters/src/main/java/ama/transcript/JdbcPublicationReplacement.java:24–33; backend/adapters/src/main/java/ama/transcript/ControlledKnowledgeClient.java:31, :48–53; backend/adapters/src/main/java/ama/transcript/JdbcPublicationService.java:109–121.
Focused live dependencies: backend/adapters/src/main/java/ama/transcript/MinutesPendingDiscovery.java:53; backend/core/src/main/java/ama/transcript/TranscriptAcquisitionBudget.java:15–43.

PublicationGraph.discover/lock/lockReplacement do not receive a Ledger and retain multiple full metadata inventories, comparisons, normalized maps and copies. reserve checks fixed per-table 256 ceilings rather than consuming the effective shared fact/edge/byte/scalar/UTF16/copy allowance. The helper performs independent LIMIT 257 queries, including durable_task ordered by id, rather than the adopted metadata history keyset acquisition. A later charge of the separate MinutesGraph declaration does not charge these additional publication inventories.

The proof loop also calls replacementProof/exchange and parses/verifies each bounded 64KiB provider response without passing the operation ledger. Those retained receipt/proof objects, canonical copies and response bytes accumulate across predecessors. render and transport create further copies outside the shared reservation path. Per-call caps and a finite maximum number of rows do not enforce the smaller effective aggregate minima.

Consequence: a valid large or deliberately low-budget history can cross the configured cumulative budget while every individual helper remains below its local hard cap. This is an implementation defect, not merely an unrun large-history test or an author risk note.

Minimal remedy: thread one effective cumulative ledger through declaration, protected reads, provider responses, parsing/canonicalization/render/transport copies and bounded readback, charging before allocation/transfer. Intersect all actual configured minima and remaining+1 limits; use the prescribed whole-history keyset discovery before filtering and retain canonical task-lock order. Do not create a fresh ledger per predecessor/page.

Required focused verification: small configured effective limits and multi-predecessor histories that individually fit but cumulatively exceed fact/edge/text/copy/response budgets; SQL and transport traces prove early fail-closed with no marker/send. Count repeated S/F/lookup acquisitions, not only unique rows.

### I5 — [P2] Optional bindingTransition is not semantically bound by the application readers

Source: backend/adapters/src/main/java/ama/transcript/PublicationRecordedLookup.java:18, :25; backend/adapters/src/main/java/ama/transcript/JdbcPublicationService.java:79–84; backend/adapters/src/main/java/ama/transcript/JdbcPublicationReplacement.java:19, :31–33.
Provider mitigation: tools/providers/controlled_knowledge.py:validate_publication_binding and transition_generation.

The historical reader removes bindingTransition from its key-set check, then recomputes the execution digest only from top-level priorProofs. It does not validate the transition schema, expectedGeneration, sorted exact predecessor set, newBusinessKey/newLogicalRequestHash, or equality with top-level priorProofs. The worker reader is weaker: it checks the derived TaskSpec, pinned identities and rendered payload hash, then transports the optional field directly from the stored manifest. Replacement validateManifests likewise does not provide that missing common semantic validation for every original binding.

Trigger: an incomplete, unsupported or inconsistent persisted publication binding (for example nonempty priorProofs with missing/altered transition, divergent transition priorProofs, or mismatched new identity/generation) reaches a reader/worker. Canonical JSON equality does not reject these combinations, and the transition's slot expectation is not included in the execution digest recomputation.

Consequence: the application can accept an incomplete original request identity for receipt processing or commit a marker for a request that only the remote side then rejects. The controlled provider does revalidate the transported transition and exact execution digest and independently compares all real receipts; I did NOT find or claim that this omission bypasses its sole-document CAS. It still violates the required application original-binding and pre-marker fail-closed contract and leaves the author-declared risk unresolved.

Minimal remedy: one strict immutable manifest/transition validator, shared by replacement original-history validation, recorded lookup and worker preparation. Validate canonical supported shape, original final/task/submission/logical/execution bindings, transition nullability, exact unique sorted predecessor references, equality with top-level priorProofs, expected slot generation from admitted original receipts and new request identity. Perform parsing/hashing outside roots and recheck the exact bytes/fields in the existing locked phase.

Required focused verification: absent/null/wrong-type/extra-field transition, mismatched predecessor list/new key/new logical hash/generation, legacy incomplete manifests, and a valid mixed local-only gap. Reject malformed bindings before marker or fact acceptance, while preserving immutable historical lookup and genuine local gap semantics.

## What is supported by source and inspected evidence

The replacement carrier has a private issuer, actual DataSource transaction holder, genuine minutes owner gate, fixed task-mutation purpose, phase and one-use admission. The engine checks the installed publication policy, carries the complete declared publication specs, and locks/inserts all old plus predeclared new task IDs before the remainder. The new-task inventory exclusion is limited to that exact inserted UUID. No generic public Runnable or fabricated RUNNING task is the replacement authority.

The same-key branch CASes only the current operation request projection/number/state/epoch and appends a real PREPARED execution with no marker fields; the new/no-operation branch returns no external IDs and defers operation creation to the genuine marker. Original submission/execution manifests are not rewritten by replacement. REQUIRED transaction participation plus the unchanged outbox final-flush guard supports rollback of the acceptance transaction. These positives do not cure I1–I5.

The provider's frozen candidate source validates the new logical/execution/payload binding, signed permanent original receipts, exact whole actual predecessor set ordered by ID, no-document slot and expected generation under BEGIN IMMEDIATE. Same-key numbers must exceed the actual provider maximum; unique(key,number) prevents reuse. Local-only unseen rounds are omitted rather than fabricated. Old exact request replay returns its original receipt before fresh destination checking, without new effect. This static assessment is not a live new-source HTTP/SQL-CAS qualification.

Exact evidence inspected:
- publication-replacement-accept-04/result.json; its JUnit XML (SHA 712624c14cb33f637d41b81623a6ba4f8a71a65c562b76eab58cf91a9be2bd15); original-publication-resume/replacement-accepted-no-send.json. One test passed: original proof GET + one destination assertion POST, same-key no2 accepted READY/PREPARED, immutable original comparison and same-command replay, no publication POST.
- publication-replacement-boundary-01/result.json; JUnit XML (SHA 1932abf89c136b93bc1849057ef05b3d8e5f57fc7faa047cd0ba95a6ab240cd4); original-publication-resume/replacement-boundaries-no-send.json. One test passed: five private authority negatives and old reconstructed fence renew rejection, unchanged inventory. It is not a retained genuine initial-send-token test or old observation/replay test.
- publication-replacement-rollback-02/result.json; JUnit XML (SHA ab01cc664fe78cb0ffc065760626a5b639ec013bfe76a80bdbc07fd41129f63a); original-publication-resume/replacement-actual-rollback.json. One test passed: actual JDBC rollback after actual final outbox sequence UPDATE using an authentic sealed no-operation local predecessor. It does not qualify new-final/new-key success or every failure point.
- knowledge-transition-pure-01/result.json and run.log, plus the exact frozen test/provider source. Ten pure transition tests passed with actual Ed25519 verification of a public test vector. No live HTTP authentication or SQLite CAS runtime assertion.
- replacement-original-metadata-diagnostic.json; result.json for replacement-red-01, accept-01/02/03, diagnostic-01 and rollback-01. All six older windows retain their one-failure result. No unsuccessful window was relabelled or rerun here.

All evidence paths above are under P/artifacts/phase-b/b-remaining/task-11. The frozen integration and unit test sources were inspected to establish exactly what they assert; uninspected historical window outputs are not adopted as new passing evidence.

## Remaining qualification boundaries

1. Fix I1–I5 in the same author wave, preserve the accepted no2 and all original immutable facts, and obtain this independent reviewer's incremental source recheck before any replacement SEND. Fresh narrowly targeted negative/transaction evidence should precede the first authorized send; coordinator owns resource windows and activation.
2. The live provider is still PID 231675/port 46287 with loaded source cd80e61d6b338fca1627e2dc403acb15280ce504cebfe1286d62968b643efc13. The frozen on-disk gap source f8b6ec49d177e1a7cb63160fe297e6b98e602b403ba297d2af3c65cf4de047f8 is not loaded. A later authorized restart must preserve original store/keys/port and have ordinary controller custody ACK. Pure validation cannot stand in for that activation.
3. Actual replacement consumption, changed-destination same-key behavior, new-final/new-key business chain, a real local PREPARED gap, both same/different-operation late facts, revoked-authority receipt-only closure and fresh-JVM/combined runtime remain unqualified by this gate's inspected results. Exact zero-effect-retry HTTP facade remains explicitly unfinished. None is counted as already passed.
4. Current-source separation remains a named unresolved qualification: captureReplacement uses currentSources=true, while worker prepareRecorded and PublicationTaskDomainPolicy use capturePublication, which hardcodes false (MinutesGraphCapture.java:25, :71–76). Focused B04 checks show the skipped path checks actual original session/file binding, AVAILABLE state, current cancel epoch and configured historical backend metadata. The replacement command's prior check is not proof that later worker current-effect authority was revalidated. Do not label the old historical capture as qualified current-source authorization; add a demonstrated fresh current-effect check without making original receipt-only closure depend on current source access. This is recorded as a qualification risk, not a separate claimed exploit.
5. No general Task11 completion claim is made for positive local crash repair/fairness, all mutator freezes, official owner/member document relay, broader auth/strict parser matrices, or the rest of R1 §12. Those stay on the later full Task11 increment + total-coverage review. No need to rerun previously sealed unrelated Task7/Task10 source review.
6. Existing original UNKNOWN obligations remain OPEN. No timeout/404/absence or local failure here authorizes no-effect, resend, cleanup, or provider recreation.

The companion coverage JSON preserves the reviewed source identity, focused dependency hashes, observed evidence and unreviewed areas for reuse by this same reviewer.
