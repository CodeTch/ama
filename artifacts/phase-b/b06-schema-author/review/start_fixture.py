"""New owned fixture only; native AIO override is local to this process."""
import importlib.util
import json
import pathlib
import subprocess

root = pathlib.Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("reviewed_fixture", root / "tools/dev/mysql_fixture.py")
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)
original_docker = fixture.docker
record = {}

def local_docker(*args):
    if args and args[0] == "run":
        assert args[-1] == "--default-time-zone=+00:00"
        assert fixture.IMAGE in args
        assert args[args.index("--label") + 1] == "ama.test-fixture=b01"
        name = args[args.index("--name") + 1]
        assert name.startswith("ama-test-b01-")
        assert pathlib.Path(args[args.index("--env-file") + 1]).parent == root / ".tools/mysql-fixture" / name
        args = (*args, "--innodb-use-native-aio=OFF")
        record["run_arguments"] = list(args)
        record["name"] = name
    return original_docker(*args)

fixture.docker = local_docker
fixture.start()
name = record["name"]
fmt = '{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Config.Image}},"label":{{json (index .Config.Labels "ama.test-fixture")}},"args":{{json .Args}},"ports":{{json .NetworkSettings.Ports}}}'
record["inspection"] = json.loads(original_docker("inspect", "--format", fmt, name))
sql = "SELECT VERSION(),@@GLOBAL.innodb_use_native_aio,(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE())"
probe = subprocess.check_output(["docker", "exec", name, "sh", "-c",
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names -e "' + sql + '"'], text=True).strip()
version, aio, count = probe.split("\t")
assert version.startswith("8.4.") and aio == "0" and count == "0", probe
ports = record["inspection"]["ports"]["3306/tcp"]
assert len(ports) == 1 and ports[0]["HostIp"] == "127.0.0.1"
record.update(mysql_version=version, initial_table_count=int(count), innodb_use_native_aio=int(aio),
              credential_file_exported=False, purpose="functional V011 schema only; no production IO/capacity qualification")
directory = root / ".tools/transcript-schema-review"
(directory / "fixture-identity.json").write_text(json.dumps(record, indent=2) + "\n")
(directory / "fixture-name").write_text(name + "\n")
print("Captured owned fixture identity, loopback, native AIO OFF and initial zero tables")
