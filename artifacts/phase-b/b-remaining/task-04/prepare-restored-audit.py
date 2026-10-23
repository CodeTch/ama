from pathlib import Path
p=Path('artifacts/phase-b/b-remaining/task-04/audit-final.py');s=p.read_text().replace("'HostPort':'58062'","'HostPort':'45157'")
s=s.replace("'formal_migration_history_rows':13,","'formal_migration_history_rows':13,'current_restored_host_port':45157,'historical_test_host_port':58062,'operational_restore_is_not_database_restart_qualification':True,")
s=s.replace("'all_synthetic", "'all_synthetic")
p.write_text(s,newline='\n')
r=Path('artifacts/phase-b/b-remaining/task-04/run-final-audit.py')
r.write_text('''from pathlib import Path
import subprocess,json,hashlib,shlex
root=Path.cwd();e=root/'artifacts/phase-b/b-remaining/task-04';label='final-audit-restored'
assert not (e/(label+'.command.json')).exists()
(e/(label+'.sources.json')).write_text(json.dumps({str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((root/'backend').rglob('*')) if p.is_file() and '/src/' in str(p)},indent=2)+'\\n')
argv=['python3',str(e/'audit-final.py')];r=subprocess.run(argv,capture_output=True,text=True);log=r.stdout+r.stderr
private={}
for line in (root/'.tools/phase-b-remaining/env.sh').read_text().splitlines():
 k,v=line.removeprefix('export ').split('=',1);private[k]=shlex.split(v)[0]
log=log.replace(private['AMA_TEST_DATABASE_PASSWORD'],'[REDACTED]')
(e/(label+'.log')).write_text(log)
(e/(label+'.command.json')).write_text(json.dumps({'argv':argv,'cwd':str(root),'exit_code':r.returncode,'qualification':'Read-only restored original fullID/schema audit only; current loopback45157; historical374test receipts at58062 unchanged; not database-server restart qualification'},indent=2)+'\\n')
print(log);raise SystemExit(r.returncode)
''',newline='\n')
