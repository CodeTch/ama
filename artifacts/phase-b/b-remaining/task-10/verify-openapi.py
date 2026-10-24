from pathlib import Path
import subprocess,json,hashlib,os,yaml,jsonschema,sys
root=Path.cwd();out=root/'artifacts/phase-b/b-remaining/task-10/openapi-final-01';out.mkdir();cli=root/'node_modules/@redocly/cli/bin/cli.js';commands=[['node','--max-old-space-size=512',str(cli),'lint','contracts/openapi/api.yaml','--config','redocly.yaml'],['node','--max-old-space-size=512',str(cli),'bundle','contracts/openapi/api.yaml','--config','redocly.yaml','--output',str(out/'api.yaml')]];results=[]
for n,cmd in enumerate(commands):
 with (out/('command-'+str(n)+'.log')).open('w') as log:r=subprocess.run(['taskset','-c','0,1',*cmd],stdout=log,stderr=subprocess.STDOUT)
 results.append({'command':['taskset','-c','0,1',*cmd],'exit':r.returncode})
 if r.returncode:break
checks=[]
if all(r['exit']==0 for r in results):
 path=root/'contracts/openapi/minutes.yaml';document=json.loads(path.read_text());resolver=jsonschema.RefResolver(path.as_uri(),document,handlers={'file':lambda uri:yaml.safe_load(Path(uri.removeprefix('file://')).read_text())})
 fixtures=list((root/'artifacts/phase-b/b-remaining/task-10/safety-boundaries-01').glob('pipeline-*/actual-member-http.json'));assert len(fixtures)==1;actual=json.loads(fixtures[0].read_text())
 for field,schema in [('current','SavedCurrent'),('source','SavedSource')]:
  errors=list(jsonschema.Draft202012Validator(document[schema],resolver=resolver,format_checker=jsonschema.FormatChecker()).iter_errors(actual[field]));checks.append({'schema':schema,'source':str(fixtures[0].relative_to(root)),'errors':[{'path':list(e.path),'message':e.message} for e in errors]})
result={'commands':results,'actualResponseChecks':checks,'source':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [root/'contracts/openapi/api.yaml',root/'contracts/openapi/minutes.yaml']}};(out/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps({'commands':[r['exit'] for r in results],'actualSchemas':[{'schema':r['schema'],'errors':len(r['errors'])} for r in checks]}));sys.exit(0 if len(results)==2 and all(r['exit']==0 for r in results) and len(checks)==2 and all(not r['errors'] for r in checks) else 1)
