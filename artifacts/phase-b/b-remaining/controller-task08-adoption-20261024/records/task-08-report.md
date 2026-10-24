# Task8 bounded reconciliation fairness — implementation delivery

Status: author DONE, awaiting independent specification/quality review and root adoption. FUP-B05-CANDIDATE-01 and T6-INDEX-MODEL-SYNC remain open until that review. No Task7 consumer activation, company deployment, main merge, push, HA or capacity claim.

BASE `12ca5436b2278e5cb08d60f4d8f41c1528a0e3a6`; branch `codex/phase-b-completion-20261023`; worktree `/home/tanren/ama/.worktrees/phase-b-completion`. Delivery HEAD is the commit containing this report and its manifest, returned by exact SHA in the author handoff; a report cannot self-embed its own containing commit hash. Root central/protocol records are excluded from this commit.

## Result and authority

`JdbcExternalOperations` now retains a per-instance bounded raw keyset cursor over immutable server-allocated `reconcile_scan_seq`. It captures one fixed high-water ordinal, enumerates ALL rows without state/due/policy filters, consumes each position before its independent candidate transaction, and opens at most one pass per invocation, with no immediate wrap. Held, unknown-policy and failed candidates cannot pin the prefix. A successful claim advances only through the returned row; prefetched tail rows are refetched next call. The public budget constructor accepts page/row limits; the existing constructor remains compatible at64/64.

Candidate discovery reads the actual current execution and complete actual task spec. Registered TASK_MUTATION preparation acquires genuine complete sorted identities before subject/domain/input/task locks. Fresh locked full spec, operation subject/current execution, execution ID/no/task, both operation/execution states, next-check and expiry are revalidated; one fresh DB timestamp is read after locks. Only a successful outer commit returns a reconciliation token. UNKNOWN remains lookup-only; no initial-send capability is granted. Renewal, observation, retry, transaction helpers and public contract signatures are unchanged. Discovery errors preserve position; candidate errors and lost commit acknowledgements propagate after consumption, returning no token. Same-instance scan contention returns promptly without blocking independent renew/observe.

Owned production changes are the adapter, new `ReconciliationScanBudget`, exact new013.3 resource, and four narrowly authorized current model outputs. Full tests/helpers and execution evidence are separately owned Task08 paths. Final manifests seal exact source/artifact bytes; every selected test window has identical final production Java/SQL hashes.

## Frozen forward migration and preservation

Activation557e9eb9, formal freeze2cc1216d and separate five-target ACK868de9e3 authorized the exact164-byte `V013_3__external_reconcile_scan.sql`, SHA256 `023fe875efd2be0bbd9d59b2d22a9b6429ea88eb6fc7a7b30d8541a53c0d46ee`. No prior SQL, repair, reset, outOfOrder, raw old verifier or fixture creator was used.

All four fresh targets were normally bootstrapped through the old14 BEFORE copying/building the new resource. The behavior target then received six genuine pre-forward markers spanning the six operation states; shared already held45 operations. The old14 schema test produced its intended missing-ordinal failure. Complete pre-application physical metadata/SHOW CREATE, old-column/row digests, allocators and all-field history were captured. New013.3 then applied normally once and reran with zero migrations on shared and all four fresh targets.

`migration/preservation-PASS.json` and immutable `compare-migration.py` prove65 old tables/491 old columns, every old row/allocator and every old14 history field unchanged through controlled DDL, apart from the expressly intended new ordinal/index/operation AUTO_INCREMENT engine state. The operation column is signed BIGINT NOT NULL/defaultNULL/auto_increment at ordinal16, with one-part unique external_reconcile_scan. Task6's three-part task_input_lookup index and audit_event allocator remain intact. Shared45 and behavior6 rows all received distinct positive ordinals. Source invariant: ordinary initial inserts omit the ordinal; dedup/retry/claim/renew/observe never assign it.

The row proof consists of complete primary-key-ordered, NULL/length-framed JDBC-byte digests plus counts, not retained raw rows or reconstruction of an old ID-to-ordinal mapping that did not yet exist. New ordinal maps are separately captured. The five migrations used MySQL8.4.6, Connector/J9.7.0 and Flyway12.4.0; the new checksum is -385117638. Actual first application durations were approximately1.2–1.4seconds, with per-connection metadata lock wait5seconds. Exact engine SQL mode is in schema/final-state evidence (STRICT_TRANS_TABLES present; NO_AUTO_VALUE_ON_ZERO absent); no online ALTER or physical I/O latency claim follows.

## Fairness, bounds and recovery evidence

Dedicated fairheld and fairunregistered schemas each contain exactly130 genuine markers:129 blockers plus one eligible tail. Both tails were claimed on call5 at page16/budget32. Held-case complete owner/affected identity order and partial-lock release are asserted; absent policy receives no preparation/root/task/operation/execution locking authority. Skipped operation/execution/task/attempt/subject/meeting/input/identity/outbox/sequence graphs remain byte-identical. Held locks released, the next pass revisits the old prefix.

A distinct mixed test holds70 rows, ten at each identity/subject/root/input/task/operation/execution phase. With retained behavior population344, the tail was reached on call11 at16/32, the full skipped graph was unchanged, and released prefix work was revisited. This is separate from the dedicated129/call5 proof. Fresh-JVM restart reached its UNKNOWN tail on call18 at8/16 with one execution and zero send authority; it does not claim call5 on the larger retained behavior history.

Cursor tests cover success/prefetched tail, repeated early eligibility, fixed upper bound under continuous arrivals, fresh due and full-spec changes, genuine changed-current-execution zero-effect retry, candidate/discovery exceptions, last-position exception followed by exactly one final-empty query, lost commit ACK, same-instance tryLock versus independent renew/observe, concurrent instances, late allocated-low commit behind cursor, and all lock phases. Schema/isolated tests cover stable ordinal under lifecycle transitions, gaps/rollback/deletion, actual unique/check/FK errors, restore/import and signed maximum.

Actual explicit SQL/result+mutation rows, including both session SETs and the high-water row, satisfy R1:

| Trace | Explicit SQL | Result/mutation rows | Controls |
|---|---:|---:|---|
| Opening success P1/B1, genuine policy Q8/R8 |21|19|3 acquisitions, one candidate commit, two explicit SETs counted as SQL|
| Four unregistered P2/B4 |23|17|4 candidate commits,8 explicit SETs|
| Sixteen unregistered P8/B16 |83|65|16 candidate commits,32 explicit SETs,2 pages|
| Active-pass final empty |1|0|one discovery acquisition/close, no candidate transaction|

Conservative envelopes remain `1+ceil(B/P)+B*(11+Qpolicy)` SQL and `1+B+B*(9+Rpolicy)` rows; arbitrary policy callbacks are not presumed bounded. Driver acquisition/autocommit/commit/rollback/close controls are recorded separately. Dedicated held-case five calls measured1063SQL/795rows; unregistered five calls674SQL/535rows. Actual EXPLAIN uses the new covering ordinal index (backward index scan for max, range for page), without filesort. These are logical enumeration/query bounds, not physical InnoDB page/version-visit bounds.

## Historical010 and isolated exhaustion

Root separately authorized one empty historical010 schema on the same owned MySQL instance (ACK `be461cd4b3a304ef2d92d94d76546ad66f380f643245399f3078d376a33a18d4`). Normal Flyway target010 applied exactly the original eight migrations, then reran0; all eight complete history records remain unchanged. The minimally adapted new `Task08Historical010IT` retains all three original B05SchemaIT test methods, assertions, SQL, checksum -1187397378 and independent metadata golden; only class/import/dataSource binding changes. Its exact diff/lineage is in `historical010/`. All3 passed. No forward13.3 or Task6 index was applied there, and the original test/golden/V010 remain unchanged.

Only the dedicated overflow target received controlled restore/import and Long.MAX_VALUE. Exact full operation/execution rows and ID-ordinal mapping survive delete/reinsert in one transaction; omitted next insertion allocates above the restored value. A rolled-back explicit negative import demonstrates complete metadata validation catches hidden nonpositive values; there is no invented AUTO_INCREMENT CHECK guarantee. Initial empty-table discovery executed1SQL/0rows before a subsequently recorded setup error.

The retained explicit signed MAX row is terminal with no execution. Three raw rows scan to MAX and end the pass without +1 overflow/wrap. On this actual engine the next omitted allocation fails SQLState23000/code1062 on external_reconcile_scan, rather than the initially assumed1467. The final focused MAX case asserts the actual constraint, no operation/execution/token creation, original rows/mappings unchanged and complete10-table business/task/execution/outbox graph equality across the failed beginExternal. The counter was never reset, resequenced or reimported after MAX. The retained overflow target is intentionally exhausted and must not be reused as an ordinary marker fixture.

## Test accounting and preserved execution windows

Canonical runner: `python3 tools/checks/run.py`, called by `run-tests.py` with explicit current private environment and bounded512MiB/two-processor JVMs; standalone probes/children explicitly64MiB/two processors. No secret values appear in commands/output. Exact expanded commands, source hashes, fresh XML and return codes are retained per window.

| Selected final evidence | Unique passing IT cases |
|---|---:|
| fairness-green |2|
| inherited-forward-final |76|
| cursor-schema-budget-restart-final: completed Schema/Budget XML only |8|
| cursor-mixed-restart-final |13|
| historical010-oracle-final |3|
| overflow-resume-final: restore case only |1|
| overflow-max-complete-atomicity-final: MAX case only |1|
| **Distinct integration total** |**104**|

Final `unit '*'` ran once:254 tests,0failures,0errors,0skips. Combined unique qualification is358 cases. `test-accounting.json` lists every chosen IT testcase and XML hash and explicitly excludes overlapping earlier passes. The interrupted combined run and the two-case overflow run are not labeled whole-run PASS merely because individual suites/cases completed successfully.

The eight inherited forward copies preserve all76 original methods/assertions with explicit source/diff lineage. Adaptations are owned forward15 target/history, distinct fixture/type namespace, bounded retained-history scan, new-consumer starting cursor and bounded actual crash child; no original unmodified-class pass is claimed. Historical010 is a separate adapted oracle. Original inherited source and golden bytes remain protected.

Preserved chronology/qualifications:

- `schema-red`:2 tests,1 intended schema failure/0errors; old14 before new resource. `fairness-red`:2 setup errors from a test snapshot using wrong outbox PK; this is not product RED. `fairness-red-02`:2 genuine tail-starvation failures/0errors against old selector, followed by fairness-green2.
- Intermediate cursor windows include a foreign Spring-bound DataSource barrier setup error, an incorrectly targeted full-spec test hook, and one marker setup Optional failure whose underlying cause was not established. Diagnostic assertions were added; later cases passed, but no unsupported root-cause/product-fix claim is made.
- The first combined final run completed Schema4/Budget4 but hung in a mixed subject barrier that omitted the leading subject_kind PK component. Actual thread/DB captures and exact owned JVM1229590 termination are retained. The fixture lock was narrowed to the composite PK and helper to two complete passes; affected Cursor11, Restart1 and new Mixed1 then passed. No completed Schema/Budget rerun was needed.
- First overflow run failed because the observation proxy used null-rejecting Map.copyOf during explicit restore insertion; the transaction rolled back and retained its one genuine marker. The proxy now retains immutable null-tolerant sorted parameter snapshots, and only the overflow sequence continued without reset. Earlier trace semantics for non-null parameters remain unchanged. Restore then passed; the unverified1467 assertion failed on actual1062. Only MAX continuation was rerun; a final strengthening checks the complete graph. None of these repetitions inflate unique counts.
- Source carry is explicit: all selected windows share exact final production hashes. Earlier fixture diagnostics/null-trace support affect test instrumentation only. A trailing whitespace character in CursorIT was removed after execution. No final product/SQL or unit test source changed after all-units.
- First read-only final-state helper incorrectly asked model_reservation for a state column; its failed SELECT log is retained. Corrected count-only capture succeeded on all six targets. Resource guard initially inspected requested dynamic port bindings, then correctly compared actual allocated NetworkSettings ports; no resource changed.

## Current model and final custody

Before editing, all four exact actual BASE blobs were preserved under `task-08-current-model-preservation/base-12ca543/`, with hashes. Generator/SQL/dictionary/ER changes are restricted to the Task8 ordinal/index/shared AUTO_INCREMENT predicate and the expressly required Task6 durable_task.task_input_lookup synchronization. The76866-byte old human prefix remains exact and contiguous after the new status note; ER adds only the operation field. Other definitions and edges are unchanged. Model result B00-1.6-reconcile-scan:98tables,732columns,189explicit indexes including98PK,116checks,208FK. Current review model is not the deployed65-table schema; the old mutating verifier was never called.

Protected adopted snapshot:9431 entries,9427 unchanged and exactly the four authorized model exceptions. All old14 SQL and all old artifacts remain byte-exact. The current root protocol/central records are not staged. Final own source/artifact manifest and staged-path proof make outside-owned changes reviewable.

Final selected guards confirm exact shared CID `eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502` at46653 and Task8 CID `11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad` at44727, expected owners/images/mounts/loopback ports,768MiB memory=swap/one CPU/256PIDs, running/OOMfalse. No Config.Env was read. Six distinct current private values and private-key patterns were scanned in memory against all new public/source files and the own tracked diff:0matches; values/digests are never emitted. Four recorded owned PIDs are gone; this is not a global process-count claim.

Final complete histories equal postmigration captures on all six targets. Shared still has45 operations. Owned behavior has344 operations (maximum348 with legitimate gaps), each fairness target130, overflow3 including signedMAX, historical010 zero. These retained control fixtures intentionally include READY/RUNNING/RECONCILING tasks and failed-allocation attempts; no global active0 or completed company business-pipeline claim is made. No owned worker process remains. Existing S3/PKI were not altered.

Self-review checked bounded progress/error/commit behavior, identity/domain/task/operation lock order, fresh revalidation, no new send authority, no ordinal assignments in ordinary writes, exact old migrations/model limits, testcase/source accounting and explicit staging. Remaining limitations are the adopted terminal-history scanning latency and dependence on an available-lock opportunity/finite policy closure; repeated restarts can restart a pass. At default64/64 a100001-row pass can require1563 polls (roughly2.17hours at five-second empty-poll interval). This is a known tradeoff, not an SLA. Independent reviewers/root decide adoption and follow-up closure before Task7 activation.
