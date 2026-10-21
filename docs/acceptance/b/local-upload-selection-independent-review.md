# B-04 local selected-manifest independent review

2026-10-22. Fresh reviewer `/root/b04_selection_review`; one complete review of the supplied owned package. Review-only on the implementation checkout; this report is the sole write.

- Worktree: `/home/tanren/ama/.worktrees/phase-b-storage-selection`
- BASE: `9572b5a349a556d1f6092f8ed78337cfde76f27d`
- HEAD: `73d9a2326f12ad5f72a09219a82f24176244b14d`
- Reviewed range: one commit, all 22 changed paths (four Java files, one acceptance note, 17 evidence/manifest files).
- Package: `task-B-04-selection-review.diff`, 348039 bytes, SHA-256 `fe01cfd48c9b09f3315dfb05c3d25e1f6d8dc070ee30bcf69ecc5c8ed0bd16f7`. Its diff body is byte-identical to actual `git diff --unified=10 BASE HEAD`; the package also contains a commit/stat preamble.

## Strengths

- The behavior change is exactly the 13-line extra-part directory scan removal in `backend/file-service/src/main/java/ama/file/LocalUploadJournal.java:116`. Completion still checks nonempty ordered contiguous receipts, session-bound provider tokens, actual selected part size/SHA, selected total with subtraction-based overflow protection, and the actual concatenated full SHA before persisting a decision (`:85–139`). The journal still locks the session, compares exact serialized decisions, publishes through the same immutable durable store, records the result and syncs acknowledgements. Abort and its own scan remain unchanged (`:185–215`).
- `backend/core/src/main/java/ama/storage/FileStoragePort.java:27–36` documents the adopted storage/caller boundary precisely: trusted immutable selected receipts determine bytes, unselected physical parts do not block, and the public caller must still freeze all committed business receipts. A different valid manifest conflicts even when its added empty part yields identical bytes. This does not relax the business exact-all rule in the adopted database design `docs/superpowers/specs/2026-10-21-ama-database-design.md:173–181`.
- `backend/integration-tests/src/test/java/ama/storage/FileStorageContractIT.java:167–197` reproduces both empty and `xyz` unselected part 2 through real loopback HTTPS/mTLS and native storage; verifies result metadata, exact `abc` bytes, same-selection replay, changed-selection rejection, terminal writes and completion-preserving abort. For the nonempty changed selection, SIZE_MISMATCH is correct because that request is invalid for expectedSize=3; the empty variant specifically proves different-valid-manifest CONFLICT.
- `backend/integration-tests/src/test/java/ama/storage/LocalDurabilityIT.java:125–176` exercises both variants across actual executable JVM termination/restart before completion and again after completion acknowledgement, then verifies replay, bytes, terminal behavior and retained unselected physical bytes. It preserves the ordinary put/restart assertions and adds the unselected-part case to retryable bad-full-hash validation. The renamed missing-selected-part test retains its NOT_FOUND assertion (`:180–194`).
- The adjacent interrupted-stream fixture change is justified and confined to the test (`FileStorageContractIT.java:96–114`). The pinned JDK InputStream default bulk read catches later IOExceptions and returns already read bytes (`java.base/java/io/InputStream.java:281–304`); the old single throw could be swallowed and then emit unlimited bytes. The corrected fixture explicitly emits one byte then fails persistently. Actual production IOException/error mapping is unchanged in `LocalFileClient.java:53–101`. The first failure remains in the archive.

## Issues

### Critical

None found in the reviewed range and adopted narrow scope.

### Important

None found in the reviewed range and adopted narrow scope.

### Minor

**M1 — Narrow the database-test evidence wording.**

- Files: `docs/acceptance/b/local-upload-selection.md:24`; `artifacts/phase-b/b04-selection/manifest.json:7`.
- The statements “no SQL or database tests ran” / “no SQL or DB tests executed” are too broad. The archived final 19-unit result includes `DeploymentConfigTest.readinessRecoversFromRealDatabaseOutage`; its unchanged source at `backend/api/src/test/java/ama/api/DeploymentConfigTest.java:47–65` starts/stops a real local H2 TCP database. The same class also probes a deliberately unavailable loopback MySQL endpoint at `:38–45`. The final unit XML records both cases, and each canonical log records all nine DeploymentConfigTest cases.
- This does not indicate any access to the controlled MySQL fixture, a business upload DB test, or a selected-manifest implementation defect. It matters because the acceptance record should describe the actual scope of the reactor run accurately.
- Suggested correction: explicitly state that no controlled MySQL fixture operations or public upload/database integration tests ran, while the existing unit group includes its task-owned H2 readiness test. Align the author handoff wording as appropriate. Preserve every raw log/XML byte. This is non-blocking documentation polish and needs no test rerun.

## Spec compliance verdict

**PASS for the adopted narrow correction.** The sole production behavior change removes the unintended dependency on unselected physical parts. Selected receipt validation, actual selected content, immutable decision/replay, terminal behavior and durable publication remain in place. Shared Javadoc, scoped acceptance documentation and real HTTP/process tests implement the requested boundary. No signature, OpenAPI, DB schema/registry, S3 implementation, product limit or automatic cleanup change is present.

The stream fixture adjustment is an evidence-backed, explicitly authorized adjacent test repair, not a production error-mapping change. The one wording issue above does not change the behavioral verdict.

## Code quality verdict

**PASS with one non-blocking Minor documentation item. Ready to integrate this narrow commit: Yes.** The minimal deletion avoids adding special cases or coupling storage completion to a database. Tests assert observable bytes, metadata, errors and persistence through real existing fixtures, and preserve the relevant rejection coverage. No Critical or Important finding requires code changes before integration.

## Declined to judge / scope limits

- Real public API/MySQL exact-all freeze, receipt commit rollback, late FINALIZING receipt races, registration idempotency and business-orphan unreadability remain later B-04 integration obligations. These storage tests intentionally reproduce the resulting physical boundary rather than prove those transactions.
- S3 session protocol/transport/coalescing and a second common-contract backend are outside this local correction. No S3 group was added or counted as skipped.
- RHEL/XFS/DRBD, sudden power loss, real flush/barrier guarantees, fencing, production capacity and HA qualification are outside WSL2/ext4 process-kill evidence. The new process test kills before completion and after its acknowledgement, not at every internal crash boundary.
- Automatic orphan deletion/retention is outside the authorized behavior. Retaining unselected part 2 is consistent with the protected-artifact requirement; lack of cleanup is not a defect in this patch.
- Mutation by an untrusted/concurrently changing caller manifest is outside the explicitly trusted immutable caller precondition. This patch does not introduce a new untrusted public manifest entry point.
- This verdict does not requalify every inherited local infrastructure behavior, adopt the proposed full S3 design, complete whole B-04/B stage, or authorize a merge/push/deployment operation.

## Actual review checks and evidence

Read the selection brief and author report first, followed by the original B-04 brief, shared value/port contracts, adopted B00 upload freeze text, existing local infrastructure acceptance, and the S3 design requirement-trace/terminal/selection sections. Applied requesting-code-review (including its reviewer template) and verification-before-completion; no subagents were dispatched.

Inspected all changed source/document hunks and the full LocalUploadJournal, FileStorageContractIT and LocalDurabilityIT files. Followed relevant unchanged LocalFixture actual-process/mTLS/native-storage setup/termination, LocalFileClient completion and error mappings, and native locking/digest/controller call sites. Verified the pinned JDK source directly from its src.zip. Checked source ancestor paths for AGENTS.md; none was present in those ancestor locations or discovered under this checkout.

Read/parsed every evidence file in the complete package, independently recomputed all 16 manifest SHA-256 digests, and parsed all nine JUnit XML documents. Asserted XML aggregate counts equal manifest values and actual testcase/failure/error/skip element counts. Examined all failing testcase diagnostics/stacks and each log summary/build result; final XML runtime properties point to this worktree, the intended selectors and pinned Java 21.0.12.1.

| Captured execution | Tests / failures / errors / skips | Independent archive observation |
|---|---|---|
| red-contract | 2 / 0 / 2 / 0 | Both selected completions fail INVALID_ARGUMENT in real LocalFileClient.complete |
| red-durability | 3 / 1 / 2 / 0 | Two selected completions fail INVALID_ARGUMENT; bad-full-SHA expectation instead receives INVALID_ARGUMENT |
| green-contract-attempt1 | 7 / 1 / 0 / 0 | Only interrupted-stream assertion fails: STORAGE_UNAVAILABLE expected, SIZE_MISMATCH actual |
| green-stream-fixture | 1 / 0 / 0 / 0 | Corrected interrupted stream; log BUILD SUCCESS |
| green-contract | 7 / 0 / 0 / 0 | All local common-contract cases; log BUILD SUCCESS |
| green-durability | 39 / 0 / 0 / 0 | Full local native durability group; log BUILD SUCCESS |
| final unit XML | 19 / 0 / 0 / 0 | StorageRouterTest 8 + DeploymentConfigTest 9 + ContractClientTest 2 |

These are independently inspected committed captures, not tests newly executed by this reviewer. No suite was rerun. No DB/container/service operation, SQL, network/provider request, schema mutation, checkout/index/HEAD/branch mutation or commit was performed during this review.

Fresh read-only Git checks confirm the requested HEAD, one owned commit and 22 paths; `git diff BASE HEAD --check -- backend docs` exits 0; implementation worktree status is clean. Raw Maven log trailing spaces are intentionally preserved evidence and are not source/document whitespace defects. The sole write is this requested coordination report.

## Coordinator disposition

Behavior/spec and quality PASS adopted for the narrow source change; Minor M1 wording correction delegated to original author, with raw log/XML bytes preserved. Whole B04 and later public DB/S3 qualification remain open. No test rerun or DB operation performed for this integration.

M1 closure: source b2f933c wording-only follow-up independently rereviewed ADDRESSED/specPASS/qualityPASS, no new findings. All16 raw captures and manifest other fields unchanged. See [wording review](local-upload-selection-wording-review.md). No tests repeated or fixture operations for this correction/adoption.
