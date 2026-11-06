/** Concurrent retries on an acceptance meeting. All clients in a wave submit the same business command. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {setTimeout as delay} from 'node:timers/promises';
const {chromium}=createRequire(resolve('web/package.json'))('@playwright/test');
const origin='http://127.0.0.1:5174';
const fixture=JSON.parse(await readFile(process.env.AMA_MINUTES_E2E_MEETING_REPORT,'utf8'));
assert(fixture.pass&&fixture.checks.some(c=>c.name?.includes('explicit-first-generation-retry')));
const runtime=JSON.parse(await readFile('.tools/local-app/state.json','utf8'));
for(const service of ['cas','model','directory','knowledge'])assert.equal(runtime.integrationOrigins[service],'http://127.0.0.1:8792');
const out=resolve(process.env.AMA_MINUTES_E2E_OUTPUT??'artifacts/ai-minutes-generation-concurrency');
await mkdir(out,{recursive:true});const report={meeting:fixture.meetingId,waves:[],pass:false},browser=await chromium.launch();
try{
 const page=await browser.newPage();await page.goto(origin+'/login');await page.locator('#login-username').fill(process.env.AMA_MINUTES_E2E_OWNER??'dev-owner');await page.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'dev-password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/meetings');
 const cookie=(await page.context().cookies()).map(c=>c.name+'='+c.value).join('; ');
 const csrf=(await (await fetch(origin+'/api/v1/session',{headers:{Cookie:cookie}})).json()).csrfToken;
 const root=origin+'/api/v1/meetings/'+report.meeting+'/minutes';
 async function get(path){const r=await fetch(root+path,{headers:{Cookie:cookie}});assert.equal(r.status,200);return r.json();}
 async function post(path,body,key=crypto.randomUUID()){
  const start=performance.now(),r=await fetch(root+path,{method:'POST',headers:{Origin:origin,Cookie:cookie,'X-CSRF-Token':csrf,'Content-Type':'application/json','Idempotency-Key':key},body:JSON.stringify(body),signal:AbortSignal.timeout(90000)});
  return {status:r.status,ms:Math.round(performance.now()-start),data:await r.json()};
 }
 async function rejectOwnCandidate(candidate){
  const saved=await get('');
  const revision={expectedRevisionToken:saved.observation.revisionToken};
  assert.equal((await post('/editor-intents',revision)).status,200);
  assert.equal((await post('/candidates/'+candidate.candidateId+'/rejections',revision)).status,200);
 }
 if(process.env.AMA_MINUTES_PREVIOUS_CONCURRENCY_REPORT){
  const previous=JSON.parse(await readFile(process.env.AMA_MINUTES_PREVIOUS_CONCURRENCY_REPORT,'utf8'));
  assert.equal(previous.meeting,report.meeting);
  const own=new Set(previous.waves.flatMap(wave=>wave.taskIds));
  for(const candidate of (await get('/candidates?state=PENDING&limit=20')).items)if(own.has(candidate.taskId))await rejectOwnCandidate(candidate);
 }
 const original=await get('');
 for(const parallel of [1,10,50]){
  const current=await get('/generation-inputs/current');assert(current.eligible);
  const fields={scope:'FULL',expectedRevisionToken:current.observation.revisionToken},key=crypto.randomUUID();
  const responses=await Promise.all(Array.from({length:parallel},()=>post('/generations',fields,key)));
  const tasks=new Set(responses.map(r=>r.data.result?.taskId).filter(Boolean)),accepted=responses.filter(r=>r.status===202&&!r.data.replayed);
  report.waves.push({parallel,statuses:responses.map(r=>r.status),taskIds:[...tasks],newCommands:accepted.length,p95:responses.map(r=>r.ms).sort((a,b)=>a-b)[Math.ceil(.95*parallel)-1]});
  assert.equal(tasks.size,1);assert.equal(accepted.length,1);
  const ack=accepted[0].data,deadline=Date.now()+150000;
  let state;
  do{state=(await get('/generation-tasks/'+ack.result.taskId)).task.state;if(['READY','RUNNING','RECONCILING'].includes(state))await delay(1000);else break;}while(Date.now()<deadline);
  assert.equal(state,'SUCCEEDED');
  const candidates=(await get('/candidates?state=PENDING&limit=20')).items.filter(c=>c.inputId===ack.result.inputId);
  assert.equal(candidates.length,1);
  const saved=await get('');assert.equal(saved.body.id,original.body.id);
  await rejectOwnCandidate(candidates[0]);
  assert(responses.every(r=>r.status===202),JSON.stringify(report.waves.at(-1)));
  console.log('PASS '+parallel+' submission(s), one task and one explicitly rejected candidate');
 }
 report.pass=true;
}catch(error){report.failure=String(error);throw error;}
finally{await writeFile(out+'/report.json',JSON.stringify(report,null,2));await browser.close();}
