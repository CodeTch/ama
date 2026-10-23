# B06.2 Task3 processing facts reader delivery

2026-10-23. Implementer-only submission for root's independent SPEC and quality review. Exact BASE `ad9960595ab5de6eef7760e2c9094d23666074cb`; owned branch `codex/b06-processing-facts-20261023`, tree `/home/tanren/ama/.worktrees/b06-processing-facts`. Final delivery commit/range is written in root's ignored `task-3-report.md` after the local commit; this tracked report cannot contain its own commit hash. No Task1/2 source was imported. No subagent was spawned.

## Result and actual API

New core interface `backend/core/src/main/java/ama/transcript/ProcessingFactsReader.java` has exactly:

```java
StoredProvenanceFacts readBatch(Actor actor, String meetingId, String batchId,
    ArchiveReadBudget budget, BooleanSupplier cancelled);
StoredProvenanceFacts readStored(Actor actor, String meetingId, String inputId,
    ArchiveReadBudget budget, BooleanSupplier cancelled);
```

New adapter constructor is `JdbcProcessingFactsReader(DataSource source)`. It constructs its private `TransactionRunner`, `JdbcSubjectFences(source, Set.of("MEETING"))` and `JdbcMeetingAccess` from the identical DataSource. No service/bean/provider/API registration, write API or external access capability is added. Both entry points reject every ambient actual Spring transaction before acquiring authority. The current ACTIVE identity and owner EDIT decision come from the existing access adapter, not Actor.status or roles. Before that adapter reads identity display_name LONGTEXT, this reader locks the same identity and bounds its actual OCTET_LENGTH/CHAR_LENGTH without materializing the name. No shared access behavior is changed.

After identity→subject→meeting authorization, ordered locking current reads collect the complete domain set, then sorted file metadata, then one sorted task union. readBatch returns the exact INPUT_BATCH and all ordinal-ordered INPUT_BATCH_FILE rows plus four-field ContentFile(id,sha,size,meetingId). readStored also returns the complete TRANSCRIPTION_INPUT, every owning TRANSCRIPT_REVISION and every actual durable_task WHERE input_kind=TRANSCRIPTION_INPUT/input_id, union every revision producer_task_id. Tasks carry real IDs and every TaskSpec field; flat taskSpecs stays explicitly empty. Producer-only tasks and contradictory extra tasks retain their actual subject/input/fingerprint/fences. Foreign file meetings and ordinal gaps remain visible to S1 validation. Selected input/batch requests must belong to the authorized root; a foreign selected input is unavailable. Missing required files fail unavailable; zero revisions/tasks remain complete empty sets for the pure verifier to judge.

FullRow projections are generated solely from adopted Table.columns, with nullable cells, exact UINT long conversion and UTC six-digit timestamps. Historical row and task fence fields are never replaced with current root values. Test history is manifest meetingRevision7/subjectBusinessVersion9/cancelEpoch2, row base7/epoch2, versus current root20/5; verification succeeds with the historical values unchanged.

Every class queries LIMIT remaining+1 and fails on excess before its excess DTO/list growth. Batch-file limits are independently checked. Long.MAX_VALUE budgets are legal: LIMIT addition saturates, never becomes negative, and no list is preallocated from the budget. Actual aggregate facts fail before Java's technical list capacity (Integer.MAX_VALUE-8) rather than truncate. Edge/scalar accounting uses remaining-capacity subtraction before addition. Manifest OCTET_LENGTH/CHAR_LENGTH is measured with the row locked before the LONGTEXT projection transfers bytes; maxManifestBytes/maxTotalBytes, UTF8/UTF16/scalar limits are checked first. Materialization uses only a bounded getCharacterStream loop, with cancellation before appends and no getString(config_manifest). Every DTO and final owned list copy follows the reserved aggregate row count. Cancellation is rechecked after the defensive copy and after the actual transaction commit, before returning detached facts.

Returned facts confer no write authorization. Archive disk I/O, strict JSON parsing and ConfigBindings compute/verify happen outside this read transaction. Subsequent writes must freshly acquire and recheck their real fence. Standalone acquisition cannot be embedded in an enqueue transaction.

## Exact tests and preserved failures

All evidence is new under `artifacts/phase-b/b06-archives/facts/`. Each test run has its exact command/exit, raw stdout/stderr, run-before source copies and SHA map, and actual available failsafe XML. No compile/setup/zero-test run is called business RED. `runs.json` provides machine-readable counts.

| Run | Exact selector after `python3 tools/checks/run.py it` | Tests / failures / errors / skipped | Ruling |
|---|---|---|---|
| red-01 | ProcessingFactsReaderIT | no IT executed | setup compile failure: ambiguous test Role import |
| red-02 | ProcessingFactsReaderIT | 3 / 0 / 3 / 0 | setup: nonexistent identity updated_at column |
| red-03 | ProcessingFactsReaderIT | 3 / 0 / 3 / 0 | setup: V007 required verified_at omitted |
| red-04 | ProcessingFactsReaderIT | 3 / 0 / 3 / 0 | setup: missing actual storage_backend FK target |
| red-05 | ProcessingFactsReaderIT | 3 / 3 / 0 / 0 | genuine assertion RED: expected full batch2/task2, got0; budget exception absent |
| red-06-expanded | ProcessingFactsReaderIT | 8 / 7 / 1 / 0 | seven assertion failures plus absent stub member causing NoSuchElement; not a clean eight-assertion RED |
| green-01 | ProcessingFactsReaderIT | 8 / 0 / 0 / 0 | initial GREEN |
| red-07-commit-cancel | ProcessingFactsReaderIT#cancellationAtRealReadCommitCannotReturnSuccess | 1 / 1 / 0 / 0 | genuine assertion RED at real JDBC commit boundary |
| red-08-long-max | ProcessingFactsReaderIT#legalLongMaxFactBudgetsReadSmallDataWithoutOverflow | 1 / 1 / 0 / 0 | genuine assertion RED: legal budget wrongly rejected |
| green-02-final | ProcessingFactsReaderIT | 10 / 0 / 0 / 0 | final current source GREEN |

Final ten independent methods cover complete draft/stored verification, exact task identity/full spec/historical fence/UTC microseconds, contradictory task and wrong producer union, missing task/null producer, ordinal gap/extra member/foreign file, current DB status versus stale Actor and non-owner rejection, foreign selected input, ambient rejection, supplier cancellation and thread interruption, actual octet-length preflight before any LONGTEXT projection, domain→file→task current SQL order, tiny row/file/edge budgets, legal Long.MAX fact budgets, second task exceeding remaining facts, and cancellation at actual read commit. The JDBC probe delegates every real connection/result; it only observes SQL and invokes the cancellation callback after a real successful commit. No DB return is replaced, no sleep or giant allocation is used. Actual S1 compute/verify calls occur after reader return.

The required legal IT runner invokes Maven verify, which automatically runs overlapping dependency units. Final green-02-final additionally has fresh XML for 179 unit executions, 0 failures/errors/skips; green-01 and later RED runs also retain their 179-unit XML. They are the same 179 tests, never added across runs as unique cases. Earlier red-01..red-06 automatic unit counts are visible in raw logs but their per-run surefire XML was not copied before overwrite; only their relevant IT XML was retained. The initial compile failure stopped before three integration-module unit tests. No extra explicit old cohort/full-unit run was added; root owns final combined unit qualification. Existing S1 ConfigBindingsTest6, RawHashChainTest25, ConfigWireReaderTest6 and ArchiveBudgetTest15 pass within the final automatically executed 179. No integration selector other than ProcessingFactsReaderIT was run.

Other setup/discovery issues are separate: the initial shell JAVA_HOME/PATH command expanded a Windows-containing PATH before bash and failed parsing, so no Maven/test ran; only the tool-result/reconstructed setup note is available, not a raw test log. A nonsecret MAVEN_ARGS attempt in red-02 was ignored by this wrapper, then removed. Early read-only guessed file patterns/PowerShell loop quoting produced no content/no matches; actual files were then read. Static INSERT audit v1 missed the V009 backend FK; v2 explicitly corrects that omission rather than rewriting its history. All original failure evidence is preserved.

## Source/run linkage

Final green-02-final source snapshots match the current four delivered Java files byte-for-byte:

- ProcessingFactsReader.java: `8dc82c8cf26e38e900f50b6fa0b6ae02227486cdcb19e7e7ef1e3d8bc8ce154d`
- JdbcProcessingFactsReader.java: `7648daf8ce226b05e339d7d67bc4cf37feeaf05dfdd504a3a8973d25d9ef1533`
- ProcessingFactsReaderIT.java: `eefafe5faea6749325d1a9d4537daa265dd64c5a304e54e39f083c50cac66601`
- ArchiveProcessingFixture.java: `d2c7ab3e7f5278674d32321df851efdb9ccb95e86434fa61bac14420f0ff9dd3`

`public-allowlist.json` enumerates every changed public source/tool/report/evidence path; SHA256SUMS covers exactly those paths and excludes itself. Nested .gitattributes applies only to the new evidence subtree. Root .gitattributes is neither changed nor included in these new seals. The sealing script scans only this explicit public allowlist against the new fixture's two private password values without outputting either value. No .tools file is public. Source snapshots precede subsequent replacement of failsafe XML. Final audit/seal helpers are evidence tools, not runtime components.

## One new preserved fixture and formal SQL

Exactly one new MySQL process was created by the new `tools/b06_archives_owned_fixture.py`; the old creator was read only as reference and never executed. Name `ama-test-b01-a5e7d68612a4`; full container ID `363e6bef449436390d3e21041408b53002a48311668c86864befe2535404e45b`; owner label archives-20261023. Image is pinned `mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9` (actual version8.4.6), loopback-only published endpoint, 768MiB memory and equal memory-swap limit, one CPU, pids256, process-local native AIO OFF. Public selected Docker fields never include Config.Env/passwords. Actual initial schema table count was0. Fixture/data/container/anonymous volume remain preserved and running.

The creator initially found an empty assigned HostPort in Docker HostConfig.PortBindings. It had nonetheless created the single bounded process and recorded real initial-zero SQL. The private URL for only this new fixture was corrected from exact-container `docker port`, and the new creator was corrected for future use; no second fixture was created. Raw create argv and selected public metadata remain independent evidence of actual process settings. Do not claim the later corrected creator source was the exact original source-at-create snapshot.

Private environment PATH for authorized later sole use is `/home/tanren/ama/.worktrees/b06-processing-facts/.tools/b06-archives/env.sh`. It is not public and no value is printed. Java21 uses only the public toolchain path specified by root. No old fixture/env/PKI/S3 was read, stopped, reset, repaired or otherwise touched. MysqlTestDatabase.dataSource() is used, never reset().

Before create, all12 formal SQL bytes were compared to exact BASE git objects and SHA256-recorded in lower-sql-before.json. Selected migrations executed once in ordered 001,002,005,006,007,008,009,010,011,011.1,012,013 history. Permanent003/004 gaps remain untouched. Later SchemaMigrator calls only validate current013 and execute zero migrations. post-migration and final-history raw SQL/stdout/exit preserve the actual history/checksum/timestamp/source hashes and scoped metadata provenance. Both audit lower-SQL lists equal the original12 SHA list; no SQL, repair/reset/outOfOrder or new migration exists.

The fixture seeds controlled metadata and real immutable queued TaskSpecs with a test-only unregistered type B06_CONTROLLED_METADATA. Available file metadata is explicitly synthetic, with a distinct READ_ONLY local storage_backend row and no blob/upload/session/purpose qualification. No task ran in a Worker and no production producer key is claimed. Negative task/fence/membership rows are intentionally preserved in this new fixture. A missing mandatory task is tested; an actually dangling nonnull producer_task_id cannot be inserted under the unchanged FK, so wrong existing producer and null/no-task cases cover the attainable reader boundary without disabling constraints.

## Task4 reuse without editing sealed Task3 files

`backend/integration-tests/src/test/java/ama/transcript/ArchiveProcessingFixture.java` is public test-only and compiles against existing S1, never Task1/2. Public constructors: `ArchiveProcessingFixture()` uses the explicitly supplied approved MySQL environment; `ArchiveProcessingFixture(DataSource)` seeds a new randomized independent metadata set on the supplied DS, validates migrations, and never resets. Each constructor commits its own individual controlled SQL inserts; it does not claim an atomic business enqueue or commit-handshake protocol.

Public fields: source, JdbcTemplate j, owner, meeting, batch, input, file, task, backend, actor, archives, manifest, fingerprint. Public helpers: addTask(String taskId,String inputId,String fingerprint,long businessVersion,long cancelEpoch), revision(String nullableProducer) returning a real revision ID; static service(), id(), hash(String), map(Object...), input(StoredProvenanceFacts), B, VERSION, SHA and TIME. Same-package helpers archives(), ref(RawArchive), json(Object), row(Table,Object...) can be inspected/reused by new Task4 code in ama.transcript. revision() seeds revision8 once per fixture due the real unique meeting/revision constraint. addTask() uses this fixture's real subject and a deterministic test-only SHA(taskId) producer, priority-3/maxAttempts3.

The constructor seeds batch.input_fingerprint=SHA(batch UUID) as explicit controlled metadata, not the new ama.input-batch.v1 result. S1 processing compute does not qualify that batch algorithm. For actual Task1 helper use, Task4 should author its own narrowly composed fixture/helper (or seed its own new metadata set) with final batch v1 hash before its durable scenario; it must not rewrite preserved Task3 rows/source to make them new evidence. Task4 may use this class's public SQL building blocks and raw manifest structure, but must supply genuine separate JVM lifecycle and commit ACK proof itself. No production interface expansion is needed.

Controlled raw references independently generated by tools/b06_archives_facts_vectors.py, without importing application code, are preserved under controlled-raw/:

| Type/version | exact raw SHA256 |
|---|---|
| CANONICAL_CODEC / ama.canonical-value.v1 | 2b553c99cecc917b12a466cd62859df2ac18533e518476aa1b843279f2eb6e19 |
| PARSER_CONFIG / ama.strict-utf8-lines.v1 | 7b9f57377ad401fe040287e133f3efa81d9956ea581a5e497ee6c0835051962a |
| CONFIRMATION_HASH / ama.transcript-confirmation.v1 | 2f94e75e8d53ef37a152a1ccd63b54a61a6853e07508a5c927a4ba8e8209c0aa |
| PROCESSING_CONFIG / b06.controlled.import-txt.1 | 2efd9361bb610198a2a59c3fb7256870229a14a1aeb72751dd8f3f4155c13f8e |

Processing has the sorted three dependencies codec/confirmation/parser; confirmation depends on codec; others have none. Full parse budget is (65536,65536,2048); canonical budget (16777216,32,1000000,2097152,250000); acquisition B (65536,1048576,16,32,8,200000,32,255,65536,262144,1048576,65536,32,250000,500000). There are no omitted definitions or {} sentinels. Independent raw SHA is not called a typed business digest. The final seal exports actual bounded SQL manifest bytes, verifies their actual SQL SHA/size, and compares canonical manifests' root refs against the independent raw fixture; this is cross-check evidence rather than durable archive-source authority.

## Scope and remaining limits

This is processing-only authorized bounded acquisition, not full B06 acceptance. No actual upload/registry file-reference/download authorization, complete segment/marks/confirmation/parent/material graph, production task domain/provider/WorkerLoop policy, HTTP/API/idempotency/save/edit/confirm path, company send, external delivery or capacity/HA claim is present. No durable archive source or cross-JVM restart is claimed by Task3; Task2/4 own those. All arbitrary writers still must obey adopted root-first locking and immutable TaskSpec discipline; an opaque read snapshot is not permission for later mutation.

Cancellation is observed at acquisition/row/stream/copy/commit boundaries and preserves thread interruption. Own JDBC queries request a5-second query timeout; existing shared access SQL retains its established DS/driver locking behavior. This reader does not claim asynchronous cancellation of arbitrary blocking driver I/O or a universal30-second transaction deadline. It performs no archive or network resolver I/O inside the transaction. No other integration selectors or schema-changing cohorts ran, and no push/main merge/production activation occurred. All old tests/goldens/artifacts and root attributes remain unchanged.
