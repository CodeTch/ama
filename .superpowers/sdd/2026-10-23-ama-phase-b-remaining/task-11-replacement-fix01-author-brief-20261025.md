# Task11 replacement source gate fix round 1/5

Approved parent continuation; same original author. This gate review is complete: SPEC Fail / Quality Needs fixes /0Critical/5Important/0Minor /SEND DENY. No additional user approval needed. Read this with the full sealed report task-11-replacement-independent-source-review-r1-20261025.md (d8eea4beeb90ea9470d2f114a425e28d80b6440cdedfb42c87a361eecbf2f595) and its coverage a3a232e267119b0c278eaa652c0c90eab6793db3040ff753292dc4308d3fca21.

FIX_BASE is immutable snapshot task-11-replacement-review-source-r1-20261025.json SHA b8012cc81f0e8de43f74e3cf6c4107da8f01029f8863c3cda52e02180af47109, product HEAD still ba48f35e7ef97c1e162a2d373453bd1e9689529c. Fix source is still uncommitted Task11 work, not a final task commit. Preserve all frozen snapshots/reports/evidence and existing uncommitted Task11 work.

Apply I1–I5 as one minimal connected wave. Do not fix unrelated full Task11 pending features during this gate wave. No second author/reviewer or subagents. Same tree, serial JVM512Mi/2CPU and native64Mi/2CPU; no resource recreation/reset/schema replay/private disclosure. Actual current owned provider231675/46287 remains old loaded cd80e61d source; any required exact bounded source restart uses coordinator custody ACK.

The accepted no2 task1a8dc6bb-2076-44d2-8ff0-aa1b5f630e27 / PREPARED execution427e9751-2bca-483b-8d91-cb1711ae127c / operationccad27fc-5cf1-432b-83f8-a71341efcff5/epoch2 MUST remain unclaimed/unsent through this fix and independent re-review. No worker/combined runtime may accidentally consume it. All original immutable rows and the three historical UNKNOWN obligations remain intact; no fabricated remote state or SQL business-state repair.

Cover actual amended paths with focused RED→GREEN and necessary shared regressions, preserving failed/0test/source limits. The report below specifies required boundary behavior; use real original facts, genuine fixed capabilities, authenticated original receipts and exact transaction/trace evidence. No generic new public capability or fake empty graph/old spec replacement. No full old matrix rerun solely for green. Batch related tests at a frozen source point.

Covering files/lanes: existing Task11ReplacementIT, Task11ReplacementBoundaryIT, Task11ReplacementRollbackIT, Task11RejectedReceiptIT, Task11LostResponseIT, Task11KnowledgeProofTest and provider transition pure tests where relevant; add minimal named source-transfer/budget/manifest/history-observation cases as required by I1–I5. Actual filenames can differ if needed; report exact selector/command/result/source identity and changed-source relevance. Test-loop skips/fixtures/mocks cannot masquerade as actual remote success.

Also keep remaining boundary#4 as an explicit must-close current-effect qualification: fresh current-source recheck before replacement send, distinct from historical receipt-only access. Parent requires a concrete actual-source plan and evidence before first real replacement send. This is not a regrading/new sixth Important; address alongside relevant I2/I3 logic and include its qualification in the fix report and scoped re-review.

Append fix summary to existing task-11-implementation-report-20261025.md and write task-11-replacement-fix01-report-20261025.md. Include I1–I5 change/verification map, command/results/failures, actual traces and what remains unqualified, exact untouched original no2/UNKNOWN status, current-source qualification and shared oldfamily/observe/retry regressions. Then seal new complete source manifest/snapshot plus changed paths against FIX_BASE. Coordinator will generate scoped diff and return to SAME independent reviewer; no SEND before explicit gate approval and required evidence.

The findings below are copied verbatim from the sealed independent report.

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

