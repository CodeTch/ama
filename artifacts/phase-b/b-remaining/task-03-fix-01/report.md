# Task3 fix round 1 — independent review closure

Ready for scoped independent rereview of T03-IR-01 and T03-IR-02. This report does not grant the implementation gate or claim Phase B completion.

- FIX_BASE: `aa0b5f08a19649091b0bcd60959bd741fb5c7850`.
- Precommit HEAD: `5560381927d51001bd484642269b9432481e8c70` (root-only Task5 design adoption after FIX_BASE).
- Reviewed report: `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-03-review.md`, exact SHA256 `60f3e807d0d6e79fbdeafce015b063ce0acefbf910c0ec252dab25484952149c`.
- Scope: `FileBackendCatalog.java`, one call in `JdbcFileRegistry.java`, and new `TranscriptAuthorityReviewIT.java`. Exact paths/hashes are in `owned-source-manifest.json`.
- No Task4, Worker/parser, queue, schema, creator or public capability changes. No helpers or reviewer agents were spawned.

## T03-IR-01: locked source metadata must not derive backend hashes

**Addressed.** The reported call chain was reproduced by a real failing integration test. The prior implementation and adopted original design incorrectly treated `FileBackendCatalog.require` as metadata-only. The prior public Task3 report's no-hashing-under-gate statement was too broad; it is corrected by this appended finding closure, while the original evidence remains immutable.

Root explicitly authorized the minimum FileBackendCatalog extension and resolved the conflicting original helper choice in favor of the higher-level no-hash-under-roots rule. Backend construction already calls `ManagedBackendId.require`; `Backend` is an immutable record whose definition is a sealed Local/S3 record of immutable fields, and the catalog freezes its map. This construction proof remains intact.

The new **package-private** `requireHistoricalMetadata(id)` selects the actual configured backend and performs the same actual DB type/status/configuration-revision check. It does not derive an ID, invoke storage, accept a caller-attested flag or callback, or expose a public authority. `JdbcFileRegistry.prelockSources` calls this method after source roots are held. Shared private helpers avoid a second metadata policy. The existing public `require(id,newWrite)` still validates managed identity, then runs the same metadata check, preserving legacy B04 behavior. Post-commit open still uses that original `require` before the actual storage read.

The regression uses a temporary test-only JCA SHA-256 provider, always removed in `finally`. It delegates byte-for-byte digest computation to the JDK SUN implementation and observes the actual ManagedBackendId call stack plus Spring's actual transaction state; it does not stub production validation or assert source strings. The red trace shows `ManagedBackendId.derive → FileBackendCatalog.require → JdbcFileRegistry.prelockSources → JdbcTranscriptAuthority.readDraft` under an actual transaction. The final green run exercises genuine draft acquisition, receipt-aware first bind, worker source read, constructor identity rejection, and actual route corruption. It records **managed SHA-256 outside=5, locked=0**.

Real new fixture route rows are independently changed to mismatched revision, DISABLED status, and wrong type; each fails without opening storage. A separate exact-route disable occurs only after physical read-transaction commit, proving the retained post-commit check denies before open. Only the test's exact new backend ID is altered and restored. Existing READ_ONLY recovery and original-backend pinning are covered by the two inherited selected upload tests.

## T03-IR-02: whole task union precedes every attempt

**Addressed by added acceptance evidence; no production union logic change was needed.** The earlier singleton tests could not establish the required cross-scope property. New tests retrieve the actual registered private production policy through the established test-only reflection pattern and obtain genuine issuer-minted preparations/scopes from actual saved rows, on one genuine EDIT gate and actual transaction holder.

1. Two real distinct input batches/tasks use different completed B04 source files. Both tasks are actually RUNNING with unfinished attempts. Request/scope order is explicitly sorted in descending task UUID order, while expected lock order is ascending. After genuine source receipt acquisition and actual policy validation, the production `prelockReadTasks` must lock both distinct task UUIDs exactly once in ascending order. No attempt lock is present at that boundary. Production finalization then locks the whole attempt union. The trace asserts the last distinct durable-task acquisition precedes the first attempt acquisition. Each genuine scope finalizes once; a second attempt to finalize it fails by its private phase guard.
2. Two independently prepared/minted scopes for the same actual task prove shared task and attempt deduplication: each primary acquisition occurs exactly once, while both scopes independently finalize exactly once.
3. Normal immutable graphs cannot naturally carry contradictory snapshots through the final observation rechecks. To test the union's defensive conflict branch, a clearly labeled **test-only fault injection** replaces one private task-expectation map only after two genuine scopes and sources have been obtained. Conflicting priority expectations fail before any task/attempt acquisition or storage open. This is not a normal business-reachable graph, public API, fabricated public capability, or runtime-success proof.
4. A separate configured-provider orchestration test gives the real registry two distinct scopes over the same file. It proves one whole-scope prelock call, exactly one finalization for each scope, and zero storage opens until both have finalized. This is deliberately labeled protocol orchestration evidence, not production authority proof.

The public product still accepts its existing single operation selector. These tests exercise private production union behavior without adding a multi-request API. The direct production-policy tests use its real holder checks and DB reads; they do not independently claim a public multi-scope end-to-end workflow. Trace receipts distinguish first primary task/attempt acquisition from later locking predicate rechecks.

IR01 has a real RED→GREEN implementation sequence. IR02 was a missing test requirement against code already appearing structurally correct; its new tests passed without an artificial production defect or unrelated refactor. The existing singleton evidence was not rerun merely to reproduce its insufficiency.

## Exact verification window

All fresh commands, full output, source hashes and fresh JUnit XML are retained in this new directory. `verification-summary.json` lists every command and suite. `covering-final.sources.json` exactly matches all final modified/new source bytes in `owned-source-manifest.json`.

| Run | Tests | Failures | Errors | Skips | Meaning |
|---|---:|---:|---:|---:|---|
| hash-red | 1 | 1 | 0 | 0 | Actual locked ManagedBackendId SHA-256 call, before fix |
| hash-green | 5 | 0 | 0 | 0 | ManagedBackendIdentity4 plus genuine boundary1 |
| multiscope-proof | 4 | 0 | 0 | 0 | All new review tests before added post-commit denial assertion |
| covering-final | 35 | 0 | 0 | 0 | Exact final source window |

The final 35 are **4 ManagedBackendIdentity unit tests + 22 existing TranscriptAuthority + 4 new TranscriptAuthorityReview + 3 FileBackendCatalog + 2 selected FileRegistryUpload integration tests**. The upload selectors are `defaultSwitchPinsExistingUploadAndBusinessDownloadToOriginalBackend` and `readOnlyBackendRecoversFrozenResultButRejectsNewPartWrites`. No full reactor unit/IT collection, old restart suite, or S3 runtime was rerun. The previous 349 composed-evidence total remains confined to its originally documented source windows; it is not relabeled as run on this fix.

The previous upload handshake timeout remains unresolved. Its failed run and later healthy retry remain byte-for-byte preserved. This fix neither diagnoses that earlier timeout nor changes connection timeouts or adds a runtime workaround.

## Fixture and evidence custody

The runner guards the exact approved MySQL ID `eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502`, container name `ama-test-b01-ad9f1af29eff`, running state and loopback port58062 before loading only `.tools/phase-b-remaining/env.sh`. Same public JDK21 and Maven/test JVM512MiB/ActiveProcessorCount2 limits apply. Selected helpers were source-inspected: they create fresh target UUID storage/PKI paths; no old private assets, mounts, new containers, reset/repair/DDL or S3 fixture is used. Flyway's existing validation reports 13 current migrations and no migration necessary.

Only exact new IDs tracked by the existing approved fixture teardown are terminalized. The final read-only custody query selects only the **16 exact task UUIDs extracted from this fix's fresh receipts**, not a mutable prefix namespace. All16 tasks are terminal and all16 attempts ended; rows remain retained. `new-exact-task-custody.tsv` and the command/output seal record them. Existing successful historical fixtures and old task rows are untouched.

All **417 files** in the original `task-03/` evidence directory were rechecked against its original manifest/hash and remain unchanged. New evidence lives only in `task-03-fix-01/`. The formal SQL and Task2 creator guard are unchanged. Credential scanning reports zero hits. No generated keys/password bytes are included in evidence. Fresh nonsecret path/owner receipts and lock traces are retained.

## Self-review and handoff

Production change is narrowly limited to20 additional catalog lines/two extracted private checks and one registry call substitution. Constructors retain genuine immutable managed identity validation; metadata type/status/revision comparison is shared, not weakened. Existing public `require`, defaultWrite, readiness, and post-commit open retain their behavior. No authority issuer/holder/receipt/phase or task-union production code changed.

The new security-sensitive methods and tests use normal formatting. Minor M01 broader formatting is not expanded into unrelated churn. M02 remains explicitly unresolved as above. Final audit verifies empty index before owned-path staging, root-only intervening docs, three owned source/test paths, exact final run/source binding, unchanged417 prior evidence files, no schema/creator changes, no credentials, and closed exact new task custody.

Independent rereview should focus on the metadata-only locked path, construction proof, actual post-commit route denial, two-scope ordering/dedup/conflict trace assertions, and absence of new public bypasses. Root provides that review; no gate or overall Phase B approval is claimed here.
