"""Exact bounded B06 correction runner; never creates/resets a fixture."""
import hashlib,json,pathlib,shutil,subprocess,sys,xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
e=root/"artifacts/phase-b/b06-queue-seams-fix"
def capture(label):
 d=e/label;d.mkdir(parents=True,exist_ok=True)
 for m in (root/"backend").iterdir():
  for kind in ["failsafe-reports","surefire-reports"]:
   for f in (m/"target"/kind).glob("*"):
    if f.suffix in [".xml",".txt"]:
     t=d/m.name/kind/f.name;t.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(f,t)
 counts=[]
 for f in sorted(d.rglob("TEST-*.xml")):
  s=ET.parse(f).getroot();counts.append({"path":str(f.relative_to(e)),"suite":s.get("name"),**{k:int(s.get(k,0)) for k in ["tests","failures","errors","skipped"]}})
 (d/"counts.json").write_text(json.dumps(counts,indent=2)+"\n")
 paths=subprocess.check_output(["git","ls-files","--modified","--others","--exclude-standard"],cwd=root,text=True).splitlines()
 paths=[x for x in paths if x.startswith("backend/") and x.endswith(".java")]
 sha=[]
 for x in paths:
  data=(root/x).read_bytes();t=d/"source"/x;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes(data)
  sha.append({"path":x,"sha256":hashlib.sha256(data).hexdigest()})
 (d/"source-sha256.json").write_text(json.dumps(sha,indent=2)+"\n")
 print("captured="+label+"; suites="+str(len(counts)),flush=True)
if sys.argv[1]=="capture":
 capture(sys.argv[2]);sys.exit(0)
label,group,selector=sys.argv[1:]
assert label.replace("-","").replace("_","").isalnum()
allow={"B06QueueSeamsIT","TaskLeaseIT","ExternalExecutionIT","ReviewConcurrencyIT","MeetingConcurrencyIT","OutboxFenceIT","OutboxConsumerIT","ModelPermitIT"}
assert (group=="it" and selector.split("#",1)[0] in allow) or (group=="unit" and selector=="current")
e.mkdir(parents=True,exist_ok=True)
cmd=[sys.executable,"tools/checks/run.py",group]+([] if group=="unit" else [selector])
with (e/(label+".log")).open("w") as out:r=subprocess.run(cmd,cwd=root,stdout=out,stderr=subprocess.STDOUT)
(e/(label+".command.json")).write_text(json.dumps({"argv":cmd,"exit_code":r.returncode},indent=2)+"\n")
capture(label)
print("exit="+str(r.returncode),flush=True)
sys.exit(r.returncode)
