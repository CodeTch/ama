#!/usr/bin/env python3
"""Actual own-heartbeat interleaving during genuine local minutes preparation."""
import argparse,json,subprocess,uuid
import local_app


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',required=True);parser.add_argument('--phase',choices=['S','DISCOVERY','EXHAUST'],default='S');parser.add_argument('--trace-exceptions',action='store_true');args=parser.parse_args()
    source=(local_app.ROOT/args.source).resolve();assert source.is_relative_to(local_app.RUNTIME/'native-crash') and source.is_file()
    java,_,env=local_app.toolchain();cp=str(local_app.ROOT/'backend/worker/target/classes')+':'+local_app.application(java,env)
    classes=local_app.RUNTIME/'native-worker-classes';classes.mkdir(exist_ok=True);output=local_app.RUNTIME/'heartbeat-preparation'/str(uuid.uuid4());output.mkdir(parents=True,mode=0o700)
    def logged(command,name,timeout=120,run_env=env):
        with (output/name).open('w') as log:subprocess.run([str(x) for x in command],cwd=local_app.ROOT,env=run_env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,timeout=timeout,check=True)
    logged([java.with_name('javac'),'--release','21','-encoding','UTF-8','-cp',cp,'-d',classes,'tools/dev/worker-java/ama/localdev/LocalMinutesPreparationHeartbeatCheck.java','tools/dev/worker-java/ama/localdev/LocalMinutesPreparationBoundaryCheck.java'],'compile.log')
    paused=False
    try:
        logged(['python3','tools/dev/local_app.py','start'],'start.log')
        logged(['python3','tools/dev/local_app.py','stop-worker'],'stop-worker.log',30);paused=True
        logged(['node','tools/dev/local_worker_fixture.mjs','CANDIDATE',source,output],'queue.log',60)
        target='ama.localdev.LocalMinutesPreparationHeartbeatCheck' if args.phase=='S' else 'ama.localdev.LocalMinutesPreparationBoundaryCheck'
        trace=[]
        if args.trace_exceptions:
            settings=output/'exceptions.jfc';settings.write_text('<configuration version="2.0" label="Bounded qualification exceptions"><event name="jdk.JavaExceptionThrow"><setting name="enabled">true</setting><setting name="stackTrace">true</setting></event></configuration>')
            trace=['-XX:StartFlightRecording=settings='+str(settings)+',filename='+str(output/'exceptions.jfr')+',maxsize=16m,maxage=2m,dumponexit=true']
        logged([java,*trace,'-Xmx256m','-XX:ActiveProcessorCount=2','-cp',str(classes)+':'+cp,target,local_app.RUNTIME/'worker.properties',output/'queued.json',output,*([] if args.phase=='S' else [args.phase])],'genuine.log',150,{**env,'AMA_LOCAL_DEV':'1'})
        report=json.loads((output/'report.json').read_text());print(json.dumps({**report,'evidenceDirectory':str(output)},ensure_ascii=False))
    finally:
        state=json.loads((local_app.RUNTIME/'state.json').read_text())
        if not all(local_app.alive(state.get(r)) for r in local_app.SERVICE_ROLES if r!='worker'):
            logged(['python3','tools/dev/local_app.py','start'],'restore-all-services.log');paused=False
        if paused:logged(['python3','tools/dev/local_app.py','start-worker'],'resume-worker.log',60)
        if (output/'queued.json').is_file():logged(['node','tools/dev/local_worker_recovery_smoke.mjs',output/'queued.json','CANDIDATE',output],'ordinary-task-settlement.log',150)
        state=json.loads((local_app.RUNTIME/'state.json').read_text());assert all(local_app.alive(state.get(r)) for r in local_app.SERVICE_ROLES)
        if args.trace_exceptions and (output/'exceptions.jfr').is_file():
            with (output/'exceptions.json').open('w') as log:subprocess.run([str(java.with_name('jfr')),'print','--json','--stack-depth','128','--events','jdk.JavaExceptionThrow',str(output/'exceptions.jfr')],cwd=local_app.ROOT,env=env,stdout=log,stderr=subprocess.DEVNULL,timeout=30,check=True)
            selected=[]
            for event in json.loads((output/'exceptions.json').read_text())['recording']['events']:
                value=event['values']
                if value.get('message') not in ['PROVENANCE_UNAVAILABLE','Same-lease renewal requires fresh publication preparation','BUDGET_EXCEEDED']:continue
                frames=[]
                for frame in value.get('stackTrace',{}).get('frames',[]):
                    method=frame['method'];name=method['type']['name']
                    if name.startswith('ama/') or name.startswith('ama.'):frames.append(name.replace('/','.')+'.'+method['name']+':'+str(frame.get('lineNumber')))
                selected.append({'time':value.get('startTime'),'message':value.get('message'),'thread':value.get('eventThread',{}).get('javaName'),'frames':frames[:25]})
            (output/'provenance-stacks.json').write_text(json.dumps(selected,indent=2)+'\n');print(json.dumps({'diagnosticDirectory':str(output),'events':selected},ensure_ascii=False))

if __name__=='__main__':main()
