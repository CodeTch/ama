#!/usr/bin/env python3
"""Bounded genuine four-task coexistence and capacity-one minutes saturation."""
import argparse,json,subprocess,uuid
from pathlib import Path
import local_app
from local_native_worker_crash_check import provider_snapshot


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--publication-source',required=True);args=parser.parse_args()
    source=(local_app.ROOT/args.publication_source).resolve();assert source.is_relative_to(local_app.RUNTIME/'native-crash') and source.is_file()
    java,_,env=local_app.toolchain();cp=str(local_app.ROOT/'backend/worker/target/classes')+':'+local_app.application(java,env);classes=local_app.RUNTIME/'native-worker-classes';classes.mkdir(exist_ok=True)
    out=local_app.RUNTIME/'native-coexistence'/str(uuid.uuid4());out.mkdir(parents=True,mode=0o700);print(json.dumps({'evidenceDirectory':str(out)}),flush=True)
    def logged(command,name,timeout=120,run_env=env):
        with (out/name).open('w') as log:subprocess.run([str(x) for x in command],cwd=local_app.ROOT,env=run_env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,timeout=timeout,check=True)
    logged([java.with_name('javac'),'--release','21','-encoding','UTF-8','-cp',cp,'-d',classes,*['tools/dev/worker-java/ama/localdev/'+n+'.java' for n in ['LocalNativeWorkerCrashCheck','LocalMinutesPreparationBoundaryCheck','LocalWorkerCoexistenceCheck']]],'compile.log')
    cp=str(classes)+':'+cp;run_env={**env,'AMA_LOCAL_DEV':'1'};paused=False;settlements=[]
    try:
        logged(['python3','tools/dev/local_app.py','start'],'start.log')
        raw=[]
        for i in range(2):
            logged(['node','tools/dev/local_transcript_smoke.mjs','--queue-only'],f'raw-{i}-queue.log',60)
            value=json.loads((local_app.RUNTIME/'qualification-intake/queued.json').read_text());origin=local_app.RUNTIME/'native-crash'/str(uuid.uuid4());origin.mkdir(mode=0o700);fixture=origin/'fixture.json';fixture.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n');raw.append(fixture)
            logged(['node','tools/dev/local_worker_recovery_smoke.mjs',fixture,'RAW',origin],f'raw-{i}-complete.log',150)
        logged(['python3','tools/dev/local_app.py','stop-worker'],'stop-worker.log',30);paused=True
        cutout=local_app.RUNTIME/'native-crash'/str(uuid.uuid4());cutout.mkdir(mode=0o700)
        logged(['node','tools/dev/local_worker_fixture.mjs','MAPPING',source,cutout],'publication-queue.log',90);pub=json.loads((cutout/'queued.json').read_text());fixture=cutout/'fixture.json';fixture.write_text(json.dumps(pub,indent=2)+'\n');settlements.append((fixture,'MAPPING',cutout))
        command=[java,'-Xmx256m','-XX:ActiveProcessorCount=2','-cp',cp,'ama.localdev.LocalNativeWorkerCrashCheck',local_app.RUNTIME/'worker.properties',fixture,'MAPPING',cutout]
        with (out/'publication-native-cut.log').open('w') as log:result=subprocess.run([str(x) for x in command],cwd=local_app.ROOT,env=run_env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,timeout=100)
        assert result.returncode==86 and json.loads((cutout/'cut.json').read_text())['actualInsertRows']==1
        inspect=command.copy();inspect[-2]='INSPECT_MAPPING';logged(inspect,'publication-inspect-cut.log',30,run_env);before=json.loads((cutout/'before.json').read_text());cut=json.loads((cutout/'inspection.json').read_text());assert cut['mappings']==0 and cut['root']==before['root'] and cut['task']['state']=='RECONCILING' and len(cut['executions'])==1 and cut['executions'][0]['state']=='MARKED';provider_cut=provider_snapshot('MAPPING',pub);assert len(provider_cut['documents'])==1 and len(provider_cut['executions'])==1;(out/'provider-at-cut.json').write_text(json.dumps(provider_cut,indent=2)+'\n')
        values=[]
        for i,origin in enumerate(raw):
            dest=out/str(i);dest.mkdir();logged(['node','tools/dev/local_worker_fixture.mjs','CANDIDATE',origin,dest],f'generation-{i}-queue.log',60);values.append(json.loads((dest/'queued.json').read_text()));settlements.append((dest/'queued.json','CANDIDATE',dest))
        logged(['node','tools/dev/local_transcript_smoke.mjs','--queue-only'],'txt-queue.log',60);txt=json.loads((local_app.RUNTIME/'qualification-intake/queued.json').read_text());txtfile=out/'txt.json';txtfile.write_text(json.dumps(txt,ensure_ascii=False,indent=2)+'\n');settlements.append((txtfile,'RAW',out));allfile=out/'fixtures.json';allfile.write_text(json.dumps({'first':values[0],'second':values[1],'txt':txt,'publication':pub},indent=2)+'\n')
        logged([java,'-Xmx384m','-XX:ActiveProcessorCount=2','-cp',cp,'ama.localdev.LocalWorkerCoexistenceCheck',local_app.RUNTIME/'worker.properties',allfile,out],'genuine.log',150,run_env)
        logged(inspect,'publication-inspect-recovered.log',30,run_env);recovered=json.loads((cutout/'inspection.json').read_text());assert recovered['task']['id']==pub['taskId'] and recovered['task']['state']=='SUCCEEDED' and recovered['mappings']==1 and len(recovered['executions'])==1 and recovered['executions'][0]['id']==cut['executions'][0]['id'];provider_after=provider_snapshot('MAPPING',pub);assert provider_after==provider_cut;(out/'provider-after-recovery.json').write_text(json.dumps(provider_after,indent=2)+'\n');report=json.loads((out/'report.json').read_text());report.update({'providerDocumentExecutionGrantsIdentityUnchanged':True,'originalPublicationExecutionIdRetained':True,'mappingNativeCutDirectory':str(cutout)});(out/'parent-report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False),flush=True)
    finally:
        current=json.loads((local_app.RUNTIME/'state.json').read_text())
        if not all(local_app.alive(current.get(r)) for r in local_app.SERVICE_ROLES if r!='worker'):logged(['python3','tools/dev/local_app.py','start'],'restore-services.log');paused=False
        if paused:logged(['python3','tools/dev/local_app.py','start-worker'],'resume-worker.log',60)
        for fixture,mode,dest in settlements:logged(['node','tools/dev/local_worker_recovery_smoke.mjs',fixture,mode,dest],'settle-'+mode+'-'+fixture.parent.name+'.log',150)
        current=json.loads((local_app.RUNTIME/'state.json').read_text());assert all(local_app.alive(current.get(r)) for r in local_app.SERVICE_ROLES)

if __name__=='__main__':main()
