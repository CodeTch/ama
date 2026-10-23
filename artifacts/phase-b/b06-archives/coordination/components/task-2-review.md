# B06.2 Task2 independent SPEC and quality review

Date: 2026-10-23. Scope: immutable archive index/source, strict manifest reference discovery, controlled offline publisher, new fixtures and evidence. Reviewer is independent of the author. No source fixes, Java test reruns, DB/Docker access, production activation or adoption were performed.

**SPEC verdict: REQUEST CHANGES. Quality verdict: REQUEST CHANGES.** Three P2 implementation findings and one P3 documentation finding below. No P0/P1 findings. None is waived because the default controlled budgets are larger than the counterexamples.

## Exact review boundary and coverage

- Author tree: `/home/tanren/ama/.worktrees/b06-archive-index`.
- BASE: `ad9960595ab5de6eef7760e2c9094d23666074cb`.
- HEAD: `f58bff56c0e823b1d542d623e8db9b21e011635f`.
- Complete package: root `.superpowers/sdd/task-B-06-archives-prepared-brief/task-2-review-package.diff`, 1,440,808 bytes, SHA256 `078c94a3129c790b0e1d5b110eb3cf4789f0bcd2de4a3c05ac5e74097e881127`.
- Machine-audited all 330 changed paths: all additions; exact allowlist equality; each worktree file's bytes and executable mode match immutable HEAD; status clean. All 329 top-level manifest entries and delivery manifest entries verified. All 228 per-run source snapshot entries verified against their saved snapshot bytes; all 20 final-24 source/resource entries also match current delivery. No existing tracked paths, old S1 files, SQL, old evidence, root attributes or production wiring changed.
- Read complete common/task2/review briefs, approved prepared plan, full author report, proposal sections 4-7, wire adoption and relevant original wire proposal, original S1 handbook and fix1 addendum. Read all seven final new implementation files, the complete 200-line test class, vector/run/seal tools, and relevant unchanged contracts, schema and accounting implementation. Inspected meaningful historical source snapshots and failure logs; repetitive path/hash/XML auditing was machine-driven.

## Findings

### F1 [P2] Publication can succeed for a release the same budget cannot reopen

Location: `backend/adapters/src/main/java/ama/config/file/ControlledArchiveReleaseWriter.java:44`, with predecessor handling at lines 26-31 and source chain accounting at `ControlledConfigArchiveSource.java:19-25,32`.

The writer checks only candidate object bytes plus the new index against `maxReleaseBytes`. The reader additionally charges every predecessor index. Opening the predecessor before publication does not reserve its chain's index bytes against the candidate's budget. Consequently a successful publication is not sufficient to reopen the returned pinned release using the exact budget supplied to `publish`.

Concrete counterexample derived from committed fixture bytes (no Java rerun): use `IndexBudget(4194304,4096,4000,128)` and existing controlled ArchiveReadBudget B. Root.1 has 1,884 object bytes plus a 757-byte index (2,641 total), so it opens. Append processing2: root.2 has 2,900 object bytes and a 989-byte new index, so writer line 44 accepts 3,889 <= 4,000 and can publish. Reader startup requires 2,900 + 989 + 757 = 4,646 and fails BUDGET_EXCEEDED. Both schemas and closures are otherwise the existing positive fixture.

Impact: an explicit offline publish can return a new SHA and leave a complete but unusable release for its configured consumer, contradicting the reported chain-inclusive budget semantics. Preserve the chosen semantics consistently: preflight candidate bytes plus the entire retained index chain before staging/ACK. Required author regression should demonstrate the current incorrect success, then refusal without a new final release at 4,000 and successful publication/reopen at a sufficient boundary budget. Existing tests only publish using the maximum release budget and miss this case.

### F2 [P2] Release capacity is checked after object allocation/copy

Locations: `backend/adapters/src/main/java/ama/config/file/ControlledConfigArchiveSource.java:32` and `ControlledArchiveReleaseWriter.java:34-44`; allocation is inside `ProtectedArchiveFiles.java:57-59`.

Source startup calls `files.read` with a separate ArchiveReadBudget/1MiB capacity, allocates and fills the object array, and only then calls the chain-inclusive release `capacity.reserve(raw.length)`. Thus the operative tighter IndexBudget is not enforced before the first transport allocation. Index parsing's sum-of-objects check does not account for indexes already acquired.

Concrete source counterexample: install the ordinary root.1 fixture and open with `maxReleaseBytes=2500`, otherwise normal budgets. Strict index parsing accepts the 1,884 object-byte sum. After index (757), codec (223), confirmation (419), and parser (226), only 875 release bytes remain. The 1,016-byte processing object is allocated/read before the later reserve rejects it. Publication likewise captures caller bytes under ArchiveReadBudget at line 38 before checking the smaller release capacity at line 44; an initial publish with `maxReleaseBytes=1000` copies the 1,884-byte candidate object set before refusing.

Impact: eventual failure is fail-closed for output but violates the explicit before-allocation/copy resource boundary under legitimate tighter caller budgets. Reserve all applicable capacities before each first allocation/capture (or preflight the complete declared candidate/closure with checked arithmetic). Coordinate this with F1's chain-inclusive total rather than fixing one budget path only. Add precise boundary evidence that refusal precedes object acquisition/copy, not just an `assertThrows` after work has occurred.

### F3 [P2] Nonregular publish lock is opened before its type is checked

Location: `backend/adapters/src/main/java/ama/config/file/ControlledArchiveReleaseWriter.java:22-23`.

The `.publish.lock` path is first opened with CREATE + WRITE + NOFOLLOW_LINKS, then checked for `isRegularFile()`. An already existing FIFO named `.publish.lock` in the protected admin root is not a symlink. On the supported Unix provider, opening it for writing waits for a reader, so execution can block before the regular-file check, `tryLock`, and subsequent cooperative cancellation/deadline checks.

This needs no hostile privileged race: a pre-existing invalid administrative lock file suffices. The protected-root ruling permits path-based administrative operations; it does not make a pre-existing FIFO a valid regular lock. Runtime object reading correctly checks regularity before opening, but the publisher lock does not.

Impact: malformed local root state can hang the offline publisher instead of being rejected as unavailable. Check an existing lock's NOFOLLOW type before a potentially blocking open, safely handle the absent/create case, and retain the post-open/held-lock checks under the approved protected-admin assumption. Add a real named-pipe/nonregular-lock rejection test in a bounded child process or other harness that cannot hang the suite. Existing held-file-lock and symlink-lock tests do not exercise this path. This finding is based on final source ordering and Unix FIFO open semantics, not a reproduced reviewer test; no reviewer process was deliberately blocked.

### F4 [P3] The report overstates which public operations install a deadline

Location: `docs/acceptance/b/task-B-06-archives-index-report.md:54`; actual entries are `backend/core/src/main/java/ama/config/ProcessingManifestRefs.java:14-19` and `backend/adapters/src/main/java/ama/config/json/StrictConfigIndexReader.java:17-22`.

The report says "Every public operation wraps cancellation with an overall 30-second monotonic deadline". Standalone manifest discovery and the public strict index reader pass the caller's BooleanSupplier directly and do not install a deadline. Source open/load and writer publish do wrap it. The pure helpers remain byte/token bounded and cancellation-aware, so this is a documentation/API-contract mismatch rather than a demonstrated unbounded parse.

Narrow the report/API guidance to the operations that actually establish a deadline, explain caller responsibility for the standalone pure helpers, or deliberately implement and verify the broader promised contract. Do not imply an OS-level timeout for blocking storage. The approved prepared plan's local cooperative 30-second file-operation boundary is assessed as such; the earlier proposal's separate 5-second connect/read aspiration is not verified by this local adapter.

## What the final implementation and evidence do establish

Index grammar is independently closed and canonical: exact ordered fields, strict UTF8 decoder, duplicate detection, fixed schema, unsigned numeric syntax, type/version/SHA/size validation and unsigned UTF8 version ordering. Immutable snapshots retain index and dependency metadata, not raw arrays or descriptors. Startup follows the pinned chain with continuous generations and exact predecessor mapping inclusion, validates selected release membership and all selected objects including historical roots, and uses shared ArchiveReadState token/scalar/dependency/edge accounting. The finite adopted schema rejects cycle-shaped type/slot substitutions; it is not a general-purpose cyclic schema interpreter.

Per-load closure verifies exact root Refs and metadata dependencies, checks depth/artifact/dependency/edge bounds, and reserves manifest plus complete closure bytes before transport allocation. Each read checks regular type, length, EOF, file identity and SHA. A caller's returned transport array mutation cannot change snapshot metadata or subsequent loads. Publisher isolates appended arrays once and writes those carriers; the real mutation test changes a caller array after staging begins and verifies the validated original is published. These claims do not excuse F2's separate startup/publisher release-capacity path.

Runtime traversal uses held SecureDirectoryStream handles and NOFOLLOW from the filesystem root, and the ZIP-provider test actually exercises unsupported-SDS refusal. Offline mkdir/directory-fsync path operations, stable directory-key checks, sole publication lock, unique staging and held-handle sibling move are assessed under root's protected deployment-owned admin-root ruling. No native helper or hostile-privileged-admin race guarantee is required. Existing tests demonstrate ordinary object/directory symlink replacement rejection, file locking, cancellation with partial staging preserved, old releases remaining readable and physically separate copied object files. They do not demonstrate power-loss or hardware durability.

Failures use the existing code-only Failure class (suppression and writable traces disabled). Existing cancellation checks preserve the interrupt flag; the raw FD test covers cancellation around acquisition handoff. Local cooperative deadlines do not preempt arbitrary kernel I/O. F3 identifies a locally avoidable nonregular-file open before those checks, not a demand for general asynchronous storage cancellation.

## Raw evidence audit

All 12 run directories' command arrays, exits and actual XML counters/testcase counts were read and reconciled. Final coverage is 24 distinct new methods plus 27 existing regression invocations (ConfigBindingsTest 6, ConfigWireReaderTest 6, ArchiveBudgetTest 15), not repeated-run totals.

- red-01: exit 1; 16 tests, 0 assertion failures, 9 entrypoint errors, 0 skips. Not assertion RED.
- red-02: exit 1; one real assertion failure for missing pinned-history behavior. Historical source contains the unavailable stub and XML records AssertDoesNotThrow failure.
- green-attempt-01: exit 1; ambiguous Ref import compiler failure; no test claim. Raw stdout/stderr confirms compilation setup failure.
- green-attempt-02: exit 0; 16/0/0/0.
- red-03: exit 1; one assertion failure because null-predecessor republish unexpectedly succeeded. Its actual source lacks the final release-directory emptiness guard.
- green-03: exit 0; 22/0/0/0.
- red-04: exit 1; one actual FD assertion failure at boundary 5, 71 -> 73 descriptors. Its saved source sets handedOff before the cancellation check; final source moves that assignment after the check.
- green-04: exit 0; 23/0/0/0.
- Three existing regressions: exits 0, 6/6/15 tests respectively, all 0 failures/errors/skips.
- final-24: exit 0; 24 actual named testcases, 0 failures/errors/skips, including unchanged-S1 fixture compatibility. Raw runner log reports executed=24 and BUILD SUCCESS.

Independently checked all literal JSON files are canonical under the independent fixture encoder, each index entry resolves the exact fixture header/SHA/length, both index SHA files match actual bytes, and the independent 2,624-byte typed processing envelope hashes to `989439fb4a84b6f921ec330609a9841c81f5aa019ddb3123b2c8a2b084e0c488`. This is a business typed digest, distinct from manifest raw SHA. No generator mutation or test rerun was used.

## Deferred, parked, and verification limits

F1-F4 are active findings, not parked or silently accepted. No additional deferred repair findings.

Task4's fresh-JVM/database recovery and the root's combined current unit run remain unperformed in this scoped review. The controlled ConfigBindings facts are synthetic consistency evidence, not DB existence, completeness or authorization. Production upload/provider/policy/WorkerLoop/Supplier/API/SQL, B06.1, fairness, HA, company services and power-loss qualification remain outside scope. No Java reruns or new DB access were necessary to establish these source-level findings; the original author should provide precise real RED-to-GREEN evidence and a versioned follow-up delivery, then root should commission the limited re-review before adoption.
