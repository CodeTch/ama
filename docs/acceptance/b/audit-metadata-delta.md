# B-01 post-B00 audit metadata delta

Scope: DIFF-01 against adopted **B00-1.1-review** (source c73a765 + bd43ca3,
integration 69f33a8/bca9b89, adoption 6dd2cdd). Actual implementation BASE is
`45ce136f4340a50a518c1dc7484c70d8caf26ca0`. Existing B01 transaction, explicit
migration process and audit foundation remain in place. This records implementation
evidence for independent review; it does not approve the rest of phase B, company
interfaces, capacity or production HA.

## Change and compatibility

`V006__audit_metadata.sql` implements the adopted audit dictionary: actor/resource/trace
use `utf8mb4_0900_bin` NO PAD, action/result use `varchar(64) ascii_bin`, revision is
unsigned, and indexes are `audit_resource_time(resource_id,occurred_at,id)` and
`audit_trace(trace_id)`. Identifier charset and lengths remain unchanged; the
nonunique indexes introduce no identifier uniqueness conflict. Existing IDs and
metadata are preserved. The Java audit write explicitly truncates Instant to UTC
microseconds before JDBC binding; the metadata-only contract and caller transaction
requirement remain in force.

Before conversion, a temporary CHECK guard rejects long action codes, non-ASCII
action/result codes and negative revisions, even with strict SQL mode disabled.
It does not change source rows. All audit changes then use one atomic ALTER.
MySQL DDL is not a multi-statement transaction: rejected preflight leaves an
explicit failed Flyway V006 history entry and unchanged audit columns/rows, and a
normal rerun refuses the failed history. No repair, outOfOrder, deletion or source
coercion is used; an incompatible deployed database needs a reviewed operator
resolution before migration resumes.

Stop business writers for this narrowing/conversion and deploy the compatible
adapter to every writer before resuming. SchemaMigrator remains an explicit
deployment process; application startup does not migrate. Other domains retain
their registered versions and implementation prerequisites.

Frozen SQL SHA-256, unchanged from the adopted central registry:

| File | SHA-256 |
|---|---|
| V001__foundation.sql | `1726d9db3eb45d0fe86314dd41ad52d5f2332e3f16995cc246f3c3c532208335` |
| V002__identity_acl.sql | `68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0` |
| V005__tasks_outbox.sql | `55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7` |
| V006__audit_metadata.sql | `f36b7ab2e8d32cf5de6eadcd86524ab269dce98f7906b808eff1a0e39325178f` |

No V003/V004, other domain DDL, old migration rewrites or renumbering were added.

## Real MySQL and TDD evidence

Own helper-created fixture: `ama-test-b01-fb4e04459580`, loopback
`127.0.0.1:45443`, schema `ama_b01_fixture`, pinned MySQL 8.4 image from
`tools/dev/mysql_fixture.py`. Only this fixture was reset. Credentials stay in its
ignored local env file and are not included here.

Commands run from `/home/tanren/ama/.worktrees/phase-b-audit-delta` after sourcing
the read-only phase-a toolchain and this worktree's own fixture env:

```sh
python3 tools/dev/mysql_fixture.py start
python3 tools/checks/run.py it FoundationIT
python3 tools/checks/run.py all
git diff --check
sha256sum backend/adapters/src/main/resources/db/migration/*.sql
```

RED before production changes: **16 tests, 6 failures, 0 errors, 0 skips**. The
failures demonstrate ai_ci case equality, nanosecond rounding (including crossing
a second/epoch boundary), absent V006 and acceptance of three incompatible legacy
metadata cases. Initial GREEN and the final rerun both report **16 tests, 0 failures,
0 errors, 0 skips**. The canonical full regression reports **Python 74, web 8,
Java unit 19**, all zero failures/errors/skips, plus **2 successful OpenAPI checks**.
`git diff --check` passes. No unreported failing or skipped suite was encountered.

Upgrade coverage applies frozen `001→002→005`, inserts ID 41 with Unicode/trailing
spaces, 64-character codes and Long.MAX_VALUE revision, applies V006, and compares
the complete row plus all existing Flyway history/checksum fields unchanged.
The next insert receives ID 42; rerun executes 0 and leaves history unchanged.
V006-specific assertions use a target boundary, while later migration/reset checks
derive the current version dynamically and use a local-only relative probe.

The suite also covers all three exact identifier columns, actual NO PAD metadata,
the resource/time/id index and equal-time ordering, UTC microsecond truncation under
America/Los_Angeles JVM defaults, three visible narrowing failures without strict
SQL mode, external Spring transaction rollback, required/nested/checked-exception
rollback, no autocommit, connection loss recovery and independent migration locks.

Local preserved evidence is under `artifacts/b01-delta/` (RED/GREEN logs and fresh
JUnit XML, `all.log`, and read-only `mysql-metadata.json`). MySQL **8.4.6** reports
all **8 audit columns** and **3 index definitions / 5 index columns**, matching the
adopted dictionary, no column defaults or prefix indexes, no audit FK, and no CHECK
after replacing the signed-revision check with the unsigned column. Actual final
Flyway history:

| Rank | Version | Script | Flyway checksum | Success |
|---|---|---|---:|---:|
| 1 | 001 | V001__foundation.sql | 1201435595 | 1 |
| 2 | 002 | V002__identity_acl.sql | 1757300424 | 1 |
| 3 | 005 | V005__tasks_outbox.sql | -1999355348 | 1 |
| 4 | 006 | V006__audit_metadata.sql | -1744360556 | 1 |

After the successful suites, two read-only metadata-launch attempts encountered
host transport `Wsl/Service/0x8007274c`; they did not run the capture or change the
schema. One bounded direct WSL invocation from a Windows-local working directory
then completed the read-only capture successfully. No WSL/Docker restart, fixture
reset or suite rerun was used to resolve that host transport error.

Author self-review found no remaining scoped implementation issue. Root independent
review of the full BASE-to-HEAD delta is still required before integration and
accepting DIFF-01 closure. The formal implementer report is the phase-a orchestration record
`.superpowers/sdd/2026-10-21-ama-phase-b/task-B-01-delta-report.md`.
