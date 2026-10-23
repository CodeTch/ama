from pathlib import Path
import subprocess,json,sys
p=Path('artifacts/phase-b/b-remaining/task-04');run=['python3',str(p/'run-pure.py')]
commands=[
('all-unit-final-resumed',['python3','tools/checks/run.py','unit']),
('openapi-final-lint',['/home/tanren/ama/.worktrees/phase-a/.tools/node-v22.23.4-linux-x64/bin/node','/home/tanren/ama/.worktrees/phase-a/node_modules/@redocly/cli/bin/cli.js','lint','contracts/openapi/api.yaml','--config','redocly.yaml']),
('openapi-final-bundle',['/home/tanren/ama/.worktrees/phase-a/.tools/node-v22.23.4-linux-x64/bin/node','/home/tanren/ama/.worktrees/phase-a/node_modules/@redocly/cli/bin/cli.js','bundle','contracts/openapi/api.yaml','--config','redocly.yaml','--output',str(p/'openapi-bundle.yaml')])]
for label,args in commands:
 print('BEGIN',label,flush=True)
 result=subprocess.run(run+[label]+args)
 if result.returncode:sys.exit(result.returncode)
 print('PASS',label,flush=True)

