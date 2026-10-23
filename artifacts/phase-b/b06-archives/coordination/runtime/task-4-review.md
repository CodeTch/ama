# Task 4 independent SPEC and quality review

Review date: 2026-10-23. Fresh independent reviewer, not implementer or a prior Task reviewer. The initially requested reviewer did not start because of capacity; this replacement performed its own review without a shared prior verdict. No subagents or repairs.

**SPEC: PASS for the activated Task 4 scope. Quality: PASS for the same scope. Actionable findings: P0=0, P1=0, P2=0, P3=0. New deferred/parked findings: 0.** Root must fully read this report before adopting. This is not final whole-B06.2 review/adoption or production/full-B06 qualification.

## Exact reviewed delivery and coverage

- BASE `5563f4cebd6f3f8d5c44d67c4e9b54f0a1d83d00`.
- HEAD `41a41e1d7d345fd3b6d46e0a7bc01757298abb3c`.
- Full range `5563f4cebd6f3f8d5c44d67c4e9b54f0a1d83d00..41a41e1d7d345fd3b6d46e0a7bc01757298abb3c`, not HEAD~1 by assumption.
- Author tree `/home/tanren/ama/.worktrees/b06-archive-restart`; HEAD verified and tracked/untracked status clean at the start and after inspection.
- Root package `task-4-review-package.diff`: 4,061,732 bytes; SHA256 `47c1bbc34fb1c8517c9a6b8e5833ab14ab19e20a24a0327b39d902301f18d845`. Independently checked package header inventory for every one of its 512 paths and verified that its full diff body is byte-identical to `git diff --binary --full-index BASE..HEAD`. The package has a BASE/HEAD/stat/inventory preamble, so comparing the entire package directly with a plain diff is not the relevant equality.
- All 512 changed paths are additions, mode `100644`; every working-tree file is regular/non-symlink and byte-equal to its immutable HEAD blob. There are no baseline edits/deletions. Exact changed-path set equals the 512-entry public allowlist, without duplicate names.
- Independently recomputed all 511 SHA256SUMS entries. Its set is exactly the allowlist minus SHA256SUMS itself. Allowlist SHA256 `2d44a71434d918f56ee3440524861672ddff7e93c6b960d69d1afbe07da2f74a`; seal SHA256 `74b1479d25895e725a11140ad8e97f1b0edd75927ee90466e2c77809aa2b5938`.
- All 35 current-source-dependencies records match their SHA and exact BASE bytes; all nine resource literals equal the adopted originals. New content is restricted to the three test/helper Java files, nine copied literals plus nested attributes, three restart tools, two documents, and restart evidence. No production API, WorkerLoop, Supplier, provider, upload, SQL, root attributes, prior seal or fixture source changed.

Fully read the six root brief/preflight/report files specified in the assignment, including the complete implementer handoff; read the approved prepared plan, especially Task 4 and its scope gates; proposal sections 4–7; adopted config-wire record; original and fix1 S1 handbooks; and the relevant queue/access preparation adoption contract. Directly reviewed all three current Java files, all three new Python tools, the consumer guide and complete tracked report. Independently established that the tracked report body is exactly the body reproduced in the root handoff. Inspected unchanged queue, transaction, outbox, meeting-access, preparation, standalone facts-reader, manifest-discovery and S1 processing code where needed to validate composition. This does not reopen all accepted unchanged dependencies as a broad review.

## SPEC and composition assessment

`ArchiveRestartFixture.seed` creates new controlled metadata and a matching current meeting/subject fence 20/5. It computes batch v1 with the adopted helper. A obtains actual batch/member/four-field file facts through the authorized standalone reader and verifies the batch digest before manifest/draft calculation. The fixture supplies a bounded one-file case; it does not claim qualified upload or file-provider provenance.

Manifest construction, TEST producer-key typed hashing, archive open/load, strict discovery and S1 compute/verify are outside the write transaction. `accept` constructs exact manifest text and the full TaskSpec before entering `TransactionRunner.required`. Inside, it acquires authentic current EDIT access, checks owner/current fence and the batch/member/file facts used by the draft, inserts the exact canonical input, and calls real `JdbcTaskQueue.enqueue(spec, policy.adopt(held))`. The TEST policy revalidates the authentic same-issuer/current-transaction access capability, full-spec preparation, fixed facts and stored input provenance. Existing queue code checks the current subject fence and performs the actual durable_task INSERT and event append. No raw durable_task insert, fake StoredTask DTO, empty manifest sentinel or current-fence substitution is used as acceptance evidence.

The ambient queue joins the outer transaction and does not flush internally; the helper calls one outer `outbox.flush` at the end. `accept` returns only after the transaction template completes. A then separately reads the stored facts, verifies full TaskSpec and accepted task ID, runs stored S1 verification and checks READY plus exactly one TASK_ENQUEUED event before producing COMMIT_ACK. This supports the actual commit boundary claimed by the report.

B starts in a distinct JVM, opens exact appended index2, reads the actual old input/full task facts, checks original persisted manifest bytes and SHA, discovers old references from those bytes, loads the old closure and verifies the original digest/root.1. It separately computes a new draft selecting root.2; it does not rewrite or enqueue a pretend replacement input. Current producer key/type/pool/input/subject/fingerprint/version/priority/cancel epoch/max attempts are compared through actual stored full TaskSpec, and queued task IDs are checked.

The distinction between a committed batch plus detached read/recheck and a future uncommitted-batch/held-capability reader is explicit and correct. The standalone reader still rejects ambient transactions. Pure helpers confer no domain authorization. The TEST policy is local test registration only; output/external paths fail explicitly.

## Raw execution and source evidence

I parsed all retained JUnit XML, checked testcase/failure/error/skip counts against actual elements, checked each automatic unit suite against its raw run log, verified every run's 29 source snapshot hashes, checked unchanged snapshotted dependencies against current BASE-bound bytes, and compared final Java sources with GREEN. The test and fixture are byte-identical between genuine RED and GREEN. The only probe source change is the historical-root acquisition from `List.of(root.2)` to `ProcessingManifestRefs.roots(original, ...)`, plus its adjacent explanatory comment. Canonical selector and ProcessBuilder invocation are unchanged.

| Run | Exit | Actual IT tests / failures / errors / skipped | Automatic or selected unit tests / failures / errors / skipped |
|---|---:|---|---|
| red-01 | 1 | 0 / 0 / 0 / 0 | 210 / 0 / 0 / 0 |
| red-02 | 1 | 0 / 0 / 0 / 0 | 210 / 0 / 0 / 0 |
| red-03 | 1 | 1 / 1 / 0 / 0 | 213 / 0 / 0 / 0 |
| green-01 | 0 | 1 / 0 / 0 / 0 | 213 / 0 / 0 / 0 |
| unit-batch | 0 | none | 6 / 0 / 0 / 0 |
| unit-index | 0 | none | 24 / 0 / 0 / 0 |
| unit-index-fix1 | 0 | none | 4 / 0 / 0 / 0 |

All IT commands are exactly `python3 tools/checks/run.py it ConfigArchiveRestartIT`. The unit selectors are exactly BatchFingerprintTest, ControlledConfigArchiveSourceTest and ControlledArchiveFix1Test. The two setup attempts fail in integration test compilation (illegal expression, then ambiguous Role). They are not assertion RED. Their 210 units stop before the integration module's three support tests. The real RED XML is a parent AssertionFailedError at ConfigArchiveRestartIT line 43, expected recovery exit 0 versus actual 23, with zero errors/skips. A PID 647004 had committed actual task `09572100-e72e-4cbf-b975-490d2adf8d92` and exited 0; fresh B PID 647060 returned PROFILE_UNAVAILABLE and exited 23. A child domain exception alone is not the reported RED.

GREEN has one JUnit method/invocation and five child JVMs, not five tests. Parent PID 649138 differs from all children. Recorded Process.pid values agree with child receipts and stdout:

| Child | PID | Exit | Outcome |
|---|---:|---:|---|
| A | 649176 | 0 | COMMIT_ACK |
| B | 649230 | 0 | RECOVERED |
| missing-history | 649265 | 23 | PROFILE_UNAVAILABLE |
| mutated-history | 649289 | 23 | PROFILE_UNAVAILABLE |
| wrong-index-pin | 649375 | 23 | PROFILE_UNAVAILABLE |

Inspected actual argv/classpath, stdout/stderr, receipts and exits. They use the explicit Java21 executable and this worktree's test/classes and dependencies; no secret arguments or environment dump appears in the process invocation. Child stdout/stderr are redirected to files, avoiding pipe-fill deadlock. Waits are bounded to 30 seconds with bounded cleanup of only the owned still-running child; there is no arbitrary sleep. Distinct positive runtime roots for RED and GREEN remain on disk.

Automatic 213-unit coverage overlaps between real RED and GREEN and includes the selected batch6/archive24/fix1-4 and three integration support tests. The precise unit executions are not additive independent coverage. Final combined current full-unit verification remains root's gate.

## Persistence, negative copies and custody

For both real RED and GREEN, independently recomputed the retained manifest SHA and each index/object size and SHA in positive releases 1 and 2. Index1 is `c6e3b29cedbdc5234f368fa6d1f7152442c95f56b0caaf52bc8019bafea13e9d`; index2 is `d2436e244f966e5841941e282b0f6ad0dc6c76c6369cb90203dab42ed876472d`. GREEN original/recovered business digest is `b322434caf46dbb33cf2026396e8b3c069847a94803e83fce06e5592d0a6f640`; manifest raw SHA is `26759402b72105075210b3e588c35387f0a5b6cd622a35407ee5a8fce824cedc`; root.1 raw SHA is `2efd9361bb610198a2a59c3fb7256870229a14a1aeb72751dd8f3f4155c13f8e`. B's separate root.2 draft digest is `f31844f7210e517a72f1456730e681c2c08fd62e25157d5402900811e6442c02`.

Byte-compared each retained negative tree with the positive tree. Missing-history differs only by removal of root.1's object in release2; mutated-history differs only in that same object's bytes with unchanged size; wrong-index-pin is a byte-identical independent copy with an all-zero pin argument. Release1 remains intact in each copy. All three receipts lack success/draft digests, their logs lack recovery success, and each has typed unavailable exit 23. The successful lifecycle checks the same DB snapshot after every negative and reopens the untouched five-entry positive index afterward.

The preserved before/after snapshot is `2a0312b070f6a1e231071b1bb6f6a2b5755f1b8b8a60b07394c5adcc8befa6a1`. Its source covers full selected input/task/outbox/current meeting/subject rows and relevant table counts. This is sufficient for the stated bounded metadata nonmutation check; it is not an unrestricted snapshot of all unrelated rows. The recovery source has no write operation.

Checked retained custody-before/final public commands and exits, selected fixture ID/image/limits/loopback/owner equality, all 12 formal SQL hashes against current and BASE bytes, and complete 12-row history equality with Task3's preserved final history. Parsed actual initial/final manifest listings: all 62 transferred manifests retain exactly their SHA, with 64 final inputs including preserved RED and GREEN. Final public DB audit rows contain both actual queue task IDs/full selected TaskSpec fields, READY state, exact manifest SHAs and one corresponding TASK_ENQUEUED event each. This is retained evidence verification, not new DB access. No initial-zero rerun was required or claimed; that provenance belongs to the transferred Task3 fixture.

## Limits and disposition

No Java, Maven, integration/unit tests, DB queries, Docker commands, private environment reads, old fixture access or author helper executions occurred during this review. Verification used only Git, read-only public-file inspection and independent Python byte/hash/XML/log audits. No source or evidence was edited. The only output is this root ignored review report. Password-value scanning was not independently repeated because it would require prohibited private-environment access; the public process/log/source evidence was inspected, and the author's existing scan is a separate claim.

The current consumer guide accurately describes explicit pins/defaults, append-only protected-root publication, fail-closed history, finite budgets, cooperative local-I/O 30-second checks versus pure helper caller cancellation, standalone-reader transaction authority, and historical versus current corrected seals. Neither successful verification nor this review establishes hostile-admin protection, power-loss/HA behavior, real upload/session/purpose/file route qualification, parser/worker completion, HTTP/provider/API acceptance, production activation, full B07 history or whole B06 acceptance.

Existing future gates remain: mixed-history batch profile/version discrimination, original upload/purpose and genuine file route/reference qualification, uncommitted/held-capability facts composition, production TaskDomainPolicy/provider, atomic Worker/Supplier/API workflows, and separately governed FUP fairness/production qualification. They are outside this delivery, not new parked findings. Root still owns final combined current units, whole-B06.2 independent review and exact adoption.
