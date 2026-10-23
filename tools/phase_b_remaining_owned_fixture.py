"""Phase B remaining own-process bounded fixture; no old fixture selection or destructive path."""
import datetime, hashlib, json, os, pathlib, secrets, shlex, subprocess, time
ROOT = pathlib.Path(__file__).resolve().parents[1]
E = ROOT / "artifacts/phase-b/b-remaining/task-01/fixture"
P = ROOT / ".tools/phase-b-remaining"
IMAGE = "mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9"
E.mkdir(parents=True, exist_ok=True)
P.mkdir(parents=True, exist_ok=True, mode=0o700)
P.chmod(0o700)
def run(args, name, check=True):
    r = subprocess.run(args, text=True, capture_output=True, timeout=180)
    (E / (name + ".log")).write_text(r.stdout + r.stderr)
    (E / (name + ".command.json")).write_text(json.dumps({"argv":args,"exit_code":r.returncode},indent=2)+"\n")
    if check and r.returncode:
        raise RuntimeError("nonsecret command failed: " + name)
    return r
scripts = sorted((ROOT / "backend/adapters/src/main/resources/db/migration").glob("V*.sql"))
assert len(scripts) == 12, len(scripts)
for script in scripts:
    original=subprocess.run(["git","show","bd47aa0e1b24fa89fee5385860454f43dc0aecd7:"+str(script.relative_to(ROOT))],cwd=ROOT,capture_output=True,check=True).stdout
    assert original==script.read_bytes(), "formal migration differs from authorized BASE"
(E / "lower-sql-before.json").write_text(json.dumps([{"path":str(p.relative_to(ROOT)),"sha256":hashlib.sha256(p.read_bytes()).hexdigest()} for p in scripts],indent=2)+"\n")
assert not (P / "owned.json").exists(), "never reuse/create a second fixture"
name = "ama-test-b01-" + secrets.token_hex(6)
password, root_password = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
credentials = P / "mysql.env"
credentials.write_text("MYSQL_ROOT_PASSWORD="+root_password+"\nMYSQL_DATABASE=ama_b01_fixture\nMYSQL_USER=ama_test\nMYSQL_PASSWORD="+password+"\n")
credentials.chmod(0o600)
(P / "owned.json").write_text(json.dumps({"name":name,"image":IMAGE})+"\n")
(P / "owned.json").chmod(0o600)
run(["docker","image","inspect","--format","{{.Id}}",IMAGE],"image-selection")
args = ["docker","run","-d","--name",name,"--label","ama.test-fixture=b01","--label","ama.remaining.owner=remaining-20261023",
        "--memory","768m","--memory-swap","768m","--cpus","1","--pids-limit","256","--publish","127.0.0.1::3306",
        "--env-file",str(credentials),IMAGE,"--character-set-server=utf8mb4","--collation-server=utf8mb4_0900_ai_ci",
        "--default-time-zone=+00:00","--innodb-use-native-aio=OFF"]
run(args,"fixture-create")
fmt = '{"id":{{json .Id}},"name":{{json .Name}},"image_reference":{{json .Config.Image}},"image_id":{{json .Image}},"owner":{{json (index .Config.Labels "ama.remaining.owner")}},"mounts":{{json .Mounts}},"ports":{{json .HostConfig.PortBindings}},"memory":{{json .HostConfig.Memory}},"memory_swap":{{json .HostConfig.MemorySwap}},"nano_cpus":{{json .HostConfig.NanoCpus}},"pids_limit":{{json .HostConfig.PidsLimit}},"cmd":{{json .Config.Cmd}},"state":{"status":{{json .State.Status}},"exit_code":{{json .State.ExitCode}},"oom":{{json .State.OOMKilled}}}}'
selected=run(["docker","inspect","--format",fmt,name],"fixture-selected")
metadata=json.loads(selected.stdout)
assert metadata["name"] == "/"+name and metadata["owner"]=="remaining-20261023"
assert metadata["image_reference"]==IMAGE and metadata["memory"]==805306368 and metadata["nano_cpus"]==1000000000
binding=metadata["ports"]["3306/tcp"]
assert len(binding)==1 and binding[0]["HostIp"]=="127.0.0.1"
published=run(["docker","port",name,"3306/tcp"],"fixture-published-port")
assert published.stdout.strip().startswith("127.0.0.1:")
port=published.stdout.strip().split(":")[-1]
assert port.isdigit()
values={"AMA_TEST_DATABASE_URL":"jdbc:mysql://127.0.0.1:"+port+"/ama_b01_fixture","AMA_TEST_DATABASE_USERNAME":"ama_test","AMA_TEST_DATABASE_PASSWORD":password,"AMA_TEST_CONTAINER":name}
env=P/"env.sh"
env.write_text("".join("export "+k+"="+shlex.quote(v)+"\n" for k,v in values.items()))
env.chmod(0o600)
sql="SELECT COUNT(*) AS initial_tables FROM information_schema.tables WHERE table_schema=DATABASE(); SELECT @@version,@@global.innodb_use_native_aio,@@global.time_zone,UTC_TIMESTAMP(6),UNIX_TIMESTAMP(UTC_TIMESTAMP(6)),DATABASE();"
query='MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch -e '+shlex.quote(sql)
attempts=[]
for attempt in range(90):
    r=subprocess.run(["docker","exec",name,"sh","-c",query],text=True,capture_output=True,timeout=10)
    attempts.append({"attempt":attempt+1,"exit_code":r.returncode,"stdout":r.stdout,"stderr":r.stderr})
    if r.returncode==0:
        break
    time.sleep(1)
(E/"fixture-ready-attempts.json").write_text(json.dumps(attempts,indent=2).replace(password,"[REDACTED]").replace(root_password,"[REDACTED]")+"\n")
assert r.returncode==0, "fixture setup failure, preserved; not RED"
assert r.stdout.splitlines()[1]=="0", r.stdout
(E/"fixture-initial-zero.log").write_text(r.stdout)
print("owned="+name+"; loopback="+port+"; initial_tables=0; native_aio=OFF; bounded=768MiB/1CPU/256pids")
