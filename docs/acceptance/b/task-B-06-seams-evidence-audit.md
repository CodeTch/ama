# B06.1 independent mechanical evidence audit

Result: **PASS — no evidence/count/preservation mismatch found in the checks below.** This is a read-only evidence audit supporting the main scoped review, not a new runtime test or independent claim about every implementation behavior.

- Tree: `/home/tanren/ama/.worktrees/phase-b-transcript-runtime`.
- BASE: `6b48b2f855bbf890c3ccad68325e7fd74d434489`.
- Final HEAD: `4dcdc6b5102c7d99b043385349b4a87c98b52e80`.
- Tested source: `553196cacbf27f2fd4dfc3b65d70c535f10f9958`.
- Original prerequisite proof: `26bf77d`.
- No tests, heavy cohorts, Docker commands, DB queries, or private env/config reads were performed. Only this helper report was written.

## Range, working bytes, package and seals

`git status --porcelain=v1` was empty at the start and end; HEAD is the required immutable final HEAD. Full BASE..HEAD has **1104 changed paths: 1094 additions and 10 modifications**. Groups: 932 correction-evidence files, 144 prerequisite-evidence files, 12 backend Java files, 11 tools, 4 acceptance documents, and `.gitattributes`. All **1104/1104 working files** are byte-identical to their final HEAD blobs.

Review package `task-B-06-seams-review-package.diff` is **10,692,954 bytes** and SHA256 **`b283e5639f222a66a850b8b3e81c20f057b6ccf2394de459bb281391c5c8b0fd`**. Its 1104 `diff --git` path headers are unique and exactly cover the complete changed-path set. Its preamble names the required BASE/HEAD. Package identity and coverage were verified; its wrapper/context formatting was not treated as a raw default `git diff` byte format.

- Prerequisite `SHA256SUMS`: **143 unique entries, all hashes match**. Its 144-path allowlist is unique, equals the entire prerequisite root inventory, and differs from the manifest only by the manifest's self-exclusion.
- Correction `SHA256SUMS`: **1103 unique entries, all hashes match**. Its 1104-path full-delivery allowlist is unique and exactly equals the complete BASE..HEAD changed-path set. The only unhashed listed path is this manifest itself.
- Correction `SHA256SUMS` file SHA256: `260f838ae6778990283715d55d1df4025ad77e3aa53aca40497f8cb412c52f74`.
- Full-delivery allowlist file SHA256: `a0317880efe9967e564934755ce907c217d0936c8206e69dc92527191c01014e`.
- Every one of the **13 correction per-run source manifests** has 12 entries. All 156 stored source-copy hashes match and each stored source directory inventory equals its manifest exactly.
- The two original 11-entry source/delivery-source manifests match their historical prerequisite commit, rather than being incorrectly compared with the later correction source.
- The 734-byte batch literal, its hex representation, literal SHA and archived OpenSSL output agree: `c4a558e4dc8aff8289b4ed60193efdfbc7f64f73ae642e2eec6398c473458053`. This remains proposal evidence.

## XML and exact execution counts

Parsed all **364 retained TEST XML files across 15 archived runs**. For every suite, its declared tests/failures/errors/skipped equals the actual testcase count and failure/error/skipped children. All 15 corresponding count manifests reproduce the raw XML exactly. The correction `all-runs.json` reproduces all 13 correction run aggregates exactly.

Counts below are `tests/failures/errors/skips`; repeated unit executions are separate historical executions, not additional unique coverage.

| Evidence root / run | Actual IT | Actual Java units |
|---|---:|---:|
| prerequisite run-1 | 3/2/0/0 | 179/0/0/0 |
| prerequisite run-2 | 1/0/0/0 | 179/0/0/0 |
| initial-green | 4/0/0/0 | 179/0/0/0 |
| expanded-red | 12/1/0/0 | 179/0/0/0 |
| focused-green | 13/0/0/0 | 179/0/0/0 |
| expired-fact-red | 1/1/0/0 | 179/0/0/0 |
| final-seams | 15/0/0/0 | 179/0/0/0 |
| unregistered-red | 1/1/0/0 | 179/0/0/0 |
| final-seams-16 | 16/0/0/0 | 179/0/0/0 |
| final-seams-17 | 17/0/0/0 | 179/0/0/0 |
| inherited | 28/0/0/0 | 0; intentionally not run |
| queue-crossds-red | 1/1/0/0 | 179/0/0/0 |
| final-seams-18 | 18/0/0/0 | 179/0/0/0 |
| inherited-final | 28/0/0/0 | 0; intentionally not run |
| units-final | 0 | 179/0/0/0 |

The prerequisite's two RED methods and four correction RED runs are actual `org.opentest4j.AssertionFailedError` failures, with zero errors/skips. They are not compiler/setup failures. Recorded final command exit codes are zero; correction RED command exit codes are one. The initial GREEN and original control/RED exit files agree.

Both inherited argv selectors contain exactly **26 distinct methods**, no wildcard, and equal the normalized XML method sets and coverage JSON exactly. They equal the approved map's 25-method default plus exactly `ModelPermitIT#copiedAndReconciliationTokensCannotAdmitAndDuplicateCannotResend`; no default method was omitted. `TaskLeaseIT#heldRowDoesNotBlockOtherCandidate` contributes three invocations, explaining **28 actual invocations**. Final seams and inherited runs together have **44 distinct methods and 46 distinct invocation names**. `units-final` contains 179 distinct invocation names, exactly the same unit set rerun by final-seams-18. The final-validation JSON agrees with these measurements.

## Final source and preservation

The final-source manifest covers exactly all **12 changed/new backend Java files**. Its BASE/tested-source metadata is correct. For **each of final-seams-18, inherited-final and units-final**, the source manifest is identical to final-source; every row's hash matches the stored copy, tested `553196c` blob, final HEAD blob and working bytes. Earlier passing rounds remain distinguishable preliminary versions.

All **1759 protected baseline paths** remain byte-equal to BASE in the working tree and absent from the changed-path set: **1740 old artifact files, 7 approved plan files, and 12 formal SQL migrations**. All 144 prerequisite-root files also remain byte-identical to prerequisite commit `26bf77d`; its preservation record correctly counts the 143 hashed entries.

The 12-row lower-SQL before/after/final manifests are equal; every digest agrees with exact BASE and current bytes. Archived initial and final history logs have exactly the same 12 `(installed_rank, version, script, checksum, success)` rows, all successful, ending at 013. The final inherited log records three actual latest-migrator calls, each validating 12 migrations, reporting version 013 and no migration necessary. The public ACK/map copies are byte-identical to their current coordinator originals.

Archived fixture records support initial zero tables and the recorded final owned container `/ama-test-b01-ff63b53162a3`, owner `queue-seams-20261023`, loopback port 58060, pinned MySQL 8.4.6, memory/swap 805306368 bytes, 1000000000 nanoCPUs, pids256 and recorded running state. These are checks of preserved selected-field evidence; live container/database state was deliberately not queried.

## Public-only hygiene boundary

No changed path is a `.tools` private file, `.env`, or `env.sh`. Scanning all 1094 committed additions found no literal password assignment candidate, embedded private key, credential-bearing URL, or secret/password/token-named XML system property. Generic token-variable assignment matches were ordinary Java operation-token expressions or runtime credential generation, not embedded secrets. Both sealing scripts implement own-fixture-secret exclusion checks; they were read, not rerun.

The audit did **not** read private env values and therefore does not independently reproduce an exact-secret-value comparison against those private values. The public package's path exclusion, byte seals and public-content scan pass. No source/evidence file was altered.
