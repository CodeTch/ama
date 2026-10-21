# MySQL persistence foundation (B-01)

Business data remains in MySQL. The local qualification fixture uses MySQL 8.4.6; the production server version, HA topology, company platform database permissions and recovery qualification remain deployment prerequisites. This task does not provision production databases.

## Deployment migration

Package once, inject `DATABASE_URL` (`jdbc:mysql:...`), `DATABASE_USERNAME`, and `DATABASE_PASSWORD` through the approved secret mechanism, then run the explicit migration step:

```sh
. .tools/env.sh
./mvnw -B -ntp -pl backend/api -am -DskipTests package
bash tools/db/migrate.sh
```

The helper runs `ama.db.MigrationMain` from the packaged application using Spring Boot's PropertiesLauncher. It does not start HTTP listeners. Credentials are read from environment and are never passed as command arguments. Application startup has `spring.flyway.enabled=false`; deployments must finish migration before admitting business traffic. Application startup must never be used as a migration trigger.

Every migration runner first obtains the server-wide MySQL session advisory lock `ama.schema.migration` (up to 20 seconds). Independent JVMs therefore serialize even when first creating Flyway's history table. The session releases the lock in a finally block, and MySQL releases it on connection loss. Flyway additionally checks checksums and schema history; an existing version is never reapplied and existing data is retained. Clean is disabled. A failed migration fails deployment; do not automatically repair history or discard data. MySQL DDL is not a transactional rollback mechanism: interrupted DDL requires operator inspection and an approved forward recovery.

Versions are supplied by the already pinned Spring Boot 4.1.1 BOM: Spring JDBC/transactions 7.0.9, Flyway core/MySQL 12.4.0, Connector/J 9.7.0. No independently floating dependency is added.

## Transaction and audit boundary

Construct `TransactionRunner`, `JdbcTemplate`/repositories, and `JdbcAuditAdapter` with the same `DataSource`. `TransactionRunner.required(Callable<T>)` commits on successful completion, rolls back on runtime exceptions, checked exceptions and errors, and joins nested REQUIRED transactions. Checked exceptions are preserved as the cause of an unchecked exception. A caught inner failure still marks the shared transaction rollback-only.

`AuditPort.append(AuditEvent)` inserts into the caller's transaction and rejects calls without an active transaction on its DataSource. Audit failure aborts work; callers must not swallow persistence exceptions. The event has only actorId, action, resourceId, revision, result, traceId and time. It has no document body, credential or token field. Caller-provided metadata must itself contain only these identifiers/status values.

The foundation migration creates an InnoDB `audit_event` table with utf8mb4, resource/time and trace indexes, and microsecond `DATETIME(6)` values interpreted as UTC. The adapter converts `Instant` to UTC explicitly, independently of the JVM time zone. MySQL connections and the transaction boundary set UTF-8/UTC. Production JDBC URLs must retain bounded connect/socket timeouts and must not override UTC/UTF-8 settings.

## Reusable local test fixture

Only run these commands against the owned Docker fixture; no company/network database is admitted by the test helper:

```sh
python3 tools/dev/mysql_fixture.py start
# Source the exact ignored env.sh path printed by start:
. .tools/mysql-fixture/<owned-name>/env.sh
. .tools/env.sh
python3 tools/checks/run.py it FoundationIT
# When the fixture is no longer needed:
python3 tools/dev/mysql_fixture.py stop "$AMA_TEST_CONTAINER"
```

The script pins the installed official image digest `mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`, creates a unique `ama-test-b01-<12 hex>` container labeled `ama.test-fixture=b01`, and publishes 3306 on an ephemeral **127.0.0.1-only** port. There are no volumes or company data. The container's disposable schema is `ama_b01_fixture`; the `ama_test` user has rights only to this schema. Stop checks the owned name and label and removes only that container.

Generated credentials are stored under ignored `.tools/mysql-fixture/<owned-name>/` with directory mode 0700/file mode 0600. The test connection variables are `AMA_TEST_DATABASE_URL`, `AMA_TEST_DATABASE_USERNAME`, `AMA_TEST_DATABASE_PASSWORD`; `AMA_TEST_CONTAINER` records ownership. Never copy their values into source, command arguments or reports. `MysqlTestDatabase` is reusable test-only support and requires exactly the loopback fixture URL/schema and owned container naming pattern.

`MysqlTestDatabase.reset()` drops and recreates the entire explicitly guarded disposable `ama_b01_fixture` schema before each FoundationIT case, including all objects added by later migrations. Run it alone against its fixture; do not share that schema with concurrent tests or valuable data. Later suites must use another fixture instance when concurrent or when data must survive FoundationIT. The fixture is retained or removed explicitly in the task report.

The nine MySQL cases cover initial/repeated migration, independent migrator JVM serialization, runtime/checked rollback, connection loss with no partial commit and fresh JDBC recovery, nested rollback-only behavior, rejection of autocommit audits, and successful Chinese/emoji plus UTC metadata storage with data preserved across redeployment. A filesystem-only additional table-creating migration verifies consecutive whole-schema resets without polluting shared migration resources. A killed JDBC session intentionally triggers the transaction framework's rollback-failure log; the test captures and asserts only that specific expected ERROR, preserves unrelated logs, restores logging afterward, and checks the exception and absence of committed rows.
