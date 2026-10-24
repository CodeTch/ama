# Task 11 unversioned `publication_submission` DDL: independent static review

2026-10-24. Scope: the exact 76-line prospective SQL (`e9026197dba789e7cbdbf6acee56065d0f8fa98665595d204bece1afc8eedd43`), its 54-line report (`930eddac25fe68adf832b007c2ffc2a74581bdebd9e6535df01ac19c4933ef1d`), the complete new-file diff, R1 §3/§8.5, and focused physical parents at committed baseline `de1611a67ba483afe21f3b29bb69c2d32028efef`. The root input proof `task-11-forward-ddl-proposal-root-input-check-20261024.json` records 22 verified source/output seals, 12 selected ranges, and equality of all 15 migration blobs to that commit. This review independently read the proposed SQL/report and used `git show` on V002, V005, V007, V010, and V013 focused parent definitions. No migration version, resource copy, application, runtime activation, model synchronization, DB/test/network action, or source change is approved here.

## Verdicts

| Review | Verdict | Critical | Important | Minor |
|---|---|---:|---:|---:|
| SPEC compliance of exact prospective SQL against R1 §8.5 | **Needs changes** | 0 | 2 | 0 |
| SQL/code quality and prospective MySQL 8.4.6 validity | **Needs changes** | 0 | 2 | 0 |

The two counts refer to the same two findings below, not four independent defects. The table has all 19 R1 columns and the intended PK, unique membership key, recovery scan index, six restrictive FKs, supported submission ceiling, and decision-tuple shape. The findings concern exact stored vocabulary and whether that tuple check is legal alongside FK actions on the target MySQL version.

### I1 — `ascii_bin` comparisons do not enforce exact state/reason bytes

**File/lines:** `task-11-forward-ddl-proposal-20261024.sql:20-22,35-57`; report `task-11-forward-ddl-proposal-20261024.md:24-37`. R1 §8.5 lines 284–291 specifies exactly two states and three decided reasons. MySQL's legacy `ascii_bin` collation has PAD SPACE semantics. `VARCHAR` values with trailing spaces can therefore compare equal to the listed literals: for example, an `UNDECIDED ` row with all decision fields null passes both checks, and a decided row with `PREPARATION_EXHAUSTED ` passes the reason test. This is a persisted vocabulary violation even though the writer is also required to validate exact values. Use a byte-exact predicate (or pair each accepted literal with an exact byte length) for both vocabularies, retaining the full nullability tuple. Confirm its behavior in the later isolated MySQL gate.

### I2 — decision CHECK and FK referential actions collide on two columns

**File/lines:** `task-11-forward-ddl-proposal-20261024.sql:39-57,70-75`. `ck_publication_submission_3` refers to `local_decided_by` and `local_execution_id`; both are child FK columns with explicit `ON UPDATE RESTRICT ON DELETE RESTRICT`. MySQL 8.4 CHECK restrictions prohibit FK referential actions on columns used in a CHECK expression. This makes the exact CREATE TABLE at least a material 8.4.6 DDL-acceptance risk, and the intended full SQL-enforced decision tuple cannot simply be presumed installed. Resolve this physical conflict before version allocation while preserving R1's six restrictive FKs and full decision invariant through a reviewed MySQL-compatible mechanism. An isolated 8.4.6 create/negative-row probe is required at the later application gate; this review was barred from executing one.

## Supported static mapping

* The SQL's UUID columns are `char(36) ascii_bin` like committed V010 `durable_task.id` (lines 69–72), V010 `external_execution.id` (187–202), and V013 `publication.id`/`final_confirmation.id` (17–18,44–45). V007 alters `enterprise_identity.enterprise_id` to `varchar(255) utf8mb4_0900_bin` (30–31), matching requester and nullable decider. All six parent keys exist before this proposed table. Explicit confirmation/requester/decider/execution indexes and the left prefixes of the PK and `(publication_id,submission_no)` unique key cover the FK child lookup paths.
* `submission_no BETWEEN 1 AND 9223372036854775806` fits the R1 supported unsigned range. `OCTET_LENGTH(request_manifest) <= 65536` is a useful persisted UTF-8 byte ceiling for valid `utf8mb4`; it cannot prove the strict content-free schema, canonical bytes, or original acceptance equivalence. The writer must also enforce R1's UTF-16 bound. InnoDB, index widths, `longtext`, `datetime(6)`, explicit collations, and restrictive FK syntax otherwise have no separate static issue identified.
* PK `task_id` gives one row per actual task, and the publication/number unique key prevents duplicate allocated numbers. Neither proves monotonic allocation or completeness of publication task inventory. R1 §3 lines 85,97–105 and §8.5 lines 295–310 require complete S/A/F declaration, no-row protection, ordered locks, root CAS, task/attempt/operation/execution association and the shared marker barrier.

## Boundary of what this SQL proves

The table constraints cannot freeze the binding columns after insertion or make `UNDECIDED → NEVER_MARKED` irreversible. A later UPDATE could alter binding fields, reverse a decision, or replace its reason/actor/time/attempt while still satisfying the CHECK. R1 requires a confined private writer with no binding UPDATE, one affected-count-one atomic decision update, exact sealed replay with no write, and the genuine task/marker/attempt transaction. This is an implementation and authority gate, not evidence supplied by the DDL. FKs also do not establish same meeting, original provider/destination/final, task subject/type/producer/r/e/input, genuine no-execution versus PREPARED/local-FAILED proof, or safe prior rounds. The report correctly acknowledges these limits; its text does not repair I1 or I2.

## Cannot verify in this static gate

* Actual parser/constraint behavior on pinned MySQL 8.4.6, including I2 and the proposed replacement for I1; no DB, container, migration application, or negative-row test was run.
* The then-current migration prefix and adopted Task 7/9/10 source, private writer/privileges, marker and local terminal hook, complete runtime lock protocol, byte/schema validation, exact replay, or crash behavior. The root must repin these before activation. The earlier 235-file preflight was not repeated.
* Flyway version allocation/frozen bytes, independent application ACK, and synchronized current model generator/dictionary/DDL/ER output. The SQL remains an unversioned proposal and does not change historical migrations or legacy rows.
