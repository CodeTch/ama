# B06.2 Task2 fix1 independent scoped re-review

2026-10-23. Same independent reviewer as the original Task2 review. **SPEC verdict: PASS. Quality verdict: PASS.** Original F1-F4 are closed. New actionable findings: 0 (P0=0, P1=0, P2=0, P3=0). Deferred repair findings: 0. Parked findings: 0. This is a scoped review recommendation; root adoption and later integration gates remain separate.

## Exact scope and immutable evidence

Author tree: `/home/tanren/ama/.worktrees/b06-archive-index`.

- Fix BASE: `f58bff56c0e823b1d542d623e8db9b21e011635f`.
- Fix HEAD: `38a321a5bd11d56b8071dc03965fe413c71bd78e`.
- Original complete task context: `ad9960595ab5de6eef7760e2c9094d23666074cb..38a321a5bd11d56b8071dc03965fe413c71bd78e`.
- Package: root `.superpowers/sdd/task-B-06-archives-prepared-brief/task-2-fix1-review-package.diff`, 734,035 bytes, SHA256 `12301ce347f40d3a117f59b6dccce368a6f7d8cb7cf0ed3ebbf591fef9da9c3c`.
- Independently verified exact 132-path fix allowlist against BASE..HEAD and exact 459-path full-current allowlist against original BASE..HEAD. All 459 current file bytes and executable modes match immutable HEAD. Author worktree is clean.
- All 458 full-current SHA entries and all 29 current-source seal entries verified. Full-current seal file SHA256 is `75218ba711c3f6f3438f922a1ee5bf9c8e2d409f52e17fd5c6a51ecf4748f539`; current-source seal file SHA256 is `db8c0a560905fd2a9ceb46d5c2242162d16765361d958498b9eb8dd99166d710`.
- The only modified existing paths are the three Task2 adapters: ControlledArchiveReleaseWriter, ControlledConfigArchiveSource, ProtectedArchiveFiles. All 327 other original Task2 paths remain byte-identical to f58, including original report, tests, resources, logs and seals. All original 329 historical manifest entries were checked against f58 Git blobs, not incorrectly compared to fixed current adapter sources.
- Verified all 320 historical-plus-fix source snapshot entries against their saved bytes and all 23 final-current-28 entries against current delivery. The RED's three adapter sources equal f58 exactly. All four runs use the same new regression test/helper bytes. GREEN, original-24 regression and final-28 implementation snapshots equal the delivered fixed implementation.

I fully reread the fix report, original complete independent review, review/common/task briefs, and inspected the complete fix diff, new regression test/helper, versioned run/seal tools and relevant unchanged CanonicalJson formatter. Original broader API/schema/fixture review is retained without reopening unrelated scope. No author source edits, test executions, application runtime, DB/Docker access or private environment sourcing occurred during this re-review. Read-only hashing, Git comparisons and XML inspection were used.

## Original finding dispositions

### F1 [P2] Closed: returned releases now obey chain-inclusive admission

`ControlledConfigArchiveSource.java:16-48` introduces package-private VerifiedSnapshot and derives indexChainBytes from the actual capacity consumed by the same authenticated index traversal. It retains only a count plus the existing immutable snapshot, not chain raw arrays or a substitute public trust token. The public open signature still returns Snapshot.

`ControlledArchiveReleaseWriter.java:28-44` consumes that verified count, reserves candidate object sizes against release residual and archive acquisition capacity, then constrains exact candidate index encoding to the remaining release/index capacity. Thus 2,900 object bytes + 989 candidate index + 757 predecessor index must fit before the candidate reload/capture or staging path can run. Subtractions are bounded: verified chain bytes cannot exceed the release limit, and Capacity refuses a size larger than its residual before decrementing it. Generation and entry limits remain enforced.

The unchanged literal counterexample is now a real retained regression. RED recorded the actual root.2 SHA being returned at 4,000 bytes and a same-budget reopen refusal; the test then fails explicitly. GREEN requires BUDGET_EXCEEDED at 4,000, only the old release directory remaining, and exact 4,646-byte publication plus reopen with all five entries. This closes the original publish/open consistency defect rather than merely changing reported limits.

### F2 [P2] Closed: full candidate admission precedes object transport and owned copies

Source startup (`ControlledConfigArchiveSource.java:33-44`) checks every selected declared size and the complete object set against BOTH remaining chain-inclusive release bytes and `min(1MiB, maxTotalBytes)` before entering the object read/capture loop. Its acquisition Capacity is then the admitted total. Existing physical regularity/size/identity/EOF checks still prevent a declaration from permitting a larger actual file. Index acquisition remains independently bounded before its allocation.

Writer admission (`ControlledArchiveReleaseWriter.java:35-44`) first retains bounded RawArchive references and immutable identity/length metadata; it does not clone or decode payload arrays. Duplicate identity, count, nonempty/per-object size, complete aggregate object capacity and exact index sizing all precede `previous.loadClosure` at line 48 and both candidate `state.capture` sites at lines 48/51. The separate previous.verify call necessarily authenticates the old snapshot first and uses the corrected startup admission. This is a prerequisite verification, not candidate reload, and the report correctly does not claim zero predecessor I/O.

The candidate-index length calculation is sound for this grammar: placeholder and real SHA strings are always 64 lowercase ASCII characters, so their canonical encoded widths are identical. Actual identity/version/size/generation/previous-SHA fields participate in the placeholder encoding, which uses the existing bounded CanonicalJson formatter. After bounded capture and full graph validation, actual index encoding is limited to and required to equal that preflight length (line 57). Placeholders never become accepted object evidence or persisted index content. No alternate production encoder or ArchiveBytes-to-array copy was introduced. Caller byte-array length is stable; later caller payload mutation cannot change captured carriers, and the unchanged original isolation regression remains in the final suite.

Evidence is appropriately limited and combined with source ordering:

- Startup RED shows the ordinary codec file's atime moved from the set 1970 value on the old 2,500-byte refusal. GREEN checks all four atimes unchanged on refusal, then opens at exact 2,641. This establishes the observed local acquisition distinction, not portable noatime behavior.
- Writer RED returned INVALID_JSON for a malformed first payload whose declared candidate object lengths already exceed a 1,000-byte release budget. GREEN returns BUDGET_EXCEEDED before staging. The assertion proves admission priority; it is not a heap-copy measurement. The no-copy claim is independently supported by the concrete control/dataflow above.

### F3 [P2] Closed: an existing FIFO is refused before WRITE open

`ProtectedArchiveFiles.java:55-70` now reads relative NOFOLLOW attributes first and requires an existing lock to be a regular file with a stable key before opening WRITE. The absent case uses CREATE_NEW; FileAlreadyExists rechecks attributes and rejoins the regular-file guard. Both paths check cancellation after open and close their acquired channel on validation/cancellation failure. Existing-file identity is compared after open; the writer still requires FileChannel and obtains the actual sole publication lock.

This is sufficient under the approved deployment-owned protected-admin-root assumption. It does not add a hostile privileged replacement-race guarantee, nor is such a guarantee required for this scope.

The new test creates a real FIFO and launches only an owned Java probe. Both helper creation and probe waits are bounded; the parent terminates and waits only for its own blocked child. RED output is `FIFO child exitedBeforeDeadline=false; PUBLISH_START`, with a real assertion failure. GREEN/final output is `FIFO child exitedBeforeDeadline=true; PUBLISH_START UNAVAILABLE PROFILE_UNAVAILABLE`, with child exit 0 and no release directory. Existing normal publication, held-file-lock and symlink-lock cases pass in the unchanged original 24 tests. This converts the original source-derived FIFO finding into actual bounded RED-to-GREEN evidence.

### F4 [P3] Closed: versioned report correctly narrows deadline responsibility

`docs/acceptance/b/task-B-06-archives-index-fix1-report.md`, section F4, explicitly supersedes the historical report's overbroad sentence. It identifies source.open, Snapshot.loadClosure and writer.publish as installing a cooperative 30-second monotonic boundary, while standalone StrictConfigIndexReader.read and ProcessingManifestRefs.roots use the caller predicate and install no timer. It retains the non-preemptive/local-I/O limitation and caller deadline responsibility for pure work.

The original report is deliberately byte-exact and historical, with its original Git-bound seal intact. The new report clearly states the current contract rather than silently rewriting prior evidence. Nested internal predicates retain the outer writer deadline, so opening/verifying a predecessor does not extend that enclosing operation's deadline. No new timer or shared S1 change was introduced.

## Actual raw runs and counts

All four fix command arrays, exits, original stdout/stderr and named XML cases were inspected. XML counters and actual testcase counts agree.

| Run | Exact unit selector | Exit | Tests | Failures | Errors | Skips |
|---|---|---:|---:|---:|---:|---:|
| red-f1-f3 | ControlledArchiveFix1Test | 1 | 4 | 4 | 0 | 0 |
| green-f1-f3 | ControlledArchiveFix1Test | 0 | 4 | 0 | 0 | 0 |
| regression-original-24 | ControlledConfigArchiveSourceTest | 0 | 24 | 0 | 0 | 0 |
| final-current-28 | Controlled*Test | 0 | 28 | 0 | 0 | 0 |

The four RED assertions are: changed codec atime on premature acquisition; actual published SHA that cannot reopen under 4,000; expected BUDGET_EXCEEDED but actual INVALID_JSON; and the owned FIFO child failing to exit. They are not setup or entrypoint errors. Final wildcard discovery contains exactly ControlledArchiveFix1Test (4) and ControlledConfigArchiveSourceTest (24), matching raw runner executed=28 and BUILD SUCCESS. Four/24 reruns overlap the final 28 and are not additional independent coverage. The old 27 S1 regression invocations remain historical evidence and are not described as rerun by fix1.

The retained setup-tool-creation diagnostic is a Python quoted-string SyntaxError from a non-test helper-creation command. It is correctly separated from assertion RED and did not run Java/Python tests. No Java compile/setup failure is claimed for the four fix runs.

## Introduced effects, scope and limits

No public API, wire grammar, literal root.1/root.2 bundle, original test/golden, original report or shared S1 code changed. The internal verified-chain count, metadata-only candidate admission, bounded duplicate index formatting and lock acquisition helper preserve canonical identity/history/closure validation and final immutable-byte writes. Runtime file traversal remains SDS-relative/NOFOLLOW. Offline directory creation/fsync and atomic sibling publication retain the approved protected administrative-root boundary. Cancellation, partial staging preservation, absent-history refusal, caller-array isolation and original ConfigBindings compatibility remain covered by the final unchanged 24-case suite.

There are no active/deferred/parked findings in this fix scope. Task4's independent-JVM/real-DB recovery, root's combined current unit run and final adoption remain external gates. This scoped PASS does not establish authorization, production registration, upload/Worker/API closure, fairness, company integration, arbitrary blocking-I/O timeouts, hostile privileged races, power-loss durability or HA. No test was rerun by the reviewer; the conclusion combines inspected final implementation with immutable, source-associated author evidence.
