from pathlib import Path
p=Path('artifacts/phase-b/b-remaining/task-04');s=(p/'run.py').read_text()
a=s.index("expected='eefdd");b=s.index("jdk='/home",a)
s=s[:a]+'''assert name in ('all-unit-final-resumed','openapi-final-lint','openapi-final-bundle'), 'Pure commands only'
assert not (e/(name+'.command.json')).exists() and not (e/(name+'.sources.json')).exists(), 'Evidence label must be new'
expected=None
info={'qualification':'PURE UNIT/OPENAPI; database and storage are not used; runtime fixture guard intentionally not invoked after host interruption'}
env=os.environ.copy()
'''+s[b:]
s=s.replace("for k in ['AMA_TEST_DATABASE_PASSWORD']:log=log.replace(env[k],'[REDACTED]')","for k in ['AMA_TEST_DATABASE_PASSWORD']:\n if env.get(k):log=log.replace(env[k],'[REDACTED]')")
s=s.replace("'private_env_path':'.tools/phase-b-remaining/env.sh'","'private_env_path':None")
(p/'run-pure.py').write_text(s,newline='\n')
s=(p/'resume-final-gates.py').read_text().replace("str(p/'run.py')","str(p/'run-pure.py')")
(p/'resume-final-gates.py').write_text(s,newline='\n')
