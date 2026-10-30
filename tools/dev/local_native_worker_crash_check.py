#!/usr/bin/env python3
"""Bounded native crash checks against the current owned daily application."""
import argparse,fcntl,hashlib,json,os,socket,sqlite3,subprocess,time,uuid
from pathlib import Path
import local_app


def provider_snapshot(mode,fixture):
    if mode=='CANDIDATE':
        root=local_app.RUNTIME/'minutes/provider-store'
        content={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for folder in ['payloads','results'] for p in sorted((root/folder).glob('*.json'))}
        return {'files':content,'payloads':sum(k.startswith('payloads/') for k in content),'results':sum(k.startswith('results/') for k in content),'journalSha256':hashlib.sha256((root/'journal.bin').read_bytes()).hexdigest()}
    if mode=='MAPPING':
        root=local_app.RUNTIME/'knowledge/store.sqlite'
        with sqlite3.connect(root.as_uri()+'?mode=ro',uri=True) as db:
            rows=db.execute('SELECT id,payload_hash,result_id FROM document WHERE publication=? ORDER BY id',(fixture['publicationId'],)).fetchall()
            executions=db.execute('SELECT id,number,request_hash FROM execution WHERE publication=? ORDER BY number',(fixture['publicationId'],)).fetchall()
            grants=db.execute('SELECT actor,library,folder,can_publish,can_read,version FROM grant_record ORDER BY actor,library,folder').fetchall()
            pins=db.execute('SELECT pins FROM identity WHERE singleton=1').fetchone()[0]
            return {'documents':rows,'executions':executions,'grants':grants,'identitySha256':hashlib.sha256(pins).hexdigest()}
    return None


def restart_provider(mode,output,env):
    role='model' if mode=='CANDIDATE' else 'knowledge';portkey='modelPort' if mode=='CANDIDATE' else 'knowledgePort'
    with (local_app.RUNTIME/'lifecycle.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX);state=json.loads((local_app.RUNTIME/'state.json').read_text());record=state[role];assert local_app.alive(record)
        original={r:state[r].copy() for r in local_app.SERVICE_ROLES if r not in (role,'worker')}
        command=[value.decode() for value in Path('/proc',str(record['pid']),'cmdline').read_bytes().split(b'\0') if value]
        assert str(local_app.RUNTIME/('minutes/runtime.properties' if role=='model' else 'knowledge/provider.json')) in command
        if role=='model':
            values=dict(line.split('=',1) for line in (local_app.RUNTIME/'minutes/runtime.properties').read_text().splitlines());ready=Path(values['readyFile']);assert ready.is_relative_to(local_app.RUNTIME/'minutes') and ready.is_file() and not ready.is_symlink()
        else:ready=local_app.RUNTIME/'knowledge/provider.ready'
        local_app.stop_process(record)
        if role=='model':ready.unlink()  # Exact disposable readiness receipt only; provider CREATE_NEW on same config.
        try:
            state[role]=local_app.launch(command,{**env,'AMA_LOCAL_DEV':'1','AMA_LOCAL_DEV_INSTANCE':local_app.OWNER},role+'.log');local_app.save(local_app.RUNTIME/'state.json',state)
            deadline=time.monotonic()+30
            while time.monotonic()<deadline:
                assert local_app.alive(state[role]),'Exact provider restart exited'
                receipt_ok=ready.is_file() and (ready.read_text()==str(state[portkey]) if role=='model' else json.loads(ready.read_text()).get('pid')==state[role]['pid'])
                if receipt_ok:
                    try:
                        with socket.create_connection(('127.0.0.1',state[portkey]),timeout=.5):break
                    except OSError:pass
                time.sleep(.1)
            else:raise RuntimeError('Exact provider restart not ready')
        finally:
            assert all(state[r]==recorded and local_app.alive(recorded) for r,recorded in original.items()),'Independent restart must retain other process identities'
        report={'role':role,'previousPid':record['pid'],'currentPid':state[role]['pid'],'sameCommandAndConfiguration':True,'otherProcessesUnchanged':True,'noBootstrapOrGrantMutation':True};assert report['previousPid']!=report['currentPid'];(output/'provider-restart.json').write_text(json.dumps(report,indent=2)+'\n');return report


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode',choices=['RAW','CANDIDATE','MAPPING'])
    parser.add_argument('--fixture',help='Fresh accepted qualification task JSON for CANDIDATE/MAPPING')
    parser.add_argument('--source',help='Completed previous qualification JSON; establish next task after ordinary Worker pauses')
    parser.add_argument('--restart-provider',action='store_true',help='Independently restart exact owned provider after native cut, preserving store/identity/config')
    args=parser.parse_args()
    if args.mode!='RAW' and not (args.fixture or args.source):parser.error('An accepted fixture or completed source is required')
    if args.fixture and args.source:parser.error('Use one fixture source')
    java,_,env=local_app.toolchain();cp=str(local_app.ROOT/'backend/worker/target/classes')+':'+local_app.application(java,env)
    classes=local_app.RUNTIME/'native-worker-classes';classes.mkdir(exist_ok=True)
    output=local_app.RUNTIME/'native-crash'/str(uuid.uuid4());output.mkdir(parents=True,mode=0o700)
    def logged(command,name,timeout=90,run_env=env):
        with (output/name).open('w') as log:return subprocess.run([str(x) for x in command],cwd=local_app.ROOT,env=run_env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=timeout)
    logged([java.with_name('javac'),'--release','21','-encoding','UTF-8','-cp',cp,'-d',classes,local_app.ROOT/'tools/dev/worker-java/ama/localdev/LocalNativeWorkerCrashCheck.java'],'compile.log')
    # Install current source/config before pausing the ordinary Worker. Retains all stores and identities.
    logged(['python3','tools/dev/local_app.py','start'],'start.log',120)
    state=json.loads((local_app.RUNTIME/'state.json').read_text());assert state['owner']==local_app.OWNER and all(local_app.alive(state.get(r)) for r in local_app.SERVICE_ROLES)
    process=None;paused=False;fixture=None
    try:
        logged(['python3','tools/dev/local_app.py','stop-worker'],'stop-worker.log',30);paused=True
        if args.mode=='RAW':
            logged(['node','tools/dev/local_transcript_smoke.mjs','--queue-only'],'queue.log',60)
            fixture=json.loads((local_app.RUNTIME/'qualification-intake/queued.json').read_text())
        elif args.source:
            source=(local_app.ROOT/args.source).resolve();assert source.is_relative_to(local_app.RUNTIME/'native-crash') and source.is_file()
            logged(['node','tools/dev/local_worker_fixture.mjs',args.mode,str(source),str(output)],'queue.log',90)
            fixture=json.loads((output/'queued.json').read_text())
        else:fixture=json.loads((local_app.ROOT/args.fixture).resolve().read_text())
        assert fixture['queueOnlyAccepted'] if args.mode=='RAW' else fixture.get('taskId')
        for key in ('meetingId','taskId'):uuid.UUID(fixture[key])
        provider_before=provider_snapshot(args.mode,fixture)
        fixturefile=output/'fixture.json';fixturefile.write_text(json.dumps(fixture,ensure_ascii=False,indent=2)+'\n')
        command=[str(java),'-Xmx256m','-XX:ActiveProcessorCount=2','-cp',str(classes)+':'+cp,'ama.localdev.LocalNativeWorkerCrashCheck',str(local_app.RUNTIME/'worker.properties'),str(fixturefile),args.mode,str(output)]
        run_env={**env,'AMA_LOCAL_DEV':'1'}
        with (output/'native.log').open('w') as log:
            process=subprocess.Popen(command,cwd=local_app.ROOT,env=run_env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
            code=process.wait(timeout=100)
        assert code==86,'Exact native cut exit required; inspect '+str(output)
        cut=json.loads((output/'cut.json').read_text());assert cut['pid']==process.pid and cut['taskId']==fixture['taskId'] and cut['meetingId']==fixture['meetingId'] and cut['mode']==args.mode and cut['phase']=='AFTER_REAL_INSERT_BEFORE_COMMIT' and cut['actualInsertRows']==1 and cut['actualTransactionActive'] and not cut['autoCommit']
        inspect=command.copy();inspect[-2]='INSPECT_'+args.mode
        logged(inspect,'after-crash.log',30,run_env);after=json.loads((output/'inspection.json').read_text());(output/'after-crash.json').write_text(json.dumps(after,ensure_ascii=False,indent=2)+'\n');before=json.loads((output/'before.json').read_text())
        for key in ('root','raw','segments','orderedRawSegments','files','references','batches','inputs','completionEvents'):
            assert before[key]==after[key],f'Partial {key} after native crash'
        expected_state='RUNNING' if args.mode=='RAW' else 'RECONCILING'
        assert after['task']['state']==expected_state and after['task']['attempt']==1 and after['task']['output_id'] is None
        if args.mode!='RAW':
            for key in ('candidates','bodies','sets'):assert before[key]==after[key],f'Partial {key} after native crash'
            assert len(after['executions'])==1 and after['executions'][0]['marked'] in (True,1) and after['executions'][0]['state']=='MARKED'
        if args.mode=='MAPPING':assert after['mappings']==0
        provider_cut=provider_snapshot(args.mode,fixture)
        if args.mode=='CANDIDATE':assert provider_cut['payloads']==provider_before['payloads']+1 and provider_cut['results']==provider_before['results']+1
        if args.mode=='MAPPING':assert len(provider_cut['documents'])==1 and len(provider_cut['executions'])==1 and not provider_before['documents'] and not provider_before['executions']
        if provider_cut is not None:(output/'provider-at-cut.json').write_text(json.dumps(provider_cut,indent=2)+'\n')
        provider_restart=restart_provider(args.mode,output,env) if args.restart_provider and args.mode!='RAW' else None
        if provider_cut is not None:assert provider_snapshot(args.mode,fixture)==provider_cut,'Provider restart must retain original effects and authority'
        # Only the ordinary installed Worker performs recovery. No manual SQL lease/marker changes.
        logged(['python3','tools/dev/local_app.py','start-worker'],'resume-worker.log',60);paused=False
        logged(['node','tools/dev/local_worker_recovery_smoke.mjs',str(fixturefile),args.mode,str(output)],'api-recovery.log',150)
        logged(inspect,'after-recovery.log',30,run_env);recovered=json.loads((output/'inspection.json').read_text());(output/'after-recovery.json').write_text(json.dumps(recovered,ensure_ascii=False,indent=2)+'\n')
        assert recovered['task']['state']=='SUCCEEDED' and recovered['task']['id']==fixture['taskId']
        if args.mode=='RAW':
            assert recovered['raw']==1 and recovered['segments']==4 and recovered['orderedRawSegments']==fixture['expectedSegments'] and recovered['completionEvents']==1
            for key in ('root','files','references','batches','inputs'):assert recovered[key]==before[key],f'Recovery changed retained {key}'
        else:
            assert len(recovered['executions'])==1 and recovered['executions'][0]['state']=='SUCCEEDED'
            if args.mode=='CANDIDATE':assert recovered['candidates']==before['candidates']+1
            else:assert recovered['mappings']==1
        if provider_cut is not None:
            provider_recovered=provider_snapshot(args.mode,fixture);assert provider_recovered==provider_cut,'Recovery may not create extra remote effects or mutate authority'
            (output/'provider-after-recovery.json').write_text(json.dumps(provider_recovered,indent=2)+'\n')
        report={'pass':True,'mode':args.mode,'meetingId':fixture['meetingId'],'taskId':fixture['taskId'],'evidenceDirectory':str(output),'nativeExit':code,'actualInsertBeforeCommit':True,'zeroPartialDomainWrites':True,'acceptedFilesAndInputsRetained':True,'ordinaryWorkerRecoveredOriginalTask':True,'manualLeaseOrTaskMutation':False,'recoveredAttempt':recovered['task']['attempt'],'providerRestart':provider_restart,'unchangedRemoteEffectsAfterCut':provider_cut is not None}
        (output/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False),flush=True)
    finally:
        if process is not None and process.poll() is None:
            process.terminate()
            try:process.wait(timeout=10)
            except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
        current=json.loads((local_app.RUNTIME/'state.json').read_text())
        if not all(local_app.alive(current.get(r)) for r in local_app.SERVICE_ROLES if r!='worker'):
            logged(['python3','tools/dev/local_app.py','start'],'finally-restore-services.log',120);paused=False
        if paused:logged(['python3','tools/dev/local_app.py','start-worker'],'finally-resume-worker.log',60)
        current=json.loads((local_app.RUNTIME/'state.json').read_text());assert all(local_app.alive(current.get(r)) for r in local_app.SERVICE_ROLES),'Owned daily services must all be restored'

if __name__=='__main__':main()
