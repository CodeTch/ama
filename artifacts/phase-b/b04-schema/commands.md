# Executed verification commands

All commands ran via Windows PowerShell local cwd C:\Users\LING and WSL Ubuntu-24.04 with explicit --cd /home/tanren/ama/.worktrees/phase-b-file-schema. Linux shells sourced /home/tanren/ama/.worktrees/phase-a/.tools/env.sh. Credential env files stayed under ignored .tools/mysql-fixture; no credentials are in this archive.

Preparation-only (two invocations, no tests):
`./mvnw -B -ntp -pl backend/integration-tests -am -DskipTests test-compile`

New positive fixture:
`python3 tools/dev/mysql_fixture.py start` → ama-test-b01-204cc11baabc /127.0.0.1:45041.
`python3 artifacts/phase-b/b04-schema/capture.py ama-test-b01-204cc11baabc initial-empty` →0tables.

Real RED (after correcting module-cwd ownership lookup; original setup failure retained):
`JAVA_TOOL_OPTIONS=-Dama.file.schema.target=008 python3 tools/checks/run.py it FileSchemaMigrationIT#sixTablesMatchIndependentAdoptedMysqlMetadata`
→ expected six tables versus[]; 1failure/0error/0skip.
`capture.py ama-test-b01-204cc11baabc lower-set-red` preserves successful001/002/005/006/007/008.

After final V009 SHA79f048… was shared and root explicitly froze/ACKed via register commitb3c00ce:
`sha256sum backend/adapters/src/main/resources/db/migration/V009__file_schema.sql`
`capture.py ama-test-b01-204cc11baabc pre-v009-frozen`
`JAVA_TOOL_OPTIONS=-Dama.file.schema.target=009 python3 tools/checks/run.py it FileSchemaMigrationIT` → first-green8/0/0/0 and first V009 application.
`capture.py ama-test-b01-204cc11baabc first-success`

Java units:
`python3 tools/checks/run.py unit` →28/0/0/0.
Canonical Java units use their own H2 and intentionally unreachable loopback MySQL probe; this is not a global “no DB tests” claim.

Separate new regression fixture:
`python3 tools/dev/mysql_fixture.py start` →ama-test-b01-2281b6270fc4 /127.0.0.1:46305.
`capture.py ama-test-b01-2281b6270fc4 foundation-initial-empty` →0tables.
`python3 tools/checks/run.py it 'FoundationIT#migrationAppliesOnce+consecutiveResetsRemoveTablesFromLaterMigrations+independentMigratorsWaitForDatabaseLockThenApplyOnce+rollbackDoesNotLeaveSuccessAudit+connectionLossRollsBackAndFreshJdbcRecovers+checkedExceptionRollsBackBusinessAndAudit+auditCannotAutocommitWithoutCallerTransaction+nestedRequiredCannotCommitAfterInnerFailureIsCaught+successfulTransactionStoresUtf8mb4AndUtcMetadata+frozenBaselineUpgradesWithoutChangingRowsOrHistory+auditIdentifiersMatchCaseAndTrailingSpacesExactly+auditNanosecondsTruncateToUtcMicrosecondsRegardlessOfJvmTimezone+auditJoinsExternalSpringTransactionAndRollsBackWithIt'`
→13/0/0/0. Existing reset-based NORMAL harness operates only on this separate new regression fixture. Its existing temporary reset-probe table/test-private filesystem migration is unchanged test infrastructure, not a production version allocation.
`capture.py ama-test-b01-2281b6270fc4 foundation-final-success`

Existing backend regression:
First independently asserted glob '*[ty]IT' matches exactly FileStorageContractIT and LocalDurabilityIT in this checkout.
`python3 tools/checks/run.py it '*[ty]IT'` →local-only contract7/0/0/0 and durability39/0/0/0, combined46. No S3 group is present or counted.

After self-review added pre-seed Flyway.validate() to the own test fixture guard:
`JAVA_TOOL_OPTIONS=-Dama.file.schema.target=009 python3 tools/checks/run.py it FileSchemaMigrationIT`
→final8/0/0/0, both migrate calls0, no SQL changed.
`capture.py ama-test-b01-204cc11baabc final-positive`

Offline evidence checks:
`python3 artifacts/phase-b/b04-schema/verify_snapshots.py`
`python3 artifacts/phase-b/b04-schema/verify_delivery.py`
The latter reads raw XML testcase elements, verifies95 distinct selected passing cases (not double-counting first/final schema reruns), independently compares all three successful captures to adopted metadata, and rechecks SQL bytes/history.

Historical IdentityMigrationIT latest007 assumption, V006 refusal parameter cases, unrelated web/Python/OpenAPI suites, whole B04 registry/API/S3 and production deployment qualification were not run or counted. No failed migration was reset, repaired, or hidden.
