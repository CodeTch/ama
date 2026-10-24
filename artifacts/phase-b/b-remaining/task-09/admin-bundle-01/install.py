from pathlib import Path
import os,json,hashlib,shlex,subprocess,sys
A=Path(__file__).resolve().parent;P=A.parents[4]
assert sys.argv[1:]==['--execute']
h=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
manifest=A/'manifest.json';assert h(manifest)=='831171c0e72ab13cad2f73071634501a95a3da5e08493daec33b1330ff3cb841'
m=json.loads(manifest.read_text());config=Path(m['configuration']);assert h(config)==m['configurationSha256']
for entry in m['classpath']:assert h(Path(entry['path']))==entry['sha256']
for entry in m['source']:assert h(P/entry['path'])==entry['sha256']
owner=Path(m['owner']);assert owner.resolve()==owner and owner.stat().st_mode&0o777==0o700 and config.stat().st_mode&0o777==0o600
private=P/'.tools/task09-2d0e28ea775540f8b3525f9344edf5f0/env.sh';assert private.stat().st_mode&0o777==0o600
before=h(private);env=os.environ.copy();values={}
for line in private.read_text().splitlines():
 line=line.strip()
 if not line or line.startswith('#'):continue
 if line.startswith('export '):line=line[7:]
 key,value=line.split('=',1);parsed=shlex.split(value);assert len(parsed)==1;values[key]=parsed[0]
assert set(values)=={'AMA_TEST_DATABASE_URL','AMA_TEST_DATABASE_USERNAME','AMA_TEST_DATABASE_PASSWORD','AMA_TEST_DATABASE_CONTAINER','AMA_TASK09_OWNER','AMA_TASK09_RUN_ROOT','AMA_TASK09_S3_ENV','AMA_TASK09_PKI_ROOT'}
assert values['AMA_TEST_DATABASE_URL']=='jdbc:mysql://127.0.0.1:44727/ama_t09_2d0e28ea7755_behavior'
assert values['AMA_TASK09_OWNER']=='task09-2d0e28ea775540f8b3525f9344edf5f0'
assert values['AMA_TEST_DATABASE_CONTAINER']=='11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad'
env.update(values);env['JAVA_TOOL_OPTIONS']='-Xmx64m -XX:ActiveProcessorCount=2'
java='/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64/bin/java'
command=[java,'-Xmx64m','-XX:ActiveProcessorCount=2','-cp',':'.join(e['path'] for e in m['classpath']),'ama.minutes.ControlledMinutesAdmin',str(config)]
out=A/'execution-01';out.mkdir();(out/'command.json').write_text(json.dumps({'argv':command,'schema':m['schema'],'owner':m['owner'],'heapMiB':64,'processors':2},indent=2)+'\n')
run=subprocess.run(command,cwd=P,env=env,capture_output=True,timeout=60)
secret=values['AMA_TEST_DATABASE_PASSWORD'].encode();assert len(secret)>3 and secret not in run.stdout and secret not in run.stderr
if (owner/'provider.secret').exists():
 provider_secret=(owner/'provider.secret').read_bytes();assert provider_secret not in run.stdout and provider_secret not in run.stderr
(out/'stdout.log').write_bytes(run.stdout);(out/'stderr.log').write_bytes(run.stderr);assert h(private)==before
(out/'result.json').write_text(json.dumps({'exit':run.returncode,'envUnchanged':True,'providerStarted':False,'newGenerationEnabled':False},indent=2)+'\n')
if run.returncode==0:
 (out/'installation.json').write_bytes((owner/'installation.json').read_bytes())
print(json.dumps({'exit':run.returncode,'evidence':str(out)}));sys.exit(run.returncode)
