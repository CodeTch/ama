from pathlib import Path
import json,subprocess,sys
r=Path(__file__).parent;cmd=json.loads((r/'command-plan.json').read_text());cmd[-2]=sys.argv[1]
with (r/('output-'+sys.argv[1]+'.log')).open('w') as log: result=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT)
print('exit',result.returncode);sys.exit(result.returncode)
