"""Plan, then explicitly provision one retained Task 8 MySQL fixture.

Importing this module has no side effects. This is root coordination tooling, not a
test runner or migration installer. Read the adjacent usage note before invocation.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time
import uuid


TREE = Path(__file__).resolve().parents[3]
OWNED_PARENT = TREE / ".tools"
IMAGE = "mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9"
NAME_PREFIX = "ama-test-task08-"
LABEL_KEY = "ama.test-fixture"
LABEL_PREFIX = "task08-"
KINDS = ("behavior", "fairheld", "fairunregistered", "overflow")
UUID_RE = re.compile(r"^[0-9a-f]{32}$")
SAFE_NAME_RE = re.compile(r"^ama-test-task08-[0-9a-f]{32}$")


def fail(message):
    raise RuntimeError(message)


def owner_value(token):
    return LABEL_PREFIX + token


def private_write(path, payload):
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    fd = os.open(path, flags, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        stream.write(payload)


def replace_private(path, payload):
    temp = path.with_name(path.name + ".tmp")
    private_write(temp, payload)
    os.replace(temp, path)


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True) + "\n"


def digest(payload):
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def check_parent():
    if not TREE.is_dir() or TREE.is_symlink():
        fail("worktree path guard failed")
    if not OWNED_PARENT.is_dir() or OWNED_PARENT.is_symlink():
        fail(".tools path guard failed; create and review this parent separately")
    if OWNED_PARENT.resolve() != TREE.resolve() / ".tools":
        fail(".tools resolved outside the worktree")


def check_owned_dir(path):
    check_parent()
    if path.parent != OWNED_PARENT or not path.name.startswith("task08-"):
        fail("fixture path must be a direct .tools/task08-UUID child")
    suffix = path.name.removeprefix("task08-")
    if not UUID_RE.fullmatch(suffix) or not path.is_dir() or path.is_symlink():
        fail("fixture directory guard failed")
    if path.resolve() != OWNED_PARENT.resolve() / path.name:
        fail("fixture resolved outside .tools")
    if path.stat().st_mode & 0o077:
        fail("fixture directory is not private")
    return suffix


def read_private_file(path):
    if path.is_symlink() or not path.is_file() or path.stat().st_mode & 0o077:
        fail("private file guard failed: " + path.name)
    return path.read_text(encoding="utf-8")


def run(argv, *, input_text=None, timeout=20, permit_failure=False):
    try:
        result = subprocess.run(argv, input=input_text, text=True, capture_output=True,
                                timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired):
        fail("command unavailable or timed out; owned state preserved")
    if result.returncode and not permit_failure:
        # Docker/mysql output may contain sensitive values. Never relay it.
        fail("command failed; owned state preserved")
    return result


def inspect_selected(cid):
    template = ('{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Config.Image}},'
                '"image_id":{{json .Image}},"owner":{{json (index .Config.Labels "ama.test-fixture")}},'
                '"memory":{{json .HostConfig.Memory}},"memory_swap":{{json .HostConfig.MemorySwap}},'
                '"nano_cpus":{{json .HostConfig.NanoCpus}},"pids":{{json .HostConfig.PidsLimit}},'
                '"ports":{{json .NetworkSettings.Ports}},"mounts":{{json .Mounts}},'
                '"state":{{json .State.Status}},"running":{{json .State.Running}},'
                '"oom_killed":{{json .State.OOMKilled}}}')
    raw = run(["docker", "container", "inspect", "--format", template, cid]).stdout
    try:
        value = json.loads(raw)
    except ValueError:
        fail("selected Docker inspect was invalid")
    return value


def verify_inspect(value, plan, image_id, cid):
    if (value.get("id") != cid or value.get("name") != "/" + plan["name"]
            or value.get("image") != IMAGE
            or value.get("image_id") != image_id
            or value.get("owner") != owner_value(plan["uuid"])
            or value.get("state") != "running" or value.get("running") is not True
            or value.get("oom_killed") is not False
            or value.get("memory") != 805306368 or value.get("memory_swap") != 805306368
            or value.get("nano_cpus") != 1000000000 or value.get("pids") != 256):
        fail("selected Docker identity or resource bounds mismatch")
    ports = value.get("ports", {}).get("3306/tcp")
    if (not isinstance(ports, list) or len(ports) != 1
            or ports[0].get("HostIp") != "127.0.0.1"
            or not str(ports[0].get("HostPort", "")).isdigit()):
        fail("loopback port guard failed")
    port = int(ports[0]["HostPort"])
    if not 1 <= port <= 65535:
        fail("loopback port range guard failed")
    mounts = value.get("mounts")
    expected = {(plan["data_dir"], "/var/lib/mysql"),
                (plan["secret_dir"], "/run/ama-task08")}
    if (not isinstance(mounts, list) or
            {(item.get("Source"), item.get("Destination")) for item in mounts} != expected):
        fail("Docker mount guard failed")
    by_destination = {item["Destination"]: item for item in mounts}
    if (by_destination["/var/lib/mysql"].get("RW") is not True
            or by_destination["/run/ama-task08"].get("RW") is not False):
        fail("Docker mount mode guard failed")
    return port


def plan_new():
    check_parent()
    token = uuid.uuid4().hex
    folder = OWNED_PARENT / ("task08-" + token)
    folder.mkdir(mode=0o700, exist_ok=False)
    os.chmod(folder, 0o700)
    name = NAME_PREFIX + token
    schema_prefix = "ama_t08_" + token[:12] + "_"
    plan = {
        "version": 1, "uuid": token, "name": name,
        "owner_label": LABEL_KEY + "=" + owner_value(token),
        "image": IMAGE, "data_dir": str(folder / "data"),
        "secret_dir": str(folder / "secrets"),
        "memory_bytes": 805306368, "memory_swap_bytes": 805306368,
        "nano_cpus": 1000000000, "pids_limit": 256,
        "publish": "127.0.0.1::3306",
        "schemas": {kind: schema_prefix + kind for kind in KINDS},
        "test_user": "ama_t08_" + token[:12],
    }
    payload = canonical(plan)
    private_write(folder / "plan.json", payload)
    print("Plan saved:", folder / "plan.json")
    print("Plan SHA-256:", digest(payload))
    print("Review the full plan and actual host guard before apply. Nothing was provisioned.")


def apply(folder, approved_hash):
    token = check_owned_dir(folder)
    payload = read_private_file(folder / "plan.json")
    plan = json.loads(payload)
    if payload != canonical(plan) or digest(payload) != approved_hash:
        fail("plan hash or canonical form mismatch")
    expected_name = NAME_PREFIX + token
    if (plan.get("version") != 1 or plan.get("uuid") != token
            or plan.get("name") != expected_name or not SAFE_NAME_RE.fullmatch(expected_name)
            or plan.get("image") != IMAGE
            or plan.get("owner_label") != LABEL_KEY + "=" + owner_value(token)
            or plan.get("data_dir") != str(folder / "data")
            or plan.get("secret_dir") != str(folder / "secrets")
            or plan.get("memory_bytes") != 805306368
            or plan.get("memory_swap_bytes") != 805306368
            or plan.get("nano_cpus") != 1000000000 or plan.get("pids_limit") != 256
            or plan.get("publish") != "127.0.0.1::3306"
            or plan.get("schemas") != {kind: "ama_t08_" + token[:12] + "_" + kind for kind in KINDS}
            or plan.get("test_user") != "ama_t08_" + token[:12]):
        fail("plan fields differ from the fixed Task 8 bounds")
    guard = json.loads(read_private_file(folder / "host-guard.json"))
    if (guard != {"task6_adopted": True, "host_guard_passed": True,
                  "approved_plan_sha256": approved_hash, "reviewer": "root"}):
        fail("root Task 6 adoption and actual host resource guard record is missing")
    if (folder / "state.json").exists() or (folder / "data").exists() or (folder / "secrets").exists():
        fail("fixture already attempted or path exists; no retry/recreate")
    image_id = run(["docker", "image", "inspect", "--format", "{{.Id}}", IMAGE]).stdout.strip()
    if not image_id.startswith("sha256:"):
        fail("pinned image is not locally inspectable")
    listing = run(["docker", "container", "ls", "-a", "--filter", "name=^" + expected_name + "$",
                   "--format", "{{.Names}}"]).stdout.splitlines()
    if listing:
        fail("container name exists; refusing adoption or replacement")
    data = folder / "data"
    secret_dir = folder / "secrets"
    data.mkdir(mode=0o700, exist_ok=False)
    secret_dir.mkdir(mode=0o700, exist_ok=False)
    root_password = secrets.token_urlsafe(48)
    test_password = secrets.token_urlsafe(48)
    private_write(secret_dir / "mysql.env", "MYSQL_ROOT_PASSWORD=" + root_password + "\n")
    private_write(secret_dir / "root.cnf", "[client]\nuser=root\npassword=" + root_password + "\n")
    private_write(secret_dir / "test.cnf", "[client]\nuser=" + plan["test_user"]
                  + "\npassword=" + test_password + "\n")
    state_path = folder / "state.json"
    state = {"phase": "creating", "name": expected_name, "image": IMAGE,
             "image_id": image_id, "plan_sha256": approved_hash,
             "data_dir": str(data), "secret_dir": str(secret_dir), "cleanup": "forbidden"}
    private_write(state_path, canonical(state))
    try:
        result = run([
            "docker", "run", "-d", "--pull=never", "--name", expected_name,
            "--label", LABEL_KEY + "=" + owner_value(token),
            "--memory", "768m", "--memory-swap", "768m", "--cpus", "1", "--pids-limit", "256",
            "--publish", "127.0.0.1::3306", "--env-file", str(secret_dir / "mysql.env"),
            "--mount", "type=bind,src=" + str(data) + ",dst=/var/lib/mysql",
            "--mount", "type=bind,src=" + str(secret_dir) + ",dst=/run/ama-task08,readonly",
            IMAGE, "--innodb-use-native-aio=0", "--character-set-server=utf8mb4",
            "--collation-server=utf8mb4_0900_ai_ci", "--default-time-zone=+00:00",
        ], timeout=30)
        cid = result.stdout.strip()
        if not re.fullmatch(r"[0-9a-f]{64}", cid):
            fail("Docker returned no valid full container ID")
        state["container_id"] = cid
        state["phase"] = "created"
        replace_private(state_path, canonical(state))
        selected = inspect_selected(cid)
        port = verify_inspect(selected, plan, image_id, cid)
        state["selected_inspect_initial"] = selected
        state["port"] = port
        state["phase"] = "created_guarded"
        replace_private(state_path, canonical(state))
        deadline = time.monotonic() + 60
        check = ["docker", "exec", "-i", cid, "mysql",
                 "--defaults-extra-file=/run/ama-task08/root.cnf", "--protocol=TCP",
                 "-h127.0.0.1", "--batch", "--skip-column-names"]
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                fail("MySQL readiness exceeded 60 seconds")
            result = run(check, input_text="SELECT 1;\n", timeout=min(5, remaining), permit_failure=True)
            if result.returncode == 0 and result.stdout.strip() == "1":
                break
            time.sleep(min(1, max(0, deadline - time.monotonic())))
        selected = inspect_selected(cid)
        port = verify_inspect(selected, plan, image_id, cid)
        state["selected_inspect"] = selected
        state["port"] = port
        state["phase"] = "ready"
        replace_private(state_path, canonical(state))
        # All SQL identifiers are derived from a checked UUID; password is URL-safe.
        user = plan["test_user"]
        sql = "".join("CREATE DATABASE `" + plan["schemas"][kind] + "`;\n" for kind in KINDS)
        sql += "CREATE USER '" + user + "'@'%' IDENTIFIED BY '" + test_password + "';\n"
        sql += "".join("GRANT ALL PRIVILEGES ON `" + plan["schemas"][kind]
                       + "`.* TO '" + user + "'@'%';\n" for kind in KINDS)
        run(check, input_text=sql, timeout=20)
        state["phase"] = "provisioned"
        replace_private(state_path, canonical(state))
        print("Owned Task 8 fixture provisioned:", expected_name)
        print("Loopback port:", port, "schemas:", ", ".join(plan["schemas"].values()))
        print("Private credentials:", secret_dir)
        print("Selected metadata:", state_path)
    except BaseException:
        # Never delete, stop, reset, retry creation, or print raw command output.
        print("Provisioning failed; owned files/container retained. Review state.json and selected Docker fields.",
              file=sys.stderr)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("plan")
    apply_parser = sub.add_parser("apply")
    apply_parser.add_argument("folder", type=Path)
    apply_parser.add_argument("--approved-plan-sha256", required=True)
    args = parser.parse_args()
    if args.command == "plan":
        plan_new()
    else:
        apply(args.folder, args.approved_plan_sha256)


if __name__ == "__main__":
    main()
