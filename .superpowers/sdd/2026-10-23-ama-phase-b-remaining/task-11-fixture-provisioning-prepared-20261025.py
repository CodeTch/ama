"""One-shot, root-reviewed Task 11 empty-schema provisioning on the owned Task 8 MySQL.

No work occurs on import. Read the adjacent prepared note before explicit execution.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import uuid


TREE = Path(__file__).resolve().parents[3]
W = TREE / ".superpowers/sdd/2026-10-23-ama-phase-b-remaining"
TOOLS = TREE / ".tools"
PARENT_OWNER = "task08-848e93aace8b4f0fa34fc548267f3dc5"
PARENT = TOOLS / PARENT_OWNER
TASK10_OWNER = "task10-1e48b41fc5c84a98af3a14b51b17884f"
TASK10_SCHEMA = "ama_t10_1e48b41fc5c8_behavior"
TASK10_ENV = TOOLS / TASK10_OWNER / "mysql-recovery-20261024" / "env.sh"
TASK5_OWNER = "task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5"
TASK5_ENV = TOOLS / TASK5_OWNER / "client.env"
TASK5_PKI = TOOLS / TASK5_OWNER / "pki"
CID = "11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad"
IMAGE = "mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9"
SERVER_UUID = "1bdea6eb-cf32-11f1-8fe3-fef4aa4b3a25"
PORT = 46151
HISTORICAL_PORT = 44727
PARENT_GUARD = W / "task-10-fixture-creation-guard-20261024.json"
PARENT_ACK = W / "task-10-root-fixture-application-ack-20261024.json"
RECOVERY_ACK = W / "task-10-mysql-recovery-application-ack-20261024.json"
ADOPTION_ACK = W / "task-10-consumer-activation-root-ack-20261025.json"
ADOPTION_ACK_SHA256 = "763c7da6870188b6ac07177391e197df956c121bdea10a9df2afd81d998f5fb3"
ADOPTION_SOURCE_COMMIT = "86777940c3f9500b1b89d884deaae2a48740d9a9"
ADOPTION_COMMIT = "ba48f35e7ef97c1e162a2d373453bd1e9689529c"
ADOPTION_DOCUMENT = "docs/acceptance/b/task-B-remaining-10-minutes-editor-adoption-20261025.md"
OWNER_RE = re.compile(r"task11-([0-9a-f]{32})\Z")
SCHEMA_RE = re.compile(r"ama_t11_([0-9a-f]{12})_behavior\Z")
ASSIGN_RE = re.compile(r"(?:export )?([A-Z][A-Z0-9_]*)=(.*)\Z")


def fail(message):
    raise RuntimeError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def inside_tools(path, *, existing):
    if TREE.is_symlink() or not TREE.is_dir() or TOOLS.is_symlink() or not TOOLS.is_dir():
        fail("product tree/.tools path guard failed")
    if TOOLS.resolve() != TREE.resolve() / ".tools":
        fail(".tools resolves outside product tree")
    if not path.is_relative_to(TOOLS) or path == TOOLS:
        fail("path is outside .tools")
    if existing:
        if path.is_symlink() or not path.resolve().is_relative_to(TOOLS.resolve()):
            fail("existing private path resolves outside .tools")
    elif path.exists() or path.is_symlink():
        fail("fresh owner path already exists; inspect prior outcome, never retry")


def private_owned(path, owner, *, directory=False):
    root = TOOLS / owner
    inside_tools(root, existing=True)
    if (root.is_symlink() or not root.is_dir() or root.stat().st_mode & 0o777 != 0o700
            or root.resolve(strict=True) != TOOLS.resolve(strict=True) / owner
            or not path.is_relative_to(root)):
        fail("private owner root guard failed: " + owner)
    current = root
    for part in path.relative_to(root).parts:
        current = current / part
        if current.is_symlink() or not current.exists():
            fail("private path component guard failed: " + current.name)
        if not current.resolve(strict=True).is_relative_to(root.resolve(strict=True)):
            fail("private path escapes owner: " + current.name)
        if current != path and (not current.is_dir() or current.stat().st_mode & 0o077):
            fail("private intermediate directory guard failed: " + current.name)
    if directory:
        if not path.is_dir() or path.stat().st_mode & 0o777 != 0o700:
            fail("private directory mode/type guard failed: " + path.name)
    elif not path.is_file() or path.stat().st_mode & 0o777 != 0o600:
        fail("private file mode/type guard failed: " + path.name)
    return path


def public_file(path):
    if not path.is_relative_to(TREE) or TREE.is_symlink() or not TREE.is_dir():
        fail("public evidence tree guard failed")
    current = TREE
    for part in path.relative_to(TREE).parts:
        current = current / part
        if current.is_symlink() or not current.exists():
            fail("public evidence symlink/missing guard failed")
    if not path.is_file() or path.resolve(strict=True) != TREE.resolve(strict=True) / path.relative_to(TREE):
        fail("public evidence canonical file guard failed")
    if path.is_relative_to(TOOLS):
        fail("private path is forbidden as public evidence")
    return path


def run(argv, *, sql=None, timeout=20):
    try:
        result = subprocess.run(argv, input=sql, text=True, capture_output=True,
                                timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired):
        fail("command unavailable or timed out; outcome may be partial; inspect retained state")
    if result.returncode:
        # Docker/mysql output may contain secrets. Never print or persist it.
        fail("selected command failed; outcome may be partial; inspect retained state")
    return result.stdout.strip()


def selected():
    template = ('{"id":{{json .Id}},"name":{{json .Name}},'
                '"image":{{json .Config.Image}},'
                '"owner":{{json (index .Config.Labels "ama.test-fixture")}},'
                '"memory":{{json .HostConfig.Memory}},'
                '"swap":{{json .HostConfig.MemorySwap}},'
                '"cpus":{{json .HostConfig.NanoCpus}},'
                '"pids":{{json .HostConfig.PidsLimit}},'
                '"ports":{{json .NetworkSettings.Ports}},'
                '"mounts":{{json .Mounts}},'
                '"state":{{json .State.Status}},'
                '"running":{{json .State.Running}},'
                '"oom":{{json .State.OOMKilled}}}')
    try:
        value = json.loads(run(["docker", "container", "inspect", "--format", template, CID]))
    except ValueError:
        fail("selected Docker fields were invalid")
    parent = json.loads(PARENT_GUARD.read_text(encoding="utf-8"))["selectedAfter"]
    expected = {
        "id": CID, "name": parent["name"], "image": IMAGE,
        "owner": PARENT_OWNER, "memory": 805306368, "swap": 805306368,
        "cpus": 1000000000, "pids": 256, "state": "running",
        "running": True, "oom": False,
    }
    if any(value.get(key) != item for key, item in expected.items()):
        fail("selected parent identity/state/resource guard mismatch")
    ports = value.get("ports", {}).get("3306/tcp")
    if ports != [{"HostIp": "127.0.0.1", "HostPort": str(PORT)}]:
        fail("selected parent loopback port guard mismatch")
    mounts = value.get("mounts")
    if not isinstance(mounts, list) or len(mounts) != 2 or not all(isinstance(m, dict) for m in mounts):
        fail("selected parent mount guard mismatch")
    if sorted(mounts, key=lambda m: m.get("Destination", "")) != sorted(
            parent["mounts"], key=lambda m: m.get("Destination", "")):
        fail("selected parent mount guard mismatch")
    return value


def mysql(sql, *, test=False, database=None):
    cnf = "/run/ama-task08/test.cnf" if test else "/run/ama-task08/root.cnf"
    argv = ["docker", "exec", "-i", CID, "mysql", "--defaults-extra-file=" + cnf,
            "--protocol=TCP", "-h127.0.0.1", "--batch", "--skip-column-names"]
    if database is not None:
        if not SCHEMA_RE.fullmatch(database):
            fail("test database argument is outside Task 11 scope")
        argv.extend(["--database", database])
    return run(argv,
               sql=sql, timeout=20)


def server():
    fields = mysql("SELECT VERSION(), @@server_uuid, @@global.innodb_use_native_aio, "
                   "@@global.partial_revokes, @@global.sql_mode;\n").split("\t")
    engine = mysql("SELECT SUPPORT FROM information_schema.ENGINES WHERE ENGINE='InnoDB';\n")
    if (len(fields) != 5 or fields[:4] != ["8.4.6", SERVER_UUID, "0", "0"]
            or not re.fullmatch(r"[A-Z0-9_,]*", fields[4])
            or "NO_BACKSLASH_ESCAPES" in fields[4].split(",") or engine != "DEFAULT"):
        fail("selected MySQL version/AIO/engine/server UUID/grant-mode mismatch")
    return {"version": fields[0], "serverUuid": fields[1], "aio": 0,
            "engine": "InnoDB", "engineSupport": engine,
            "partialRevokes": 0, "sqlMode": fields[4]}


def check_records():
    # Historical Task 10 guard/application ACK remain pinned at 44727. The separate,
    # exact recovery ACK binds this same owner/CID to the current loopback port/env.
    for path in (PARENT_GUARD, PARENT_ACK, RECOVERY_ACK):
        public_file(path)
    guard_bytes = PARENT_GUARD.read_bytes()
    ack_bytes = PARENT_ACK.read_bytes()
    recovery_bytes = RECOVERY_ACK.read_bytes()
    guard = json.loads(guard_bytes)
    ack = json.loads(ack_bytes)
    recovery = json.loads(recovery_bytes)
    if (sha(guard_bytes) != "8d999f4af9ccee70d9903755c0a0d4594e05a70e81310c9026a879991f902c62"
            or sha(ack_bytes) != "808a552c4afbfdb77e9663979ba44f6068f11b6af7611adb633af4bbd2991df3"
            or sha(recovery_bytes) != "096cbdc38cb350b756c6958a0982d31277e4f0a4bc2f7a10e4f24fd1fc40b767"
            or ack.get("base") != "97e788fe96744af88513cc33bfed652b204c9972"
            or ack.get("creationGuardSha256") != sha(guard_bytes)
            or ack.get("task") != 10
            or ack.get("cid") != CID or ack.get("parentResourceOwner") != PARENT_OWNER
            or ack.get("owner") != TASK10_OWNER or ack.get("schema") != TASK10_SCHEMA
            or ack.get("port") != HISTORICAL_PORT
            or ack.get("ready", {}).get("serverUuid") != SERVER_UUID
            or ack.get("ready", {}).get("schema") != TASK10_SCHEMA
            or ack.get("privateEnv") != str((TOOLS / TASK10_OWNER / "env.sh").relative_to(TREE))
            or guard.get("containerId") != CID or guard.get("parentOwner") != PARENT_OWNER
            or guard.get("owner") != TASK10_OWNER or guard.get("schema") != TASK10_SCHEMA
            or guard.get("port") != HISTORICAL_PORT
            or guard.get("selectedAfter", {}).get("id") != CID
            or guard.get("selectedAfter", {}).get("owner") != PARENT_OWNER
            or recovery.get("task") != 10
            or recovery.get("status") != "RECOVERY_APPLICATION_ACK_TESTS_MAY_RESUME"
            or recovery.get("recoveryEpoch") != "mysql-recovery-20261024"
            or recovery.get("sourceAdoption") != "97e788fe96744af88513cc33bfed652b204c9972"
            or recovery.get("cid") != CID or recovery.get("owner") != TASK10_OWNER
            or recovery.get("schema") != TASK10_SCHEMA
            or recovery.get("oldHistoricalPort") != HISTORICAL_PORT
            or recovery.get("currentEffectivePort") != PORT
            or recovery.get("host") != "127.0.0.1"
            or recovery.get("privateEnv") != str(TASK10_ENV.relative_to(TREE))
            or recovery.get("originalPrivateEnv") != ack["privateEnv"]
            or recovery.get("originalApplicationAck") != str(PARENT_ACK.relative_to(TREE))
            or recovery.get("originalApplicationAckSha256") != sha(ack_bytes)
            or recovery.get("privateEnvMode") != "0600"
            or recovery.get("overlayDirectoryMode") != "0700"
            or recovery.get("originalEnvUnchanged") is not True
            or recovery.get("sameImageLimitsMounts") is not True
            or recovery.get("history15x10ExactlyOriginal") is not True
            or recovery.get("actualStartInvocations") != 1):
        fail("recorded Task 10 historical/recovery guard/ACK mismatch")
    return {"guardSha256": sha(guard_bytes), "ackSha256": sha(ack_bytes),
            "recoveryAckSha256": sha(recovery_bytes)}


def check_adoption(args):
    # This exact public machine ACK is issued by root only after real Task 10 adoption.
    if Path(args.task10_adoption_evidence) != ADOPTION_ACK:
        fail("Task 10 evidence must be the fixed public root ACK")
    public_file(ADOPTION_ACK)
    raw = ADOPTION_ACK.read_bytes()
    if not re.fullmatch(r"[0-9a-f]{64}", args.task10_adoption_sha256) or sha(raw) != args.task10_adoption_sha256 or sha(raw) != ADOPTION_ACK_SHA256:
        fail("fixed Task 10 root ACK SHA-256 mismatch")
    try:
        ack = json.loads(raw)
    except ValueError:
        fail("fixed Task 10 root ACK is invalid JSON")
    if (type(ack) is not dict or type(ack.get("task")) is not int or ack["task"] != 10
            or ack.get("status") != "COMPLETE_ADOPTED"
            or ack.get("base") != "97e788fe96744af88513cc33bfed652b204c9972"
            or type(ack.get("sourceCommit")) is not str
            or ack["sourceCommit"] != ADOPTION_SOURCE_COMMIT
            or type(ack.get("adoptionCommit")) is not str
            or ack["adoptionCommit"] != ADOPTION_COMMIT
            or ack.get("reviewSpec") != "PASS" or ack.get("reviewQuality") != "Approved"
            or type(ack.get("openCriticalImportant")) is not int
            or ack["openCriticalImportant"] != 0
            or ack.get("adoptionDocument") != ADOPTION_DOCUMENT
            or type(ack.get("adoptionDocumentSha256")) is not str
            or not re.fullmatch(r"[0-9a-f]{64}", ack["adoptionDocumentSha256"])):
        fail("fixed Task 10 root ACK completion fields mismatch")
    document = public_file(TREE / ADOPTION_DOCUMENT)
    if sha(document.read_bytes()) != ack["adoptionDocumentSha256"]:
        fail("fixed Task 10 adoption document SHA-256 mismatch")
    return {"path": str(ADOPTION_ACK), "sha256": sha(raw),
            "sourceCommit": ack["sourceCommit"], "adoptionCommit": ack["adoptionCommit"],
            "adoptionDocument": ADOPTION_DOCUMENT,
            "adoptionDocumentSha256": ack["adoptionDocumentSha256"]}


def env_clone(schema, owner, folder, parent_selected):
    private_owned(TASK10_ENV.parent, TASK10_OWNER, directory=True)
    source = private_owned(TASK10_ENV, TASK10_OWNER)
    raw = source.read_text(encoding="utf-8")
    if "\x00" in raw:
        fail("Task 10 env has invalid bytes")
    values = {}
    for plain in raw.splitlines():
        if not plain or plain.lstrip().startswith("#"):
            continue
        match = ASSIGN_RE.fullmatch(plain)
        if not match or match.group(1) in values:
            fail("Task 10 env syntax/key guard failed")
        try:
            tokens = shlex.split(match.group(2), posix=True)
        except ValueError:
            fail("Task 10 env literal parsing failed")
        if len(tokens) != 1:
            fail("Task 10 env value is not one literal token")
        values[match.group(1)] = tokens[0]
    expected = {"AMA_TEST_DATABASE_URL", "AMA_TEST_DATABASE_USERNAME",
                "AMA_TEST_DATABASE_PASSWORD", "AMA_TEST_CONTAINER", "AMA_TASK10_OWNER",
                "AMA_TASK10_RUN_ROOT", "AMA_TASK10_S3_ENV", "AMA_TASK10_PKI_ROOT"}
    if set(values) != expected:
        fail("Task 10 env keys differ from root-created allowlist")
    if (values["AMA_TEST_DATABASE_URL"] != "jdbc:mysql://127.0.0.1:" + str(PORT) + "/" + TASK10_SCHEMA
            or values["AMA_TEST_CONTAINER"] not in (CID, parent_selected["name"].removeprefix("/"))
            or values["AMA_TASK10_OWNER"] != TASK10_OWNER
            or values["AMA_TASK10_RUN_ROOT"] != str(TOOLS / TASK10_OWNER)
            or values["AMA_TEST_DATABASE_USERNAME"] != "ama_t08_848e93aace8b"
            or not values["AMA_TEST_DATABASE_PASSWORD"]
            or not values["AMA_TASK10_S3_ENV"] or not values["AMA_TASK10_PKI_ROOT"]):
        fail("Task 10 env reference/credential guard failed")
    if (values["AMA_TASK10_S3_ENV"] != str(TASK5_ENV)
            or values["AMA_TASK10_PKI_ROOT"] != str(TASK5_PKI)):
        fail("Task 10 S3/PKI references differ from pinned Task 5 owner paths")
    private_owned(TASK5_ENV, TASK5_OWNER)
    private_owned(TASK5_PKI, TASK5_OWNER, directory=True)
    private_owned(TASK5_PKI / "keystore-password", TASK5_OWNER)
    new_values = {
        "AMA_TEST_DATABASE_URL": "jdbc:mysql://127.0.0.1:" + str(PORT) + "/" + schema,
        "AMA_TEST_DATABASE_USERNAME": values["AMA_TEST_DATABASE_USERNAME"],
        "AMA_TEST_DATABASE_PASSWORD": values["AMA_TEST_DATABASE_PASSWORD"],
        "AMA_TEST_CONTAINER": CID,
        "AMA_TASK11_OWNER": owner,
        "AMA_TASK11_RUN_ROOT": str(folder),
        "AMA_TASK11_S3_ENV": values["AMA_TASK10_S3_ENV"],
        "AMA_TASK11_PKI_ROOT": values["AMA_TASK10_PKI_ROOT"],
    }
    return "".join("export " + key + "=" + shlex.quote(value) + "\n"
                   for key, value in new_values.items())


def write_private(path, data):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    os.fchmod(fd, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8", newline="") as stream:
        stream.write(data)


def save_state(folder, state):
    payload = json.dumps(state, sort_keys=True, indent=2) + "\n"
    temp = folder / ("state-" + uuid.uuid4().hex + ".tmp")
    write_private(temp, payload)
    os.replace(temp, folder / "state.json")


def provision(args):
    if not args.execute_reviewed_task11_provisioning:
        fail("explicit --execute-reviewed-task11-provisioning flag required")
    if sha(Path(__file__).read_bytes()) != args.approved_helper_sha256:
        fail("approved helper source SHA-256 mismatch")
    adoption = check_adoption(args)
    owner_match, schema_match = OWNER_RE.fullmatch(args.owner), SCHEMA_RE.fullmatch(args.schema)
    if not owner_match or not schema_match or owner_match.group(1)[:12] != schema_match.group(1):
        fail("fresh Task 11 owner/schema names must share the exact UUID prefix")
    owner, schema = args.owner, args.schema
    folder = TOOLS / owner
    inside_tools(folder, existing=False)
    private_owned(PARENT, PARENT_OWNER, directory=True)
    private_owned(PARENT / "secrets", PARENT_OWNER, directory=True)
    for name in ("root.cnf", "test.cnf"):
        private_owned(PARENT / "secrets" / name, PARENT_OWNER)
    records = check_records()
    before_selected = selected()
    before_server = server()
    # Read only into memory, never print, hash, or persist the original env values.
    cloned_env = env_clone(schema, owner, folder, before_selected)
    absent = mysql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='" + schema + "';\n")
    if absent != "0":
        fail("fresh Task 11 schema is not absent")
    test_user = "ama_t08_848e93aace8b"
    create_sql = "CREATE SCHEMA `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    grant_pattern = schema.replace("_", "\\_")
    grant_sql = "GRANT ALL PRIVILEGES ON `" + grant_pattern + "`.* TO '" + test_user + "'@'%';"
    grant_session_sql = ("SET SESSION sql_mode='" + before_server["sqlMode"] + "';\n"
                         + grant_sql + "\nSELECT @@session.sql_mode;\n")
    state = {"status": "ATTEMPT_RECORDED_BEFORE_SQL", "owner": owner, "schema": schema,
             "parentOwner": PARENT_OWNER, "containerId": CID, "port": PORT,
             "historicalPort": HISTORICAL_PORT,
             "task10Adoption": adoption,
             "recordHashes": records, "selectedBefore": before_selected,
             "serverBefore": before_server, "schemaAbsentBefore": True,
             "creationSqlWithoutSecrets": [create_sql, grant_session_sql],
             "privateEnvPath": str(folder / "env.sh"), "privateEnvMode": "0600",
             "cleanup": "forbidden", "retryProvisioning": "forbidden"}
    try:
        folder.mkdir(mode=0o700, exist_ok=False)
        os.chmod(folder, 0o700)
        save_state(folder, state)
    except BaseException:
        print("Pre-SQL owner/state creation failed; no SQL was issued. Owner path may exist without state; inspect exact path and do not retry with a new UUID.", file=sys.stderr)
        raise
    try:
        mysql(create_sql + "\n")
        state["status"] = "SCHEMA_CREATE_RETURNED_GRANT_PENDING"
        save_state(folder, state)
        effective_grant_mode = mysql(grant_session_sql)
        if effective_grant_mode != before_server["sqlMode"]:
            fail("grant session SQL mode differed after scoped grant; inspect partial outcome")
        state["status"] = "GRANT_RETURNED_VERIFY_PENDING"
        save_state(folder, state)
        after_selected = selected()
        after_server = server()
        if after_selected != before_selected or after_server != before_server:
            fail("parent identity/server changed after SQL; outcome requires root inspection")
        count = mysql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + schema + "';\n")
        if count != "0":
            fail("new schema is not empty")
        # Existing test.cnf is the sole business credential; root.cnf is provisioning only.
        test_context = mysql("SELECT DATABASE(), CURRENT_USER();\n", test=True, database=schema)
        if test_context != schema + "\t" + test_user + "@%":
            fail("existing test client principal/schema access mismatch")
        write_private(folder / "env.sh", cloned_env)
        if (folder / "env.sh").stat().st_mode & 0o777 != 0o600:
            fail("new private env mode is not 0600")
        state.update(status="READY_EMPTY_SCHEMA_ROOT_APPLICATION_ACK_PENDING",
                     selectedAfter=after_selected, serverAfter=after_server,
                     ready={"schema": schema, "tables": 0, "testPrincipal": test_user + "@%",
                            "serverUuid": SERVER_UUID, "engine": "InnoDB", "aio": 0,
                            "grantSessionSqlMode": effective_grant_mode})
        save_state(folder, state)
        print("Task 11 empty schema ready; root must issue separate application ACK.")
        print("State:", folder / "state.json")
        print("Private env path:", folder / "env.sh", "mode 0600")
    except BaseException:
        state["lastKnownStatus"] = state["status"]
        state["status"] = "PARTIAL_OR_UNKNOWN_OUTCOME_ROOT_INSPECTION_REQUIRED"
        try:
            save_state(folder, state)
        except BaseException:
            pass
        print("Provisioning outcome may be partial; retained owner path/state. Do not rerun or drop.", file=sys.stderr)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute-reviewed-task11-provisioning", action="store_true")
    parser.add_argument("--approved-helper-sha256", required=True)
    parser.add_argument("--task10-adoption-evidence", required=True)
    parser.add_argument("--task10-adoption-sha256", required=True)
    parser.add_argument("--owner", required=True)
    parser.add_argument("--schema", required=True)
    provision(parser.parse_args())


if __name__ == "__main__":
    main()
