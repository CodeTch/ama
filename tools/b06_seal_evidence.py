import hashlib,json,pathlib,shutil,subprocess
root=pathlib.Path.cwd();e=root/"artifacts/phase-b/b06-queue-seams";p=root/".tools/b06-queue-seams"
credentials=dict(line.split("=",1) for line in (p/"mysql.env").read_text().splitlines())
for f in e.rglob("*"):
 if f.is_file():
  data=f.read_bytes()
  for k in ["MYSQL_PASSWORD","MYSQL_ROOT_PASSWORD"]:assert credentials[k].encode() not in data,"secret found in evidence"
assert not list(e.rglob("*.env")) and not list(e.rglob("env.sh"))
paths=[".gitattributes","backend/integration-tests/src/test/java/ama/task/B06QueueSeamsIT.java","docs/acceptance/b/task-B-06-seams-proposal.md","docs/acceptance/b/task-B-06-seams-proof-report.md"]
paths += [str(x.relative_to(root)) for x in sorted((root/"tools").glob("b06_*.py"))]
(e/"delivery-source-sha256.json").write_text(json.dumps([{"path":x,"bytes":(root/x).stat().st_size,"sha256":hashlib.sha256((root/x).read_bytes()).hexdigest()} for x in paths],indent=2)+"\n")
files=sorted(str(x.relative_to(root)) for x in e.rglob("*") if x.is_file() and x.name not in ["allowlist.txt","SHA256SUMS"])
allowed=sorted(files+[str((e/"allowlist.txt").relative_to(root)),str((e/"SHA256SUMS").relative_to(root))])
(e/"allowlist.txt").write_text("\n".join(allowed)+"\n")
sealable=[x for x in allowed if x!=str((e/"SHA256SUMS").relative_to(root))]
(e/"SHA256SUMS").write_text("".join(hashlib.sha256((root/x).read_bytes()).hexdigest()+"  "+x+"\n" for x in sealable))
coordinator=pathlib.Path("/home/tanren/ama/.superpowers/sdd/2026-10-23-resume/task-B-06-seams-proposal.md")
assert coordinator.parent.is_dir()
note=(root/"docs/acceptance/b/task-B-06-seams-proposal.md").read_bytes()
summary=b"\n\n---\nOwned proof report: /home/tanren/ama/.worktrees/phase-b-transcript-runtime/docs/acceptance/b/task-B-06-seams-proof-report.md\nEvidence: /home/tanren/ama/.worktrees/phase-b-transcript-runtime/artifacts/phase-b/b06-queue-seams/\nState NEEDS_CONTEXT (root ACK); current BASE..HEAD empty; no production changes. Initial3 IT=2 assertion failures/0errors/0skips, supplementary output control1pass; same179 units pass per run.\n"
coordinator.write_bytes(note+summary)
assert coordinator.read_bytes().startswith(note)
print("sealed public evidence paths="+str(len(allowed))+"; secret check passed; coordinator proposal copied")
