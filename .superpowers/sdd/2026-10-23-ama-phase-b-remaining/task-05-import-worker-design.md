# Task 5 — actual TXT Worker, measured batch parsing and atomic RAW

2026-10-23. DESIGN ONLY; independent review, implementation and activation pending. Sole output: this report. No implementation/tests/Git mutation/DB/Docker/private environment access/subagents/fixture creation. No new DDL. W is .superpowers/sdd/2026-10-23-ama-phase-b-remaining in /home/tanren/ama/.worktrees/phase-b-completion.

## 1. Sources and status

Read Task5 brief/plan-extract/parser-fixture-preflight, runtime-author-common, controlled-runtime specification including Normative R1, authority/external adoption specification, Task1 completion report, adopted Task3 original/R1/pre-gate-read R1, Task4 intake R1, and Task7 Worker ownership/monitor/close. Facade names remain provisional until Task3/4 source adoption. Read-only baseline resolved to 3e393dbc9485b187b89bfc03abd88821a0aabddb. Initial Windows Git ownership rejection was resolved by a per-command safe.directory argument, not persistent settings. One current FileRegistry interface read exposed changing Task3 signatures; exclude it as source authority, and baseline was reread using git show. No changing Task3 implementation is reviewed or adopted.

Exact design-input SHA256:

| W document | SHA256 |
|---|---|
| task-03-authority-design.md | 121b58e04c0077d8a7e46b5efbae49d5d0b2955308d2ad486fdb0c017a0155ba |
| task-03-authority-design-r1.md | 6fcb1f1d816cd96d389f83e67b0ceaba3298c4657cda3ed8bdcc27f3ab6537e9 |
| task-03-pre-gate-read-addendum-r1.md | 58e085bc64a85f89e9ff3c9bdabf8dce31d5feb2205ebed46b56dfa12101387a |
| task-04-intake-design-r1.md | fcbde3fa7885d0fe3553646c2fbec28e9ad6da78aba4678f8e29461c3f35666b |

Actual source read: WorkerLoop/Main/Runtime/RuntimeProvider/PreparedWorkerLoopTest; TaskQueue/TaskDomainPolicy/JdbcTaskQueue/TaskContracts; TranscriptDocumentParser/TextTranscriptParser; ConfigBindings/ConfigSchema/ConfigArchiveSource/ControlledConfigArchiveSource; JdbcProcessingFactsReader; V011/V013.1; API MeetingConfiguration/FileConfiguration/RuntimeConfiguration/DeploymentConfig and module POMs; LocalFileClient/ManagedBackendAdapters/S3 RangeReader/S3Objects; ConfigArchiveRestartIT/test TaskWorkerProvider/public tools/dev/s3_fixture.py. Inspected S3FileStorage timeout/close and file-service HTTPS/read/start locations. None establishes runtime PASS.

| Stable source | SHA256 |
|---|---|
| backend/worker/src/main/java/ama/worker/WorkerLoop.java | 4479f78fc37566a2bf6ebdb3118512e377bd0987808d4ba1e246f8aba05f4027 |
| backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java | 83c2ba70b1a80449e83e49b0e5bf6efbd4574c7597bc687d2c41721768794b14 |
| backend/adapters/src/main/java/ama/transcript/TextTranscriptParser.java | 497c1bfbbab55554b3fc8dc26c4b4f0031cba0ecae35ecd9979a5bb7394ac04a |
| backend/core/src/main/java/ama/transcript/TranscriptDocumentParser.java | cfb4c1d8e31a86367f82c7e02148321ff8588576654937fe12aaf27a587b10f3 |
| backend/adapters/src/main/resources/db/migration/V011__transcript_schema.sql | a926c8b5d9806082d248f38bc7c7dd2bdbc46dd319d34b787e02f86132096ccb |

Concrete gaps: no production provider, only TEST_TASK test SPI; worker currently depends on core while API owns backend construction; no installed-loop health or handler cancellation context. Close immediately stops heartbeats/interrupts workers, waiting2s per pool. Lease hardcoded20s, heartbeat/poll5s. Parser counts exact decodedUTF16 and bytes but exposes neither. Standalone JdbcProcessingFactsReader constructs another B03 issuer, unsuitable for shared runtime composition. Local request timeout is not evidence of bounded streamed-body reads.

## 2. Cycle-free runtime and actual installation

One explicit independent importer process and existing WorkerLoop; add ama-worker -> ama-adapters. API already depends on adapters, adapters on core. No worker->api/adapters->worker edge, controller reuse or second scheduler.

Extract/reuse a non-Spring adapters assembly factory (provisional TranscriptRuntimeAssembly) from Task4 wiring. Inputs are validated deployment data and exact process-owned DataSource/TransactionRunner/JdbcSubjectFences/JdbcMeetingAccess/JdbcOutbox/backends. API supplies existing beans; worker constructs one of each. Factory creates Task3 authority/complete policy/registry through private pre-exposure attach-once, verifies exact objects, then production ImportTaskDomainPolicy/JdbcTaskQueue. Merge concrete meeting/transcription event validators with duplicate rejection before outbox construction. No reference-policy->queue/outbox/service cycle; no static/ThreadLocal authority, URL-equivalent DataSource, missing-provider fallback, public setter/Lazy bean. All components in each JVM share its identical source/B03/registry/private issuer/policy. Separate JVMs acquire fresh DB authority with internally consistent local instances.

Move property-to-ManagedBackendAdapters mapping into shared typed adapters factory; API Environment only maps settings. Preserve managed identity, exact historical backend/revision/status, production S3 HTTPS, local mTLS, actual TRANSCRIPT_INPUT purpose/complete INPUT_BATCH_FILE registration. No route inference from default/DeploymentConfig defaults. Catalog startup registration never migrates objects. READ_ONLY historical route remains readable; DISABLED fails. Writable-default health and historical-task readability are distinct.

Actual bootstrap: TranscriptWorkerMain explicitly constructs TranscriptWorkerRuntimeProvider returning composite runtime with actual WorkerLoop prepared mode, queue, pool TRANSCRIPT_IMPORT, handler TRANSCRIPT_IMPORT_V1, configured capacity/workerId, bounded stream watchdog and management health. Add small WorkerMain.run(WorkerRuntime) lifecycle helper used by explicit entry and existing SPI entry. Keep exact-one-ServiceLoader behavior; no production META-INF service entry colliding with integration-tests TaskWorkerProvider. Production launches TranscriptWorkerMain and truly starts/polls/claims. Missing reviewed settings/archive/policy fails before polling. Partial construction closes resources. No model/publication handlers installed.

Proposed private /internal/worker/ready reports bounded fixed schema: installation/boot IDs, exact handler/pool/profile, STARTING/RUNNING/STOPPING/UNAVAILABLE, actual poll age/active count/safe reason. Configured loopback/private management binding, mTLS exact peer identity, finite request/body/timeouts; no text/routes/credentials/SQL/DB URL. API adapters owns authenticated bounded probe, no worker dependency. Unconfigured => NOT_INSTALLED; stale/unreachable/wrong identity/profile => UNAVAILABLE; fresh actual-running => INSTALLED. Class/config presence/task count cannot prove installation.

WorkerLoop exposes observation from existing start/poll/claim/renew/failure paths, no health queue scanner. Empty successful poll proves queue reachability; saturated pool reports bounded active progress without requiring a new claim. Stalled scanner fails finite health-age bound. Missing global archive snapshot disables runtime; individual damaged input may fail while installation stays healthy. No historical source open for health. Task4 canSubmit may be true without execution; overall processing readiness requires installed healthy execution. Accepted replay remains accessible despite current intake/worker unavailability per Task4 R1.

## 3. Bounds, cancellation and close

Required finite typed settings: pool capacity; file count; aggregate bytes/decodedUTF16/segments; metadata/archive/index bounds; connect/body-idle/file/batch deadline; DB statement/lock/transaction bounds; close bound; health age. No unlimited queue/executor/thread-per-read. One open source per worker slot, immutable ordinal processing. Bound retained output, readback copy and JDBC chunks. Check collection int limits/subtraction/overflow before growth. Effective content bounds intersect original archived per-file budget with explicit deployment aggregate capability. Lowered operational limits can make old task unavailable but never rewrite its archive. No hidden filename quota/parser selection/clipping/firstN.

Declare and validate supported lease20s/heartbeat5s/poll5s; reject different values. No broad timing refactor. DB limits leave renewal margin (candidate2s statement/lock and10s completion; root freezes actual settings). I/O deadlines may span renewals but remain finite.

Minimal additive seam preserving PreparedHandler/factory/tests: contextual prepared factory receives ContextualPreparedHandler.prepare(TaskLease, PreparationCancellation). Context exposes read-only sticky lost/closed/cancelled observations plus bounded registration of the current source close/abort action. It exposes no completion/renew/lease-grant/authority method. Fake signal can only deny work; DB policy still decides permission. Old handler adapts unchanged. Pass signal into parser/metadata/archive work; watchdog closes registered stream on deadline/loss/close. Registration after cancellation immediately closes and denies; release idempotent. No second lease timer.

Align with adopted Task7 per-execution monitor: serialize sticky renewal loss, callback-entry admission and close/cancel selection. Monitor may cover bounded JDBC renewal, never network/parse/archive/hash. Preserve checks before dispatch and inside Supplier after lock waits. Loss/close winning before entry means zero writer. Completion admitted first still needs actual current queue/DB authority; later cancellation cannot undo committed output. Writer checks cancellation between bounded chunks/before return and throws to roll back. No new authority DTO or ThreadLocal.

Close atomically disables claims, signals contexts closed/cancelled, prevents callback admission, closes/aborts streams outside monitor, interrupts/joins workers boundedly, then closes heartbeat/clients/DB. Optional explicit quiesce-before-close stops claims while keeping in-flight heartbeats and permits bounded draining; once close signal is set, future callbacks are denied. Do not stop heartbeat while allowing silent post-close preparation commit. Preserve interrupt/idempotence/finite total shutdown/ordinary lease recovery. Task7 later adds MARKING/OPERATION handoff under same monitor; Task5 does not implement external behavior or release model permits. Local semaphore release is not permit settlement.

## 4. Original input/archive/source preparation

Only actual registered TRANSCRIPT_IMPORT_V1/TRANSCRIPT_IMPORT lease, MEETING subject/TRANSCRIPTION_INPUT input, priority0/maxAttempts3. Exact canonical producer envelope/full TaskSpec comparison mandatory; manifest IMPORT_TXT step is not queue type.

1. Reject ambient transaction. Thin runtime facts reader uses configured Task3 authority/B03 and bounded actual input/batch/sidecar/member/producer facts. Discover owner before root, reject owner changes, follow adopted full rank. Close standalone read before archive work. Do not instantiate old JdbcProcessingFactsReader separate issuer, mint held authority from DTO, or retain holder; preserve old reader/regressions.
2. Strictly read actual stored manifest outside roots: original IMPORT/TXT/EMPTY, exact IDs/order/SHA/size/base/cancel/spec. Load four original refs PROCESSING_CONFIG/PARSER_CONFIG/CANONICAL_CODEC/CONFIRMATION_HASH from configured immutable index with manifest-byte reservation. ConfigBindings.verifyProcessing checks actual rows/producer, raw SHA/canonical/schema/refs/edges/complete closure/fingerprint. Current processing default never reinterprets input. Missing old ref/profile fails closed.
3. Add data-only parseBudget() to ValidatedProcessingBinding, populated from existing verified ConfigSchema.parseBudget. No second handwritten config parser or caller-attested budget. Wire/hash/profile unchanged. Require actual ama.strict-utf8-lines.v1 measured implementation.
4. Before streams validate complete file count and checked declared aggregate/per-file bytes. All graph/string/manifest limits before allocation/LONGTEXT transfer; metadata size is admission, not actual-byte proof. No filename used. Later exact observations must match, never adapt changed input.
5. Immutable ordinal loop uses adopted ProcessingSourceReader.open(lease,inputId,fileId): complete registered policy, one B03 gate, full domain/original sessions/files, all tasks before attempts, actual RUNNING/current lease/unfinished attempt/DB expiry, then successful commit and private historical pin/open. No browser generic download/direct key/public pin/DTO expiry/late queue.validLease. Fresh owner EDIT/never-published/lifecycle for every source.
6. Compare returned ID/meeting/SHA/size and full offset0,length=size to prepared original. Stream through measured parser with EOF/digest/size/aggregate checks. Close before accepting file result; any open/read/parse/close/cancel failure discards all accumulated segments. No transaction over archive/I/O/hash/parse/close. After every stream closes, produce private immutable prepared rows/spec/observations, data only, with no retained held authority.

Failure leaves no RAW/segments/marks/output. Correct task failure/retry metadata is distinct from partial domain output.

## 5. Exact measured parsing, old-contract compatibility

Choose separate additive MeasuredTranscriptDocumentParser extends TranscriptDocumentParser. Leave parse signature, Result record components, Segment fields, ErrorCode meaning, profile/version and stable-ID algorithm unchanged. Add:

```java
record Usage(long sourceBytes, long decodedUtf16CodeUnits, int segments) {}
record MeasuredResult(TranscriptDocumentParser.Result result, Usage usage) {}
MeasuredResult parseMeasured(InputStream source, UUID fileId, String expectedSha,
    ParseBudget budget, BooleanSupplier cancelled) throws ParseException;
```

TextTranscriptParser implements this port. Old parse delegates to same internal implementation and returns measured.result; no changed old success/exception behavior. Successful usage comes from actual BoundedSource.used, Lines.units and segments.size after EOF/digest/finishLine/final cancellation/NO_TEXT checks. No guessed fallback for old implementations; absent measured capability unavailable. No partial usage result on failure, user callback inside parser or new persistence/hash field. Stable UUID remains exact length-prefixed(profile,fileId,sourceHash,paragraphNo) SHA with original UUIDv8 bits.

Lines.units counts BEFORE BOM removal, CRLF handling, blank dropping and line growth: initial BOM1, CRLF2, every blank separator, supplementary character2. Count all decoded source, not returned text lengths. EOF finishLine adds no invented unit. Bytes include BOM/separators in original digest; successful usage equals actual file size. Existing remaining+one overflow sentinel remains. No whitespace normalization/profile change/source-hash substitution.

Whole-batch algorithm: usedBytes/units/segments initially0. Before fileN, remaining=aggregateLimit-used; reject known declared bytes greater than remaining before open. Per-dimension parser budget=min(original per-file bound, aggregate remainder). If a necessary positive units/segments budget is exhausted before another required nonempty TXT file, return batch-exceeded, not parser INVALID_ARGUMENT; preserve per-file NO_TEXT. Parser bounds actual byte/line/segment growth against remainder. On success require usage bytes==completed metadata.size, segments==result size, exact profile; checked-add usage then append only after cardinality admission. No capacity allocation from arbitrary numeric budget. Fixed bounded decoder read-ahead is allowed but never unbounded retained text. Global persisted ordinals1..total concatenate file order; source paragraph numbers/stable IDs remain original.

Required proof: multi-file tiny returned text with BOM/blank/CRLF exhausting full units; split UTF8/supplementary sequences across reads; exact limit/limit+1; byte growth/shrink/hash/NO_TEXT/NUL/UTF8/cancellation. Old/new parse result equality and independent golden stable IDs must remain unchanged. Last-file aggregate failure leaves zero domain writes from earlier files.

## 6. Real source deadline/abort

Parser never closes source or guarantees interruption of blocked I/O. Handler owns Download try-with-resources and context-registered close/abort. Finite watchdog capacity bounded by active worker slots; this is I/O cancellation, not task lease scheduling. Deadline/loss/close sets sticky cancellation and closes exact body; wrapper checks before/after reads and before accepting success. Timed-out work cannot race to success. Release watchdog registration/transport permits once on EOF/error/timeout/rejected callback/close. Close exception denies RAW success.

S3 RangeReader validates206, content-range/response length/version-or-ETag and exact EOF; S3Objects.release aborts and releases permit. S3FileStorage config has connection/socket/API bounds. Preserve and qualify actual GET/open/stalled body/abort with owned S3. Do not rely on API timeout after stream return. Whole-file/batch cap applies even with periodic progress. Known-length full range must still count exact actual bytes; metadata/stat is insufficient without parser digest/EOF.

LocalFileClient uses ofInputStream, request timeout5minutes/connect10s; this does NOT prove returned-body deadline. Add explicit configured streaming body timeout/close at this boundary or reviewed transport with proven body-idle bound; preserve mTLS/redirect denial/wire. Qualify that close releases an actual blocked read; open/header wait also needs finite bound before a body exists. Bound error body reads too. No unbounded per-read threads or success with an abandoned permanent reader. Failure to demonstrate abort/unblock leaves local execution unavailable. Importer never bypasses managed HTTPS by direct filesystem access.

## 7. Atomic RAW, exact revision, one completion owner

Handler returns one Supplier<TaskResult>, never completes/renews task or advances meeting/subject. Queue is sole completion owner:

```text
WorkerLoop preparation outside transaction
 -> queue.completeWithEffects(lease,Supplier)
 -> authentic policy identity -> subject+meeting -> complete existing domain
 -> all original sessions -> all files -> task/attempt
 -> exact current lease/fence/input -> callback-entry lost/closed guard
 -> bounded new RAW+segment insert/readback
 -> return SUCCEEDED/TRANSCRIPT_REVISION:newRawId
 -> registered policy validates ACTUAL output
 -> task/attempt/audit/outbox -> one final flush -> commit
```

Preserve Task1 Supplier order and old Runnable semantics. completing is set before queue call, so failure/unknown commit ACK never triggers second ordinary completion. False queue result invokes no writer and discards preparation. Lost heartbeat before callback suppresses effects even after lock waiting; reclaim only through actual queue on expiry.

Supplier is trusted composition, not an authority source. Internal DB-only writer has no public controller/service entry, requires active configured-source transaction, and receives prepared data only. It neither mints Task3 token, acquires another gate nor caches authority. Registered policy authentic acquisition owns authorization; writer/output validator perform bounded exact DB checks of prelocked existing/new rows as adopted Task3/4 designs permit. If final source lacks necessary prelock/read capability, report precise facade amendment instead of adding DTO authority or late locks.

Reserve new RAW UUID/timestamp outside completion. Existing FK parents meeting,batch,input,producer task,source files already held; parent_id and speaker fields null. Insert RAW then all segments in bounded JDBC chunks, verify affected counts. Read just-created rows under permitted new-row exception; never acquire a new existing root after task locks. Duplicate UUID/version conflicts abort; no UPSERT/ignore-insert/overwrite/adoption of another task's output. No marks/speakers/WORKING/confirmation/current-pointer mutation.

Actual schema column is transcript_revision.revision, NOT revision_no, with UNIQUE transcript_history(meeting_id,revision). Set exactly immutable actual producer TaskSpec.businessVersion, equal input.base_revision/batch.base_revision and current meeting/subject at successful ordinary completion. Intake already advanced r->r+1; RAW does not advance again. Owner history later checks equality to ORIGINAL producer version <= current, never equal today's version. Task6 WORKING save uses its later meeting version. Concurrent business write winning first makes old import stale, not eligible for MAX(revision)+1/rebase. Existing row at same version with otherwise fresh facts is conflict/integrity failure, not renumbering. Schema has no unique producer_task_id; enforce exactly one matching RAW through serialized root/task and actual output validation, do not assume missing SQL constraint.

RAW fields: same meeting/batch/processing input/fingerprint, actual lease task producer, kind RAW,parent null, exact authorized processing-owner created_by, server timestamp. This actor attribution does not make RAW a human save. Segment body/stableID/source file/source paragraph unchanged, global ordinal, null speaker/start/end. Prepare verifies complete ordering/uniqueness/scalars/nonblank text; underlocks bounded length-gated character reads compare actual complete rows/bodies to preparation without hashing/canonicalization. Policy independently validates actual RAW/input/task/version/fingerprint, segment order/count/source membership and empty marks; return-ID alone insufficient. DB created-row readback is actual-state evidence, not detached success minting.

Queue TASK_SUCCEEDED keeps existing TRANSCRIPTION_INPUT event reference. Any new RAW_CREATED event requires concrete TRANSCRIPT_REVISION validator registered before use. Audit contains safe IDs/revision/outcome only. DB-only reference validation, audit/outbox staging follows actual output validation, one final flush last/no later SQL. Null/wrong result, middle insert, readback, output validator, audit/event/flush/commit failure rolls back RAW+segments+task output/attempt/events together. Unknown ACK rediscovered via real task output, not immediate second completion.

## 8. Error and cancellation outcomes

Importer has no external side effects; it cannot use RECONCILING as invented business state. Map named preparation failures into no-write Supplier returning allowed FAILED/RETRYABLE with null output and fixed safe code. Immutable invalid UTF8/NUL/NO_TEXT/hash/size/manifest/profile/budget -> FAILED. Named bounded transient storage/DB/archive acquisition unavailable may -> RETRYABLE; existing maxAttempts3/backoff bounds it. Missing exact historical artifact/schema contradiction never selects latest. Any operator correction uses subsequent explicit intake, not reinterpretation.

Shutdown/interruption must preserve interrupt and suppress callback via context; ordinary actual lease expiry/reclaim handles abandoned attempt. Subject cancellation/version/revocation remains actual policy fence. Existing generic WorkerLoop unexpected exception quarantine attempts RECONCILING; import policy must reject it, leaving no domain output and allowing normal eventual expiry/attempt-limit. Do not relax policy merely to make fallback pass. Expected parse/storage failures map before generic fallback. No catch-all converted to success or zero-effect external proof.

Logs/client errors allowlisted codes/IDs/attempts; no exception messages, source text, provider URLs/bodies, SQL, archive paths or credentials. Worker health reveals installation only. B04 source grant is one-shot at open (periodic D06 revocation remains separate), but source cancellation closes on known loss and completion always rechecks fresh authority.

## 9. Real local/S3/queue/Worker evidence and fresh JVM restart

Main proof uses current owned MySQL, actual uploads/intake/JdbcTaskQueue/WorkerLoop, actual managed local HTTPS and S3 source bytes, real strict parser. SQL-seeded RAW cannot be its main proof. Source-controlled historical fixtures remain only narrow labelled reader regressions.

For EACH local and S3 route create new test meeting/owner/session/input. Upload two or more TXT files via genuine begin/parts/complete (real HTTP with Task4 available), file ordinal deliberately differs from UUID order, text includes BOM/CRLF/blank/supplementary characters. Accept Task4 import and observe persisted READY, unchanged saved pointer. Start actual runtime, allow its poll/claim/parse/complete, observe SUCCEEDED actual typed RAW/segments. Verify full exact text/stable IDs/global ordinals/source paragraphs, original producer/version/config, no marks/time/speaker and atomic attempt/event state. RAW remains owner-private, no human-save/current/member implication. Task6 later separately proves authorized history/effective APIs.

Restart A: real API/intake JVM with worker disabled/not installed accepts real uploads and commits actual READY task; record IDs/manifest/producer/archive hashes/PID/exit, then exit. Keep all MySQL/storage/config. Publish new append-only archive release retaining exact old artifacts plus changed default PROCESSING_CONFIG; optionally change default storage backend while retaining historical route. Fresh JVM B launches actual TranscriptWorkerMain with new default/new index pin, claims existing READY through actual WorkerLoop, reconstructs exact original DB input/manifest/archive refs/route, parses real retained bytes and completes. No re-enqueue or direct harness claim. Prove original digest/result, new draft under changed default differs. Record actual distinct PIDs/classpath/nonsecret argv/start/exit receipts. Handoff file only identifies rows; no process-local cache/handoff DTO may replace DB authority. API refresh/restart discovers task/input/output from server.

Negative archive tests use new owned COPIES or distinct fixtures, never damage retained positive/history. Missing/mutated old object, wrong index pin/dependency/profile must reject startup or task closed with zero RAW. Global snapshot startup failure leaves READY untouched; post-claim failure may update actual retry/failure metadata but no output. This is process restart, NOT DB/container restart. Extra owned-child crash after claim/before completion then real expiry/reclaim is useful; no SQL state reset. Old ConfigArchiveRestartIT alone proves neither actual Worker consumption nor source bytes.

Required matrix, with exact safe before/after snapshots and source/command binding:

* Actual absent output before Supplier, actual written RAW visible to registered validator; complete multi-file success and single completion owner.
* Inject after RAW insert/middle segment chunk/readback/wrong output/validator/audit/event/final flush: rollback RAW/segments/task terminal result/attempt/events. Keep failing runs.
* Expired/revoked/stale/cancel/published/governed/deleted, forged task/fullspec/owner/attempt; renewed DB expiry with old DTO succeeds, future DTO cannot rescue expiry. Failed source finalization opens nothing; failed completion fence writes nothing.
* Heartbeat loss before dispatch and after queue dispatch while blocked on roots; close before/during open/body/parse/callback; no doublecomplete, no late ordinary quarantine after contextual close/loss. All streams/permits/executors close within bound. Implementation must guard quarantine with contextual closed/cancelled as well as lost; an unexpected closed handler cannot issue a late ordinary completion.
* Final-file aggregate bytes/ALL decodedUTF16/segments overflow, BOM/blank/separator-heavy inputs; invalid UTF8/hash/size/short/extra body, stall/disconnect/read/close errors and cancellation produce no partial RAW.
* Historical READ_ONLY/default switch positive; DISABLED/wrong route/original purpose/epoch/location/duplicate original upload negative; SQL/I/O instrumentation shows every open after committed complete authorization and no archive/parse/hash under roots.
* Same-meeting CAS/WORKING race: only original version RAW, no MAX+1/rebase/pointer change; duplicate completion cannot duplicate producer output. Actual immutable output chain later remains valid under newer meeting version.
* Missing runtime settings/provider/purpose/complete policy/archive/TLS/budget: unavailable and zero placeholder work. Actual health/bootstrap/SPIs/canSubmit/overall-ready/replay distinctions.

Regressions: TaskLeaseIT, B06QueueSeamsIT, PreparedCompletionIT, WorkerLoopTest, PreparedWorkerLoopTest, TextTranscriptParserTest, ConfigArchiveRestartIT; adopted Task3 authority/source and Task4 intake/replay and affected FileRegistry tests after their exact source freeze. Preserve standalone facts-reader contract and golden vectors. Current forward13-SQL fixture only, no historical reset/exact-schema suites. Meaningful focused RED/GREEN, then one relevant final regression/fresh XML counts; no stale XML or repeated broad reruns without changes.

## 10. Fixture custody and reproducible future commands

Sole existing authorized MySQL: ama-test-b01-ad9f1af29eff, fullID eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502, owner remaining-20261023, loopback127.0.0.1:58062/schemaama_b01_fixture. Root checks selected ID/image/label/mount/port/resources only, never Config.Env/old assets. Preserve all13 scripts/history including frozen V013.1. No reset/drop/repair/outOfOrder/newDDL/DB restart.

Before future fixture creation root guards exact new unique S3 NAME/owner/absent target directory+receipt, new local volume and HTTPS file-service child, fresh PKI/worker-health peers/loopback ports. Public tools/dev/s3_fixture.py mechanism may be reused ONLY with explicit current-run AMA_S3_FIXTURE_NAME, never default s3-java/old .tools env/data. Public helper currently allows existing HOME and internally loads full docker inspect in inspect_owned; current-run wrapper/hardening must enforce exact absent target and selected-field inspection before use. Do not blindly invoke it as though required guard already exists. RustFS pinned image rustfs/rustfs@sha256:128220dc9382bac81940a1a9616ee8c274a3f3b8f647eedbeb50df5bb8b49922; memory+swap2GiB,2CPU,256PIDs. Its HTTP loopback needs a NEW owned TLS frontend/cert for production HTTPS assembly; do not relax production HTTPS or change an old route. Fresh data/control buckets/keys only; retain resources/data/failed evidence.

Evidence under new artifacts/phase-b/b-remaining/task-05/, with nested .gitattributes raw preservation. Retain selected custody metadata, owned synthetic source bytes, archive originals/index SHA, safe before/after projections, source BASE/final hashes, exact nonsecret argv/workdir/JDK, subprocess PID/exit, fresh JUnit XML/counts/errors/skips. Credentials/env paths only, files0600/dirs0700, private TLS ignored; exact current-run secret scan before staging. No full environment/inspect or mocked API/worker successes.

Prospective entrypoints (names become exact at implementation, NOT executed here):

```text
python3 tools/checks/run.py unit TextTranscriptParserTest
python3 tools/checks/run.py unit PreparedWorkerLoopTest
python3 tools/checks/run.py it TranscriptImportWorkerIT
python3 tools/checks/run.py it TranscriptImportProcessRestartIT
./mvnw -B -ntp -pl backend/integration-tests -am -Dit.test=TranscriptImportWorkerIT,TranscriptImportProcessRestartIT,PreparedCompletionIT,TaskLeaseIT,B06QueueSeamsIT,ConfigArchiveRestartIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Root's run-owned launcher supplies only .tools/phase-b-remaining/env.sh plus new guarded resources, explicit new evidence root, public read-only JDK21 and pinned Maven3.10.0. Capture actual safe expanded argv and fresh XML at execution; this template is not evidence. Add exact Task3/4 suite names after adoption. Explicit UNC workdir for Windows; established reviewed WSL route for future Java tests. No such test/resource command ran in this design task.

## 11. Ownership and unresolved technical adoption gates

Proposed Task5 ownership: additive measured parser interface/TextTranscriptParser internals/ConfigBindings parse-budget getter; runtime detached reader/preparer/internal RAW writer and actual output validation aligned with Task4 policy; shared adapters assembly/settings; worker->adapters POM; explicit provider/main/WorkerMain helper; minimal contextual cancellation/monitor/close/health observation; proven local source abort; authenticated health/probe and API readiness composition; focused actual upload/Worker/restart evidence. Coordinate shared policy/config files with root before authoring; preserve Task3 issuer and Task4 replay/intake. No new schema.

No unresolved product choice. Technical adoption gates:

1. Review measured parser port/usage and binding getter, preserve old parser Result/profile/stable IDs/goldens. Exact full-decoded aggregate is solved by this proposal, not currently implemented.
2. Align final adopted Task3/4 facades with runtime reader/private assembly/prelock/output checks; reject any need for second issuer, DTO authority or late root acquisition. Names provisional.
3. Adopt explicit bootstrap/private authenticated health schema and minimal ordinary contextual cancellation/monitor/close seams; verify SPI-provider isolation and later Task7 monitor compatibility without activating external behavior.
4. Root freezes explicit aggregate/I/O/DB/close settings; actual local body-abort/unblock and S3 deadline proof required. Existing request headers timeout cannot close this gate.
5. Root custody guard for new S3/local/TLS resources, then independently reviewed real local+S3/READY-task/fresh-JVM source/evidence before activation. Task1/archive-only tests cannot substitute.

Status: design report complete; implementation/test/runtime approval pending. B01 IMM remains PARTIAL, B04 Q401 UNKNOWN; company adapters/HA/native-I/O/capacity/correction/external qualification unchanged.

### Source-level final lease check clarification

Inspected JdbcTaskQueue.validLease currently locks/checks actual durable_task full spec/state/owner/attempt/expiry/current fence, then validateInput; it does not itself SELECT the unfinished task_attempt row before Supplier. Task3 source finalization separately proves that row, but its earlier transaction cannot supply completion authority. Final implementation must explicitly verify exact actual task_attempt(taskId,attempt), matching owner and ended_at IS NULL after all task locks and before effects. Prefer a narrow additive check in JdbcTaskQueue.validLease (or an adopted equivalent queue-owned after-task seam), preserving existing constructors/lease20s and Runnable/Supplier ownership. Do not place this check in policy.lockRoots before queue task lock or call source-reader/queue.validLease recursively from writer. This is a concrete source-alignment review gate, not a claim existing Task1 already performs the SELECT. Test ended/missing/foreign attempt suppresses callback and preserves original expiry/renew regressions.
