import pathlib,sys,subprocess,json,hashlib,xml.etree.ElementTree as ET,os
ROOT=pathlib.Path(__file__).resolve().parents[4];OUT=pathlib.Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/'tools/checks'));import run
name=sys.argv[1];kind=sys.argv[2];selector=sys.argv[3] if len(sys.argv)>3 else '*'
files=list(ROOT.glob('backend/**/src/**/*.java'))+list(ROOT.glob('backend/adapters/src/main/resources/db/migration/*.sql'))
(OUT/(name+'.sources.json')).write_text(json.dumps({str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(files)},indent=2)+'\n')
(OUT/(name+'.command.json')).write_text(json.dumps(dict(argv=sys.argv[1:],runner='tools/checks/run.py java_tests',jdk=os.environ['JAVA_HOME'],fixture=os.environ.get('AMA_TEST_CONTAINER'),private_env='.tools/phase-b-remaining/env.sh'),indent=2)+'\n')
original_execute=run.execute
def capture_execute(command,cwd,reports):
    path=OUT/(name+'.command.json');value=json.loads(path.read_text());value['actual_command']=command;value['cwd']=str(cwd);path.write_text(json.dumps(value,indent=2)+'\n')
    return original_execute(command,cwd,reports)
run.execute=capture_execute
if kind=='it':
    reports=set(ROOT.glob('backend/*/target/failsafe-reports/TEST-*.xml'))
    for one in selector.split(','):
        for src in (ROOT/'backend/integration-tests/src/test/java').rglob(one.split('#')[0]+'.java'):
            package=src.read_text().split('package ',1)[1].split(';',1)[0].strip()
            reports.add(ROOT/'backend/integration-tests/target/failsafe-reports'/('TEST-'+package+'.'+src.stem+'.xml'))
    command=[str(ROOT/'mvnw'),'-B','-ntp','-pl','backend/integration-tests','-am','-Dtest=RuntimeMetadataNoUnitSelector','-Dsurefire.failIfNoSpecifiedTests=false','-Dit.test='+selector,'-Dfailsafe.failIfNoSpecifiedTests=false','verify']
    status=run.execute(command,ROOT,sorted(reports))
else:
    status=run.java_tests(selector,integration=False)
counts=[]
for p in sorted(ROOT.glob('backend/*/target/'+('failsafe-reports' if kind=='it' else 'surefire-reports')+'/TEST-*.xml')):
 t=ET.parse(p).getroot();counts.append(dict(report=str(p.relative_to(ROOT)),**{k:t.get(k) for k in ['name','tests','failures','errors','skipped']}))
 (OUT/(name+'-'+p.name)).write_bytes(p.read_bytes())
(OUT/(name+'.counts.json')).write_text(json.dumps(counts,indent=2)+'\n');sys.exit(status)
