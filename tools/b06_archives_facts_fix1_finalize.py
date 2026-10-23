"""Fix1-only read-only audit and versioned current full delivery seal; old seals stay immutable."""
import hashlib,json,pathlib,shlex,subprocess,xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
OLD=ROOT/"artifacts/phase-b/b06-archives/facts"
E=OLD/"fix1"
BASE="298695761706419ee20c1b6b98f3ad73eff65cdd"
owned=json.loads((ROOT/".tools/b06-archives/owned.json").read_text())
name=owned["name"]
selected=json.loads((OLD/"fixture-selected.log").read_text())
identity=subprocess.run(["docker","inspect","--format",'{{.Id}} {{index .Config.Labels "ama.b06.owner"}}',name],capture_output=True,text=True,check=True).stdout.strip()
assert identity==selected["id"]+" archives-20261023"
def query(sql,stem):
 command=["docker","exec",name,"sh","-c",'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --raw -e '+shlex.quote(sql)]
 result=subprocess.run(command,capture_output=True,text=True,timeout=60)
 (E/(stem+".sql")).write_text(sql+"\n")
 (E/(stem+".stdout.log")).write_text(result.stdout)
 (E/(stem+".stderr.log")).write_text(result.stderr)
 (E/(stem+".command.json")).write_text(json.dumps({"argv":command,"exit_code":result.returncode},indent=2)+"\n")
 assert result.returncode==0
 return result.stdout
history=query("SELECT installed_rank,version,description,type,script,checksum,installed_on,execution_time,success FROM flyway_schema_history ORDER BY installed_rank;","history")
assert history.splitlines()==(OLD/"final-history.stdout.log").read_text().splitlines()[:13]
assert len(history.splitlines())==13
metrics=query("SELECT @@version,@@global.innodb_use_native_aio; SELECT enterprise_id,OCTET_LENGTH(display_name) AS bytes,CHAR_LENGTH(display_name) AS scalars,OCTET_LENGTH(CONVERT(display_name USING utf16)) DIV 2 AS utf16_units,status FROM enterprise_identity ORDER BY enterprise_id; SELECT id,OCTET_LENGTH(config_manifest) AS bytes,CHAR_LENGTH(config_manifest) AS scalars,OCTET_LENGTH(CONVERT(config_manifest USING utf16)) DIV 2 AS utf16_units,SHA2(config_manifest,256) AS sha256 FROM transcription_input ORDER BY id;","text-metrics")
current=query("SELECT id,SHA2(config_manifest,256) AS sha256 FROM transcription_input ORDER BY id;","manifest-hashes")
current=dict(line.split("\t") for line in current.splitlines()[1:])
old_manifests=json.loads((OLD/"raw-manifests.json").read_text())
assert all(current[record["inputId"]]==record["sha256"] for record in old_manifests)
# Preserve every preexisting delivery artifact/tool/report, except the two explicitly reopened source files.
changed_allowed={"backend/adapters/src/main/java/ama/transcript/JdbcProcessingFactsReader.java","backend/integration-tests/src/test/java/ama/transcript/ProcessingFactsReaderIT.java"}
old_paths=json.loads((OLD/"public-allowlist.json").read_text())["paths"]+[str((OLD/"SHA256SUMS").relative_to(ROOT))]
for path in old_paths:
 if path not in changed_allowed:
  original=subprocess.run(["git","show",BASE+":"+path],cwd=ROOT,capture_output=True,check=True).stdout
  assert (ROOT/path).read_bytes()==original,path
sql=[]
for p in sorted((ROOT/"backend/adapters/src/main/resources/db/migration").glob("V*.sql")):
 raw=p.read_bytes();assert raw==subprocess.run(["git","show",BASE+":"+str(p.relative_to(ROOT))],cwd=ROOT,capture_output=True,check=True).stdout
 sql.append({"path":str(p.relative_to(ROOT)),"sha256":hashlib.sha256(raw).hexdigest()})
assert sql==json.loads((OLD/"lower-sql-before.json").read_text())
(E/"preservation.json").write_text(json.dumps({"historical_commit":BASE,"unchanged_preexisting_public_paths":len(old_paths)-2,"old_manifest_rows_preserved":len(old_manifests),"old_allowlist_sha256":hashlib.sha256((OLD/"public-allowlist.json").read_bytes()).hexdigest(),"old_seal_sha256":hashlib.sha256((OLD/"SHA256SUMS").read_bytes()).hexdigest(),"formal_sql":sql},indent=2)+"\n")
runs=[]
for run in sorted(p for p in E.iterdir() if p.is_dir() and (p/"command.json").is_file()):
 item={"run":run.name,"command":json.loads((run/"command.json").read_text()),"junit":[]}
 snapshots=json.loads((run/"source-sha.json").read_text())
 for record in snapshots:assert hashlib.sha256((run/"source"/record["path"]).read_bytes()).hexdigest()==record["sha256"]
 for group in ["xml","unit-xml"]:
  totals={key:0 for key in ["tests","failures","errors","skipped"]}
  for xml in (run/group).rglob("TEST-*.xml"):
   suite=ET.parse(xml).getroot();cases=suite.findall("testcase");assert len(cases)==int(suite.get("tests"))
   for key in totals:totals[key]+=int(suite.get(key,"0"))
  item["junit"].append({"group":group,**totals})
 runs.append(item)
(E/"runs.json").write_text(json.dumps(runs,indent=2)+"\n")
for record in json.loads((E/"green-02-final/source-sha.json").read_text()):
 assert hashlib.sha256((ROOT/record["path"]).read_bytes()).hexdigest()==record["sha256"]
new_sources=["tools/b06_archives_facts_fix1_run.py","tools/b06_archives_facts_fix1_finalize.py","docs/acceptance/b/task-B-06-archives-facts-fix1-report.md"]
allowlist=E/"full-current-allowlist.json"
paths=sorted(set(old_paths+new_sources+[str(p.relative_to(ROOT)) for p in E.rglob("*") if p.is_file() and p.name!="SHA256SUMS"]+[str(allowlist.relative_to(ROOT))]))
allowlist.write_text(json.dumps({"historical_commit":BASE,"historical_seal_is_commit_bound":True,"paths":paths},indent=2)+"\n")
passwords=[line.split("=",1)[1].encode() for line in (ROOT/".tools/b06-archives/mysql.env").read_text().splitlines() if line.split("=",1)[0].endswith("PASSWORD")]
for path in paths:assert not any(value in (ROOT/path).read_bytes() for value in passwords),path
(E/"SHA256SUMS").write_text("\n".join(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+"  "+path for path in paths)+"\n")
print("current_public_files="+str(len(paths))+" current_allowlist_sha256="+hashlib.sha256(allowlist.read_bytes()).hexdigest())
