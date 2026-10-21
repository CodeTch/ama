# B-04 local selected-manifest correction

2026-10-22. Author scope implemented and committed; **pending root-owned independent BASE..HEAD review, not whole B-04 completion**.

- Worktree: `/home/tanren/ama/.worktrees/phase-b-storage-selection`
- Branch: `codex/phase-b-storage-selection`
- BASE: `9572b5a349a556d1f6092f8ed78337cfde76f27d`
- HEAD: `73d9a2326f12ad5f72a09219a82f24176244b14d` (`fix: complete local uploads from selected receipts`)
- 22 committed paths: four Java paths, one scoped acceptance record, and 17 evidence files (16 captured files plus their manifest). Tracked worktree/index clean after commit.

## Root cause and exact change

Read the full exact selection brief, original B04 brief, actual StorageContracts/FileStoragePort/LocalUploadJournal and local contract/durability tests, B00-1.2 committed-receipt freeze text, existing local infrastructure record, and S3 design requirement-trace/terminal sections. Applied systematic-debugging, test-driven-development plus writing-good-tests, and verification-before-completion skills. No child agents.

The sole production behavior change removes the 13-line `fs.visit` extra-part gate from `LocalUploadJournal.complete` immediately after selected-total validation (now line 117). Previously expectedSize=3, accepted part1=`abc`, accepted part2=empty, supplied manifest=[part1] failed INVALID_ARGUMENT because part2 existed. The supplied list is the trusted caller-frozen selection; storage acceptance does not imply receipt commitment in the business database. The same storage-boundary case is also tested with unselected part2=`xyz`, proving this is not a zero-only exception.

Shared `FileStoragePort.complete` Javadoc at line 27 now states exact selected-manifest semantics, actual selected validation, public exact-all-committed responsibility, immutable request replay/conflict (including identical bytes from different valid selections), retryable bad full SHA while open, terminal write rejection and completion-preserving abort. No signature or wire contract changed.

All original selected checks, exact serialized decision/fingerprint, concatenation, actual part and whole SHA checks, overflow-safe expected-size checks, flock, publication, file/directory syncing, mount/active-role checks, mTLS, path protection and abort behavior remain. Abort's own part scan remains unchanged. No unselected-artifact deletion, automatic retention policy, zero-part ban or product cap was added. The public database manifest rule is unchanged; no DB/API/registry/S3 implementation was attempted.

## Test behavior and preserved RED

`FileStorageContractIT.localMultipartCompletionSelectsSuppliedReceipts` (line 169) uses actual LocalFixture loopback HTTPS/mTLS/native storage for empty and `xyz` extra parts. It validates chosen result metadata and exact `abc` read bytes, same selected replay, late empty write rejection, and abort preserving completion. Appending an already accepted empty receipt after the decision produces a different valid manifest with identical bytes and is CONFLICT; appending `xyz` remains SIZE_MISMATCH. No fake receipt acceptance or mock database is used.

`LocalDurabilityIT.multipartSelectedPartsAndCompletedResultSurviveActualProcessKill` (line 127) expands the previous real-process test into empty/nonempty extra variants: acknowledge selected and unselected parts; forcibly kill/restart the actual 64-MiB executable file-service process; complete selected bytes; forcibly kill/restart again; replay exact selected result, reject changed selection and new writes, preserve completion under abort, and check the unselected physical part remains intact. This also preserves the original ordinary put/restart and read assertions.

`fullHashMismatchDoesNotPoisonCompletionRetry` (line 167) now includes accepted unselected empty part2 and still demands CHECKSUM_MISMATCH followed by successful correct-hash retry. The prior `completeRejectsMissingAndUnlistedAcceptedParts` test was narrowed/renamed `completeRejectsMissingSelectedPart` (line 181): it retains the original missing-selected-byte NOT_FOUND assertion, while removing only the obsolete expectation that unlisted accepted bytes must block completion. Existing bad order, duplicate, forged/foreign receipt, total, selected receipt immutability and terminal checks remain in the full local contract.

Before implementation, exact new HTTP selected completion produced **2 tests / 0 failures / 2 errors / 0 skips**, both Storage INVALID_ARGUMENT at valid `complete`; exact process-restart/hash tests produced **3 / 1 / 2 / 0**, the two valid completions again INVALID_ARGUMENT, and CHECKSUM_MISMATCH expectation seeing INVALID_ARGUMENT. These are behavior failures caused by the extra scan, not absent fixtures, typo or infrastructure failures. Production code was changed only after both RED captures.

## Adjacent test-fixture defect found during verification

The first full contract after the scan removal ran **7 / 1 / 0 / 0**: all new selection cases passed, but unchanged `localRejectsUnverifiedBytes` expected STORAGE_UNAVAILABLE from its interrupted stream and saw SIZE_MISMATCH. This exact attempt is retained as `green-contract-attempt1.log/xml`; it was not discarded or rerun unchanged to obtain a pass.

Inspected the actual pinned JDK `java.base/java/io/InputStream.java`: default bulk read catches an IOException after the first byte and returns the bytes read. The old test used `n++ == 1`, so it threw only once and then generated unbounded `a` bytes. Excess-data rejection was a legitimate outcome. Root authorized correcting this owned test fixture only. Its `read()` now persistently throws after the first byte and its explicit bulk read emits only that byte before later reads throw. Production LocalFileClient and error mappings are unchanged. Focused corrected stream case passed before the final full contract execution.

## Exact commands and final evidence

Every canonical run used this worktree and sourced:

```sh
source /home/tanren/ama/.worktrees/phase-a/.tools/env.sh
source /home/tanren/ama/.worktrees/phase-a/.tools/mysql-fixture/ama-test-b01-f86e8451e91e/env.sh
```

The second file was explicitly authorized by root **solely for the current runner admission guard**. No credentials were printed; no connection to or SQL/reset/migration/stop/other operation on that controlled MySQL fixture occurred. No public business MySQL integration tests ran. The existing 19-unit reactor group includes its own H2 TCP readiness/outage test (`DeploymentConfigTest.readinessRecoversFromRealDatabaseOutage`) and a deliberately unavailable loopback MySQL endpoint probe. Only explicit local IT selectors ran; no FoundationIT, wildcard IT, MySQL database check or unrelated `all` suite.

| Evidence stem under `artifacts/phase-b/b04-selection/` | Exact canonical command | Tests / failures / errors / skips |
|---|---|---|
| `red-contract` | `python3 tools/checks/run.py it FileStorageContractIT#localMultipartCompletionSelectsSuppliedReceipts` | 2 / 0 / 2 / 0 |
| `red-durability` | `python3 tools/checks/run.py it LocalDurabilityIT#multipartSelectedPartsAndCompletedResultSurviveActualProcessKill+fullHashMismatchDoesNotPoisonCompletionRetry` | 3 / 1 / 2 / 0 |
| `green-contract-attempt1` | `python3 tools/checks/run.py it FileStorageContractIT` | 7 / 1 / 0 / 0; fixture defect above |
| `green-durability` | `python3 tools/checks/run.py it LocalDurabilityIT` | **39 / 0 / 0 / 0** |
| `green-stream-fixture` | `python3 tools/checks/run.py it FileStorageContractIT#localRejectsUnverifiedBytes` | **1 / 0 / 0 / 0** |
| `green-contract` | `python3 tools/checks/run.py it FileStorageContractIT` | **7 / 0 / 0 / 0**, after fixture correction |

Each stem has exact raw stdout/stderr log plus copied fresh JUnit XML. Canonical Maven verify also executed **19 Java unit cases**, all passing with failures/errors/skips0: StorageRouterTest8, DeploymentConfigTest9, ContractClientTest2. Final unit XML is captured as `green-unit-TEST-*.xml`. `manifest.json` records exact selectors, parsed test counts, and SHA256 for all 16 evidence files; every digest was reverified before commit. `toolchain.log` captures Zulu Java21.0.12.1, Maven3.10.0, OpenSSL3.0.13, WSL2 kernel and ext4.

No OpenAPI/signature changes, so no extra OpenAPI run was needed. No invented/skipped S3 parameter group was added or counted. Actual native process evidence is WSL/ext4 only, not RHEL/XFS/DRBD, power-loss, capacity or fencing qualification. Public DB receipt freeze/race/rollback behavior remains later B04 integration work; these tests reproduce its required storage boundary only.

## Self-review and handoff

Reviewed the complete owned source/test/doc diff and staged path list. `git diff 9572b5a..HEAD --check -- backend docs` passes; tracked worktree/index are clean. Unrestricted raw-evidence diff checking reports Maven's original `[INFO] ` / `[ERROR] ` trailing spaces in captured logs. Those bytes are intentionally preserved for exact evidence; no source/doc whitespace errors were reported, and no repository attributes/config were changed to suppress them.

Acceptance note: `docs/acceptance/b/local-upload-selection.md`. Existing reviewed infrastructure acceptance record/history remains untouched. No root/main checkout, source designs, schema, company network, push, merge or fencing action occurred. The only shared coordinator write is this requested report. Root should independently review full **9572b5a..73d9a23** before integration. This scoped implementation is ready for that review; it does not mark B-04, B stage or production qualification complete.

## M1 evidence wording correction after independent review

The independent review in `task-B-04-selection-review.md` returned scoped spec PASS and quality PASS, with no Critical/Important finding and one Minor evidence-wording item. That item is corrected in a documentation-only follow-up, pending root-owned wording-delta review.

- FIX_BASE: `73d9a2326f12ad5f72a09219a82f24176244b14d`
- FIX_HEAD: `b2f933c6256f4c56597b2376823ba3fb205f436c` (`docs: clarify local selection database test scope`)
- Exactly two tracked changed paths: `docs/acceptance/b/local-upload-selection.md` and `artifacts/phase-b/b04-selection/manifest.json`; one fact-text line replaced in each, 2 insertions/2 deletions total. This shared implementation report is also corrected in its runner-admission paragraph above and this addendum; it is outside the committed implementation worktree.

Verified the review against unchanged `DeploymentConfigTest.java:38–65`: the existing unit group includes a real test-owned H2 TCP database outage/recovery test and a deliberately unavailable loopback MySQL endpoint probe. The former broad “no SQL or database tests ran” claim was inaccurate. Corrected wording states that the controlled MySQL fixture was used for runner admission only, with no connection to or SQL/reset/migration/other operation on that fixture and no public business MySQL integration tests. The pre-existing H2 readiness test is now explicitly acknowledged in the acceptance note, manifest and this report. The local selected-manifest implementation itself remains DB-free.

| Tracked path | FIX_BASE SHA-256 | FIX_HEAD SHA-256 |
|---|---|---|
| `artifacts/phase-b/b04-selection/manifest.json` | `ac0cdf271604c128e349867da3bad12445299bdfa9066d07c263398941cb53e8` | `d63c875f0af61f2d86da08ebe4bcaa81f88c3b31740899194bde5c78850d9a50` |
| `docs/acceptance/b/local-upload-selection.md` | `06f4e74603f9765407febd9dd80653670a582cc0661b91f2a4c40e4ddd517790` | `5ed5192050b2c832596329251aa291f43a830a2af98329ea21abde0b51542d15` |

Fresh verification compared every one of the 16 captured evidence files byte-for-byte with `git show FIX_BASE:<path>` and recomputed each SHA-256: **16/16 identical**, matching the original unchanged manifest hash entries. Parsed old/new manifest objects are equal after removing only `runner_admission`; all selectors, counts, environment/scope metadata and hash entries remain unchanged. `git diff FIX_BASE FIX_HEAD --check` passes, and tracked worktree/index are clean. No passing tests were rerun, no production/test/SQL source was changed, and no database/fixture/network action occurred during this wording correction.
