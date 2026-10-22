# B07 S1 fix1 consumer addendum

This supersedes only the affected API/accounting paragraphs in the historical [consumer handbook](../b07-pure-s1/consumer-handbook.md). The original remains byte-identical. Candidate correction of F1/F2/F3; independent review and coordinator adoption are still required. No runtime capability is enabled.

`Artifact(String version, String sha256, ArchiveBytes bytes)` replaces the new pure API's previous raw-array constructor. `Artifact.bytes()` now returns the same immutable, size-inspectable `ArchiveBytes`, rather than a cloned array. No legacy raw-array overload remains. `ArchiveBytes` has no public unchecked constructor; its only raw capture checks maximum and remaining cumulative capacity before cloning, and checks cancellation before/after capture. Arrays must not be mutated concurrently during capture. Later caller mutation cannot change an existing capture.

For alternate detached Artifact facts, establish the actual acquisition budget first. A shared `ArchiveReadState` is the normal cumulative capture path:

```java
var acquisition = new ArchiveReadState(readBudget, cancelled);
var artifact = new Artifact(version, expectedSha256, acquisition.capture(raw, false));
// Capture subsequent arrays with the SAME acquisition state.
```

Lower-level `ArchiveBytes.capture(raw, maximum, remaining, cancelled)` is available to bounded acquisition code that already maintains remaining capacity. A generous unrelated capacity does not enforce a stricter operation's acquisition policy; consumers must supply the operative limits before their first owned copy. `Artifact` is still an untrusted fact, not proof of raw schema/profile/storage authenticity. Its stated SHA is verified by the consuming index/profile validator.

`ArchiveBytes.size()`, `sha256(cancelled)`, `openStream()` and byte equality inspect/reuse the isolated bytes. S1 consumers do not request array copies. `ConfigBindings.Context.artifact` shares the same original holder used for SHA, strict parsing and canonical-byte equality; it no longer uses `readAllBytes` and a second clone. Context acquisition captures each exact raw archive once and each reached stored processing manifest once. Existing duplicate identity rejection and raw exact-closure checks remain in force.

`GraphBudget.canonical.maxEncodedBytes` also caps cumulative unique detached Artifact bytes across supplied configurations. Exact equal `(version, sha256, content)` facts are charged once, including when reused by several input configurations. Distinct versions/content remain separate acquisitions. Each carrier's size is checked before digest work; cumulative subtraction checks precede charging. The raw `ArchiveReadBudget.maxTotalBytes` remains independently enforced across archives and historical manifests.

Each business compute/verify/review-source/final-source operation now uses one `StoredFactsIndex`, one source-graph session and one raw configuration context. Selected final reviews and nested recomputations share that state. A standalone inspection projection creates its own bounded session and still does not certify raw prerequisites.

The session retains each visited input's complete pinned-set `SourceGraph`. It validates intrinsic confirmation occurrences from every retained graph, every reached input's primary confirmation, each owning raw processing chain, exact archived generation roots and all immutable task fences before successful return. The verifier walks iteratively, then recomputes stored inputs with the shared state; it does not recursively re-enter public stored verification.

`maxOriginNodes` counts unique reached generation-origin input IDs cumulatively within that session. Repeated occurrences remain distinct in projections, while repeated verification/acquisition of one historical input does not reset or consume another node allowance. `maxOriginDepth` bounds the longest path of generation-origin inputs: a root origin has depth 1; each pinned-set origin adds 1. Cached completed inputs retain their entire subtree height, so a reused non-leaf cannot hide deeper descendants. The final owning body input is walked explicitly, even when it has no evidence, retaining the existing final-source prerequisite. A draft's own not-yet-stored input is not a historical origin. Intrinsic confirmations are bounded through aggregate facts/raw-chain accounting and are all verified, but do not count as generation-origin nodes. Active cycles fail `INPUT_INVALID`; over-budget paths/nodes fail `BUDGET_EXCEEDED`; cancellation cannot return a digest.

Profile identities/domain schemas, complete occurrence multiplicity, raw JSON grammar, supported IMPORT/TXT/EMPTY/FULL history and explicit unsupported paths are unchanged. This addendum does not prove authoritative storage completeness, ACL, archive durability, model quality, or runtime workflow acceptance.
