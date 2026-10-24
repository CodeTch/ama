# Task 13 preparation: deletion of an actually prefetched reconciliation candidate

Status: **test-only design, not implemented**. T8SR-M1 remains **OPEN** until Task 13 implements the case, runs the scoped test, and obtains review. Source observations below are pinned to committed base `de1611a67ba483afe21f3b29bb69c2d32028efef`, never the concurrent Task 7 checkout.

## Narrow finding and committed evidence

The independent Task 8 review calls T8SR-M1 Minor: `Task08SchemaIT:36-38` inserts a new highest-ordinal terminal row, then deletes that ID on the *first* page query. Since existing `behavior` rows exceed page size 8, the deleted row was not in that page. This proves a deleted high-water/gap can end a finite sweep, but not deletion of a candidate whose `(reconcile_scan_seq,id)` was already returned. Preserve this original case and its rollback-gap assertions. The Task 13 issue register likewise assigns the additional case to Task 13 and keeps T8SR-M1 open.

The scanner's page query maps every raw row into a `List<ScanCandidate>` before the candidate loop (`JdbcExternalOperations:89-98`). It advances the cursor before `claimCandidate`; its first candidate SQL returns empty if the operation disappeared (`:113-115`). `Task08ProbeSource:22-39` already demonstrates test-side connection/statement/result-set proxies, but its existing `after` callback (`:34-37`) sees SQL execution before the row mapper consumes the page. Reusing that callback cannot establish the exact prefetched ID.

`JdbcTaskQueue:25-33` binds the queue, transaction runner, and outbox to one DataSource. `TaskTransactionContext:19-23` requires that same bound DataSource; `JdbcOutbox:35-38` participates in each queue work call. A wrapper used only for the scanner over an existing `Task08Fixture.source` would therefore be wrong. Task 13 should construct its **owned guarded test fixture/schema** with one page-aware test DataSource instance passed consistently to its `JdbcTaskQueue`, `TransactionRunner`, fences, outbox, preparations/policy if any, and JDBC template. Keep the legacy Task 8 `behavior` fixture and old case untouched. No production hook is needed.

## Small deterministic Task 13 case

Add one Task 13 integration case and a nested, case-local `DataSource` proxy (or equivalent Task 13-only helper). Wrap the guarded Task 13 driver DataSource at fixture construction. Its connection proxy retains the SQL string for each `prepareStatement`. For only the exact raw query prefix `SELECT reconcile_scan_seq,id FROM external_operation FORCE INDEX (external_reconcile_scan)`, its statement/result-set proxies record `(getLong(1), getString(2))` from rows actually mapped after `next()==true`. On the raw page's `PreparedStatement.close` **after delegating the real close** (and after its result set has closed), inspect the captured pairs. If and only if the pairs contain the armed target `(sequence,id)`, perform `DELETE FROM external_operation WHERE id=?` via a separate connection from the same guarded *underlying* driver source; require update count 1. Record one-shot `fetched`, `deleted`, and page number. Do not delete on statement execution, on a query expected to contain the row, or from the scanner transaction. Reentrant deletion must bypass the page proxy. Fail if the page closes twice with the target or a second deletion is attempted.

Seed with the Task 13 fixture's valid committed operation as the `INSERT ... SELECT` parent used by `Task08SchemaIT:36`. Import two fresh **execution-free, terminal** enumeration rows with distinct IDs/business keys: target first, survivor immediately after. Verify both have positive unique ordinals, `targetSeq < survivorSeq`, and `survivorSeq` is the opening maximum; both have zero `external_execution` children, so the target delete has no FK dependent. Use an empty policy map for this scan, matching the existing deletion case's no-authority setup. No global reset or writes to the Task 8 schema are required. Restrict all inserted IDs/type names to the Task 13 namespace.

Start a fresh scanner with `ReconciliationScanBudget(8,16)`. Before each `claimReconciliation`, the test-only tracer can record SQL order; require every call returns `Optional.empty()`. The page-close hook must record the exact target ID and ordinal **from the mapped raw ResultSet** before deleting. On the subsequent candidate discovery SQL `SELECT *, (next_check_at... WHERE id=?` for that ID, assert the delete flag is already set and record that the query was attempted; its result must be empty. A second result-set proxy can count `next()==true` for this exact candidate query and require zero rows, establishing the absent-operation guard rather than merely relying on an empty final answer.

Compute `R = COUNT(*) FROM external_operation` after both imports and before scanning, with the Task 13 schema isolated from concurrent writers. Use at most `ceil(R/16)+1` scan calls and fail if the hook or pass completion has not occurred; there is no unbounded await. Require: fetched pair equals the target; delete count 1; exactly one post-delete initial candidate read and zero result rows; scanner's final `after` is `survivorSeq` and `active` is false; all calls return empty; survivor row retains its original `reconcile_epoch`/business columns; no execution is created for either imported ID, and the total outbox row count stays unchanged across this scan. The target is absent. The cursor reaching the higher survivor proves the missing candidate did not pin or terminate progress. Retain the raw query statement/parameter trace and bounded call count as diagnostic output, without changing the production SQL budget envelope.

The hook runs synchronously after the raw page has been read and its statement has closed, before `JdbcTemplate.query` returns the page to `claimReconciliation`. That page query is an ordinary nonlocking read outside `q.work`; the candidate transaction begins later. The separate deletion connection therefore should not wait on a scanner-held row lock or repeatable-read snapshot. The page connection may still be open but has no active transaction or row lock. Keep the guarded driver's existing finite connection/socket/lock timeouts and fail immediately on delete error. A latch/thread is unnecessary and would introduce a false scheduling barrier. If a future JDBC wrapper does not invoke statement close before candidate processing, the ordering assertion must fail and the hook placement must be revised without weakening the fetched-ID requirement.

Pseudocode, deliberately limited to the new case:

```java
var seen = new PageCapture();
var fixture = task13FixtureWithOneSharedPageAwareDataSource(seen);
var pair = fixture.insertCommittedTargetAndSurvivor();
seen.arm(pair.targetId(), pair.targetSeq());
var scan = new JdbcExternalOperations(fixture.queue(Map.of()), new ReconciliationScanBudget(8, 16));
boolean completed = false;
for (int call = 0; call < (rowCount + 15) / 16 + 1 && !completed; call++) {
    assertTrue(scan.claimReconciliation("T13_PREFETCH_DELETE").isEmpty());
    completed = seen.deleted() && !Task08CursorIT.active(scan);
}
assertTrue(completed);
assertEquals(new RawRow(targetSeq, targetId), seen.fetchedPair());
assertEquals(1, seen.deleteCount());
assertTrue(seen.deleteBeforeInitialRead());
assertEquals(0, seen.initialReadRows());
assertEquals(survivorSeq, Task08CursorIT.position(scan, "after"));
assertFalse(Task08CursorIT.active(scan));
```

For assertions on actual SQL order, use the Task 13 tracer on the shared source; `Task08ProbeSource` shows the minimal proxy pattern and query event fields. Preserve original failures verbatim if the delete gets a constraint/timeout error, an expected close never occurs, or candidate lookup unexpectedly returns a row. Do not turn those failures into a passing fallback path.

## Future gate and scope

This preparation supplies no execution receipt and changes no source. Task 13 must repin the focused source against actual upstream Task 7/9/10/11 states before implementation, especially its owned schema, guarded DataSource/history, policy/transaction construction, and any SQL shape changes. Run only the new scoped case and relevant existing gap case after implementation, then obtain independent review. The original Task 8 passing evidence remains valid for its narrower high-water/gap claim; T8SR-M1 closes only after the new observed race passes.
