"""Seal complete owned delivery; no database or service mutation."""
import hashlib,json,pathlib,subprocess
root=pathlib.Path(__file__).resolve().parents[1];e=root/"artifacts/phase-b/b06-queue-seams-fix"
base="6b48b2f855bbf890c3ccad68325e7fd74d434489";source="553196cacbf27f2fd4dfc3b65d70c535f10f9958"
assert subprocess.check_output(["git","rev-parse","HEAD"],cwd=root,text=True).strip()==source
snapshot=json.loads((e/"final-seams-18/source-sha256.json").read_text())
assert len(snapshot)==12
for row in snapshot:
 raw=(root/row["path"]).read_bytes();assert hashlib.sha256(raw).hexdigest()==row["sha256"]
 assert subprocess.check_output(["git","show",source+":"+row["path"]],cwd=root)==raw
for label in ["inherited-final","units-final"]:
 assert json.loads((e/label/"source-sha256.json").read_text())==snapshot
(e/"final-source-sha256.json").write_text(json.dumps({"base":base,"tested_source_commit":source,"files":snapshot},indent=2)+"\n")
units=json.loads((e/"units-final/counts.json").read_text());assert sum(x["tests"] for x in units)==179
assert all(x["failures"]==x["errors"]==x["skipped"]==0 for x in units)
inherited=json.loads((e/"inherited-final-counts.json").read_text());assert sum(x["tests"] for x in inherited)==28
assert all(x["failures"]==x["errors"]==x["skipped"]==0 for x in inherited)
seams=json.loads((e/"final-seams-18/counts.json").read_text());it=[x for x in seams if "/failsafe-reports/" in x["path"]];assert sum(x["tests"] for x in it)==18
assert all(x["failures"]==x["errors"]==x["skipped"]==0 for x in it)
(e/"final-validation.json").write_text(json.dumps({"tested_source_commit":source,"seams_IT":18,"inherited_IT_invocations":28,"inherited_distinct_methods":26,"final_java_units":179,"failures":0,"errors":0,"skipped":0,"source_hashes_match_all_three_final_runs":True,"independent_review":"pending"},indent=2)+"\n")
extra=[".gitattributes","docs/acceptance/b/task-B-06-seams-report.md","tools/b06_seal_fix.py"]
paths=set(subprocess.check_output(["git","diff","--name-only",base,"HEAD"],cwd=root,text=True).splitlines()+extra)
paths.update(str(p.relative_to(root)) for p in e.rglob("*") if p.is_file() and p.name not in ["full-delivery-allowlist.txt","SHA256SUMS"])
paths.add(str((e/"full-delivery-allowlist.txt").relative_to(root)));paths.add(str((e/"SHA256SUMS").relative_to(root)))
assert all(".tools/" not in x and not x.endswith("env.sh") and not x.endswith(".env") for x in paths)
paths=sorted(paths)
(e/"full-delivery-allowlist.txt").write_text("\n".join(paths)+"\n")
(e/"SHA256SUMS").write_text("".join(hashlib.sha256((root/x).read_bytes()).hexdigest()+"  "+x+"\n" for x in paths if x!=str((e/"SHA256SUMS").relative_to(root))))
credentials=dict(x.split("=",1) for x in (root/".tools/b06-queue-seams/mysql.env").read_text().splitlines())
for path in paths:
 raw=(root/path).read_bytes()
 for key in ["MYSQL_PASSWORD","MYSQL_ROOT_PASSWORD"]:assert credentials[key].encode() not in raw,path
subprocess.run(["git","add","-f","--pathspec-from-file=-","--pathspec-file-nul"],cwd=root,input=b"".join(x.encode()+b"\0" for x in paths),check=True)
subprocess.run(["git","diff","--cached","--check"],cwd=root,check=True)
actual=set(subprocess.check_output(["git","diff","--name-only",base],cwd=root,text=True).splitlines())
assert actual==set(paths),(actual-set(paths),set(paths)-actual)
for path in paths:
 assert subprocess.check_output(["git","show",":"+path],cwd=root)==(root/path).read_bytes(),path
print("sealed complete BASE..HEAD delivery paths="+str(len(paths))+"; final source/units/IT+all index bytes verified; no own secrets",flush=True)
