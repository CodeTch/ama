"""One-shot, root-reviewed Task 9 empty-schema provisioning on the owned Task 8 MySQL.

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
TASK7_OWNER = "task07-0cea85a0e4724e78bd7aa6aabfc243bc"
TASK7_SCHEMA = "ama_t07_0cea85a0e472_behavior"
TASK7_ENV = TOOLS / TASK7_OWNER / "env.sh"
CID = "11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad"
IMAGE = "mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9"
SERVER_UUID = "1bdea6eb-cf32-11f1-8fe3-fef4aa4b3a25"
PORT = 44727
PARENT_GUARD = W / "task-07-fixture-creation-guard-20261024.json"
PARENT_ACK = W / "task-07-root-fixture-application-ack-20261024.json"
OWNER_RE = re.compile(r"task09-([0-9a-f]{32})\Z")
SCHEMA_RE = re.compile(r"ama_t09_([0-9a-f]{12})_behavior\Z")
ASSIGN_RE = re.compile(r"(?:export )?([A-Z][A-Z0-9_]*)=(.*)\Z")


def fail(message):
    raise RuntimeError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def private_file(path):
    if path.is_symlink() or not path.is_file() or path.stat().st_mode & 0o077:
        fail("private file mode/path guard failed: " + path.name)
    return path


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
    parent = json.loads(PARENT_GUARD.read_text(encoding="utf-8"))["selected"]
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
    if not isinstance(mounts, list) or len(mounts) != 2:
        fail("selected parent mount guard mismatch")
    actual_mounts = {(m.get("Source"), m.get("Destination"), m.get("RW")) for m in mounts}
    expected_mounts = {(m["Source"], m["Destination"], m["RW"])
                       for m in parent["mounts"]}
    if actual_mounts != expected_mounts:
        fail("selected parent mount guard mismatch")
    return value


def mysql(sql, *, test=False, database=None):
    cnf = "/run/ama-task08/test.cnf" if test else "/run/ama-task08/root.cnf"
    argv = ["docker", "exec", "-i", CID, "mysql", "--defaults-extra-file=" + cnf,
            "--protocol=TCP", "-h127.0.0.1", "--batch", "--skip-column-names"]
    if database is not None:
        if not SCHEMA_RE.fullmatch(database):
            fail("test database argument is outside Task 9 scope")
        argv.extend(["--database", database])
    return run(argv,
               sql=sql, timeout=20)


def server():
    fields = mysql("SELECT VERSION(), @@server_uuid, @@global.innodb_use_native_aio, "
                   "@@global.partial_revokes, @@global.sql_mode;\n").split("\t")
    engine = mysql("SELECT SUPPORT FROM information_schema.ENGINES WHERE ENGINE='InnoDB';\n")
    if (len(fields) != 5 or fields[:4] != ["8.4.6", SERVER_UUID, "0", "0"]
            or "NO_BACKSLASH_ESCAPES" in fields[4].split(",") or engine != "DEFAULT"):
        fail("selected MySQL version/AIO/engine/server UUID/grant-mode mismatch")
    return {"version": fields[0], "serverUuid": fields[1], "aio": 0,
            "engine": "InnoDB", "engineSupport": engine,
            "partialRevokes": 0, "sqlMode": fields[4]}


def check_records():
    guard_bytes = PARENT_GUARD.read_bytes()
    ack = json.loads(PARENT_ACK.read_text(encoding="utf-8"))
    guard = json.loads(guard_bytes)
    if (ack.get("base") != "de1611a67ba483afe21f3b29bb69c2d32028efef"
            or ack.get("creationGuardSha256") != sha(guard_bytes)
            or ack.get("cid") != CID or ack.get("parentResourceOwner") != PARENT_OWNER
            or ack.get("port") != PORT or ack.get("ready", {}).get("serverUuid") != SERVER_UUID
            or ack.get("privateEnv") != str(TASK7_ENV.relative_to(TREE))
            or guard.get("selected", {}).get("id") != CID
            or guard.get("selected", {}).get("owner") != PARENT_OWNER):
        fail("recorded Task 7 parent guard/ACK mismatch")
    return {"guardSha256": sha(guard_bytes), "ackSha256": sha(PARENT_ACK.read_bytes())}


def env_clone(schema, owner, folder):
    inside_tools(TASK7_ENV, existing=True)
    if TASK7_ENV.parent.is_symlink() or TASK7_ENV.parent.stat().st_mode & 0o077:
        fail("Task 7 owner env directory is not private")
    source = private_file(TASK7_ENV)
    raw = source.read_text(encoding="utf-8")
    if "\x00" in raw:
        fail("Task 7 env has invalid bytes")
    values = {}
    for plain in raw.splitlines():
        if not plain or plain.lstrip().startswith("#"):
            continue
        match = ASSIGN_RE.fullmatch(plain)
        if not match or match.group(1) in values:
            fail("Task 7 env syntax/key guard failed")
        try:
            tokens = shlex.split(match.group(2), posix=True)
        except ValueError:
            fail("Task 7 env literal parsing failed")
        if len(tokens) != 1:
            fail("Task 7 env value is not one literal token")
        values[match.group(1)] = tokens[0]
    expected = {"AMA_TEST_DATABASE_URL", "AMA_TEST_DATABASE_USERNAME",
                "AMA_TEST_DATABASE_PASSWORD", "AMA_TEST_CONTAINER", "AMA_TASK07_OWNER",
                "AMA_TASK07_RUN_ROOT", "AMA_TASK07_S3_ENV", "AMA_TASK07_PKI_ROOT"}
    if set(values) != expected:
        fail("Task 7 env keys differ from root-created allowlist")
    if (values["AMA_TEST_DATABASE_URL"] != "jdbc:mysql://127.0.0.1:44727/" + TASK7_SCHEMA
            or values["AMA_TEST_CONTAINER"] != CID
            or values["AMA_TASK07_OWNER"] != TASK7_OWNER
            or values["AMA_TEST_DATABASE_USERNAME"] != "ama_t08_848e93aace8b"
            or not values["AMA_TEST_DATABASE_PASSWORD"]
            or not values["AMA_TASK07_S3_ENV"] or not values["AMA_TASK07_PKI_ROOT"]):
        fail("Task 7 env reference/credential guard failed")
    new_values = {
        "AMA_TEST_DATABASE_URL": "jdbc:mysql://127.0.0.1:44727/" + schema,
        "AMA_TEST_DATABASE_USERNAME": values["AMA_TEST_DATABASE_USERNAME"],
        "AMA_TEST_DATABASE_PASSWORD": values["AMA_TEST_DATABASE_PASSWORD"],
        "AMA_TEST_CONTAINER": CID,
        "AMA_TASK09_OWNER": owner,
        "AMA_TASK09_RUN_ROOT": str(folder),
        "AMA_TASK09_S3_ENV": values["AMA_TASK07_S3_ENV"],
        "AMA_TASK09_PKI_ROOT": values["AMA_TASK07_PKI_ROOT"],
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
    if not args.execute_reviewed_task9_provisioning:
        fail("explicit --execute-reviewed-task9-provisioning flag required")
    if sha(Path(__file__).read_bytes()) != args.approved_helper_sha256:
        fail("approved helper source SHA-256 mismatch")
    evidence = Path(args.task7_adoption_evidence)
    if not evidence.is_file() or evidence.is_symlink() or not evidence.resolve().is_relative_to(TREE.resolve()):
        fail("Task 7 adoption evidence path guard failed")
    if sha(evidence.read_bytes()) != args.task7_adoption_sha256:
        fail("Task 7 adoption evidence SHA-256 mismatch")
    owner_match, schema_match = OWNER_RE.fullmatch(args.owner), SCHEMA_RE.fullmatch(args.schema)
    if not owner_match or not schema_match or owner_match.group(1)[:12] != schema_match.group(1):
        fail("fresh Task 9 owner/schema names must share the exact UUID prefix")
    owner, schema = args.owner, args.schema
    folder = TOOLS / owner
    inside_tools(folder, existing=False)
    inside_tools(PARENT, existing=True)
    if not PARENT.is_dir() or PARENT.stat().st_mode & 0o077:
        fail("parent owner directory is not private")
    for name in ("root.cnf", "test.cnf"):
        private_file(PARENT / "secrets" / name)
    records = check_records()
    before_selected = selected()
    before_server = server()
    # Read only into memory, never print, hash, or persist the original env values.
    cloned_env = env_clone(schema, owner, folder)
    absent = mysql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='" + schema + "';\n")
    if absent != "0":
        fail("fresh Task 9 schema is not absent")
    test_user = "ama_t08_848e93aace8b"
    create_sql = "CREATE SCHEMA `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    grant_pattern = schema.replace("_", "\\_")
    grant_sql = "GRANT ALL PRIVILEGES ON `" + grant_pattern + "`.* TO '" + test_user + "'@'%';"
    folder.mkdir(mode=0o700, exist_ok=False)
    os.chmod(folder, 0o700)
    state = {"status": "ATTEMPT_RECORDED_BEFORE_SQL", "owner": owner, "schema": schema,
             "parentOwner": PARENT_OWNER, "containerId": CID, "port": PORT,
             "task7AdoptionEvidence": str(evidence.resolve()),
             "task7AdoptionSha256": args.task7_adoption_sha256,
             "recordHashes": records, "selectedBefore": before_selected,
             "serverBefore": before_server, "schemaAbsentBefore": True,
             "creationSqlWithoutSecrets": [create_sql, grant_sql],
             "privateEnvPath": str(folder / "env.sh"), "privateEnvMode": "0600",
             "cleanup": "forbidden", "retryProvisioning": "forbidden"}
    save_state(folder, state)
    try:
        mysql(create_sql + "\n")
        state["status"] = "SCHEMA_CREATE_RETURNED_GRANT_PENDING"
        save_state(folder, state)
        mysql(grant_sql + "\n")
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
                            "serverUuid": SERVER_UUID, "engine": "InnoDB", "aio": 0})
        save_state(folder, state)
        print("Task 9 empty schema ready; root must issue separate application ACK.")
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
    parser.add_argument("--execute-reviewed-task9-provisioning", action="store_true")
    parser.add_argument("--approved-helper-sha256", required=True)
    parser.add_argument("--task7-adoption-evidence", required=True)
    parser.add_argument("--task7-adoption-sha256", required=True)
    parser.add_argument("--owner", required=True)
    parser.add_argument("--schema", required=True)
    provision(parser.parse_args())


if __name__ == "__main__":
    main()
