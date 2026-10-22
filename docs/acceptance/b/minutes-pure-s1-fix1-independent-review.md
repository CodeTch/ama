# B07 pure S1 fix1 — independent continuation review

2026-10-23. **SPEC: PASS within the assigned F1–F3 correction scope.** **Technical quality: PASS within that scope; no actionable new finding.** F1/P1, F2/P2 and F3/P2 are each **ADDRESSED**. This is an independent review recommendation for coordinator consideration, not self-adoption or runtime qualification.

The original reviewer session could not be restored. I am the explicitly designated independent continuation reviewer, not the original reviewer and not either implementation author. I retained the original finding identities, priorities and requirements. I did not broaden this into another review of the original 273-path delivery.

## Exact range and read scope

Read-only author tree: `/home/tanren/ama/.worktrees/phase-b-minutes-pure`.

Correction range: **`7e052a88fcd03d60523d013839701dd35a7f2b48..13b8f1d16f85787c12626faa41c7e511403f16f9`**. The latter is the inspected HEAD; the author tree was clean at the beginning and end. No author source, test, artifact, report, index, branch or HEAD was changed by this review.

I first read the complete assigned fix brief, original independent review and correction author report. The root SDD author report is byte-identical to the committed report (SHA256 `fda5077e2090469f1954fca0d139957db4cf36b89119917ccc0073b1b0591be4`). I read the adopted minutes/config-wire records and the relevant normative origin, profile recovery, complete-domain, budget and raw-copy provisions. Historical proposed/pending statements were not treated as overriding the adopted records and later confirmed UX decision.

The supplied complete package is **857,980 bytes**, SHA256 **`cefcf050122a844f16bbb05e6832764f7e5aa827f5daaa6fbe8c208f80f9d628`**. Its entire diff is byte-identical to `git diff --no-ext-diff --full-index --binary -U10 BASE HEAD`; all **79** path sections, final sizes and SHA records match the exact committed blobs and current bytes. All **77** payload entries match the explicit allowlist and SHA256SUMS; the two excluded seal files are separately covered by the 79-path package. All **16** final source/attribute hashes and **22** XML hashes match.

FULLREAD: the complete production Java, test Java and `.gitattributes` correction diff; complete final `StoredSourceGraph`, `StoredFactsIndex`, `OriginChainVerifier`, `GenerationFingerprint`, `TranscriptConfirmationHash`, `ResultHashProfiles`, `DomainHash`, `ConfigBindings` and `ConfigArchiveContracts` where needed to follow the changed state/API; the new fixture helper, relevant complete fixture methods, consumer addendum, author report, verification commands, audit/capture/seal scripts. The changed Artifact declaration/constructors/equality/hash implementation was read in full. Initial combined output truncation was repaired by separate bounded production/test diff reads and line-numbered final-source reads; it is not evidence of PASS.

Named outside-diff checks were limited to the concrete shared-state and carrier risks: the complete `ArchivedProfileResolver` and `ArchiveReadState`, `RowsetProjection` hash-budget use, all production/test Artifact construction and byte-consumption callsites, and the relevant existing strict reader/canonical-equality functions. These establish resolver ownership, cumulative acquisition, hash-budget restoration and reuse of the same holder. No broader implementation or feature crawl was performed.

Raw logs, XML, path maps, historical blobs, manifests and repeated archived source/custody copies received **programmatic full-byte audit**, distinct from handwritten-code FULLREAD. The full package diff was machine compared, not represented as a human reading of thousands of raw XML/property/log lines. The author audit was inspected but not executed because it writes author evidence; I used a separate reviewer script that writes only new root SDD evidence.

## F1 — P1: ADDRESSED

Relevant final locations: `backend/core/src/main/java/ama/minutes/contract/StoredSourceGraph.java:20`, `:155`; `OriginChainVerifier.java:9`; `GenerationFingerprint.java:88`, `:115`, `:124`; `TranscriptConfirmationHash.java:48`; `ResultHashProfiles.java:85`, `:144`.

Each operation owns one `StoredFactsIndex` and its `sources` session. The iterative walk retains the complete pinned-set graph for every reached generation input in `inputGraphs`, including inputs whose set is empty. Both GENERATION_INPUT and APPLIED_INPUT dependencies are traversed. The verifier collects intrinsic TranscriptVersion confirmation IDs from the root graph and every retained input graph, and additionally verifies every reached input's primary confirmation. Validation deduplication does not deduplicate the source usages included in the business projection.

For each reached input, the verifier resolves its exact raw roots, compares the bound configuration, checks the historical task fence and recomputes its stored generation fingerprint. Confirmation hashing uses the same index and active resolver/context, validates the owning processing chain/profile, and compares the exact saved content hash. `recomputeStored` no longer opens independent operations or recursively calls public verification. Its temporary nested rowset hash budget is restored with `finally`.

The direct stored, draft, review-source and final-source routes all reach these prerequisites before returning success. Final selected reviews use private bound helpers with the same index/resolver rather than re-entering the public methods with fresh graph budgets. The walk reaches its full immutable-fact closure before the verifier snapshots input IDs; later recomputation uses the same deterministic pinned sets. No unvalidated descendant is introduced by those computations.

The preserved baseline XML/log show that direct G2 verification already rejected the corrupt nested C2, while outer draft/review/final failed their expected-rejection assertions. Final XML records the direct rejection, all three outer rejections, and the valid matching nested chain succeeding through all four business routes. The fixture deliberately builds a self-consistent invalid saved fingerprint with the public projection; it is a negative consistency fixture, not independent golden-value evidence.

## F2 — P2: ADDRESSED

Relevant final location: `backend/core/src/main/java/ama/minutes/contract/StoredSourceGraph.java:155` (especially `:163`–`:176`).

The session memoizes a completed node's complete subtree height. For a reused node at current depth `d`, it checks `height <= maxOriginDepth - d + 1`; thus both reused leaves and reused non-leaf subtrees are bounded. Descendant completion propagates `childHeight + 1` using `long` before checking/casting. The preliminary positive depth bound makes the subtraction safe, including an `Integer.MAX_VALUE` configured depth. This is not the insufficient fix of merely moving a depth check before a completed-node skip.

Active-cycle detection occurs before cache reuse. The stack is iterative; each loop/dependency and graph extraction checks cancellation. `visited` is retained by the operation session, and only a previously uncompleted unique input consumes the cumulative node allowance. Reusing a completed subtree does not consume another node allowance or omit its depth. All facts/configuration and byte budgets remain shared rather than being recreated per origin.

Final regression evidence covers both supplied origin orders, a shared leaf, reused non-leaf/diamond, exact depth/one-over, exact unique-node count/one-over, a raw-backed nine-input chain, a raw-backed cycle and cancellation. The two DAG assertion counterexamples genuinely fail on the old code. Complete raw-backed business entrypoints are used; these are not projection-only depth tests. Static inspection also checked overflow and cached-height propagation, which the small numerical fixtures do not alone prove.

## F3 — P2: ADDRESSED

Relevant final locations: `backend/contracts/src/main/java/ama/contract/MinutesContracts.java:270`; `ConfigArchiveContracts.java:17`; `backend/core/src/main/java/ama/config/ArchiveReadState.java:10`; `ConfigBindings.java:119`; `backend/core/src/main/java/ama/minutes/contract/StoredFactsIndex.java:23`; `DomainHash.java:10`.

The unchecked `Artifact(..., byte[])` and copying byte-array accessor are removed. Artifact accepts and returns the immutable size-inspectable `ArchiveBytes`. Its array-owning constructor is private; the only raw capture checks the supplied maximum and remaining capacity and cancellation before the first defensive clone. A detached acquisition must establish the operative limits, normally by one shared `ArchiveReadState`; the addendum explicitly documents this obligation. The API does not infer a later operation's policy from an unrelated generous earlier capture.

`StoredFactsIndex` and `DomainHash` inspect holder size before their digest work and never request an array copy. The index uses subtraction-safe cumulative accounting for unique equal `(version, SHA, content)` facts; reused historical configurations do not doublecharge one logical acquisition, while distinct version/content does. Raw context acquisition separately counts its raw archives/manifests cumulatively. `Context.artifact` returns the original bounded holder, eliminating its previous `readAllBytes` plus constructor clone. SHA, parsing and canonical-byte comparison use that same isolated holder. Production/test callsite search found no legacy Artifact raw-array constructor/accessor bypass.

Small final tests establish maximum/remaining refusal, exact capacity, cancellation, original-array mutation isolation, same-holder reuse, unique and excess detached cumulative bytes, and raw whole-operation exact/one-less capacity across history. The preserved original complete-domain tests remain in the final 97-test subset, including generation/confirmation/review/final raw bytes and occurrence multiplicity.

F3's pre-copy guarantee is a **static control/dataflow conclusion plus small API/behavior regressions**. No allocation-observing runtime RED or huge-memory test was performed or inferred. Round 02 is an old-API compile/setup failure and is not treated as assertion RED.

## Actual independent audit and recorded verification

No suite was rerun by this reviewer. No DB, integration test, service, Docker, S3, network/model/company request, allocation stress or child agent was used. There was no unresolved concrete question requiring a new Java business probe; I did not rerun the old probe as a ritual.

The separate reviewer audit reads every new raw log and archived XML in full, validates XML counts against testcase/failure/error/skipped elements, checks source/custody/allowlist hashes and compares original/final testcase identity sets. Independently counted results:

| Recorded run | Verified result |
|---|---|
| 01 graph baseline | 8 tests; **5 assertion failures**, 0 errors/skips; BUILD FAILURE |
| 02 Artifact API setup | compilation failure; no executed tests; not RED |
| 03 combined setup | 4 contracts tests passed; core test compilation failed; no adapter tests; not product RED |
| 04 focused | 3 suites / 20 tests; 0 failures/errors/skips |
| 05 expanded | 3 suites / 24 tests; 0 failures/errors/skips |
| 06 complete owned | **13 suites / 112 unique tests**; 0 failures/errors/skips |
| 07 canonical | 25 tests, all a subset of the 112; eight reactor modules compiled; no IT execution |
| 09 evidence audit | retained script AssertionError; setup, not product RED |
| 10/11 custody audits | recorded successful integrity checks; reviewer independently checked the relevant original bytes |
| 12 RED source custody | reconstructed test-only prefix over exact BASE matches the saved run-01 source SHA; later reconstruction remains disclosed |

All five baseline failures are `org.opentest4j.AssertionFailedError` with the safe failure expected but not thrown:

- `fix1OuterDraftRejectsCorruptedNestedIntrinsicConfirmation`
- `fix1ReviewRejectsCorruptedNestedIntrinsicConfirmation`
- `fix1FinalRejectsCorruptedNestedIntrinsicConfirmation`
- `fix1SharedLeafDepthUsesEveryPathInEitherOriginOrdering`
- `fix1ReusedNonleafDiamondDepthUsesCachedSubtreeHeight`

The original **97** testcase identities are a subset of the final **112**, leaving exactly **15** new tests. Focused and canonical reruns do not inflate this count. No warning/deprecation note is recorded in new rounds 01–07. Run 03's filename containing “green” does not change its setup-failure classification. Exact RED production execution is supported by the saved author custody/log evidence; this review did not recreate and rerun historical classes. The reconstructed test sources are not described as copies made before the original run.

The review audit initially assumed diff context `-U80` and failed its package-equality assertion. Read-only comparison identified the actual package's `-U10`; the final audit uses that exact format and passes byte equality. This was reviewer audit setup, not a product failure or a silently ignored mismatch.

## Historical preservation and scope limits

All **273** original BASE forms match their historical Git blobs and baseline map. **258** remain byte-identical in the current author tree; the **15** differences are precisely authorized correction Java paths and `.gitattributes`. All **271** original payloads match BASE; **256** remain identical now. Every old artifact/archive/report/handbook/resource and all six copied original independent custody inputs remain byte-exact. No correction was folded back into the original evidence. The 79-path delta contains no POM, SQL, runtime, HTTP, worker, model, parser capability or supported-history expansion.

**New findings: none (no P0/P1/P2/P3).** No remaining F1–F3 blocker or introduced scoped breakage was identified. This conclusion does not reopen or certify the rest of the original delivery.

This is supplied-fact consistency review only. It does not establish storage/index authenticity or authoritative read completeness, ACL, immutable publication/durability, model or renderer quality, runtime activation, CAS/lease/transaction behavior or broader B07 acceptance. Existing IMPORT/TXT/EMPTY/FULL limits, D01 prerequisites, and the now-confirmed first-save/source-review UX remain outside this correction's activation authority. Caller concurrent mutation during initial capture and external blocking-I/O deadlines remain acquisition responsibilities.

## New reviewer evidence

Only new root SDD files were written under `/home/tanren/ama/.superpowers/sdd/2026-10-23-resume/`:

- `task-B-07-fix1-review-audit.py` — SHA256 `cdb354eb6693ccec7ae2e7cb2fb1abbdcab486fe3504008a581140c97320a01e`
- `task-B-07-fix1-review-audit.json` — SHA256 `399af262e5ca09d9a398e1900546a6ebffb5e2dee74ad08da54a5fa6695c59eb`
- `task-B-07-fix1-review-audit.log` — SHA256 `f7832a33d816021fb008d9e938a4b138b7130da5e1c46dad6b54906e412662f2`
- this independent review report; its SHA is returned separately to avoid self-reference.