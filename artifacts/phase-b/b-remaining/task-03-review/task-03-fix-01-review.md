# Task 3 fix round 1 — bounded independent re-review

## Verdict

**Spec compliance: PASS for the scoped correction. Code quality: APPROVED for the scoped correction.**

- **T03-IR-01: ADDRESSED.**
- **T03-IR-02: ADDRESSED.**
- **New blocking findings introduced by this fix: none found.**

This closes the two Important findings within this review's range and evidence limits. Original minor findings T03-IR-M01 and T03-IR-M02 remain recorded and deferred. This does not qualify later workflows or Phase B completion.

## Exact scope

Reviewed range: `aa0b5f08a19649091b0bcd60959bd741fb5c7850..912945df2ad09d7ff265f6ba97e304a2b98a4b79`.

Read the original task brief, fix requirements/root conflict ruling, complete original review, and appended fix report. Independently checked raw SHA-256:

- Original `task-03-review.md`: `60f3e807d0d6e79fbdeafce015b063ce0acefbf910c0ec252dab25484952149c`.
- Bounded `task-03-fix-01-review.diff` (73810 bytes): `d508b82379b4f7399434c34320953d5f1b52210bb3f44943384c4486138ecf7c`.
- Retained audit `task-03-fix-01-review-audit.diff` (1164118 bytes): `66e7fdda063e0cc5213016ee18db64a8a4fc14d6acdbd46d164ab1daa6bc4420`.

Inspected all three changed source/test files through the bounded diff and its executable evidence scripts, commands, manifests and report. Intervening root Task 5 design documents are excluded.

## T03-IR-01 — ADDRESSED

**Locations:** `backend/adapters/src/main/java/ama/storage/FileBackendCatalog.java:128`, `:140`, `:144`, `:153`; `backend/adapters/src/main/java/ama/storage/JdbcFileRegistry.java:649`; `backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityReviewIT.java:14`, `:38`, `:47`, `:250`.

The source mapper now calls package-private `requireHistoricalMetadata(id)`. This path selects the configured immutable backend, rejects missing/disabled configuration, and shares the actual database type/revision/status comparison with public `require`. It performs no managed-ID derivation or storage operation.

The constructor identity proof remains substantive: Backend construction invokes ManagedBackendId.require; the definition is restricted to sealed immutable Local/S3 records; the catalog copies its backend map. Public `require(id,newWrite)` retains configured status/new-write rejection, managed identity validation and the same database comparison. The new method therefore relies on validated immutable construction without introducing caller-attested authority, a mutable definition, public mint or callback.

The unchanged registry open path at `JdbcFileRegistry.java:509` rejects an active transaction and invokes original `backends.require(...,false)` before storage.read. Source file type/original-route checks remain in the mapper. This implements root's explicit ruling that the no-hash-under-roots requirement supersedes the original design's incorrect characterization of require as metadata-only.

The regression installs a temporary JCA provider, delegates actual digest computation to SUN SHA-256, and observes ManagedBackendId stack frames plus Spring's actual transaction state. Removal occurs in finally. The archived RED log contains the real ManagedBackendId → catalog.require → prelockSources → readDraft failure. Final GREEN exercises genuine draft, receipt-aware first bind and worker source acquisition, retains constructor identity rejection, and records `outside=5 locked=0`.

Actual owned backend rows are independently changed to revision mismatch, DISABLED and wrong type; each denies without another storage read. The separate post-physical-commit disable also denies before open. Inspection of Task03TraceSource confirms that this hook runs after the underlying connection.commit and uses a separate actual connection. It demonstrates the retained post-commit check.

## T03-IR-02 — ADDRESSED

**Locations:** `backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityReviewIT.java:64`, `:89`, `:98`, `:115`, `:143`, `:168`, `:204`, `:224`.

The production-policy test uses two distinct completed B04 files, genuinely prepared/bound batches, two actual RUNNING tasks and two unfinished attempts. Test-only reflection retrieves the actual registered private policy. Preparations/scopes come from the real issuer, registry, data source, EDIT gate and active holder. Request order is deliberately descending task UUID; required acquisition order is ascending.

After genuine source receipt acquisition and policy validation, the actual production task-union method must acquire both distinct task UUIDs exactly once before any attempt. Both scopes then finalize, and repeat finalization is rejected by their private phase guards.

The final archived `covering-final-custody/f92bd709-9cd7-49ea-aeac-443f42a3be86-two-worker-scopes.txt` independently shows task primary acquisitions at lines 29–30: `b3640e52-1ee6-4111-9aa4-5bea7fb5552d`, then `d1a380f3-501a-41e0-a93c-fd7c9aa5f293`. First attempt acquisitions occur at lines 33–34 in that order. Lines 35–38 are later predicate rechecks of already held rows.

The shared-task test prepares two distinct genuine scopes for one actual task. Its final shared trace has one primary task acquisition at line 25 and one primary attempt acquisition at line 27; both scopes finalize once. Later RUNNING/owner/expiry/unfinished predicates are correctly distinguished from new primary acquisitions.

The conflict branch is honestly labeled test-only fault injection: after obtaining genuine scopes and validating sources, reflection changes one private expected task priority. It demonstrates defensive conflict rejection before primary task/attempt acquisition and with zero storage reads. The final conflict trace contains no task/attempt events. It is not represented as a normal business-reachable graph or public capability.

A separate configured-provider test exercises real registry orchestration: one whole-scope prelock call, one finish per distinct domain, zero opens during both finishes, and one read afterward. Those synthetic counters supplement the genuine production-union proof. No production union logic or public multi-request API was added.

## Verification and introduced breakage

Independently parsed archived JUnit XML:

| Window | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| hash-red | 1 | 1 | 0 | 0 |
| hash-green | 5 | 0 | 0 | 0 |
| multiscope-proof | 4 | 0 | 0 | 0 |
| covering-final | 35 | 0 | 0 | 0 |

Final suites are ManagedBackendIdentity 4, FileBackendCatalog 3, selected FileRegistryUpload 2, existing TranscriptAuthority 22, and new TranscriptAuthorityReview 4. Read invocation/exit receipts and final output; the final log has no warning/error matches.

Independently hashed all three modified/new source files against owned-source-manifest.json and covering-final.sources.json: all match. Rootproof additionally attests committed blobs, 86 evidence entries and 417 unchanged original evidence files. This reviewer did not repeat that full Git/blob/historical-file audit.

No introduced authority, route, legacy behavior or orchestration blocker was found in the bounded production extraction/call substitution or new tests. The existing issuer/holder/phase checks and task union remain unchanged. Covering tests include original-backend pinning and READ_ONLY recovery. No test rerun or database operation was performed by this reviewer.

Initial diff/evidence and full-trace outputs were truncated; omitted diff/command ranges and relevant trace events were retrieved in bounded reads. Changed source was not reread wholesale. The diff cut the constructor/immutable identity proof and post-commit open/complete-read methods, so their named surrounding context was inspected for these concrete risks. Focused unchanged-source checks were limited to ManagedBackendId, JdbcTranscriptAuthority union/finalization, TranscriptAuthorityFixture preparation/bind/task creation, and Task03TraceSource real SQL/commit observation. One attempted tracing-helper filename was absent; the actual Task03TraceSource was then read.

No repository crawl, Git command, Docker, network, private-environment or database operation was used. A first write attempt failed because a PowerShell provider-qualified path was passed to .NET; it created no review file. The retry used the explicit UNC path. Only this review file was written.

## Deferred and out-of-scope limits

T03-IR-M01 broader formatting remains deferred. T03-IR-M02's original upload handshake failure remains unresolved and explicitly retained. The old 349 composed-evidence total is not relabeled as fresh coverage of this fix.

The genuine multi-scope tests qualify private production-union behavior, not a new public multi-scope workflow. Controlled task/attempt rows demonstrate read-policy behavior, not production enqueue, importer/Worker execution or successful parser output. Production Spring/HTTP assembly, parser bytes, saved transcript flow, external material runtime, S3 and overall Phase B qualification remain outside this review. No additional blocking observation is raised outside the assigned correction.

