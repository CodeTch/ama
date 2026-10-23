# B06.2 Task2 controlled archive source and offline publisher

2026-10-23. Implementer handoff for independent SPEC and quality review; no adoption or full B06 acceptance claimed. Owned branch `codex/b06-archive-index-20261023`, worktree `/home/tanren/ama/.worktrees/b06-archive-index`, exact BASE `ad9960595ab5de6eef7760e2c9094d23666074cb`. Review the complete `BASE..HEAD` of this branch, not `HEAD~1`. The root ignored `task-2-report.md` records the final resolved HEAD after this report is committed.

Task2 now supplies a strict content-addressed index, complete startup validation of canonical original archives and their historical mappings, immutable snapshot metadata, bounded per-load transport acquisition, strict manifest Ref discovery, and explicit offline append publication. Existing S1 wire readers, profiles, RawArchive semantics, and ConfigBindings remain unchanged. The new controlled bundle was accepted by unchanged ConfigBindings using its original manifest and caller-supplied unit-test facts; this is not evidence of database existence or authorization.

## Public API and restart consumer contract

All Ref/RawArchive/ArchiveReadBudget types below are the existing `ama.contract.ConfigArchiveContracts` types. IndexBudget is nested in new `ama.config.ConfigArchiveIndex`.

```java
record ConfigArchiveIndex(long generation, String previousIndexSha256, List<Entry> entries) {
    record Entry(Ref ref, long sizeBytes) {}
    record IndexBudget(long maxIndexBytes, int maxEntries,
                       long maxReleaseBytes, int maxGenerations) {}
    CanonicalValue.ObjectValue wire();
}
interface ConfigArchiveSource {
    Snapshot open(Path protectedAbsoluteRoot, String exactIndexSha,
                  IndexBudget indexBudget, ArchiveReadBudget validationBudget,
                  BooleanSupplier cancel);
    interface Snapshot {
        String indexSha256();
        ConfigArchiveIndex index();
        List<RawArchive> loadClosure(List<Ref> roots, ArchiveReadBudget budget,
                                    long reservedManifestBytes, BooleanSupplier cancel);
    }
}
new ControlledConfigArchiveSource(); // ama.config.file, no arguments
new ControlledArchiveReleaseWriter().publish(
    Path root, String previousIndexSha, List<RawArchive> appended,
    IndexBudget indexBudget, ArchiveReadBudget validationBudget,
    BooleanSupplier cancel); // String exact new index SHA
new ProcessingManifestRefs(new StrictProcessingManifestReader()).roots(
    byte[] originalManifest, ArchiveReadBudget budget, BooleanSupplier cancel); // List<Ref>
new StrictConfigIndexReader().read(byte[] originalIndex, IndexBudget budget,
                                 BooleanSupplier cancel); // ConfigArchiveIndex
```

`open` is an instance interface implemented by ControlledConfigArchiveSource, rather than a static factory on the core interface. This keeps the JDK-only core independent of adapters. The publisher is a separate, unwired library entry point. No service/bean/provider/API/Worker registration was added.

The trust anchor is deployment's explicit SHA under an already existing protected, normalized absolute root. Runtime does not create the root, choose latest, discover defaults, repair history, or substitute versions. A default root Ref for a new draft is separate explicit caller configuration. Old manifests retain their own exact type/version/SHA roots. Snapshot entries and dependencies are immutable; no raw arrays, open descriptors, or parsed archive DTOs are cached. Every load reserves the entire selected closure before its first array and rechecks regular-file size, exact EOF, stable file identity, and raw SHA. Each return owns fresh transport arrays. Pass those arrays directly to unchanged ConfigBindings, which performs its one owned capture and remains final authority for schema, exact operation closure, facts, and business digest.

Consumer sequence: construct a fresh source/strict readers in each JVM; open the pinned index; discover roots from original manifest bytes; load with `reservedManifestBytes=originalManifest.length`; invoke `computeProcessing`/`verifyProcessing` with the same original manifest and real detached facts. Discovery returns references only, cannot mint any ConfigBindings success token, checks canonical/raw schema again, captures at most 65536 bytes once and never exports an ArchiveBytes-to-array roundtrip. JDBC LONGTEXT acquisition and the real facts reader belong to Task3.

## Layout, grammar, history, and budgets

Final release layout is `releases/<indexSha>/index.json` plus `objects/<rawSha>.json` beneath that release. The independent index grammar is exactly `{indexSchema,generation,previousIndexSha256,entries}` and each entry exactly `{artifactType,version,sha256,sizeBytes}`. It rejects noncanonical JSON/whitespace, malformed UTF8/BOM, duplicate or unknown fields, unknown schema/types, invalid unsigned numbers, zero/oversized objects, duplicate identities and wrong ordering. Entries order by artifactType ASCII and then unsigned UTF8 version bytes; the supplementary-plane versus BMP test catches accidental UTF16 ordering. Existing StrictConfigJson's field set was not widened.

`IndexBudget(4194304,4096,67108864,128)` is the controlled maximum. Constructor values may tighten but cannot enlarge those maxima. Index bytes and entries are checked before parser/carrier allocation. Startup retains only selected and rolling previous metadata, not all historical raw indexes; generations must descend continuously to 1, and every older entry must exist unchanged in its successor including type/version/SHA/size. Chain index bytes plus selected raw object bytes share maxReleaseBytes, a deliberately stricter interpretation than checking selected release bytes alone. Selected release directories must contain exactly index.json/objects and exactly the indexed object names; every selected object's complete canonical schema/header/SHA/dependency graph is checked, including roots not selected by a later load. Missing historical indexes, remaps, drops, missing objects, unsafe paths, and unknown definitions fail closed.

Controlled ArchiveReadBudget, in existing positional order, is `(65536,1048576,16,32,8,200000,32,255,65536,262144,1048576,65536,32,250000,500000)`. Startup uses one ArchiveReadState across all selected objects for bytes, tokens, decoded scalars, dependency count and graph edges/depth; no per-node reset. The publisher also validates its complete candidate graph with a shared state after bounded isolation. Its predecessor startup check is a separate prerequisite verification, never a substitute for validating the whole candidate. Per-object transport stays at most 65536 bytes; archive transport plus manifest reservation stays at most 1MiB and respects tighter caller budgets. The later S1 owned capture has its own same 1MiB operation bound; discovery adds at most 64KiB. All are controlled technical limits, not production defaults or product quotas.

Every public operation wraps cancellation with an overall 30-second monotonic deadline, checks interrupts and boundaries, and reports code-only existing Failure values with no payload/path/cause/suppressed trace. The deadline is cooperative for local regular-file work, with checks around each channel operation and bounded chunking. It is not an OS-level timeout for arbitrary blocking storage/network I/O. No network source is supported. SecureDirectoryStream/relative NOFOLLOW traversal starts at the filesystem root; symlinks in any root ancestor, release/object path, or the final file fail closed. Unsupported secure-directory providers actually fail in the ZIP filesystem test. Local file identity is checked before/after acquisition; cancellation restores the original interrupt semantics and closes handles, including the acquisition handoff boundary covered by a real `/proc/self/fd` assertion.

## Offline publication and explicit ruling

`publish(root,null,initialBundle,...)` requires an empty releases directory; it cannot reset an existing release history. For append, pass an exact existing predecessor SHA and only new identities. Duplicate or remapped existing identities, incomplete closures, and unsafe definitions fail before publication. There is no delete/drop argument. A single relative NOFOLLOW `.publish.lock` serializes administrative publishers; held-lock and symlink-lock tests fail closed. The lock file remains under the protected root.

The publisher isolates each input transport array once after per-object and cumulative reservation, validates that immutable carrier, then writes from that same carrier's InputStream. A test mutates the caller array after validation/staging starts and confirms the published bytes remain the validated original. Objects are copied into the new release with CREATE_NEW; no mutable shared hardlinks are used. An assertion verifies old/new objects are distinct files. Each unique `.staging-<UUID>` receives the full candidate objects/index, fsync of files and directories, and a sibling SecureDirectoryStream.move with ATOMIC_MOVE semantics. Existing destinations are rejected while holding the lock. Previous releases are neither overwritten, repaired nor deleted. Cancellation after one real object file is written leaves the old release readable, no new final release, and the unique partial staging evidence intact. There is no staging garbage collection. For an interrupted initial publication, the retained nonempty staging directory also prevents a new initial publication until an administrator separately investigates it; the library does not silently erase it.

Task2 ruling ACK from root: JDK SecureDirectoryStream has no relative mkdir/directory-fsync API. Runtime reads remain fully handle-relative. Offline mkdir/fsync may use protected administrative paths with stable directory identity checks before/after, one publishing lock and unique staging; final sibling publication uses held secure directory handles. This relies on the already specified deployment-owned/protected administrative directory. It does not defend against a malicious privileged administrator racing mutations inside that trusted directory. Unsupported providers, changed directory identities or unconfirmed atomic publication fail unavailable. No native helper was introduced. Cost if that deployment assumption is wrong: replace the platform adapter or publication mechanism, not permit untrusted writable roots. File/directory fsync and atomic rename succeeded on this actual WSL Linux filesystem, but no power-loss, HA, hostile-privileged-race or hardware durability qualification is claimed. A failure after atomic rename but before post-rename fsync/ACK can leave a complete new release with an unavailable result; this is not partial publication or permission to retry with overwrite.

Other rulings: the core source factory is an instance method to preserve module separation; controlled maxima are hard ceilings; maxReleaseBytes also covers rolling chain index acquisition. Costs if wrong are callsite adaptation or stricter-than-required refusal. None introduces production activation or weakens S1 authority.

## Independent controlled resources

`tools/b06_archives_index_vectors.py` imports no application code and emits canonical raw JSON with independent SHA256 and a small independent typed codec for a controlled processing-digest cross-check. Public resources are under `backend/adapters/src/test/resources/ama/config/file/b06/`, with a new local `* -text` attributes file. Original fixture/golden paths are untouched.

| File | Raw bytes |
|---|---:|
| codec.json | 223 |
| parser.json | 226 |
| confirmation.json | 419 |
| processing1.json | 1016 |
| processing2.json | 1016 |
| index1.json | 757 |
| index2.json | 989 |
| manifest1.json | 1791 |

Index1 SHA `c6e3b29cedbdc5234f368fa6d1f7152442c95f56b0caaf52bc8019bafea13e9d`; index2 SHA `d2436e244f966e5841941e282b0f6ad0dc6c76c6369cb90203dab42ed876472d`. The publisher's actual results equal these independent literals. Root.1 is PROCESSING_CONFIG `b06.controlled.import-txt.1`; root.2 appends only `b06.controlled.import-txt.2`, retaining the four original identities. Codec/parser/confirmation raw bytes are unchanged between releases. Controlled processing business SHA `989439fb4a84b6f921ec330609a9841c81f5aa019ddb3123b2c8a2b084e0c488` is the typed TRANSCRIPTION_INPUT envelope, not the raw manifest SHA. The final new test passes these actual acquired four nodes and original manifest into unchanged ConfigBindings and compares that independent business literal.

Task4 can read these committed fixture files by repository path after adoption and construct the existing RawArchive carriers from their exact declared header identities; no test-only implementation API or production default is needed. New offline releases must be published into a new dedicated real root using the actual writer, rather than treating resources or a memory list as persistence proof. These unit facts are synthetic and establish schema/closure compatibility only. Cross-JVM and actual DB recovery are exclusively Task4's uncompleted acceptance here.

## Verification and complete failure history

All commands ran in the owned WSL tree using only the publicly assigned Java21 JAVA_HOME/PATH; no old private environment was sourced. `tools/b06_archives_index_run.py` invokes exact `python3 tools/checks/run.py unit <selector>`, captures separate original stdout/stderr/exit, snapshots changed app/test/resource sources before execution, and saves fresh XML before the next run replaces it. Every row below is retained, including setup/initial errors; names such as green-attempt do not imply PASS.

| Evidence run | Exact selector | Result |
|---|---|---|
| red-01 | ControlledConfigArchiveSourceTest | 16 executed; 0 assertion failures, 9 unavailable-entrypoint errors; initial incomplete stub run, not assertion RED |
| red-02 | ControlledConfigArchiveSourceTest#pinnedHistoryLoadsExactOldClosureWithFreshTransportArrays | 1 assertion failure, 0 errors/skips; missing historical source behavior |
| green-attempt-01 | ControlledConfigArchiveSourceTest | compilation setup failure: ambiguous existing Ref type import; no test claim |
| green-attempt-02 | ControlledConfigArchiveSourceTest | 16 passed, 0 failures/errors/skips |
| red-03 | ControlledConfigArchiveSourceTest#initialPublicationCannotResetAnExistingReleaseHistory | 1 assertion failure, 0 errors/skips: second null-previous publication unexpectedly succeeded |
| green-03 | ControlledConfigArchiveSourceTest | 22 passed, 0 failures/errors/skips after initial-history refusal |
| red-04 | ControlledConfigArchiveSourceTest#cancellationAtDirectoryAcquisitionBoundariesClosesEveryHandle | 1 assertion failure, 0 errors/skips: boundary5 leaked two actual descriptors, 71→73 |
| green-04 | ControlledConfigArchiveSourceTest | 23 passed, 0 failures/errors/skips after ownership handoff cancellation fix |
| regression-bindings | ConfigBindingsTest | 6 passed, 0 failures/errors/skips |
| regression-wire | ConfigWireReaderTest | 6 passed, 0 failures/errors/skips |
| regression-budget | ArchiveBudgetTest | 15 passed, 0 failures/errors/skips |
| final-24 | ControlledConfigArchiveSourceTest | 24 passed, 0 failures/errors/skips, including unchanged-S1 controlled fixture compatibility |

Final distinct coverage is 24 new methods plus 27 existing regression invocations, not the sum of repeated rows. No parameterized invocations or repeated runs are counted as extra independent methods. Every new method is named in final XML. Full current unit verification is intentionally reserved to root once after integration per the activated common brief. No IT, DB, Docker, company service, external send or production run occurred. Initial errors and compile failure are not promoted to RED/PASS. The new tests use real temporary regular files, symlinks, secure directories, locks, file/directory fsync, atomic moves, cancellation and actual descriptors; no mocked I/O, database return substitutions, sleeps or giant allocations.

The final suite covers pinned old closure/fresh arrays, no fallback/missing history, per-load mutation/size/EOF/missing objects, startup validation of unselected history, extra release objects, predecessor remap/drop/generation gap, strict UTF8/closed fields/canonical syntax, index size/order/bounds, cumulative startup bytes/tokens/scalars, whole-closure reservation/depth/artifact limits, symlink replacement, nonregular/unsupported filesystems, cancellation/interrupt diagnostics, literal publication/old-file preservation/no hardlinks, duplicate/incomplete/cancelled publish, strict manifest discovery, initial-history reset, held/symlink publishing locks, real partial staging cancellation, mutable caller isolation, unsigned UTF8 version ordering, invalid header/missing or cycle-shaped references, descriptor closure, and original controlled S1 validation. Cycle-shaped dependency attempts are rejected by the unchanged finite S1 slot/type grammar before they can become an accepted graph; no unsupported cyclic schema was invented to claim a wider interpreter.

## Delivery allowlist, seals, preservation, and remaining gates

Only new paths are changed: core ConfigArchiveSource/ConfigArchiveIndex/ProcessingManifestRefs; adapters StrictConfigIndexReader and config/file ControlledConfigArchiveSource, ControlledArchiveReleaseWriter, ProtectedArchiveFiles; the new config/file test and b06 resources; tools prefixed `b06_archives_index_`; this new report; and `artifacts/phase-b/b06-archives/index/`. There are no modifications to old SQL, fixtures, tests/goldens, handbooks, B06.1 evidence, root .gitattributes, shared core/config behavior, queue fairness, policy/provider/WorkerLoop/API or another owner's tree.

The complete explicit file allowlist is `artifacts/phase-b/b06-archives/index/ALLOWLIST.txt`. `delivery-SHA256SUMS` seals the new current source/test/resources/tools/report; per-run source-SHA256SUMS preserve the earlier actual test inputs; top-level task SHA256SUMS covers all public delivery/evidence paths except itself. A new nested evidence .gitattributes preserves raw bytes. Neither new manifest hashes root .gitattributes. `runs.json` summarizes original XML, and preservation.json records the exact BASE and absence of changes to any existing tracked path. These seal tools exclude private runtime/env material and never enumerate unrelated evidence or private tools.

Root must independently review the exact committed range before adopting. Task4 still owes fresh JVM recovery with real persisted DB metadata, actual history loss/mutation refusals and combined final current units. No HTTP/Worker/upload/model/ASR/material/HA/power-loss/business authorization or full B06 acceptance is claimed by this task.
