"""Owned, loopback-only disposable MySQL fixture. Never accepts external database addresses."""
import argparse
import json
import pathlib
import secrets
import shlex
import subprocess
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]
IMAGE = "mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9"
LABEL = "ama.test-fixture"

# Select individual fields in Docker, never its full inspect object or Config.Env.
FAILURE_INSPECT = ('{"name":{{json .Name}},"image_reference":{{json .Config.Image}},'
                   '"image_id":{{json .Image}},"label":{{json (index .Config.Labels "ama.test-fixture")}},'
                   '"state":{"status":{{json .State.Status}},"exit_code":{{json .State.ExitCode}},'
                   '"oom_killed":{{json .State.OOMKilled}},"error":{{json .State.Error}}},'
                   '"port_bindings":{{json .HostConfig.PortBindings}}}')

def docker(*args):
    return subprocess.check_output(["docker", *args], text=True).strip()

def failure_command(*args):
    # Capture stderr too: Docker failures may contain unsafe text. Never print it.
    return subprocess.run(["docker", *args], capture_output=True, text=True,
                          errors="replace", timeout=10, check=True)

def private_write(path, text):
    path.touch(mode=0o600, exist_ok=True)
    path.chmod(0o600)
    path.write_text(text, encoding="utf-8")

def startup_failure(directory, name, passwords):
    outcome = {"inspect": "failed", "logs": "omitted", "cleanup": "refused"}
    # If redaction inputs are unavailable, retain only fixed outcome strings.
    known_secrets = len(passwords) == 2 and all(isinstance(p, str) and p for p in passwords)
    def redact(text):
        for password in sorted(passwords, key=len, reverse=True):
            text = text.replace(password, "[REDACTED]")
        return text
    def save():
        text = json.dumps(outcome, indent=2) + "\n"
        private_write(directory / "failure.json", redact(text) if known_secrets else text)

    owned = False
    try:
        if known_secrets:
            raw = json.loads(failure_command("inspect", "--format", FAILURE_INSPECT, name).stdout)
            state = raw["state"]
            # Rebuild the allowlist even if an unexpected inspect response has extra fields.
            container = {key: raw[key] for key in ("name", "image_reference", "image_id", "label")}
            if not all(isinstance(value, str) for value in container.values()):
                raise ValueError("invalid selected metadata")
            if (not isinstance(state["status"], str) or not isinstance(state["error"], str)
                    or type(state["exit_code"]) is not int or type(state["oom_killed"]) is not bool):
                raise ValueError("invalid selected state")
            container["state"] = {key: state[key] for key in ("status", "exit_code", "oom_killed", "error")}
            bindings = raw["port_bindings"]
            valid_binding = (isinstance(bindings, dict) and set(bindings) == {"3306/tcp"}
                             and isinstance(bindings["3306/tcp"], list) and len(bindings["3306/tcp"]) == 1)
            binding = bindings["3306/tcp"][0] if valid_binding else None
            valid_binding = (isinstance(binding, dict) and binding.get("HostIp") == "127.0.0.1"
                             and isinstance(binding.get("HostPort"), str)
                             and (binding["HostPort"] == "" or
                                  (binding["HostPort"].isascii() and binding["HostPort"].isdigit()
                                   and 1 <= int(binding["HostPort"]) <= 65535)))
            container["port_bindings"] = ({"3306/tcp": [{key: binding[key] for key in ("HostIp", "HostPort")}]}
                                          if valid_binding else None)
            outcome["container"] = container
            outcome["inspect"] = "captured"
            owned = (container["name"] == "/" + name and container["label"] == "b01"
                     and container["image_reference"] == IMAGE and valid_binding)
    except BaseException:
        # No raw exception strings, command arguments, or partial inspect output.
        pass
    try:
        if owned:
            try:
                logs = failure_command("logs", "--tail", "200", name)
                private_write(directory / "docker.log", redact(logs.stdout + logs.stderr))
                outcome["logs"] = "captured"
            except BaseException:
                outcome["logs"] = "failed"
        # Evidence must be persisted before any destructive action.
        save()
    except BaseException:
        return "capture=failed; cleanup=refused"
    if owned:
        try:
            failure_command("rm", "-f", name)
            outcome["cleanup"] = "removed"
        except BaseException:
            outcome["cleanup"] = "failed"
    try:
        save()
    except BaseException:
        return "capture=failed; cleanup=" + outcome["cleanup"]
    return "inspect=" + outcome["inspect"] + "; logs=" + outcome["logs"] + "; cleanup=" + outcome["cleanup"]

def start():
    name = "ama-test-b01-" + secrets.token_hex(6)
    directory = ROOT / ".tools/mysql-fixture" / name
    directory.mkdir(parents=True, mode=0o700)
    credentials = directory / "mysql.env"
    password = secrets.token_urlsafe(32)
    root_password = secrets.token_urlsafe(32)
    credentials.write_text("MYSQL_ROOT_PASSWORD=" + root_password +
                           "\nMYSQL_DATABASE=ama_b01_fixture\nMYSQL_USER=ama_test\nMYSQL_PASSWORD=" + password + "\n")
    credentials.chmod(0o600)
    docker("image", "inspect", IMAGE)
    docker("run", "-d", "--name", name, "--label", LABEL + "=b01",
           "--publish", "127.0.0.1::3306", "--env-file", str(credentials), IMAGE,
           "--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci",
           "--default-time-zone=+00:00")
    try:
        for attempt in range(90):
            # Authenticate over TCP so the temporary socket-only init server cannot look ready.
            check = subprocess.run(["docker", "exec", name, "sh", "-c",
                                    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names -e "SELECT 1"'],
                                   capture_output=True, timeout=10)
            if check.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError("owned MySQL fixture did not become ready")
        port = json.loads(docker("inspect", name))[0]["NetworkSettings"]["Ports"]["3306/tcp"][0]["HostPort"]
        env = directory / "env.sh"
        values = {"AMA_TEST_DATABASE_URL": "jdbc:mysql://127.0.0.1:" + port + "/ama_b01_fixture",
                  "AMA_TEST_DATABASE_USERNAME": "ama_test", "AMA_TEST_DATABASE_PASSWORD": password,
                  "AMA_TEST_CONTAINER": name}
        env.write_text("".join("export " + key + "=" + shlex.quote(value) + "\n" for key, value in values.items()))
        env.chmod(0o600)
        print("Owned fixture: " + name + "; loopback port " + port + "; schema ama_b01_fixture")
        print("Source credentials locally: " + str(env))
    except BaseException as error:
        try:
            outcome = startup_failure(directory, name, (password, root_password))
        except BaseException:
            outcome = "capture=failed; cleanup=refused"
        try:
            error.add_note("Owned MySQL startup diagnostics: " + outcome)
        except BaseException:
            pass
        raise

def stop(name):
    if not name.startswith("ama-test-b01-") or docker("inspect", "--format", '{{index .Config.Labels "ama.test-fixture"}}', name) != "b01":
        raise SystemExit("Refusing to remove a container without the owned AMA name and label")
    docker("rm", "-f", name)
    print("Removed owned fixture: " + name)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["start", "stop"])
    parser.add_argument("name", nargs="?")
    args = parser.parse_args()
    if args.action == "start":
        start()
    elif args.name:
        stop(args.name)
    else:
        parser.error("stop requires the owned container name")
