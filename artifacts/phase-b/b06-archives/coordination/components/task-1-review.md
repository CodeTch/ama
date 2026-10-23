# B06.2 Task 1 independent SPEC and quality review

## Verdict and scope

**SPEC: PASS for Task 1. Quality: PASS for Task 1.** No actionable P0/P1/P2/P3 findings were identified in the exact reviewed range. This is an independent scoped review recommendation to the root coordinator, not adoption, full B06/B06.2 acceptance, integration qualification, or production authorization.

Reviewer acted independently of the implementation author. The requesting-code-review skill was consulted; the reviewer role supplied by root was performed directly without further agents. No author source, evidence, tests, DB, fixtures, private environment or configuration was modified. No Java/Python test suite or author evidence-runner was rerun. Read-only machine audits, Git comparisons, independent literal reconstruction and an OpenSSL digest of the frozen public bytes were performed.

## Exact reviewed identity

- Author worktree: /home/tanren/ama/.worktrees/b06-batch.
- BASE: ad9960595ab5de6eef7760e2c9094d23666074cb.
- HEAD: 0b65a68f100beed8172c3c3f270a19c5e1cf8523.
- Full range: ad9960595ab5de6eef7760e2c9094d23666074cb..0b65a68f100beed8172c3c3f270a19c5e1cf8523.
- Review package: /home/tanren/ama/.superpowers/sdd/task-B-06-archives-prepared-brief/task-1-review-package.diff.
- Package: 104 paths, 581925 bytes, SHA256 ebeadbfaf6fa2b08fb33db53b54087a1b1d9ae41eb9b8885dd5c6ad2b058b13a.

The package was independently regenerated in memory using its exact BASE/HEAD, complete Git log/stat, all path sizes/SHA/modes, and full-index U10 diff. Its bytes exactly matched the supplied package. Every changed current path was compared to its HEAD blob. All 104 changes are additions, every Git mode is 100644, physical files are regular with no executable bit, no symlink is present, and git status --porcelain was empty. Coverage is the complete range, not HEAD~1.

## Inputs and coverage

Read the activated common/task-1 briefs and review-common, the approved prepared implementation plan, proposal sections 4–7, original adopted S1 consumer handbook and fix1 addendum, full author root report, and tracked task report. Reviewed the complete new 109-line BatchFingerprint source and 189-line test, all four literal formats and nested attributes, both new Python tools, the existing CanonicalValue/CanonicalValueCodec authority, relevant MinutesContracts definitions (FullRow/Table, ContentFile, validators, CanonicalBudget, Failure/check), existing codec/fact-budget tests, and the existing checks runner used by the captured commands.

All 104 delivery paths were covered by byte/mode and seal audits. Repeated evidence source copies were compared through their complete source-sha256 lists; the unique source implementations and meaningful deltas were read directly. Raw command/exit/stderr/stdout and actual XML were audited, with direct inspection of the RED stub and assertion messages and the meaningful runner snapshot deltas. No file was excluded because it was merely an evidence copy.

The full delivery is restricted to the new helper/test/resources, new task tools/report, and the new batch evidence directory. The complete Git diff has no modified or deleted baseline path: root .gitattributes, old SQL, old vector/tool, B06.1 artifacts, tests, handbooks and shared implementation remain byte-identical to BASE in this range. No private .tools/env file, credential/config-value file, service wiring, policy, WorkerLoop, provider, API, fairness query or production default was added or changed.

## Specification checks

1. The exact closed envelope is fixed in source: profile ama.input-batch.v1; envelope kind INPUT_BATCH; data meetingId, batchId, kind TRANSCRIPT, baseRevision and request-ordered files with ordinal/fileId/sha256/sizeBytes. No generic field-map input or extension path can insert extra envelope metadata. The existing typed CanonicalValueCodec.sha256 is the sole production encoding/hash implementation.

2. Member construction uses adopted strict uuid/hash/uint validators. IDs/hash remain canonical lowercase with no normalization; nonempty membership, contiguous ordinal 1..N, supplied request order and unique file IDs are enforced. Negative numeric values reject; Long.MAX_VALUE remains representable and valid. Numeric values do not cause proportional allocations or size summation.

3. Every required identity/content/size/order participates in the digest. Tests mutate meeting, batch, base revision, file ID, SHA, size, membership and request order. Created timestamp is absent from the envelope and changing actual FullRow.created_at retains the expected digest. The helper does not include task IDs, storage location or current defaults.

4. verify checks actual INPUT_BATCH and INPUT_BATCH_FILE table types, batch IDs, saved ordinals, file IDs and saved SHA values. Files must have matching nonnull meetingId. Cardinality check, putIfAbsent duplicate rejection, removal by member ID and the final empty-map check together establish a supplied-set bijection. Missing, extra, duplicate, substituted, wrong-meeting, three-argument/missing-meeting and contradictory metadata cannot silently disappear through map overwrite. Metadata list order is intentionally irrelevant. File sizes come from supplied four-field ContentFile facts.

5. verify recomputes using actual batch ID, meeting ID, kind and base_revision, compares to actual input_fingerprint with FINGERPRINT_MISMATCH, checks cancellation before success, and returns the computed digest. It neither substitutes a supplied hash nor returns a success boolean that could obscure the computed value.

6. Count bounds run before the first projected HashSet/HashMap/ArrayList in both entry points. maxContainerEntries and necessary per-member maxNodes/maxEncodedBytes bounds limit projected work. Each fixed-shape member has 9 codec nodes and 209 encoded bytes (including object keys); division avoids overflowing count multiplication. The existing codec subsequently preflights the full envelope including fixed overhead, keys, depth, string length and all nodes/bytes before hashing success. Fixed strings and strict UUID/hash fields prevent an unbounded scalar hidden inside a member. Cancellation checks cover entry, each loop, the envelope handoff, codec scan/emission and final return; interruption is retained.

7. The new helper is explicit v1 only and makes no automatic historical-profile selection. Public documentation correctly describes supplied consistency and the caller's complete/authorized bounded-acquisition obligation. It cannot establish database existence, immutable-source origin, upload purpose, AVAILABLE state, file read permission or domain authority. Zero-byte input may hash; it does not imply successful TXT parsing.

## Literal and codec domain

Independently reconstructed the typed byte sequence from the delivered literal JSON using a separate short audit encoder with tag 03/04/05/06, big-endian 8-byte uint/length/count, UTF-8 strings, UTF-8 key ordering and retained array order. It exactly equalled the delivered bytes and hex. All four new literal resources were compared byte-for-byte with their preserved B06.1 originals.

- Typed length: 734 bytes.
- Independent Python SHA256: c4a558e4dc8aff8289b4ed60193efdfbc7f64f73ae642e2eec6398c473458053.
- Independent audit OpenSSL SHA256: c4a558e4dc8aff8289b4ed60193efdfbc7f64f73ae642e2eec6398c473458053.

The captured vector command/exit/stdout/stderr also records agreement of independent Python and OpenSSL. The author vector tool imports no application code and defaults to verification; --freeze refuses existing targets. It is evidence tooling, not a second runtime codec.

The Java literal test separately checks codec bytes, fixed digest, helper digest and inequality with raw JSON SHA. Existing codec tests actually cover primitive tags, UTF-8 key order (including supplementary characters), list order, controls, domain profile/kind separation, duplicate keys, lone surrogates, all budget dimensions and cancellation.

## Actual retained execution evidence

Each recorded command is python3 tools/checks/run.py unit followed by the exact selector below. The existing checks runner removes stale expected reports before execution, expands matched test sources, fails on no matches, uses Surefire for unit, and requires fresh executed passing tests with no skips. Reactor compilation is not counted as test execution.

| Run | Selector | Actual XML cases | Failures | Errors | Skips | Raw exit |
|---|---|---:|---:|---:|---:|---:|
| red-1 | BatchFingerprintTest | 6 | 6 | 0 | 0 | 1 |
| green-1 | BatchFingerprintTest | 6 | 0 | 0 | 0 | 0 |
| codec-green | CanonicalValueCodecTest | 5 | 0 | 0 | 0 | 0 |
| fact-budget-green | MinutesFactBudgetTest | 4 | 0 | 0 | 0 | 0 |

XML case counts/names agree with stdout test summaries and exit records. The six RED XML failures are org.opentest4j.AssertionFailedError: four expected the fixed hash but received null; two expected the adopted Failure but no exception was thrown. The actual RED source snapshot is a compilable compute/verify null-return stub. These are assertion RED failures, not setup/compilation errors. The earlier unquoted-PATH launch is disclosed separately as setup and is not counted.

All six new methods executed in both RED and GREEN:
- literalVectorMatchesIndependentBytes
- orderAndEveryIdentityFieldAffectDigest
- invalidOrIncompleteMembersFailClosed
- verifyComparesActualBatchFingerprintAndReturnsComputedValue
- verifyRejectsMissingExtraDuplicateForeignAndContradictoryMetadata
- canonicalBudgetsAndCancellationNeverReturnSuccess

The actual negative cases include empty/null collections and members, noncontiguous/reordered/duplicate members, zero/negative ordinal, negative size/base, malformed/uppercase/whitespace/nil UUIDs, malformed/uppercase hashes, lone surrogates, unsupported/null kind, saved hash/base/size mismatch, metadata missing/extra/duplicate/foreign/missing meeting, wrong rows/batch IDs, all canonical budget dimensions, immediate/during-work cancellation and preserved interruption. The code-only Failure shape is asserted. Constructor-level validation accounts for invalid values that cannot legally reach the helper body.

There are 15 unique passing methods across the three GREEN selectors (6+5+4); no parameterized invocations. RED/GREEN repetition is not added to the unique total.

## Source association and seals

Verified every line of each run's source-sha256.txt against its retained source bytes: 14 red-1, 14 green-1, 15 codec-green, 15 fact-budget-green. Final helper/test/resources and unchanged codec/contracts/checks implementations match the GREEN snapshots.

Final source hashes independently computed:
- BatchFingerprint.java: 0559a2809dc03759241a8bc881c7ee4b7dd56af91ba49d2603e08675617db315.
- BatchFingerprintTest.java: 0a20664c0187e8841d60fb76003e5d7e99a76446a1ebfdc387e1136bf7861531.

The capture runner differs from the early snapshots because its unused nonexistent MinutesContractsTest choice was replaced with MinutesFactBudgetTest and that actual test source was added to snapshots. Relative to the later codec/fact-budget snapshots, the only final runner change adds *.log -whitespace when writing the evidence-local .gitattributes. No selector command was changed retrospectively and no Java implementation/test/resource changed after its GREEN association. The final capture-runner bytes were not executed as a new suite; this is a reviewed tooling-only delta, not concealed executable coverage.

PUBLIC_ALLOWLIST.txt equals all 104 changed paths exactly, with no duplicates. SHA256SUMS verifies all 103 listed files and excludes only itself from the delivery. Neither includes root .gitattributes or private files.

- Allowlist SHA256: 28221957b0f59ffd8297a9ebb454ab3dad383a0894af065e28ba585c68b9be66.
- Manifest SHA256: 6a6670c0d950d616de95d4d2dd8f4eaa8d3e0604d93f63e68e9d12e81f248831.

Evidence-local attributes preserve raw bytes, and the *.log whitespace exception does not edit the raw logs. The recorded preservation command has empty stdout/stderr and exit 0. Independent complete-range Git inspection also proves no baseline-path modifications.

## Findings, deferred work and limitations

Actionable findings: P0 none; P1 none; P2 none; P3 none. No repair requested. No important gap was waived.

Deferred/parked findings in this Task 1 source: none. Existing planned follow-on obligations remain outside this review: authoritative bounded JDBC acquisition, archive storage/publication/restart integration, original upload/purpose and AVAILABLE/file authorization, explicit future batch-algorithm persistence/version discrimination, and the pre-existing queue fairness qualification. This helper supplies none of those permissions or qualifications.

Verification limits:
- No fresh DB, Docker, private environment, integration suite, full Java unit suite, author-runner execution or production path was used by this reviewer.
- Existing raw evidence was audited; author Java execution was not independently repeated. Root owns the single combined current-unit run and subsequent adoption.
- BatchFingerprint does not have a dedicated enormous lazy-list trap test; count-before-projection ordering was inspected directly. Current tests use small bounded valid/invalid facts. Its use of normal, stable, caller-owned Lists assumes no concurrent mutation as documented.
- The pure verify API proves exact consistency of the supplied set, not authoritative query completeness or storage provenance.
- No full B06/stage B/company/HA/fairness/production qualification is asserted.

Recommendation: root may proceed with its planned integration validation and adoption decision for this exact Task 1 candidate, subject to its remaining combined checks.

