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

def docker(*args):
    return subprocess.check_output(["docker", *args], text=True).strip()

def start():
    name = "ama-test-b01-" + secrets.token_hex(6)
    directory = ROOT / ".tools/mysql-fixture" / name
    directory.mkdir(parents=True, mode=0o700)
    credentials = directory / "mysql.env"
    password = secrets.token_urlsafe(32)
    credentials.write_text("MYSQL_ROOT_PASSWORD=" + secrets.token_urlsafe(32) +
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
    except BaseException:
        docker("rm", "-f", name)
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
