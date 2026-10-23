# B06.2 Task4 cross-JVM archive/queued-metadata report

2026-10-23. Implementer submission for root's fresh independent SPEC and quality review; no adoption or full B06 acceptance is claimed. Exact reviewed Task4 BASE is `5563f4cebd6f3f8d5c44d67c4e9b54f0a1d83d00`; exclusive tree `/home/tanren/ama/.worktrees/b06-archive-restart`, branch `codex/b06-archive-restart-20261023`. Review range is BASE through this delivery HEAD; the exact resulting commit is recorded in root's ignored `task-4-report.md` after the local commit, avoiding a report/commit self-reference.

## Result and precise boundary

The new lifecycle test proves genuine queue acceptance followed by an actual JVM process boundary: A reads real bounded batch/file facts, computes the old processing fingerprint outside transactions, then commits the exact canonical input and a full TaskSpec through `JdbcTaskQueue.enqueue`. A fresh B process opens appended pinned index2 and recovers the unchanged persisted manifest/root.1/digest through the adopted authorized standalone facts reader, manifest ref discovery, archive source and S1 verifier. A separate new draft explicitly selects root.2. Three further fresh processes reject missing-history, mutated-history and wrong-index-pin copies; the positive releases and checked DB state remain intact.

This is controlled metadata qualification. No qualified upload/session/reference, actual file-provider read, TXT parser execution, transcript output or producer revision, worker completion, HTTP workflow, production policy/provider, current production default, capacity/HA/power-loss or full B06 acceptance is claimed. Actual owning transcript revisions are a complete empty set, so there is no invented producer_task_id. The genuine queued task ID, producer key and every immutable TaskSpec field are read back from MySQL and compared. No `{}` sentinel, direct durable_task INSERT, fake StoredTask DTO or historical9/2 placeholder is used for acceptance.

## Short-write composition and API decisions

- New `ArchiveRestartFixture` seeds unique bounded metadata at matching current meeting/task-subject fence20/5. Batch digest uses the adopted `BatchFingerprint` v1; a standalone authorized read then verifies the actual saved batch/member/file facts before the processing draft.
- Every archive read, canonical manifest/producer computation, strict JSON/S1 operation and original-byte comparison occurs outside write transactions. Helpers assert this boundary. The short write reacquires authentic `JdbcMeetingAccess` EDIT gate and rechecks current owner/fence plus complete batch/member/immutable file facts under locks. It inserts the exact precomputed input, adopts that authentic gate into `JdbcTaskPreparations`, enqueues through the real JDBC queue and flushes the outbox once at the outer end.
- A's ACK is emitted only after `TransactionRunner.required` returns from actual outer commit, followed by separate authorized stored facts/S1/fullspec/READY/outbox verification. Nothing in a transaction callback is labelled commit ACK. Original manifest bytes and postcommit handoff stay on disk for fresh B.
- This composition starts from a committed batch, detached read and fixed-fact recheck. It does not add a reader for an uncommitted new batch or consume held transaction facts. The standalone reader still rejects ambient transactions; the future B06.3 held-capability/uncommitted-facts seam remains unsupported and separately reviewed.
- The only registered policy is `B06_RESTART_TEST`, with explicit authentic preparation and closed input provenance checks. It rejects output/external work. Producer key is a typed canonical digest with explicit TEST domain `ama.b06-restart-test-producer.v1`, kind TASK_PRODUCER and taskType/meetingId/inputId/inputFingerprint/businessVersion/cancelEpoch. It does not assert the proposed production producer contract.
- No shared interface gap or source change was needed. No sealed Task1/2/3 file, old evidence/report/golden, production policy/API/Worker/Supplier/upload implementation, SQL, schema, root attributes or unrelated fixture was changed.

## Assertion RED, GREEN and all other executions

Each canonical run has the exact command, raw stdout/stderr/exit, source-before-run copies/SHA map and fresh JUnit XML. Compilation/setup remains separate in `setup-history.md`.

| Run | Command after `python3 tools/checks/run.py` | IT tests/failures/errors/skips | Automatic unit tests/failures/errors/skips | Result |
|---|---|---|---|---|
| red-01 | `it ConfigArchiveRestartIT` | 0/0/0/0 | 210/0/0/0 | exit1, integration compilation syntax setup |
| red-02 | `it ConfigArchiveRestartIT` | 0/0/0/0 | 210/0/0/0 | exit1, ambiguous Role compilation setup |
| red-03 | `it ConfigArchiveRestartIT` | 1/1/0/0 | 213/0/0/0 | exit1, genuine parent recovery assertion RED |
| green-01 | `it ConfigArchiveRestartIT` | 1/0/0/0 | 213/0/0/0 | exit0, complete lifecycle GREEN |
| unit-batch | `unit BatchFingerprintTest` | none | 6/0/0/0 | exit0, precise explicit unit run |
| unit-index | `unit ControlledConfigArchiveSourceTest` | none | 24/0/0/0 | exit0, precise original archive tests |
| unit-index-fix1 | `unit ControlledArchiveFix1Test` | none | 4/0/0/0 | exit0, precise archive fix1 tests |

There is one restart test method/invocation, with five actual child JVMs in GREEN: A, B and three negatives. It is not five JUnit tests or a parameterized method. The canonical IT runner's213 units include the now-adopted batch6 and archive24+4 tests, plus the integration module's3 support units; the prepared Task3-era179-unit count is not current. Dependency-unit executions overlap across runs and with the three precise unit selectors; they do not create multiplied independent coverage. Setup runs stop before the integration module's3 support units, explaining210. Root owns final combined current full-unit verification; no additional whole-unit or old integration cohort was run by this author.

RED source snapshot A genuinely committed task `09572100-e72e-4cbf-b975-490d2adf8d92`, PID647004, exited0 with COMMIT_ACK. Fresh B PID647060 used the new default's closure against the unchanged old persisted manifest, returned typed `PROFILE_UNAVAILABLE` and exited23. Actual XML parent assertion reports expected recovery exit0 versus23, with1 failure and0 errors. This domain exception alone is not called the assertion RED. Original RED releases/rows remain preserved.

For GREEN, the test source, helper source and process invocation remain byte-identical to real RED. Only B's historical roots selection changes from `List.of(defaultroot2)` to `ProcessingManifestRefs.roots(originalPersistedManifest,...)`; `red-to-green-probe.diff` records the complete difference. The helper/test/current probe match the successful source-before-run snapshots.

## Actual GREEN process and raw provenance

Retained run `green-01/runtime-548b3cd2-901e-41b0-9086-2a46e0a8163d` is identified by its actual directory under restart evidence rather than a mutable alias. Exact receipts and ProcessBuilder arguments/classpath are included, with no secret arguments or environment dump. `Process.pid()` is compared to each child's own current PID; process waits are bounded30 seconds and only owned still-running children may be terminated.

| Role | Actual PID | Exit | Receipt |
|---|---:|---:|---|
| A accepted/committed old input | 649176 | 0 | COMMIT_ACK |
| B old recovery + new independent draft | 649230 | 0 | RECOVERED |
| missing historical object copy | 649265 | 23 | UNAVAILABLE / PROFILE_UNAVAILABLE |
| same-size mutated historical object copy | 649289 | 23 | UNAVAILABLE / PROFILE_UNAVAILABLE |
| wrong exact index SHA copy | 649375 | 23 | UNAVAILABLE / PROFILE_UNAVAILABLE |

GREEN input `fda32e16-c387-4603-8e22-4f983429b936`; actual task `6dc632c2-dc51-46f0-ba37-3d84d873c76a`; current meeting `03102320-06cf-49e0-a5ef-7d6af9b0ed45`; producer key `dcdf321e0c223e9161cda7bfe5bc04c330380ed8e0ddee9fa33bd87e5a069e8e`.

- Index1 `c6e3b29cedbdc5234f368fa6d1f7152442c95f56b0caaf52bc8019bafea13e9d`; appended index2 `d2436e244f966e5841941e282b0f6ad0dc6c76c6369cb90203dab42ed876472d`. Both actual publisher returns equal adopted independent literal SHA values.
- Old processing raw SHA `2efd9361bb610198a2a59c3fb7256870229a14a1aeb72751dd8f3f4155c13f8e`; original persisted manifest raw SHA `26759402b72105075210b3e588c35387f0a5b6cd622a35407ee5a8fce824cedc`.
- Original and fresh-recovered business digest `b322434caf46dbb33cf2026396e8b3c069847a94803e83fce06e5592d0a6f640`; independent root.2 draft digest `f31844f7210e517a72f1456730e681c2c08fd62e25157d5402900811e6442c02`. Business digest is not raw JSON SHA.
- Before/after checked DB snapshot `2a0312b070f6a1e231071b1bb6f6a2b5755f1b8b8a60b07394c5adcc8befa6a1` covers full input/task/outbox/current meeting/subject rows and relevant table counts. Each negative has no recovered/draft-success receipt, and the same snapshot is rechecked after it. This is the stated bounded metadata snapshot, not an unrestricted dump of every unrelated row.
- Missing/mutation negatives alter only their own copy's root.1 object under release2, while release1 and the complete positive root stay intact. Wrong-pin uses another unique copy. After all negatives the original positive snapshot reopens with five entries.

## Fixture custody, lower SQL and preservation

Root explicitly transferred sole fixture custody from completed Task3: name `ama-test-b01-a5e7d68612a4`, ID `363e6bef449436390d3e21041408b53002a48311668c86864befe2535404e45b`, owner archives-20261023. The private env was loaded only from the authorized path `/home/tanren/ama/.worktrees/b06-processing-facts/.tools/b06-archives/env.sh`; no value is in delivery. Java21 used only the public path specified in the brief. No old environment/fixture/PKI/S3 access, container creation/stop/reset, migration repair or data cleanup occurred.

`custody-before/` and `custody-final/` contain read-only selected-container ID/image/resource/loopback/owner proof, exact public SQL, raw output/exit and twelve formal lower SQL SHA values. Both compare the complete twelve-row Flyway history byte-for-byte to Task3's final history, through013 with permanent003/004 gaps. No migrate/reset method is called by the new fixture. Initial-zero/image/nativeAIO/resource qualification remains Task3's historical evidence, referenced by path; Task4 does not invent a new zero database.

The finalizer compares every manifest present at custody transfer with the final actual DB SHA by input ID and preserves all those prior hashes. New RED/GREEN rows remain in the fixture; every unique runtime positive release and negative copy stays on disk under versioned public evidence. Source dependencies are hashed against exact BASE bytes; unchanged peer source/history seals are not rewritten or falsely checked against corrected current source. Original Task2 f58-era and Task3 2986957-era source seals remain historical, while their independently adopted fix1 manifests govern current corrected source.

## New allowlist, seal and handoff

Only these new path groups are deliverable: the three restart Java test/helper files; new `backend/integration-tests/src/test/resources/ama/transcript/restart/` literal copies/nested attributes; three `tools/b06_archives_restart_*.py` tools; new integration/report documents; and `artifacts/phase-b/b06-archives/restart/` evidence/nested attributes. The public allowlist enumerates every exact file. SHA256SUMS covers the new public delivery and excludes only itself; no private .tools file or root .gitattributes is hashed. Secret checking compares allowed bytes against private password values without printing values. Nested attributes preserve literal evidence bytes only inside the new directories.

`runs.json` reports actual XML counts. `current-source-dependencies.json` records exact current immutable source SHA/BASE association; run snapshots preserve the code actually executed before any report replacement. Adopted independent literal copies are byte-checked against Task2 originals. Root's ignored final report records exact final BASE..HEAD, allowlist/seal SHA and clean local commit state. Root dispatches independent review and final combined units; this author has not reviewed their own submission as an independent reviewer, adopted it, merged, pushed or activated production.

Rulings: use the already committed metadata + detached draft + authentic recheck composition because no ambient reader exists; retain one bounded lifecycle method/five GREEN child JVMs rather than count child cases as tests; preserve runtime directories directly in new public evidence instead of @TempDir cleanup; leave final combined full units to root as explicitly instructed; count compilation attempts solely as setup and retain all raw evidence. No finding was silently deferred or existing requirement repaired outside scope.
