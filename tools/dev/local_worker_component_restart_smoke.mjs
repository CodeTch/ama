/** Actual private-health component restart; keeps the same browser session and other owned processes. */
import assert from 'node:assert/strict';import {createRequire} from 'node:module';import {resolve} from 'node:path';import {readFile,writeFile} from 'node:fs/promises';import {spawnSync} from 'node:child_process';
const {chromium,expect}=createRequire(resolve('web/package.json'))('@playwright/test'),[fixturePath,output]=process.argv.slice(2),fixture=JSON.parse(await readFile(fixturePath,'utf8')),base='http://127.0.0.1:5173';
const state=async()=>JSON.parse(await readFile('.tools/local-app/state.json','utf8'));function command(action){const result=spawnSync('python3',['tools/dev/local_app.py',action],{encoding:'utf8',timeout:60000});assert.equal(result.status,0,result.stdout+'\n'+result.stderr);}
const browser=await chromium.launch({headless:true});let stopped=false;
try{
 const page=await browser.newPage();await page.goto(base+'/api/dev/login');await page.getByLabel('开发账号').selectOption('dev-owner');await page.getByRole('button',{name:'登录本地环境'}).click();await page.waitForURL(base+'/meetings');
 const get=path=>page.evaluate(async path=>{const r=await fetch(path);return {status:r.status,value:await r.json()};},path);
 const session=await get('/api/v1/session'),before=await get('/api/v1/meetings/'+fixture.meetingId+'/minutes');assert.equal(before.status,200);assert(before.value.body);const capabilities=()=>get('/api/v1/meetings/'+fixture.meetingId+'/processing-capabilities');await expect.poll(async()=>(await capabilities()).value.execution).toBe('INSTALLED');const previous=await state();
 command('stop-worker');stopped=true;
 const probe=spawnSync('python3',['-c',`import socket,json
port=${previous.workerHealthPort}
plain=socket.socket();blocked=False
try:plain.bind(('127.0.0.1',port))
except OSError as error:blocked=error.errno==98
finally:plain.close()
with socket.socket() as reusable:
 reusable.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);reusable.bind(('127.0.0.1',port))
print(json.dumps({'plainBindBlockedByClosedConnection':blocked,'reuseBindAvailable':True}))`],{encoding:'utf8',timeout:5000});assert.equal(probe.status,0,probe.stderr);const portObservation=JSON.parse(probe.stdout);
 command('start-worker');stopped=false;await expect.poll(async()=>(await capabilities()).value.execution,{timeout:20000}).toBe('INSTALLED');const current=await state();assert.notEqual(current.worker.pid,previous.worker.pid);
 for(const role of ['frontend','backend','files','model','knowledge'])assert.deepEqual(current[role],previous[role],role+' must keep exact PID/start ticks');
 const after=await get('/api/v1/meetings/'+fixture.meetingId+'/minutes');assert.equal(after.status,200);assert.deepEqual(after.value.body,before.value.body);assert.deepEqual(after.value.elements,before.value.elements);assert.deepEqual((await get('/api/v1/session')).value,session.value);
 const report={pass:true,meetingId:fixture.meetingId,previousWorkerPid:previous.worker.pid,currentWorkerPid:current.worker.pid,otherProcessRecordsUnchanged:true,sameSessionAndSavedMinutesRetained:true,privateHealthInstalledAfterRestart:true,portObservation};await writeFile(output+'/component-restart.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report));
}finally{if(stopped)command('start-worker');await browser.close();}
