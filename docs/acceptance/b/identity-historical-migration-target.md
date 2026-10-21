# Historical V007 identity migration test target

Scope: test maintenance only, pending independent review. Reviewed BASE `f4e2c6fc0baf6c95c01a4b51cfb25ad8200e0ab4` includes V008. `IdentityMigrationIT` previously invoked the production latest migrator but asserted history only through007 and no meeting tables, so normal V008 classpath growth broke the historical test.

The test now uses a private Flyway helper with explicit target007, classpath migration location, UTF8/UTC init SQL and cleanDisabled=true. Both initial migration and the zero-change rerun use that helper. All existing history, NO PAD IDs, department FK, verification/generation/role/session constraints, long display name and future-table absence assertions remain unchanged. Production SchemaMigrator still migrates latest; no SQL, shared fixture helper, runner or POM changed.

## Real RED → GREEN

Command for both runs, with the pinned runtime and the respective newly owned fixture env sourced:

```text
python3 tools/checks/run.py it IdentityMigrationIT
```

|Run|Owned fixture / loopback port|Actual result|
|---|---|---|
|RED, original test unchanged|ama-test-b01-92d85c5fdcb3 /44891|1 test, 1 assertion failure, 0 errors/skips: expected001/002/005/006/007, actual also008. All six migrations succeeded. Fixture, failure XML/log and metadata preserved; no reset afterward.|
|GREEN, explicit007 helper|ama-test-b01-afb009479e40 /45577|1 test, 0 failures/errors/skips. History001→002→005→006→007 allsuccess1, later meeting/grant/roster tables0, rerun0. Canonical verify also ran28 Java unit tests, all passing.|

Both fixtures were newly created for this task and confirmed ready, ownership-labeled, loopback-bound and zero-table before migration. The original B02 worktree, commits, reports,45-file evidence and all old fixtures were untouched. The RED fixture has a test assertion failure, **not** failed Flyway history.

## Disclosed startup profile exception

Two earlier new helper containers failed before readiness or SQL/migration/tests. The first container's Docker logs were lost when the existing helper cleaned it up; that loss is retained and no logs were reconstructed. Actual second startup state/log showed InnoDB io_setup EAGAIN and AIO initialization failure. Read-only host observation was aio-nr61455/max65536, with ample memory/disk.

The coordinator authorized a process-local wrapper around the unchanged reviewed helper, strictly matching its unique owned docker-run argument structure and pinned image, to append `--innodb-use-native-aio=OFF` only for the two subsequent new task fixtures. Ownership/loopback checks remained intact. Both actual qualification fixtures report `@@innodb_use_native_aio=0`. No sysctl, global helper, RAM limit, daemon restart or other fixture stop/cleanup was used. This validates historical SQL structure/IDs/FKs only; it is not the original native-AIO profile or production I/O/capacity qualification.

## Immutable inputs and evidence

All formal SQL files001/002/005/006/007/008 retain the reviewed BASE bytes. V007 SHA256 remains `859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d`; V008 remains `fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3`. Flyway checksums001=1201435595,002=1757300424,005=-1999355348,006=-1744360556,007=157746768; RED additionally008=-1747387036. No repair/outOfOrder or SQL edit occurred.

Ignored allowlisted evidence: `artifacts/phase-b/b02-historical-target/` in the owning worktree. It contains actual RED/GREEN logs and XML, both initial-empty inventories, post-run history/DDL/metadata, unchanged SQL SHA comparison, process-local wrapper source and redacted argument structure, host AIO snapshot, actual second startup failure state/log and explicit first-log-loss observation. Credential/env files are excluded. The coordinator report records the final commit and complete owned range.

No all/web/OpenAPI reruns were needed or run for unchanged production. The producer implementation and full B02 ACL closure remain OPEN; no company calls, main merge, push or subagents occurred.
