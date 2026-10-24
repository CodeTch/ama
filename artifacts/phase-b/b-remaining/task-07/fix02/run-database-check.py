from pathlib import Path
import importlib.util,subprocess,json,shutil,hashlib
F=Path(__file__).parent;O=F.parent
spec=importlib.util.spec_from_file_location('owned',O/'run-owned.py');owned=importlib.util.module_from_spec(spec);spec.loader.exec_module(owned)
e=owned.env();e['JAVA_TOOL_OPTIONS']='-Xmx64m -XX:ActiveProcessorCount=2'
classes=F/'classes';classes.mkdir(exist_ok=True);cp=owned.classpath()
commands=[[str(owned.JAVA/'bin/javac'),'-J-Xmx64m','-J-XX:ActiveProcessorCount=2','-cp',cp,'-d',str(classes),str(F/'Task07Fix02DatabaseProbe.java')],[str(owned.JAVA/'bin/java'),'-Xmx64m','-XX:ActiveProcessorCount=2','-cp',str(classes.resolve())+':'+cp,'Task07Fix02DatabaseProbe',str(F/'final-database.json')]]
exits=[]
with (F/'final-database.log').open('w') as log:
 for cmd in commands:
  r=subprocess.run(cmd,env=e,stdout=log,stderr=subprocess.STDOUT);exits.append(r.returncode);assert r.returncode==0
result=json.loads((F/'final-database.json').read_text());assert result['normalRerun']==0 and len(result['history'])==15 and result['history']==json.loads((O/'migration.json').read_text())['after']
(F/'final-database-receipt.json').write_text(json.dumps({'commands':commands,'exits':exits,'normalRerun':0,'all15FullHistoryFieldsEqual':True,'source':{str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__),F/'Task07Fix02DatabaseProbe.java',O/'run-owned.py']}},indent=2)+'\n')
print('normal rerun=0; all15 complete history rows byte-value equal')
