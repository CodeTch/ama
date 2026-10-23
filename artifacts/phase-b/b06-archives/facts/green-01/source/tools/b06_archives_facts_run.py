"""Precise source-before-run evidence; no private environment serialization."""
import hashlib,json,pathlib,shutil,subprocess,sys,os,shlex
ROOT=pathlib.Path(__file__).resolve().parents[1]
E=ROOT/"artifacts/phase-b/b06-archives/facts"
name,group,selector=sys.argv[1:]
D=E/name
D.mkdir(parents=True,exist_ok=False)
paths=["backend/core/src/main/java/ama/transcript/ProcessingFactsReader.java","backend/adapters/src/main/java/ama/transcript/JdbcProcessingFactsReader.java","backend/integration-tests/src/test/java/ama/transcript/ProcessingFactsReaderIT.java","backend/integration-tests/src/test/java/ama/transcript/ArchiveProcessingFixture.java","tools/b06_archives_owned_fixture.py","tools/b06_archives_facts_run.py"]
hashes=[]
for name in paths:
 p=ROOT/name;out=D/"source"/name;out.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,out);hashes.append({"path":name,"sha256":hashlib.sha256(p.read_bytes()).hexdigest()})
(D/"source-sha.json").write_text(json.dumps(hashes,indent=2)+"\n")
command=["python3","tools/checks/run.py",group,selector]
with (D/"stdout.log").open("w") as out,(D/"stderr.log").open("w") as err:
 env=os.environ.copy()
 for line in (ROOT/".tools/b06-archives/env.sh").read_text().splitlines():
  assignment=shlex.split(line)[1];key,value=assignment.split("=",1);env[key]=value
 env["JAVA_HOME"]="/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64"
 env["PATH"]=env["JAVA_HOME"]+"/bin:"+env["PATH"]

 result=subprocess.run(command,cwd=ROOT,stdout=out,stderr=err,env=env)
(D/"command.json").write_text(json.dumps({"argv":command,"exit_code":result.returncode,"MAVEN_ARGS":env.get("MAVEN_ARGS")},indent=2)+"\n")
for p in ROOT.glob("backend/*/target/*-reports/TEST-*.xml"):
 if ("failsafe" in str(p))==(group=="it"):
  out=D/"xml"/p.parent.parent.parent.name/p.name;out.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,out)
print(name+": exit="+str(result.returncode)+" evidence="+str(D))
sys.exit(result.returncode)
