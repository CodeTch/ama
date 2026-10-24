# Task5 resource activation preflight — 2026-10-23

PREPARED ONLY. No activation, test, product edit, Git/index operation, container creation/start/pull, directory/PKI/credential generation, network test, DB access or private environment read occurred. This report is the only write. Root must independently ACK this concrete plan at Task5 activation and repeat absence/containment/image checks then. Task4 author source/index freeze is respected; no inference of Task5 BASE from historical briefs.

## Evidence and exact selection

Read first task-05-brief-prepared.md, task-05-parser-fixture-preflight.md, resume-20261023-active-context.md; then the adopted import-worker design and public helpers below. Digest comes from the actual prior parser/fixture preflight and tools/dev/s3_fixture.py, not an invented release tag:

`rustfs/rustfs@sha256:128220dc9382bac81940a1a9616ee8c274a3f3b8f647eedbeb50df5bb8b49922`

Selected `docker image inspect --format '{{json .Id}} {{json .RepoDigests}} {{json .Os}} {{json .Architecture}} {{json .Config.User}} {{json .Config.ExposedPorts}}' IMAGE` exited 0: ID sha256:128220dc9382bac81940a1a9616ee8c274a3f3b8f647eedbeb50df5bb8b49922; RepoDigests contains that exact reference; linux/amd64; Config.User rustfs; exposed 9000/tcp and 9001/tcp. Image is locally PRESENT. No image pull is needed or authorized by this preflight. No Config.Env was requested.

Generated proposal UUID (no resource generation): `5237a2db-e8d5-4cbe-b357-b35df5cfcba5`.

- Workspace R = `/home/tanren/ama/.worktrees/phase-b-completion`.
- Private run root P = `R/.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5`.
- Public evidence E = `R/artifacts/phase-b/b-remaining/task-05/run-5237a2db-e8d5-4cbe-b357-b35df5cfcba5`.
- Proposed container name NAME = `ama-task05-s3-5237a2db-e8d5-4cbe-b357-b35df5cfcba5`.
- Exact owner label OWNER = `ama-task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5`.
- Receipt = `P/s3/fixture.json`; server credentials = `P/s3/server.env`; JVM client environment = `P/client.env`; new PKI = `P/pki`; native durable files = `P/native/data`, with sibling role/config/process logs all inside `P/native`; fresh JVM durable configuration = `P/runtime`.

Observed P and E absent, not symlinks, resolve exactly to the stated paths, and resolved paths are descendants of resolved R. Report path was also absent before this write. Existing R, R/.tools, R/artifacts/phase-b/b-remaining and W were nonsymlinks, resolved exactly, mode0755 uid1000/gid1000. All future child resources are absent by absent P/E; recheck every existing ancestor and reject dangling symlinks as well as existing targets before creating anything.

Exact selected `docker container inspect --format '{{.Id}}' NAME` exited 1, stdout empty, stderr `Error response from daemon: No such container: ama-task05-s3-5237a2db-e8d5-4cbe-b357-b35df5cfcba5`. The subsequent selected image query succeeded, so this was an actual absent-name response rather than a silent daemon outage. No container inventory dump occurred.

## Activation guard and resource argv template (prospective only)

The Task5 author must implement the fresh driver under its authorized task evidence/source scope; do not run tools/dev/s3_fixture.py unchanged. Its defaults point to s3-java, create allows an existing HOME, its internal container name is random and its inspect helper internally reads a complete inspect object. The template below deliberately uses the exact guarded name, new roots, exclusive writes and selected public inspection. No historical .tools env/data/PKI or old b06 helper is an input.

Before mutations, inspect only the selected digest and NAME as above. Accept name absence only for the exact Docker no-such-container response; reject transport/daemon errors, ambiguity, image absence or wrong platform/digest. Recheck root and target paths; `exists()` alone is insufficient for dangling symlinks. A Python guard should apply the following to P/E and each child before creation:

```python
R = Path('/home/tanren/ama/.worktrees/phase-b-completion')
P = R / '.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5'
E = R / 'artifacts/phase-b/b-remaining/task-05/run-5237a2db-e8d5-4cbe-b357-b35df5cfcba5'
assert R.resolve(strict=True) == R
for target in (P, E):
    assert not target.exists() and not target.is_symlink()
    assert target.resolve().is_relative_to(R)
    for ancestor in target.parents:
        if ancestor.exists():
            assert not ancestor.is_symlink()
        if ancestor == R: break
# Immediately before each creation repeat its target check; mkdir(exist_ok=False).
# Private root and private child directories: mode0700, umask077.
# Exclusive files: os.open(path, O_WRONLY|O_CREAT|O_EXCL, 0o600).
```

Create only P and its named children; E and a missing task-05 public parent may be created only as the Task5 artifact scope permits. No recursively changed parent permissions, reset, deletion or replacement. s3/data and s3/logs are the only children that need uid10001/gid10001 mode0750, matching the approved public helper. Exact creation argv after absent child checks:

```text
sudo -n install -d -o 10001 -g 10001 -m 750 P/s3/data
sudo -n install -d -o 10001 -g 10001 -m 750 P/s3/logs
```

Generate fresh access/secret with `secrets.token_hex(12)` / `secrets.token_hex(24)` privately after guards. Write only new0600 server.env containing RUSTFS_ACCESS_KEY, RUSTFS_SECRET_KEY, RUSTFS_CONSOLE_ENABLE=false, RUSTFS_VOLUMES=/data, RUSTFS_OBS_LOG_DIRECTORY=/logs, RUST_LOG=warn. Never echo or place the values in argv, public JSON, manifests or logs.

Exact Docker argv array, with P substituted by the exact absolute private root above and IMAGE by the pinned digest:

```python
['docker','run','-d',
 '--name','ama-task05-s3-5237a2db-e8d5-4cbe-b357-b35df5cfcba5',
 '--platform','linux/amd64', '--user','10001:10001',
 '--cpus','2', '--memory','2g', '--memory-swap','2g', '--pids-limit','256',
 '--log-opt','max-size=10m','--log-opt','max-file=2',
 '--label','ama.owner=ama-task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5',
 '-p','127.0.0.1::9000',
 '--env-file',str(P/'s3/server.env'),
 '--mount','type=bind,source='+str(P/'s3/data')+',target=/data',
 '--mount','type=bind,source='+str(P/'s3/logs')+',target=/logs',
 'rustfs/rustfs@sha256:128220dc9382bac81940a1a9616ee8c274a3f3b8f647eedbeb50df5bb8b49922']
```

Bounds are memory2147483648, MemorySwap2147483648 (total RAM+swap, not extra2GiB), NanoCpus2000000000, PidsLimit256. Console disabled and9001 not published. Docker allocates the host port atomically; do not reserve then release a Python socket as the old helper does. Read the actual assigned port from selected NetworkSettings.Ports, never assume HostConfig's empty requested ephemeral port is actual.

Capture stdout CID privately, validate64 lowercase hex, and write a new exclusive0600 receipt. Thereafter address only that full CID, while checking name+owner+image+mount+limits. Exact selected post-create inspection (no Config.Env):

```text
docker container inspect --format '{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Config.Image}},"owner":{{json (index .Config.Labels "ama.owner")}},"memory":{{json .HostConfig.Memory}},"memorySwap":{{json .HostConfig.MemorySwap}},"nanoCpus":{{json .HostConfig.NanoCpus}},"pids":{{json .HostConfig.PidsLimit}},"ports":{{json .NetworkSettings.Ports}},"mounts":{{json .Mounts}}}' CID
```

Require exact receipt CID/name/owner/digest and only two expected bind mount source/destination pairs; actual9000 binding exactly one127.0.0.1 with nonempty numeric port; no published9001. Reject unexpected mounts/bindings. Store sanitized selected metadata publicly. Store all failed setup commands/statuses sanitized; retain created resources rather than replacing or clearing them on failure. Any future stop/restart requires exact CID/receipt ownership revalidation and its own authorized task scope. Do not touch existing containers, objects, routes or networks.

## Transport, native service and credential custody

Root clarified during this preflight: actual LOCAL mTLS is required; controlled S3 loopback HTTP is acceptable for this adopted harness. The S3 endpoint is `http://127.0.0.1:ACTUAL_PORT`, with fresh explicit SigV4 credentials, path style and region us-east-1 as in S3Fixture. The helper does NOT provide S3 TLS; this plan adds no proxy/dependency and makes no production transport qualification. Optional S3 TLS would be a separately specified plan, never a claim inferred from LOCAL mTLS or Task12 browser HTTPS.

Reuse LocalFixture/Task04Pki public mechanisms, not their old resource destinations: Task04Pki currently makes target/local-fixtures/task04-UUID and LocalFixture.ensurePki makes target/local-fixtures/pki-UUID. For Task5 the author must explicitly bind a fresh Task5 PKI directory P/pki before LocalFixture can invoke ensurePki; no static old PKI reuse. Supply `new LocalFixture(P.resolve("native/data"))`, which keeps its sibling data-role/data-service.properties/data-process.log inside P/native. Preserve genuine kernel mount identity/volume guard and0700/0600 custody, no invented mount facts.

Fresh PKI command patterns from the actual helpers (prospective working directory P/pki, each command timeout20s, each separate sanitized status/log):

```text
openssl req -x509 -newkey rsa:2048 -noenc -keyout ca.key -out ca.crt -days 2 -subj "/CN=AMA local test CA"
openssl req -newkey rsa:2048 -noenc -keyout server.key -out server.csr -subj /CN=server
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt -days 2 -extfile server.ext
openssl pkcs12 -export -inkey server.key -in server.crt -certfile ca.crt -out server.p12 -passout file:keystore-password
keytool -importcert -noprompt -alias ca -file ca.crt -keystore trust.p12 -storetype PKCS12 -storepass:file keystore-password
```

server.ext: subjectAltName=DNS:localhost, extendedKeyUsage=serverAuth, basicConstraints=CA:FALSE, keyUsage=digitalSignature,keyEncipherment. Repeat req/sign/export with writer and reader and URI SAN spiffe://ama/writer or reader, clientAuth. For negative TLS tests mirror the inspected rogue CA/rogue client, outsider and expired identities, all newly generated under P/pki; never reuse Task4 material. Use umask077 and private files throughout. A fresh0600 keystore-password file lets command argv retain no password value; the Task5 fixture must pass that same private password to ServiceTls rather than assume the existing static test-only PASSWORD. This is a small future test-helper adaptation, not implemented here.

Native process argv mechanism already present:

```text
JAVA -Xmx64m -jar R/backend/file-service/target/ama-file-service-0.1.0-SNAPSHOT-exec.jar P/native/data-service.properties
```

Properties contain exact new data/mount/role/keystore/truststore paths, backendId and loopback bindAddress127.0.0.1/port0; keystore/truststore passwords pass only in private child environment AMA_FILE_KEYSTORE_PASSWORD/AMA_FILE_TRUSTSTORE_PASSWORD. URI remains https://localhost:ACTUAL_PORT to match SAN; actual listener is127.0.0.1. LocalFixture startup is25s and owned-process stop wait10s. Application/health/test proxy listeners likewise127.0.0.1 port0. Capture allocated ports/PIDs and owned process handles; bounded joins and close target only these processes. Fresh JVM uses the same new durable config/PKI/archive resources and original retained task after defaults change. Existing RegistryApiProcess mechanism illustrates this lifecycle but its DB-password command-line construction must not be copied; child environment/private config custody must keep secrets out of argv/logs.

Only preexisting approved current13 MySQL remains permitted at127.0.0.1:46653; do not create/restart/reset/migrate it. This preflight did not read its private .tools/phase-b-remaining/env.sh. At activation the already authorized harness may privately inject required existing DB environment into its child without printing it; no DB credential regeneration or copied public environment dumps. New S3 client env0600 supplies only this new endpoint/access/secret. Public manifest contains hashes of public evidence/source, not private credentials/PKI/config contents. No shell xtrace or complete environment serialization.

## How genuine lifetime/deadline/abort proof fits this resource plan

All following are FUTURE TESTS, not executed or passed. Use genuine production LOCAL mTLS and real fresh RustFS SDK requests/bytes through the controlled HTTP harness, actual Task4 intake and production WorkerLoop/parser/MySQL RAW; no SQL RAW/direct-claim stand-in. Every test gets fresh owned bucket/object/task identifiers. S3Fixture already creates UUID data/control buckets and closes clients without deleting history; retain those resources and record only sanitized identifiers.

LocalFileClient.send currently establishes a5-minute HttpRequest timeout and uses BodyHandlers.ofInputStream, then may read an error body before returning; json/empty subsequently read bodies. This does not prove a full body lifetime bound. Tests must stall success metadata, success data, and error body separately after headers using a fresh loopback mTLS test endpoint/controlled handler with new P/pki. Install actual transport lifetime/abort authority before these points; prove the reader exits within the configured bounded deadline, not only that a watchdog/caller returns. Use latches/barriers, bounded futures/joins and actual server exchange/socket/reader termination observations; release the fixture's stall in finally only after capturing the deadline/abort outcome so fixture cleanup cannot falsely cause the asserted unblock.

S3FileStorage configures connection/socket/API-call/API-attempt deadlines and disables retries(maxAttempts1); S3Objects.raw retains an admission permit until release invokes actual ResponseInputStream.abort. RangeReader's plain boolean closed is currently not proof against normal-close/watchdog races. WireProxy supplies actual upstream/downstream counters, exact request selectors, latches and pause/drop faults; it is plain loopback HTTP and redacts Authorization/native UploadId. Its existing pause occurs before downstream headers and has45s auto release; it does not by itself test a stalled body after headers. The Task5 author must add narrowly scoped real-body stall/barrier support for an actual GET/control-metadata/error body and observe downstream abort/upstream closure. Keep fresh proxy listener127.0.0.1:0 and track sockets; bound waits below helper auto release and assert actual reading task termination before release. Use short explicit controlled S3Config deadlines for focus tests rather than waiting default20s where appropriate.

For both transports test watchdog vs normal close/EOF/error races with deterministic barriers. Assert transport abort/close is called exactly once, actual blocked read exits, no reader or selected watchdog remains, and every acquired request/admission slot is released exactly once (including a follow-up genuine request/capacity check). Merely observing flags, interrupted headers, outer future cancellation, socket timeout config, or process termination is insufficient. Also prove lease loss/Worker shutdown aborts actual source I/O outside DB roots/monitors, and retained stream ownership survives until parse completion. Do not count a fresh JVM or connection as a database-server restart. None of these results follows from image availability.

## Source seals and qualification

Read-only SHA256 snapshot of referenced helper/transport files (Task5 must reread actual current sources at activation):

```text
e513d0c774a310aa306d717d149dd8fe4fa9fa6bb3527c3cf602404876bf17ab tools/dev/s3_fixture.py
c4f66058ffa3505fd70aeda3830e4e8105400a4040edc8d146942fe1ef79008f backend/integration-tests/src/test/java/ama/storage/LocalFixture.java
883f956af5df9acd32a1482d765d135e061fa48b9de55b2a991562b9233b7cda backend/integration-tests/src/test/java/ama/storage/Task04Pki.java
ce4b6556c03518e705478613723780c6ea8dfbefe6f79311edd15314fa72dffd backend/integration-tests/src/test/java/ama/storage/s3/S3Fixture.java
5a4fb28797b12dfd4aed5a6eb3ab25e5189629a37a608fbe6c7ca28e0e286ff2 backend/integration-tests/src/test/java/ama/storage/s3/WireProxy.java
22b4e977900f70bc643b239eb5cd67c8b853340fd4e16260f41f364ba86444c7 backend/adapters/src/main/java/ama/storage/LocalFileClient.java
1bd498716eb36844606e63f894956baf6b6fa862a536a72befd26764ce5f931f backend/adapters/src/main/java/ama/storage/s3/S3Objects.java
401b1821e6dc8ef237c4606dfcd012b1a6123d9b1d2f847517b8a7c2e95464cb backend/adapters/src/main/java/ama/storage/s3/RangeReader.java
```

No present resource blocker was observed: selected image is present and exact proposed targets/name are absent. Root ACK plus contemporaneous guard recheck is the activation gate. Future driver/helper adaptation and real deadline/abort proof remain implementation work. This is resource preparation only; it grants no production/company/HA/capacity or completed LOCAL/S3 Worker qualification.
