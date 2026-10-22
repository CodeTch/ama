import hashlib,json,pathlib,shlex,shutil,subprocess,xml.etree.ElementTree as ET
root=pathlib.Path.cwd();e=root/"artifacts/phase-b/b06-queue-seams";p=root/".tools/b06-queue-seams"
owned=json.loads((p/"owned.json").read_text())
dest=e/"run-1";dest.mkdir(exist_ok=True)
for module in (root/"backend").iterdir():
    for folder in ["surefire-reports","failsafe-reports"]:
        report=module/"target"/folder
        for file in report.glob("*"):
            if file.suffix in [".xml",".txt"]:
                target=dest/module.name/folder/file.name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(file,target)
src=root/"backend/integration-tests/src/test/java/ama/task/B06QueueSeamsIT.java"
shutil.copyfile(src,dest/"B06QueueSeamsIT.java")
counts=[]
for file in sorted(dest.rglob("TEST-*.xml")):
    suite=ET.parse(file).getroot();counts.append({"path":str(file.relative_to(e)),"suite":suite.get("name"),**{k:int(suite.get(k,0)) for k in ["tests","failures","errors","skipped"]}})
(e/"run-1-counts.json").write_text(json.dumps(counts,indent=2)+"\n")
sql="SELECT installed_rank,version,description,type,script,checksum,installed_on,execution_time,success FROM flyway_schema_history ORDER BY installed_rank; SELECT COUNT(*) AS tables_after_migration FROM information_schema.tables WHERE table_schema=DATABASE(); SELECT @@version,@@global.innodb_use_native_aio,@@global.time_zone,UTC_TIMESTAMP(6),UNIX_TIMESTAMP(UTC_TIMESTAMP(6));"
query='MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch -e '+shlex.quote(sql)
cmd=["docker","exec",owned["name"],"sh","-c",query]
r=subprocess.run(cmd,text=True,capture_output=True,timeout=30)
(e/"history-after.log").write_text(r.stdout+r.stderr)
(e/"history-after.command.json").write_text(json.dumps({"argv":cmd,"exit_code":r.returncode},indent=2)+"\n")
assert r.returncode==0
before=json.loads((e/"lower-sql-before.json").read_text())
for row in before:
    raw=(root/row["path"]).read_bytes();assert hashlib.sha256(raw).hexdigest()==row["sha256"]
    baseline=subprocess.run(["git","show","6b48b2f855bbf890c3ccad68325e7fd74d434489:"+row["path"]],capture_output=True,check=True).stdout
    assert raw==baseline
(e/"lower-sql-after.json").write_text(json.dumps(before,indent=2)+"\n")
passwords=dict(line.split("=",1) for line in (p/"mysql.env").read_text().splitlines())
for f in dest.rglob("*"):
    if f.is_file():
        data=f.read_bytes()
        assert passwords["MYSQL_PASSWORD"].encode() not in data and passwords["MYSQL_ROOT_PASSWORD"].encode() not in data
print("preserved raw XML/text/source; lower12 match exact BASE and pre-run SHA; no fixture passwords in XML; history captured")
