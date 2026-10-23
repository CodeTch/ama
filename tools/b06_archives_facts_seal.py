"""Read-only bounded raw-manifest export and explicit public delivery seal."""
import hashlib,json,pathlib,shlex,subprocess,sys,xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
E=ROOT/"artifacts/phase-b/b06-archives/facts"
owned=json.loads((ROOT/".tools/b06-archives/owned.json").read_text())
name=owned["name"]
label=subprocess.run(["docker","inspect","--format",'{{index .Config.Labels "ama.b06.owner"}}',name],capture_output=True,text=True,check=True).stdout.strip()
assert label=="archives-20261023"
def query(sql,stem):
 command=["docker","exec",name,"sh","-c",'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --default-character-set=utf8mb4 --batch --raw --skip-column-names -e '+shlex.quote(sql)]
 result=subprocess.run(command,capture_output=True,text=True,timeout=60)
 (E/(stem+".stdout.log")).write_text(result.stdout)
 (E/(stem+".stderr.log")).write_text(result.stderr)
 (E/(stem+".command.json")).write_text(json.dumps({"argv":command,"exit_code":result.returncode},indent=2)+"\n")
 assert result.returncode==0
 return result.stdout
metadata={}
for line in query("SELECT id,OCTET_LENGTH(config_manifest),CHAR_LENGTH(config_manifest),SHA2(config_manifest,256) FROM transcription_input ORDER BY id","raw-manifest-sizes").splitlines():
 identifier,size,characters,sha=line.split("\t");assert int(size)<=65536 and int(characters)<=65536
 metadata[identifier]=(int(size),sha)
refs=json.loads((E/"controlled-raw/refs.json").read_text())
expected=next(r for r in refs if r["artifactType"]=="PROCESSING_CONFIG")
export=E/"raw-manifests";export.mkdir(exist_ok=True)
records=[]
for line in query("SELECT id,config_manifest FROM transcription_input ORDER BY id","raw-manifest-export").splitlines():
 identifier,value=line.split("\t",1);raw=value.encode("utf-8");size,sha=metadata[identifier]
 assert len(raw)==size and hashlib.sha256(raw).hexdigest()==sha
 try:
  parsed=json.loads(value);assert parsed["configs"]["processing"]==expected;kind="json"
 except json.JSONDecodeError:kind="invalid.txt"
 (export/(identifier+"."+kind)).write_bytes(raw)
 records.append({"inputId":identifier,"sizeBytes":size,"sha256":sha,"kind":kind})
(E/"raw-manifests.json").write_text(json.dumps(records,indent=2)+"\n")
summary=[]
for run in sorted(p for p in E.iterdir() if p.is_dir() and (p/"command.json").is_file()):
 command=json.loads((run/"command.json").read_text());item={"run":run.name,"command":command,"junit":[]}
 for group in ["xml","unit-xml"]:
  totals={k:0 for k in ["tests","failures","errors","skipped"]}
  for xml in (run/group).rglob("TEST-*.xml"):
   attributes=ET.parse(xml).getroot().attrib
   for k in totals:totals[k]+=int(attributes.get(k,0))
  item["junit"].append({"group":group,**totals})
 summary.append(item)
(E/"runs.json").write_text(json.dumps(summary,indent=2)+"\n")
# Deliberate public allowlist only; never enumerate a .tools tree or another owner.
source=["backend/core/src/main/java/ama/transcript/ProcessingFactsReader.java","backend/adapters/src/main/java/ama/transcript/JdbcProcessingFactsReader.java","backend/integration-tests/src/test/java/ama/transcript/ProcessingFactsReaderIT.java","backend/integration-tests/src/test/java/ama/transcript/ArchiveProcessingFixture.java","docs/acceptance/b/task-B-06-archives-facts-report.md"]
source += [str(p.relative_to(ROOT)) for p in sorted((ROOT/"tools").glob("b06_archives_facts_*.py"))]
source += ["tools/b06_archives_owned_fixture.py"]
allowlist_path=E/"public-allowlist.json"
paths=sorted(set(source+[str(p.relative_to(ROOT)) for p in E.rglob("*") if p.is_file() and p.name!="SHA256SUMS"]+[str(allowlist_path.relative_to(ROOT))]))
allowlist_path.write_text(json.dumps({"paths":paths},indent=2)+"\n")
# Check only private password values against allowlisted public bytes; never print a value.
private={}
for line in (ROOT/".tools/b06-archives/mysql.env").read_text().splitlines():
 key,value=line.split("=",1)
 if key.endswith("PASSWORD"):private[key]=value.encode()
for path in paths:
 data=(ROOT/path).read_bytes()
 assert not any(value in data for value in private.values()), "private secret in public path: "+path
records=[hashlib.sha256((ROOT/path).read_bytes()).hexdigest()+"  "+path for path in paths]
(E/"SHA256SUMS").write_text("\n".join(records)+"\n")
print("public_files="+str(len(paths))+" allowlist_sha256="+hashlib.sha256(allowlist_path.read_bytes()).hexdigest())
