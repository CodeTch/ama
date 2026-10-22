# B07 pure S1 fix1 correction report

2026-10-23. This is the explicitly assigned continuation author because the original author session could not be restored. Scope: the accepted independent findings F1/P1, F2/P2 and F3/P2 only, plus necessary tests, consumer API documentation and new evidence. Status: implemented and locally verified candidate; **not independently adopted**.

Owned tree `/home/tanren/ama/.worktrees/phase-b-minutes-pure`; branch `codex/phase-b-minutes-pure`. Correction BASE is exactly `7e052a88fcd03d60523d013839701dd35a7f2b48`. The review range is **`7e052a88fcd03d60523d013839701dd35a7f2b48..HEAD`**, where HEAD is the single final local correction commit containing this report. Its final commit identity is sealed in the root SDD copy/final handoff, avoiding a self-referential commit SHA. No peer/main merge, rebase, cherry-pick or implementation import occurred. The coordination branch was not edited.

The only requirements input was the supplied fix1 brief, SHA `45d3e6a522a87e95b5deefef1962533448ff3803d5c62d82c0415aa695f41507`. Read the complete independent review (`08abe8f930185d114d06eaf96debdc2f3fd1c308c000c060320ba0cc1294d186`), its probe Java/log and audit script; parsed/read the complete audit JSON as machine evidence. Normative inputs were the adopted minutes contract and exact original proposal plus overriding fix1, and the adopted config-wire contract plus exact original proposal. Initial tool truncations of proposal output were repaired with bounded reads. Systematic debugging and TDD were applied; no new subagent was started by this continuation author.

## Root causes and corrections

F1: the original walker retained catalogs but discarded nested pinned-set usages. The verifier collected intrinsic confirmations only from the initial graph; nested stored recomputation omitted that prerequisite. The new operation session retains each reached input's complete `SourceGraph`. The iterative origin closure collects confirmations from every retained graph, every root graph occurrence and each reached input's primary confirmation. All stored fingerprints, exact raw root bindings, processing chains and immutable task fences are checked before success. Nested recomputation accepts the same index/session instead of recursively reopening independent source walks.

Business generation compute/verify, review sources and final sources now share one `StoredFactsIndex`/session per operation, including selected final reviews and nested confirmation hashing. The existing raw `ConfigBindings.Context` remains shared across those checks. Confirmation and generation helper internals take that same index. Hash-budget changes for a nested generation rowset are restored afterward; a historical input's budget cannot leak into its caller. Direct stored G2 verification and outer draft/review/final routes now reject the same corrupted intrinsic C2 with `FINGERPRINT_MISMATCH`. The valid matching chain succeeds through all four routes.

F2: completed-node membership did not express how deep a cached subtree remained. The walker now memoizes each validated input's subtree height and checks `depth + height - 1` through subtraction-safe bounds whenever a completed input is reused. It retains active-cycle detection, an iterative stack, cancellation and a cumulative unique-input visited set for the operation. Height extension uses `long` before checking/casting; permissive traversal order cannot hide a reused leaf or non-leaf subtree. Tests cover both origin orders, a diamond, exact/one-over depth, exact/one-over unique-node budget, a nine-input raw-backed chain, a raw-backed cycle and cancellation.

F3: the public `Artifact` raw-array constructor cloned before any operation budget, its accessor cloned again, and `Context.artifact` allocated `readAllBytes` plus another clone. `Artifact` now accepts and returns immutable `ArchiveBytes`; the raw-array constructor/accessor path is removed. The holder can only be initially captured through checked `maximum`/`remaining` capacity and cancellation. Detached callers must use the operative acquisition limits, normally one shared `ArchiveReadState` for all captures. `Context.artifact` reuses its original bounded holder, and profile/index consumers inspect size and hash that same holder without copying. Parsing/canonical-byte equality already use the same Context-owned original.

The index's cumulative artifact-byte cap charges exact equal `(version, SHA, content)` acquisitions once across configurations and checks each carrier's size before hashing. The raw archive context separately enforces cumulative archive/manifest capacity and captures the shared processing manifest only once. Different content/versions remain separate charges. Small tests cover refused maximum/remaining capture, exact capacity, source-mutation isolation, holder reuse, cancellation, detached whole-snapshot overflow, raw whole-operation exact/one-less budget and complete business domains. **No large-allocation observation was attempted.** The F3 baseline is a static dataflow finding with an honestly recorded old-API compile/setup failure; it is not claimed as a runtime allocation RED.

## API and consumer impact

Public change: `Artifact(String, String, byte[])` becomes `Artifact(String, String, ConfigArchiveContracts.ArchiveBytes)` and `bytes()` returns that immutable holder. No legacy bypass overload remains. `ArchiveBytes` adds content equality/hash helpers without exposing its array. All actual source callsites were searched and updated; there is no external runtime consumer in this task. Package-private nested recomputation/confirmation helpers now accept the shared index. Public business entrypoint signatures, profile/domain identities, serialized schema and source occurrence multiplicity are unchanged.

The new [consumer addendum](../../../artifacts/phase-b/minutes-pure-s1-fix1/consumer-handbook.md) gives the exact new capture pattern, cumulative byte accounting, unique generation-origin node accounting, longest-path depth, direct/nested/final semantics and the distinction between inspection projections and raw-validated business success. The historical handbook remains exact, including its original historical UX status. The user's guarded first-save/source-review UX is outside this pure correction.

## Actual verification and evidence

All new evidence is under `artifacts/phase-b/minutes-pure-s1-fix1/`. [Actual commands](../../../artifacts/phase-b/minutes-pure-s1-fix1/verification-commands.md) lists each selector, exit and raw log. Runs 01–07 used the pinned private runtime without archiving its environment/classpath file or any credentials. Reproduction uses the workspace Maven wrapper with nonsecret JDK path `/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64`; Maven home `/home/tanren/.m2/wrapper/dists/apache-maven-3.10.0-bin/9e1da7b8/apache-maven-3.10.0`; Python `python3`, Node unused. Raw 08 records Zulu 21.0.12.1 / Maven 3.10.0.

| Evidence | Actual result |
|---|---|
| 01 graph baseline | Exit 1, 8 tests, **5 assertion failures**, 0 errors/skips. Production was still exact BASE. |
| 02 Artifact old-API setup | Exit 1, compile error: `ArchiveBytes cannot be converted to byte[]`; no test execution, not RED. |
| 03 first combined attempt | Exit 1; contracts 4 passed, core test compile failed because the existing package-private recomputeStored test still used the old signature. Adapter tests did not run. Log filename contains “green” but this run is a setup failure. |
| 04 focused GREEN | Exit 0, 3 suites / 20 tests; 0 failures/errors/skips. |
| 05 expanded focused GREEN | Exit 0, 3 suites / 24 tests; 0 failures/errors/skips. |
| 06 complete owned selector | Exit 0, **13 suites / 112 unique tests = original 97 + 15 new**, 0 failures/errors/skips. |
| 07 canonical entrypoint | Exit 0, 25 tests, all a subset of the 112; all eight reactor modules compiled. No integration test was executed. |
| 09 evidence-audit setup | Exit 1; audit initially compared intentionally changed `.gitattributes` against the old current-worktree checksum. Corrected to verify its historical BASE blob and classify intended source changes; not product RED. Original failure log retained. |
| 10 read-only evidence audit | Exit 0; all 273 historical BASE paths and 271 listed payloads match Git; 18 independently encoded complete vector groups match bytes/SHA; original 97 are a subset of final 112; canonical 25 are a subset. |
| 11 final audit | Exit 0 after final custody byte-preservation attributes; same counts, 16 final source/attribute hashes, 22 archived XML hashes. |
| 12 RED source custody | Exit 0; two exact test source snapshots reconstructed from BASE plus the original test-only prefix and verified against SHA captured immediately after run 01. Reconstruction is disclosed; these copies were not claimed to have been archived before the run. |

The five actual assertion-RED methods were:

- `fix1OuterDraftRejectsCorruptedNestedIntrinsicConfirmation`
- `fix1ReviewRejectsCorruptedNestedIntrinsicConfirmation`
- `fix1FinalRejectsCorruptedNestedIntrinsicConfirmation`
- `fix1SharedLeafDepthUsesEveryPathInEitherOriginOrdering`
- `fix1ReusedNonleafDiamondDepthUsesCachedSubtreeHeight`

Each failed because the expected safe `Failure` was not thrown, not because of setup/fixture/compile errors. The direct-G2 baseline assertion already passed and is not counted as newly demonstrated RED. The valid-chain and cumulative-node assertions in the baseline likewise passed and are not claimed as RED. The additional four adapter methods and three contract/core methods expand regression coverage; they are not misreported as additional pre-change assertion failures.

`junit/` contains 22 XML files copied before subsequent runners overwrote live reports. [xml-hashes.json](../../../artifacts/phase-b/minutes-pure-s1-fix1/xml-hashes.json) records every archived XML SHA. [final-source-hashes.json](../../../artifacts/phase-b/minutes-pure-s1-fix1/final-source-hashes.json) records all 16 changed source/attribute paths. The initial two graph test sources have their separate `01-graph-red-source.sha256`. Per-run JSON summaries preserve exact method names and counts, and `audit.json` records the old/new testcase set comparison. Focused reruns and canonical overlap do not inflate the 112 unique count. No warning or deprecated-API note occurred in rounds 01–07.

`red-source/` additionally preserves the exact two run-01 test source forms, verified by the pre-correction captured SHA; raw 12 explains their later reconstruction. Final `git diff --cached --check` exited 0 without output; the explicit allowlist seal verification exited 0 with every staged payload SHA/path accounted for. The exact final path/payload counts are machine-recorded in `delivery-paths.txt` and `payload-allowlist.txt` rather than estimated from source-only diff summaries.

The original vector writer was intentionally not rerun because it writes historical fixture paths. The new audit is read-only toward those files and independently encodes all 18 committed JSON vector groups with Python stdlib, without using the Java production encoder. Existing complete-domain golden assertions remain in the 97-test subset. The graph counterexample fixture uses production projection only to construct a deliberately self-consistent saved invalid fingerprint; it is a negative consistency fixture, not an independent golden-value claim.

## Changed paths and custody

The correction changes these existing source/attribute paths; the new helper is also listed:

- `.gitattributes` — new evidence-only `-text` / raw-XML whitespace rules; original rules retained.
- `backend/contracts/src/main/java/ama/contract/ConfigArchiveContracts.java`
- `backend/contracts/src/main/java/ama/contract/MinutesContracts.java`
- `backend/contracts/src/test/java/ama/contract/MinutesFactBudgetTest.java`
- `backend/core/src/main/java/ama/config/ConfigBindings.java`
- `backend/core/src/main/java/ama/minutes/contract/DomainHash.java`
- `backend/core/src/main/java/ama/minutes/contract/GenerationFingerprint.java`
- `backend/core/src/main/java/ama/minutes/contract/OriginChainVerifier.java`
- `backend/core/src/main/java/ama/minutes/contract/ResultHashProfiles.java`
- `backend/core/src/main/java/ama/minutes/contract/StoredFactsIndex.java`
- `backend/core/src/main/java/ama/minutes/contract/StoredSourceGraph.java`
- `backend/core/src/main/java/ama/minutes/contract/TranscriptConfirmationHash.java`
- `backend/core/src/test/java/ama/minutes/contract/GenerationFingerprintTest.java`
- `backend/core/src/test/java/ama/minutes/contract/StoredSourceGraphTest.java`
- `backend/adapters/src/test/java/ama/config/json/RawHashChainTest.java`
- `backend/adapters/src/test/java/ama/config/json/OriginGraphFix1Cases.java` — new counterexample fixture utility, no extra suite.

The other new tracked paths are this report and the explicit evidence payload allowlist/SHA manifest under `artifacts/phase-b/minutes-pure-s1-fix1/`. No POM, SQL, runtime, HTTP, worker, model, parser capability or peer implementation path changed. `delivery-paths.txt` lists the exact full staged correction path set; `payload-allowlist.txt` and `SHA256SUMS` account for the exact new payloads and report. Raw logs/XML/custody have `-text`, preserving raw bytes.

Before edits, `capture.py baseline` verified and hashed the exact **273 paths** of the original full delivery range `8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a..7e052a88fcd03d60523d013839701dd35a7f2b48`. Every original baseline form remains byte-exact in Git. At final audit, 258 of those paths remain byte-identical in the working tree; the 15 intended differences are only Java correction paths plus `.gitattributes`. Of the original 271 SHA-listed payloads, all 271 are verified against their BASE blobs and 256 remain identical now. This distinction is explicit: claiming all old source files remained unchanged would be false. All old artifact/archive/report/handbook/resources and original independent evidence remain byte-identical.

`custody/` contains exact new copies of the original fix brief, independent review, audit Python/JSON and probe Java/log; `custody.json` records source paths, sizes and SHA and audit 10 rechecks both source and copy. In particular the original probe Java SHA remains `4dc8d1bb7575b201ce73017efd450417eb26cc9b791b11a3a87f3cf9132b5a63`, probe log `2be3201cdc7002e9d0d4d3ee69559227593bd7f9548aa0b4679ef014690a43e3`, review `08abe8f930185d114d06eaf96debdc2f3fd1c308c000c060320ba0cc1294d186`. The old author report `docs/acceptance/b/task-B-07-pure-s1-report.md` and every `artifacts/phase-b/b07-pure-s1/` file are preserved, not rewritten to reflect this correction.

## Limits and handoff

No DB, IT, Docker, S3, native/company/model call, real service activation, broad unrelated suite or huge-memory stress was run. The canonical runner's integration module compiled only. These are supplied-fact consistency tests, not storage authenticity, authoritative completeness, ACL, immutable archive publication/durability, model quality, CAS/lease/transaction or runtime adoption evidence. Supported profiles/history and D01 restrictions remain as adopted. External blocking I/O/caller concurrent mutation during initial capture remain acquisition responsibilities.

F3 allocation safety is established by the reviewed static dataflow and bounded immutable-carrier API with small behavior regressions, not a heap-allocation detector. The existing public `ArchiveBytes.capture` still requires consumers to pass their operative maximum/remaining budget; the addendum makes that obligation explicit. No known F1/F2/F3 counterexample remains in the executed cases. A fresh independent review scoped to F1/F2/F3 and introduced breakage is still required, followed by coordinator full read and adoption decision. The continuation author does not self-adopt.
