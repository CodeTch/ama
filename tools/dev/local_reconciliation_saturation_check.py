#!/usr/bin/env python3
"""Two genuine marked publications and actual capacity-one reconciliation coexistence."""
import argparse,json,subprocess,uuid
import local_app
from local_native_worker_crash_check import provider_snapshot


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--publication-sources',nargs=2,required=True);parser.add_argument('--minutes-source',required=True);args=parser.parse_args()
    origins=[(local_app.ROOT/p).resolve() for p in args.publication_sources+[args.minutes_source]];assert all(p.is_relative_to(local_app.RUNTIME) and p.is_file() for p in origins)
    java,_,env=local_app.toolchain();app=local_app.application(java,env);classes=local_app.RUNTIME/'native-worker-classes';classes.mkdir(exist_ok=True);cp=str(classes)+':'+str(local_app.ROOT/'backend/worker/target/classes')+':'+app;out=local_app.RUNTIME/'native-saturation'/str(uuid.uuid4());out.mkdir(parents=True,mode=0o700);print(json.dumps({'evidenceDirectory':str(out)}),flush=True)
    def logged(command,name,timeout=120,run_env=env):
        with (out/name).open('w') as log:subprocess.run([str(x) for x in command],cwd=local_app.ROOT,env=run_env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,timeout=timeout,check=True)
    logged([java.with_name('javac'),'--release','21','-encoding','UTF-8','-cp',cp,'-d',classes,*['tools/dev/worker-java/ama/localdev/'+n+'.java' for n in ['LocalNativeWorkerCrashCheck','LocalMinutesPreparationBoundaryCheck','LocalReconciliationSaturationCheck']]],'compile.log')
    paused=False;settlements=[];run_env={**env,'AMA_LOCAL_DEV':'1'};values={'publications':[]};fixture=out/'fixtures.json'
    def command(mode):return [java,'-Xmx384m','-XX:ActiveProcessorCount=2','-cp',cp,'ama.localdev.LocalReconciliationSaturationCheck',local_app.RUNTIME/'worker.properties',fixture,out,mode]
    try:
        logged(['python3','tools/dev/local_app.py','start'],'start.log');logged(['python3','tools/dev/local_app.py','stop-worker'],'stop-worker.log',30);paused=True
        for i,source in enumerate(origins[:2]):
            dest=out/str(i);dest.mkdir();logged(['node','tools/dev/local_worker_fixture.mjs','MAPPING',source,dest],f'publication-{i}-queue.log',90);value=json.loads((dest/'queued.json').read_text());values['publications'].append(value);settlements.append((dest/'queued.json','MAPPING',dest))
        fixture.write_text(json.dumps(values,indent=2)+'\n');logged(command('INSPECT'),'initial-inspect.log',30,run_env);initial=json.loads((out/'inspection.json').read_text());(out/'initial.json').write_text(json.dumps(initial,indent=2)+'\n')
        for i in range(2):
            with (out/f'native-cut-{i}.log').open('w') as log:result=subprocess.run([str(x) for x in command('CUT'+str(i))],cwd=local_app.ROOT,env=run_env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,timeout=100)
            assert result.returncode==86 and json.loads((out/f'CUT{i}-cut.json').read_text())['actualInsertRows']==1
            logged(command('INSPECT'),f'cut-{i}-inspect.log',30,run_env);shots=json.loads((out/'inspection.json').read_text());(out/f'after-cut-{i}.json').write_text(json.dumps(shots,indent=2)+'\n')
            for n in range(i+1):assert shots[n]['root']==initial[n]['root'] and shots[n]['mappings']==0 and shots[n]['task']['state']=='RECONCILING' and len(shots[n]['executions'])==1 and shots[n]['executions'][0]['state']=='MARKED'
        cut=[provider_snapshot('MAPPING',p) for p in values['publications']];assert all(len(p['documents'])==1 and len(p['executions'])==1 for p in cut);(out/'provider-at-cut.json').write_text(json.dumps(cut,indent=2)+'\n')
        dest=out/'minutes';dest.mkdir();logged(['node','tools/dev/local_worker_fixture.mjs','CANDIDATE',origins[2],dest],'minutes-queue.log',60);values['minutes']=json.loads((dest/'queued.json').read_text());settlements.append((dest/'queued.json','CANDIDATE',dest));logged(['node','tools/dev/local_transcript_smoke.mjs','--queue-only'],'txt-queue.log',60);values['txt']=json.loads((local_app.RUNTIME/'qualification-intake/queued.json').read_text());txtfile=out/'txt.json';txtfile.write_text(json.dumps(values['txt'],ensure_ascii=False,indent=2)+'\n');settlements.append((txtfile,'RAW',out));fixture.write_text(json.dumps(values,indent=2)+'\n')
        logged(command('COEX'),'genuine.log',150,run_env);logged(command('INSPECT'),'final-inspect.log',30,run_env);final=json.loads((out/'inspection.json').read_text());after=[provider_snapshot('MAPPING',p) for p in values['publications']];assert after==cut
        for i in range(2):assert final[i]['mappings']==1 and final[i]['task']['state']=='SUCCEEDED' and len(final[i]['executions'])==1 and final[i]['executions'][0]['id']==shots[i]['executions'][0]['id']
        (out/'provider-after-recovery.json').write_text(json.dumps(after,indent=2)+'\n');report=json.loads((out/'report.json').read_text());report.update({'sameOriginalExecutionIds':True,'singleMappingPerPublication':True,'providerEffectsGrantsIdentityUnchanged':True});(out/'parent-report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False),flush=True)
    finally:
        state=json.loads((local_app.RUNTIME/'state.json').read_text())
        if not all(local_app.alive(state.get(r)) for r in local_app.SERVICE_ROLES if r!='worker'):logged(['python3','tools/dev/local_app.py','start'],'restore-all.log');paused=False
        if paused:logged(['python3','tools/dev/local_app.py','start-worker'],'resume-worker.log',60)
        for f,mode,dest in settlements:logged(['node','tools/dev/local_worker_recovery_smoke.mjs',f,mode,dest],'settle-'+mode+'-'+f.parent.name+'.log',150)
        state=json.loads((local_app.RUNTIME/'state.json').read_text());assert all(local_app.alive(state.get(r)) for r in local_app.SERVICE_ROLES)

if __name__=='__main__':main()
