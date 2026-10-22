# B07 pure S1 independent full SPEC and technical-quality review

SPEC compliance: **FAIL — fixes required.**
Technical quality: **Needs fixes — not approved for adoption.**

Reviewed on 2026-10-22 by a fresh independent reviewer, not the author. Three actionable findings: one P1 and two P2. No P0 finding. Passing recorded tests do not close the counterexamples below.

## Exact scope and custody

Owned tree: /home/tanren/ama/.worktrees/phase-b-minutes-pure.
Full range: **8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a..7e052a88fcd03d60523d013839701dd35a7f2b48**.
Included commits: 83f1db9a650ae087d3120fb28171b7a9cf1f5882, cb2dd17c48d5f955b0e6667cf58af51de85759a5, 1281feb9ec20927855fac8994afd45e47e67eb38, 7e052a88fcd03d60523d013839701dd35a7f2b48. This is not a HEAD~1 review.

All **273 paths / 1,660,264 final bytes** were accounted for and byte-hashed against the supplied complete delivery map. The full diff has exactly the same 273 path sections. All **271 SHA256SUMS-listed payloads** match their hashes and the explicit allowlist. The two remaining paths are accounted for by the delivery map. The machine evidence includes one individual record for every path.

The complete diff, complete source supplement and delivery map match the review-package size/SHA values:
- full diff: 61cd77d67553b842e0cd71081f6a8bb1b26dc375331a14c2efdfd99aae5de9c9;
- source supplement: 4231a1c6f30fa599ba9168746e5ccb565d2141c6571ab62cf2ee88747a94fd56;
- delivery map: 506d33a5a7285cc2c4ac2496a2e1f937bee0ec919731f6e985108b6790d12fce.

FULLREAD: all 32 handwritten production Java files, all 13 test suites, all three complete changed POMs, .gitattributes, author report narrative and receipt, complete consumer handbook, entire independent_vectors.py, verification commands, both assertion-method listings, original task brief and review brief. The audit classifies 55 delivered files as FULLREAD and 218 repetitive resources/raw/index files as MACHINE_FULL_BYTES. Every production/test/POM file was read in bounded line-numbered passes; the supplied complete final-source package was used as the scope inventory and checked in full by hash. Full diff path coverage and raw/vector content received machine audit rather than pretending that thousands of hex/log lines were handwritten code. Initial overlarge tool output truncations were repaired by bounded reads of the omitted narrative/spec sections; no missing evidence is being silently treated as passing.

Normative FULLREAD: adopted minutes spec, its exact original proposal eaae0ee7... and overriding fix1 e9a5a7cc..., adopted config-wire spec and complete proposal 427451ba.... Review calibration followed task-reviewer-prompt.md. The original proposal's historical pending/v1 statements were not treated as overriding the adopted v2/fix1 scope. D01 remains unadopted and was not imported.

## Strengths and conforming areas

- Binary tags, big-endian counts, explicit null/list/object distinction, unsigned UTF-8 ordering, scalar rejection, whole-tree preflight and streaming SHA are implemented directly in CanonicalValueCodec.java:31-99. Independent complete vectors cover the required business domains, not just primitive round trips.
- The model reader uses a dedicated Jackson 2 factory, strict UTF-8 Reader, escaped duplicate detection, explicit exact schemas, bounded reads and no caller stream close (StrictMinutesJsonReader.java:20-66, 75-175). Its numeric/date/Unicode/EOF and container boundary tests exercise actual parsing.
- Physical locator identity and saved usage identity are separated; BODY, directory, ACTION suggestion, intrinsic transcript and applied-exclusion routes are explicitly derived from stored shapes (StoredSourceGraph.java:23-80). Unsupported copied local material does fail rather than selecting an attractive candidate.
- Raw archive identity/schema/J-byte/SHA checks are revalidated by ConfigBindings.java:42-53; generation roots and exact profile references are compared at :132-145. Private binding construction, stored task fence agreement, draft generation without an own task/fingerprint, and explicit unavailable future variants are present.
- Review target hashes include whole target content, evidence membership is derived by intersection, and final review membership comes from frozen joins (ResultHashProfiles.java:23-62, 95-125). Coverage does not union partial spans into whole-target support (ReviewCoverageEvaluator.java:13-29).
- All 32 FullRow descriptors' columns, nullable flags, SQL type families and complete primary keys match the already reviewed B00-1.3 actual metadata, allowing only the three explicitly adopted mutable-status projection exceptions. The audit checked the complete 32-table set, not a sample.
- POM changes are limited to versionless managed jackson-core and contracts/core test-only junit-jupiter. Parent/AWS/parser/task APIs and runtime wiring are not changed. The known material capability limitation and storage-authority limitation are accurately documented.

Paths below are relative to the owned tree unless explicitly identified as root SDD evidence.

## Findings

### F1 — P1: recursively inherited intrinsic confirmations escape verification

**Primary location:** backend/core/src/main/java/ama/minutes/contract/OriginChainVerifier.java:11-20.
**Related:** StoredSourceGraph.java:150-160; GenerationFingerprint.java:88-98, 118-120, 132-136.

OriginChainVerifier collects intrinsic transcript confirmations only from the initial graph. The origin walk discovers nested generation inputs through their pinned element sets, but discards those sets' intrinsic confirmation usages. For each discovered input, the verifier invokes recomputeStored, which hashes that input's projection and validates its own confirmation; it does not run the pinned-set OriginChainVerifier branch used by the public stored verify method. Consequently an intrinsic confirmation inside a nested input set can enter the outer hash without recomputing its saved content hash or validating that complete owning chain.

**Confirmed counterexample:** an outer draft's pinned set has a note produced by G2. G2's own fixed confirmation C is valid; G2's pinned old set contains ordinary element evidence intrinsically referring to a second saved revision/confirmation C2. C2 claims an all-zero content_hash although its exact saved body hashes to 256224cda292064218be15d60eff2d93f14d45333543a486e543f97ecf6d6812. G2's stored fingerprint/task consistently include that false nested value. Direct verifyGeneration(G2) rejects FINGERPRINT_MISMATCH, but computing the outer draft succeeds and returns ced3eb2a7cab728493dc194d9efe0fb1e331058261797f65747cd45d00193c78. No raw archive substitution or unsupported material type is required.

Evidence: root SDD task-B-07-pure-s1-review-probe.java, nested(); task-B-07-pure-s1-review-probe.log:4-6. The fixture uses the public projection to construct a matching stored G2 digest deliberately; it is a negative consistency test, not an independent golden-value claim.

This violates fix1 §§3-5 and config-wire §§2,5: every reached stored origin, exact confirmation and full reachable history must validate before digest success. Author claims that all successful business hashes finish full raw-chain validation are too broad for this path.

**Required fix:** walk and validate all occurrence kinds across the entire transitive origin graph with one shared operation state. Retain/check intrinsic confirmation usages from every visited input set, not only root usages and each input's primary confirmation. Make direct stored verification and nested recomputation enforce equivalent prerequisite checks without uncontrolled recursive re-entry. Add a regression where direct G2 verification and outer compute/review/final all reject the same corrupted nested C2.

### F2 — P2: shared completed origins bypass the maximum origin depth

**Primary location:** backend/core/src/main/java/ama/minutes/contract/StoredSourceGraph.java:153-159.

The DFS skips an already completed input at line 156 before checking depth at line 157. Completion records that a node was previously validated, but not the maximum remaining path length of its descendants. A root visited shallowly can therefore be reused from a deeper origin without charging that path depth.

**Confirmed counterexample:** a selected set has two note origins in traversal order G1 then G2; G2 itself pins a set whose usages originate from G1. With maxOriginDepth=1, G1 is completed first, then G2→G1 skips the depth check. A complete raw-backed GenerationFingerprint.compute succeeds, returning 97461ab121d552d36cd8eae1a9f2b6dfeb12a54d6dbd695d52eb00e94c4b448c. The graph has a two-input origin path. This is not merely a projection-only test.

Evidence: root SDD task-B-07-pure-s1-review-probe.java, depth(); task-B-07-pure-s1-review-probe.log:3.

The adopted whole-origin-walk depth budget must bound every reachable path, independent of whether the same node was previously visited through another branch. Simply moving the depth test before the done check covers a reused leaf but still misses a previously cached subtree's descendants.

**Required fix:** memoize validated subtree depth/height or otherwise validate longest reachable path while retaining cycle detection and shared cumulative node accounting. Add shared-DAG tests with both direct and indirect references, including a reused non-leaf subtree, and require the same failure under any permissible supplied ordering.

### F3 — P2: public Artifact copies raw arrays before any caller budget

**Primary location:** backend/contracts/src/main/java/ama/contract/MinutesContracts.java:270-272.
**Related:** backend/core/src/main/java/ama/minutes/contract/StoredFactsIndex.java:24-29; DomainHash.java:10-13; ConfigBindings.java:119-120.

RawArchive/ArchiveBytes correctly defer the first owned raw-array copy until length and remaining-byte checks, but the parallel public Artifact API still unconditionally clones any caller byte[] in its constructor and clones again in bytes(). Neither constructor nor accessor has a size/remaining-budget argument. StoredFactsIndex and DomainHash call that copying accessor before inspecting its length against their budgets. Therefore a caller-supplied large Artifact creates an uncontrolled defensive copy before the very limit that should refuse it. Passing a tiny GraphBudget/CanonicalBudget does not prevent the pre-check allocation. Context.artifact also constructs readAllBytes plus another clone instead of reusing its already isolated raw storage.

This is a static dataflow finding; no large allocation was attempted. It contradicts the explicit raw-array-before-first-defensive-copy/public-fact requirement in the review brief and config-wire §6, not a speculative product quota request. The existing tests cover RawArchive/ArchiveBytes and list-size preflight, but not this alternative public byte carrier.

**Required fix:** use an immutable, size-inspectable bounded byte holder for Artifact too, or require checked maximum and cumulative capture capacity at construction. Consumers must inspect size before requesting any additional copy, and should reuse the isolated original for SHA/schema/equality work. Add a small or instrumented regression proving the alternate Artifact path cannot copy before the operational bound is established.

## Actual verification performed

No author source, tests, tracked reports, raw evidence, index, HEAD or branch was changed. No broad/ceremonial test suite was rerun. No DB, Docker, service, migration, network, company/model call, or child agent was used.

1. Offline complete-path integrity and independent vector/schema/raw audit:
   - task-B-07-pure-s1-review-audit.py executed once successfully.
   - All 273 paths and 271 payload checks passed.
   - Independently implemented binary encoder reproduced all **18 complete JSON/hex/SHA vector groups**; all adapter hex/SHA copies also matched.
   - Independently implemented strict canonical formatter reproduced all **25 raw config/manifest JSON resources**; all references in the **19-node full archive closure** matched identity and raw SHA.
   - All **32** fixed row descriptors matched the reviewed B00-1.3 actual-metadata oracle's column order, nullability, type family and full PK, with adopted status exceptions. This is an offline oracle comparison, not new DB evidence.
   - Entire contents of **82 raw run logs** were read by the auditor, assertion failures and setup failures independently extracted, and all saved final XML files parsed.
2. One focused Java probe compiled and executed once against the existing owned build classes and saved round-82 classpath/JDK, writing only new root SDD probe/evidence files. It answered the two specifically named code doubts F1/F2 and produced the counterexamples above. It did not run JUnit suites or regenerate author fixtures.
3. Full handwritten implementation/test/document review; inspection of the exact POM and .gitattributes changes. The raw and generated resource audit is explicitly distinguished from human code reading.

Recorded evidence, independently counted:
- Round 82: **13 suites, 97 unique tests, 0 failures, 0 errors, 0 skips**.
- Round 79 canonical runner: **13 tests, all a subset of those 97**, PASS; all eight reactor modules compiled. This is not 110 unique tests and not an integration-test execution.
- **51** distinct observed assertion-failing methods; **48** listed meaningful assertion-RED methods exist in that observed set. Nine setup-error rounds are exactly **01, 06, 11, 31, 32, 37, 38, 45, 66**, excluded from assertion-RED.
- Fixture/tooling failures in 68/71/72/75/80 were retained rather than reclassified as product RED. Their presence does not close or create F1-F3.
- Round 82 has no reported warning; round 79 contains an existing DeclaredErrorResponse.java deprecated-API compiler note. It is outside the owned changes and is recorded as evidence noise, not attributed to this implementation.

The independent encoder/script and fixtures are genuinely separate from the Java production encoder. The authored independent_vectors.py uses Python literal structures/stdlib and reads adopted raw fixtures, not Java-emitted expected results. The review's own encoder likewise reads only committed JSON vectors to derive expected bytes/SHA. Reproducing a fixture does not prove all semantic graph cases, as F1/F2 demonstrate.

## Named outside-diff checks and limits

The only expanded product-data check was the concrete risk of an incorrect FullRow whitelist/type/nullability/PK projection: compared the 32 descriptors against docs/acceptance/b/database-generation-addendum-metadata-evidence.json in the integration tree. That already reviewed actual-metadata oracle was read offline. No other feature crawl or live-schema discovery was performed. The local task-reviewer-prompt and review-package files were process inputs, not additional implementation scope.

I did not claim storage/index authenticity, authoritative DB read completeness, immutable archive publication/durability, ACL, model quality, transactions/CAS/leases, or real runtime parser capability from DTOs or green pure tests. Those remain B06/S2/S3/S4/S5/B08/ops obligations. D01's material parser mapping and durable copied-material provenance are still prerequisites for required future capabilities. Current IMPORT/TXT/EMPTY and FULL confirmed-transcript scope is a documented adopted limitation, not a missing pure-S1 runtime implementation. The user's now-confirmed guarded first-save and source-review UX are outside this review; no UI/runtime gate is demanded here.

## Review evidence custody

All files below are NEW root SDD evidence under /home/tanren/ama/.worktrees/phase-a/.superpowers/sdd/2026-10-21-ama-phase-b/:

- task-B-07-pure-s1-review-audit.py — SHA256 b6a62ee098c3721a0b72c5242c6f4c4f5e1a56d0231da71ed5a959d4efbc88f4
- task-B-07-pure-s1-review-audit.json — SHA256 50561ce2a5a51617c3fcc8d6a0c23475766f3f9a7ed65c879c460f5a34aeda82
- task-B-07-pure-s1-review-probe.java — SHA256 4dc8d1bb7575b201ce73017efd450417eb26cc9b791b11a3a87f3cf9132b5a63
- task-B-07-pure-s1-review-probe.log — SHA256 2be3201cdc7002e9d0d4d3ee69559227593bd7f9548aa0b4679ef014690a43e3
- task-B-07-pure-s1-review-probe-classes/ — compiler output for the focused probe only.

The report's SHA is returned separately to avoid self-reference. Adoption should wait for fixes and appropriately scoped fresh review of F1-F3 and their interacting graph/budget paths.

