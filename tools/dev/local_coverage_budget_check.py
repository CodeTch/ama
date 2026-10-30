#!/usr/bin/env python3
"""Real local coverage recapture budget with transparent real-call-only qualification instrumentation."""
import argparse,json,pathlib,subprocess,uuid
import local_app


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',required=True);parser.add_argument('--mode',choices=['CUMULATIVE','DEADLINE','CANCEL'],default='CUMULATIVE');args=parser.parse_args();source=(local_app.ROOT/args.source).resolve();assert source.is_relative_to(local_app.RUNTIME/'native-crash') and source.is_file()
    java,_,env=local_app.toolchain();cp=local_app.application(java,env);classes=local_app.RUNTIME/'qualification-classes';classes.mkdir(exist_ok=True);out=local_app.RUNTIME/'coverage-budget'/str(uuid.uuid4());out.mkdir(parents=True,mode=0o700)
    repository=pathlib.Path('/home/tanren/.m2/repository');names=['net/bytebuddy/byte-buddy/1.18.11/byte-buddy-1.18.11.jar','net/bytebuddy/byte-buddy-agent/1.18.11/byte-buddy-agent-1.18.11.jar'];jars=[repository/n for n in names];assert all(p.is_file() for p in jars);cp=str(classes)+':'+cp+':'+':'.join(map(str,jars))
    def logged(command,name,timeout=120,run_env=env):
        with (out/name).open('w') as log:subprocess.run([str(x) for x in command],cwd=local_app.ROOT,env=run_env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,timeout=timeout,check=True)
    logged([java.with_name('javac'),'--release','21','-encoding','UTF-8','-cp',cp,'-d',classes,'tools/dev/qualification-java/ama/api/LocalAcquisitionBudgetCheck.java','tools/dev/qualification-java/ama/api/LocalCoverageBudgetCheck.java','tools/dev/qualification-java/ama/transcript/LocalCoverageChargeTrace.java'],'compile.log')
    paused=False
    try:
        logged(['python3','tools/dev/local_app.py','start'],'start.log')
        logged(['python3','tools/dev/local_app.py','stop-worker'],'stop-worker.log',30);paused=True
        logged(['node','tools/dev/local_worker_fixture.mjs','CANDIDATE',source,out],'queue.log',60)
        logged([java,'-Xmx384m','-XX:ActiveProcessorCount=2','-javaagent:'+str(jars[1]),'-cp',cp,'ama.api.LocalCoverageBudgetCheck',local_app.RUNTIME/'worker.properties',out/'queued.json',out,args.mode],'genuine.log',120,{**env,'AMA_LOCAL_DEV':'1'})
        print(json.dumps({**json.loads((out/'report.json').read_text()),'evidenceDirectory':str(out)},ensure_ascii=False))
    finally:
        state=json.loads((local_app.RUNTIME/'state.json').read_text())
        if not all(local_app.alive(state.get(r)) for r in local_app.SERVICE_ROLES if r!='worker'):
            logged(['python3','tools/dev/local_app.py','start'],'restore-all-services.log');paused=False
        if paused:logged(['python3','tools/dev/local_app.py','start-worker'],'resume-worker.log',60)
        if (out/'queued.json').is_file():logged(['node','tools/dev/local_worker_recovery_smoke.mjs',out/'queued.json','CANDIDATE',out],'ordinary-task-settlement.log',150)
        state=json.loads((local_app.RUNTIME/'state.json').read_text());assert all(local_app.alive(state.get(r)) for r in local_app.SERVICE_ROLES)

if __name__=='__main__':main()
