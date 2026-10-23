from pathlib import Path
import subprocess,json,hashlib,os,shlex,re
root=Path.cwd();e=root/'artifacts/phase-b/b-remaining/task-04';w=root/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-04-report.md'
def git(*a):return subprocess.check_output(['git',*a],text=True).strip()
assert git('rev-parse','HEAD')=='875047cdbcd6ddd17f5c267f8998b7a5a4378b6c'
assert git('diff','--cached','--name-only')==''
sources=json.loads((e/'owned-source-manifest.json').read_text());assert len(sources)==32
assert all(hashlib.sha256((root/p).read_bytes()).hexdigest()==h for p,h in sources.items())
assert w.exists() and (e/'report.md').read_bytes()==w.read_bytes()
checks=json.loads((e/'self-review-checks.json').read_text());assert checks['credential_hit_count']==0 and checks['final_gate_java_source_seals_match']
private={}
for line in (root/'.tools/phase-b-remaining/env.sh').read_text().splitlines():
 k,v=line.removeprefix('export ').split('=',1);private[k]=shlex.split(v)[0]
native=re.search(r'PASSWORD\s*=\s*"([^"\n]+)"',(root/'backend/integration-tests/src/test/java/ama/storage/LocalFixture.java').read_text());assert native
secrets=[private['AMA_TEST_DATABASE_PASSWORD'].encode(),native.group(1).encode()];files=[p for p in e.rglob('*') if p.is_file()]+[root/p for p in sources]+[w]
hits=[str(p.relative_to(root)) for p in files if any(secret in p.read_bytes() for secret in secrets)];assert not hits,{'credential_hit_paths':hits}
(e/'credential-scan.json').write_text(json.dumps({'exact_owned_database_password_hit_count':0,'exact_native_tls_test_password_hit_count':0,'scan_file_count':len(files),'scopes':['new Task4 raw evidence','32 owned source paths',str(w.relative_to(root))],'private_env_value_printed':False},indent=2)+'\n')
manifest=e/'evidence-manifest.json'
expected=set(sources)|{str(w.relative_to(root))}|{str(p.relative_to(root)) for p in e.rglob('*') if p.is_file()}|{str(manifest.relative_to(root)),str((e/'staging-plan.json').relative_to(root))}
(e/'staging-plan.json').write_text(json.dumps({'precommit_head':git('rev-parse','HEAD'),'index_empty_before_explicit_staging':True,'exact_owned_stage_paths':sorted(expected),'forbidden_add_all':True},indent=2)+'\n')
manifest.write_text(json.dumps({str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(e.rglob('*')) if p.is_file() and p!=manifest},indent=2)+'\n')
subprocess.run(['git','add','--']+sorted(sources),check=True)
# Force-add only the explicit report and enumerated new Task4 evidence paths.
subprocess.run(['git','add','-f','--']+sorted(expected-set(sources)),check=True)
staged=set(git('diff','--cached','--name-only').splitlines());assert staged==expected,staged^expected
subprocess.run(['git','diff','--cached','--check'],check=True)
for p in sorted(expected):
 blob=subprocess.check_output(['git','show',':'+p]);assert blob==(root/p).read_bytes(),('index/worktree byte difference',p)
for p,h in json.loads(manifest.read_text()).items():assert hashlib.sha256(subprocess.check_output(['git','show',':'+p])).hexdigest()==h,p
print('EXACT_STAGE_PASS',len(staged),'paths; source32; credential_hits0; every indexed blob matches seal',flush=True)
subprocess.run(['git','commit','-m','feat(processing): add authenticated transcript import intake and discovery'],check=True)
commit=git('rev-parse','HEAD');assert git('diff','--cached','--name-only')==''
assert set(git('diff-tree','--no-commit-id','--name-only','-r',commit).splitlines())==expected
for p,h in sources.items():assert hashlib.sha256(subprocess.check_output(['git','show',commit+':'+p])).hexdigest()==h,p
for p,h in json.loads(manifest.read_text()).items():assert hashlib.sha256(subprocess.check_output(['git','show',commit+':'+p])).hexdigest()==h,p
print('COMMIT',commit,'committed_source_and_evidence_blobs_verified',len(expected),flush=True)
print('STATUS',git('status','--short'))
