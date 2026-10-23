import pathlib,subprocess,json,hashlib
root=pathlib.Path.cwd();e=root/'artifacts/phase-b/b-remaining/task-03-fix-01'
def git(*a):return subprocess.check_output(['git',*a],text=True).strip()
assert git('rev-parse','HEAD')=='5560381927d51001bd484642269b9432481e8c70'
assert not git('diff','--cached','--name-only')
sources=json.loads((e/'owned-source-manifest.json').read_text());assert len(sources)==3
for p,h in sources.items():assert hashlib.sha256((root/p).read_bytes()).hexdigest()==h
checks=json.loads((e/'self-review-checks.json').read_text());assert checks['old_task03_files_verified_unchanged']==417 and checks['source_seal_matches_covering_final'] and checks['new_exact_terminal_tasks']==16
assert checks['tests']=={'tests':35,'errors':0,'failures':0,'skipped':0}
manifest={str(p.relative_to(e)):{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(e.rglob('*')) if p.is_file() and p.name!='evidence-manifest.json'}
(e/'evidence-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
subprocess.run(['git','add','--',*sources],check=True)
subprocess.run(['git','add','-f','--',str(e.relative_to(root))],check=True)
expected=set(sources)|{str(p.relative_to(root)) for p in e.rglob('*') if p.is_file()}
assert set(git('diff','--cached','--name-only').splitlines())==expected
subprocess.run(['git','diff','--cached','--check'],check=True)
result=subprocess.run(['git','commit','-m','Keep transcript source route validation hash-free under locks'],check=True,capture_output=True,text=True);print(result.stdout.splitlines()[0])
commit=git('rev-parse','HEAD');assert not git('status','--porcelain')
for p,h in sources.items():assert hashlib.sha256(subprocess.check_output(['git','show',commit+':'+p])).hexdigest()==h
manifest_hash=hashlib.sha256((e/'evidence-manifest.json').read_bytes()).hexdigest()
report=root/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-03-report.md'
with report.open('a') as out:
 out.write('\n\n---\n\n# Task3 fix round1 committed handoff\n\nExact fix commit: `'+commit+'`. FIX_BASE: `aa0b5f08a19649091b0bcd60959bd741fb5c7850`.\n\nNew evidence manifest SHA256: `'+manifest_hash+'`. Clean worktree; committed source blobs match exact covering-final source hashes. 35 covering tests passed (4 unit +31 integration). 16 new exact synthetic tasks/attempts terminal/ended. Original417 Task3 evidence files unchanged.\n\n'+(e/'report.md').read_text(encoding='utf-8-sig'))
print(json.dumps({'commit':commit,'fix_base':'aa0b5f08a19649091b0bcd60959bd741fb5c7850','source_paths':len(sources),'evidence_files':len(manifest)+1,'evidence_manifest_sha256':manifest_hash,'worktree_clean':True,'report_path':str(report),'tests':35},indent=2))
