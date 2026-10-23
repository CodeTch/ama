from pathlib import Path
import json,subprocess,os,sys
r=Path('/home/tanren/ama/.worktrees/phase-b-completion');base=r/'artifacts/phase-b/b-remaining/task-05';e=base/'cleanup-round2'
receipt=json.loads((base/'pipeline-774a2194-a61a-4756-a783-e486c3ee6354/restart.json').read_text());cp=receipt['command'][4]
cmd=[receipt['command'][0],'-Xmx64m','-XX:ActiveProcessorCount=2','-cp',cp,str(e/'Task05FixtureMaintenance.java'),sys.argv[1],str(e)]
(e/('cleanup-command-'+sys.argv[1]+'.json')).write_text(json.dumps(cmd,indent=2)+'\n')
subprocess.run(cmd,cwd=r,check=True)
