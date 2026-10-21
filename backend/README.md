# API foundation (phase A-01)

Run from the repository root in Linux/WSL x86_64. No global Java installation is needed:

```sh
python3 tools/bootstrap.py
. .tools/env.sh
npm ci --ignore-scripts
python3 tools/checks/run.py all
./mvnw -B -ntp package
java -jar backend/api/target/ama-api-0.1.0-SNAPSHOT-exec.jar
```

`tools/toolchain-lock.json` records official URLs and digests. It pins Azul Java 21.0.12.1, Node 22.23.4 / npm 10.9.9, Apache Maven 3.10.0 and wrapper 3.3.4. Spring Boot 4.1.1 pins the compatible dependency/plugin versions in its BOM/parent; the root POM explicitly targets Java 21. The standard binary wrapper pins both Maven distribution and wrapper JAR SHA256; it does not require an installed `unzip` command. The build uses the standard user Maven cache, not a global Java or Maven installation.

`.mvn/maven.config` disables Resolver's optional remote prefix filter. Its automatic repository catalog probes otherwise stalled on DNS for a transitive Apache snapshot repository even with the actual release artifacts cached. This does not disable normal dependency resolution or checksum verification. See [Resolver filtering](https://maven.apache.org/resolver/remote-repository-filtering.html).

API listener: port 8080 (all requests denied until business routes/security are implemented). Management: **127.0.0.1:8081 only**. Health access additionally requires an allowed source network and the management listener's local port. `X-Forwarded-For` is ignored. Do not proxy `/internal/**` through public ingress. For remote monitoring, use an approved internal forwarding arrangement and ingress firewall; this phase defaults to local monitoring only.

```sh
curl --noproxy '*' -i http://127.0.0.1:8081/internal/health/live
curl --noproxy '*' -i http://127.0.0.1:8081/internal/health/ready
```

Liveness is independent of MySQL. Readiness includes Spring's admission state and a **real JDBC connection check**, returns 503 when the database cannot be reached, and recovers after JDBC is restored. A missing database never becomes a successful readiness response. The Hikari pool starts lazily, with a 1 second acquisition timeout and MySQL connect/socket timeouts in the default URL. Production custom URLs must retain bounded driver timeouts. This task does not certify schema migrations, storage write durability, MySQL failover, corporate interfaces or capacity. Later phases add their own dependency gates.

Configuration is injected through environment variables or controlled external Spring configuration. `DATABASE_MODE=private-ha|enterprise-platform` and `STORAGE_TYPE=local|s3` are independent. `STORAGE_DEFAULT_BACKEND_ID` defaults to `local-primary`; assign a stable deployment-specific ID before any writes. This task validates the ID without implementing file routing. `local` does not require any S3 parameters. `s3` requires `S3_ENDPOINT` (HTTP/S without embedded userinfo), `S3_BUCKET`, `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`. The config record does not retain those secrets. Do not put real credentials into tracked files or shell history.

MySQL: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`. Default URL is an intentionally unavailable local MySQL dependency until provisioned; startup succeeds and readiness remains 503. `PORT` and `INTERNAL_PORT` configure the two listener ports. `INTERNAL_HEALTH_ALLOWED_NETWORKS` is a comma-separated CIDR allowlist defaulting to IPv4/IPv6 loopback. Health/error replies never expose connection URLs, root directories or credential values. The positive local readiness test uses a real H2 JDBC/TCP server on a temporary port in the test process, stops/restarts it, and checks 200 → 503 → 200; H2 is test scope only and no production profile bypasses MySQL.

Verification entrypoint: `python3 tools/checks/run.py <unit|it|web|e2e|visual|contract|load|ops> <selector>`.

- `unit DeploymentConfigTest`: Surefire class/method selection, with actual executed count and fresh JUnit evidence paths. `unit ContractClientTest` exercises the test-only HTTP helper against a local HTTP server.
- `contract OpenApi`: validate OpenAPI 3.1 and merge external references into `artifacts/openapi/api.yaml` with pinned Redocly CLI. Other contract selectors require later registered corporate fixtures, never default production requests.
- `it <Class>`: Failsafe `-Dit.test=<Class> verify`; requires registered `*IT` tests and explicit `AMA_TEST_DATABASE_URL=jdbc:mysql:...` for an approved test dependency. No MySQL integration cases are delivered in this task.
- `web/e2e/visual/load/ops` fail explicitly until their task registers implementations and required environments. Selectors are never shell commands.
- `all`: convenience suite for **currently delivered** Python tests, OpenAPI validation/merge and all Java unit/helper tests. It does not imply future modes or external qualification passed.

Zero matches, zero executed tests, any skipped required test, unavailable tooling or subprocess failures never return PASS. Subprocess exit codes are preserved. Test-only HTTP/database fixtures own their cleanup. JUnit reports are under `backend/*/target/surefire-reports/`; OpenAPI bundle is under `artifacts/openapi/`.
