from pathlib import Path
import json,hashlib,subprocess
p=Path('contracts/openapi/files.yaml');old=p.read_bytes();x=json.loads(old);assert x['Accepted']['properties']['partSizeToken']=={'$ref':'./meetings.yaml#/ExpectedRevisionToken'}
x['Accepted']['properties']['partSizeToken']={'type':'string','pattern':'^[1-9][0-9]{0,18}$','description':'Opaque canonical decimal representation of the unchanged positive partSize value, 1..9223372036854775807. Preserve this string exactly.'}
p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',newline='\n');e=Path('artifacts/phase-b/b-remaining/task-04')
(e/'openapi-size-range-correction.json').write_text(json.dumps({'path':str(p),'before_sha256':hashlib.sha256(old).hexdigest(),'after_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'change':'Accepted.partSizeToken schema documents unchanged positive long partSize range separately from expected revision','java_source_changed':False},indent=2)+'\n')
# Fresh labels and a fresh bundle destination preserve the preceding checks verbatim.
s=(e/'run-pure.py').read_text().replace("'openapi-final-bundle')","'openapi-final-bundle','openapi-size-final-lint','openapi-size-final-bundle')")
(e/'run-pure.py').write_text(s,newline='\n')
args=['/home/tanren/ama/.worktrees/phase-a/.tools/node-v22.23.4-linux-x64/bin/node','/home/tanren/ama/.worktrees/phase-a/node_modules/@redocly/cli/bin/cli.js']
for label,tail in [('openapi-size-final-lint',['lint','contracts/openapi/api.yaml','--config','redocly.yaml']),('openapi-size-final-bundle',['bundle','contracts/openapi/api.yaml','--config','redocly.yaml','--output',str(e/'openapi-final-bundle.yaml')])]:
 subprocess.run(['python3',str(e/'run-pure.py'),label]+args+tail,check=True)
for name in ['audit-final.py','write-report.py']:
 f=e/name;s=f.read_text().replace("'openapi-final-lint'","'openapi-size-final-lint'").replace("'openapi-final-bundle'","'openapi-size-final-bundle'")
 if name=='audit-final.py':s=s.replace("shutil.copytree(restart,e/'config-restart-final-retained')","assert (e/'config-restart-final-retained').exists(), 'Retained exact config restart copied by source audit'")
 else:s=s.replace('`openapi-bundle.yaml` is new Task4 evidence.','`openapi-final-bundle.yaml` is the final Task4 evidence. The earlier bundle remains preserved. Self-review corrected only the original upload partSizeToken schema to its unchanged positive int64 range, independently of revision tokens; openapi-size-range-correction.json records this OpenAPI-only source delta, with fresh lint/bundle and no Java change.')
 f.write_text(s,newline='\n')
