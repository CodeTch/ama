#!/usr/bin/env python3
"""Current owned local read/withdrawal qualification; never uses settled private resources."""
import json,os,pathlib,subprocess,time,uuid
import local_app

def main():
    java,npm,env=local_app.toolchain();cp=local_app.application(java,env)
    classes=local_app.RUNTIME/'qualification-classes';classes.mkdir(exist_ok=True)
    with (local_app.RUNTIME/'bqual-read-withdrawal-compile.log').open('w') as log:
        subprocess.run([str(java.with_name('javac')),'--release','21','-encoding','UTF-8','-cp',str(classes)+':'+cp,'-d',str(classes),str(local_app.ROOT/'tools/dev/qualification-java/ama/api/LocalReadWithdrawalCheck.java')],env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
    meeting=json.loads((local_app.RUNTIME/'minutes-smoke/report.json').read_text())['meetingId'];output=local_app.RUNTIME/'read-withdrawal'/str(uuid.uuid4());output.mkdir(parents=True,mode=0o700)
    def api(phase):
        with (output/(phase+'.log')).open('w') as log:
            subprocess.run(['node',str(local_app.ROOT/'tools/dev/local_read_withdrawal_api.mjs'),phase,meeting,str(output)],cwd=local_app.ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=60,check=True)
    api('prepare');process=None
    try:
        run_env={**env,'AMA_LOCAL_DEV':'1'}
        with (output/'genuine.log').open('w') as log:
            process=subprocess.Popen([str(java),'-Xmx256m','-XX:ActiveProcessorCount=2','-cp',str(classes)+':'+cp,'ama.api.LocalReadWithdrawalCheck',str(local_app.RUNTIME/'worker.properties'),meeting,str(output)],cwd=local_app.ROOT,env=run_env,stdout=log,stderr=subprocess.STDOUT)
            deadline=time.monotonic()+20
            while not (output/'ready.json').is_file():
                if process.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Actual S barrier was not reached')
                time.sleep(.05)
            ready=json.loads((output/'ready.json').read_text());assert ready['pid']==process.pid and ready['meetingId']==meeting
            api('revoke');(output/'release').touch(exist_ok=False);code=process.wait(timeout=25)
            if code:raise RuntimeError('Genuine read withdrawal failed')
    finally:
        if process and process.poll() is None:
            (output/'release').touch(exist_ok=True)
            try:process.wait(timeout=25)
            except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=5)
        api('restore')
    report={'pass':True,'meetingId':meeting,'evidenceDirectory':str(output),'genuine':json.loads((output/'genuine.log').read_text().strip().splitlines()[-1]),'revoke':json.loads((output/'revoke.json').read_text()),'restore':json.loads((output/'restore.json').read_text())}
    (local_app.RUNTIME/'read-withdrawal/report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False))
if __name__=='__main__':main()
