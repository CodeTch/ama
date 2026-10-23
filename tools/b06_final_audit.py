"""Finalize selected public provenance on the SAME fixture; no reset or service mutation."""
import hashlib,json,pathlib,shlex,shutil,subprocess
root=pathlib.Path(__file__).resolve().parents[1]
e=root/"artifacts/phase-b/b06-queue-seams-fix";p=root/".tools/b06-queue-seams"
coord=e/"coordination";coord.mkdir(parents=True,exist_ok=True)
for name in ["task-B-06-seams-root-ack.md","task-B-06-regression-map.md"]:
 src=pathlib.Path("/home/tanren/ama/.superpowers/sdd/2026-10-23-resume")/name
 shutil.copyfile(src,coord/name)
owned=json.loads((p/"owned.json").read_text())
fmt='{"id":{{json .Id}},"name":{{json .Name}},"image_reference":{{json .Config.Image}},"image_id":{{json .Image}},"owner":{{json (index .Config.Labels "ama.b06.owner")}},"mounts":{{json .Mounts}},"ports":{{json .NetworkSettings.Ports}},"memory":{{json .HostConfig.Memory}},"memory_swap":{{json .HostConfig.MemorySwap}},"nano_cpus":{{json .HostConfig.NanoCpus}},"pids_limit":{{json .HostConfig.PidsLimit}},"cmd":{{json .Config.Cmd}},"status":{{json .State.Status}}}'
cmd=["docker","inspect","--format",fmt,owned["name"]]
r=subprocess.run(cmd,capture_output=True,text=True,timeout=30)
(e/"fixture-final.log").write_text(r.stdout+r.stderr);(e/"fixture-final.command.json").write_text(json.dumps({"argv":cmd,"exit_code":r.returncode},indent=2)+"\n");assert r.returncode==0
selected=json.loads(r.stdout)
assert selected["name"]=="/ama-test-b01-ff63b53162a3" and selected["owner"]=="queue-seams-20261023"
assert selected["memory"]==805306368 and selected["nano_cpus"]==1000000000 and selected["pids_limit"]==256
assert selected["ports"]["3306/tcp"]==[{"HostIp":"127.0.0.1","HostPort":"58060"}]
sql="SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank; SELECT @@version,@@global.innodb_use_native_aio,@@global.time_zone,UTC_TIMESTAMP(6),UNIX_TIMESTAMP(UTC_TIMESTAMP(6));"
query='MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch -e '+shlex.quote(sql)
cmd=["docker","exec",owned["name"],"sh","-c",query]
r=subprocess.run(cmd,capture_output=True,text=True,timeout=30)
(e/"history-final.log").write_text(r.stdout+r.stderr);(e/"history-final.command.json").write_text(json.dumps({"argv":cmd,"exit_code":r.returncode},indent=2)+"\n");assert r.returncode==0
baseline=json.loads((root/"artifacts/phase-b/b06-queue-seams/lower-sql-before.json").read_text())
assert len(baseline)==12
for row in baseline:
 raw=(root/row["path"]).read_bytes();assert hashlib.sha256(raw).hexdigest()==row["sha256"]
 assert subprocess.check_output(["git","show","6b48b2f855bbf890c3ccad68325e7fd74d434489:"+row["path"]],cwd=root)==raw
(e/"lower-sql-final.json").write_text(json.dumps(baseline,indent=2)+"\n")
checks=(root/"artifacts/phase-b/b06-queue-seams/SHA256SUMS").read_text().splitlines()
for line in checks:
 sha,path=line.split("  ",1);assert hashlib.sha256((root/path).read_bytes()).hexdigest()==sha,path
(e/"original-proof-preservation.json").write_text(json.dumps({"original_seal":"artifacts/phase-b/b06-queue-seams/SHA256SUMS","checked":len(checks),"unchanged":True},indent=2)+"\n")
allRuns=[]
for d in sorted(x for x in e.iterdir() if x.is_dir()):
 f=d/"counts.json"
 if not f.exists():continue
 rows=json.loads(f.read_text())
 it=[x for x in rows if "/failsafe-reports/" in x["path"]];unit=[x for x in rows if "/surefire-reports/" in x["path"]]
 allRuns.append({"label":d.name,"IT":{k:sum(x[k] for x in it) for k in ["tests","failures","errors","skipped"]},"unit":{k:sum(x[k] for x in unit) for k in ["tests","failures","errors","skipped"]}})
for label in ["inherited","inherited-final"]:
 rows=json.loads((e/(label+"-counts.json")).read_text());it=[x for x in rows if x["kind"]=="failsafe-reports"]
 allRuns.append({"label":label,"IT":{k:sum(x[k] for x in it) for k in ["tests","failures","errors","skipped"]},"unit":{"tests":0,"failures":0,"errors":0,"skipped":0},"unit_execution":"intentionally not run"})
(e/"all-runs.json").write_text(json.dumps(allRuns,indent=2)+"\n")
creds=dict(x.split("=",1) for x in (p/"mysql.env").read_text().splitlines())
for f in e.rglob("*"):
 if f.is_file():
  raw=f.read_bytes()
  for k in ["MYSQL_PASSWORD","MYSQL_ROOT_PASSWORD"]:assert creds[k].encode() not in raw
print("final selected fixture/history captured;12SQL and143 original artifacts unchanged; own secrets absent; ACK/map copied")
