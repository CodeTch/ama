import pathlib,subprocess,json,hashlib
root=pathlib.Path.cwd();e=root/'artifacts/phase-b/b-remaining/task-03'
def git(*a):return subprocess.check_output(['git',*a],text=True).strip()
assert git('rev-parse','HEAD')=='27143b30ea8aa15b75df65d6b44e65920cc65351'
assert all(p.startswith('artifacts/phase-b/b-remaining/task-03/') or p in (e/'owned-paths.txt').read_text().splitlines() for p in git('diff','--cached','--name-only').splitlines())
sources=json.loads((e/'owned-source-manifest.json').read_text());assert len(sources)==19
for p,h in sources.items():assert hashlib.sha256((root/p).read_bytes()).hexdigest()==h
checks=json.loads((e/'self-review-checks.json').read_text());assert checks['all_synthetic_tasks_terminal_and_attempts_ended'] and checks['credential_hit_count']==0
summary=json.loads((e/'verification-summary.json').read_text());assert summary['authority-final-normalized']['total']=={'tests':22,'failures':0,'errors':0,'skipped':0}
assert summary['upload-regression-retry']['total']=={'tests':39,'failures':0,'errors':0,'skipped':0}
manifest={str(p.relative_to(e)):{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(e.rglob('*')) if p.is_file() and p.name!='evidence-manifest.json'}
(e/'evidence-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
subprocess.run(['git','add','--',*sources],check=True)
subprocess.run(['git','add','-f','--',str(e.relative_to(root))],check=True)
staged=set(git('diff','--cached','--name-only').splitlines());expected=set(sources)|{str(p.relative_to(root)) for p in e.rglob('*') if p.is_file()}
assert staged==expected,{'unexpected':sorted(staged-expected),'missing':sorted(expected-staged)}
subprocess.run(['git','diff','--cached','--check'],check=True)
result=subprocess.run(['git','commit','-m','Implement genuine transcript source authority and bounded complete reads'],check=True,capture_output=True,text=True);print(result.stdout.splitlines()[0])
commit=git('rev-parse','HEAD');assert not git('status','--porcelain')
for p,h in sources.items():assert hashlib.sha256(subprocess.check_output(['git','show',commit+':'+p])).hexdigest()==h
manifest_hash=hashlib.sha256((e/'evidence-manifest.json').read_bytes()).hexdigest()
report=root/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-03-report.md'
report.write_text('# Task3 committed handoff\n\nExact author commit: `'+commit+'`. Original BASE: `3e393dbc9485b187b89bfc03abd88821a0aabddb`.\n\nEvidence manifest SHA256: `'+manifest_hash+'`. Final working tree clean. Committed source blobs match owned-source-manifest.json. Public report and full raw evidence are committed under artifacts/phase-b/b-remaining/task-03.\n\n'+(e/'report.md').read_text(encoding='utf-8-sig'))
print(json.dumps({'commit':commit,'source_paths':len(sources),'evidence_files':len(manifest)+1,'evidence_manifest_sha256':manifest_hash,'worktree_clean':True,'report_path':str(report),'unit_tests':221,'integration_tests':128,'standalone_custody_test':1},indent=2))
