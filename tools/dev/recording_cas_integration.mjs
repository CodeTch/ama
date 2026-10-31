import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
process.env.NO_PROXY=process.env.no_proxy='localhost,127.0.0.1,127.0.0.2';
const {chromium}=createRequire(resolve('web/package.json'))('@playwright/test');
const phase=process.env.AMA_RECORDING_PHASE??'prepare';
assert.ok(['prepare','recover'].includes(phase));
const base=process.env.AMA_LOCAL_URL??'http://127.0.0.1:5174',output=process.env.AMA_RECORDING_OUTPUT??'.tools/local-app/recording-cas';
await mkdir(output,{recursive:true});
const browser=await chromium.launch({headless:true}),checks=[],requests=[];let fixture;
async function login(actor){const page=await browser.newPage();await page.goto(`${base}/login`);await page.locator('#login-username').fill(actor);await page.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'dev-password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(`${base}/meetings`);return page;}
async function call(page,path,method='GET',body,headers={}){
 const result=await page.evaluate(async({path,method,body,headers})=>{const s=await(await fetch('/api/v1/session')).json();const r=await fetch('/api/v1'+path,{method,headers:{'X-CSRF-Token':s.csrfToken,...headers},...(body===undefined?{}:{body:headers['Content-Type']==='application/octet-stream'?new Uint8Array(body):JSON.stringify(body)})});const text=await r.text();return{status:r.status,value:text?JSON.parse(text):null};},{path,method,body,headers});
 requests.push({path,method,...result});return result;
}
function pass(name){checks.push(name);console.log('PASS '+name);}
async function chunk(page,seq,expected,{epoch=fixture.writerEpoch,conflict=false}={}){
 const pcm=new Uint8Array(64000).fill(conflict?99:seq),hash=createHash('sha256').update(pcm).digest('hex');
 const r=await call(page,`/recordings/${fixture.sessionId}/chunks`,'POST',Array.from(pcm),{'Content-Type':'application/octet-stream','X-Writer-Epoch':String(epoch),'X-Collector-Id':fixture.collectorId,'X-Part-Id':fixture.partId,'X-Seq':String(seq),'X-Sample-Start':String((seq-1)*32000),'X-Sample-Count':'32000','X-Sha256':hash});
 assert.equal(r.status,expected,JSON.stringify(r));return r.value;
}
async function claim(page){const response=await call(page,`/recordings/${fixture.sessionId}/writer-lease`,'POST',{expectedEpoch:fixture.writerEpoch,collectorId:fixture.collectorId,requestId:crypto.randomUUID()},{'Content-Type':'application/json'});assert.equal(response.status,200,JSON.stringify(response));assert.equal(response.value.writerEpoch,fixture.writerEpoch+1);fixture={...fixture,...response.value};}
try{
 const owner=await login('dev-owner'),member=await login('dev-member');
 if(phase==='prepare'){
  const meeting=await call(owner,'/meetings','POST',{}, {'Content-Type':'application/json','Idempotency-Key':crypto.randomUUID()});assert.equal(meeting.status,201,JSON.stringify(meeting));
  const session=await call(owner,`/meetings/${meeting.value.id}/recordings`,'POST');assert.equal(session.status,201,JSON.stringify(session));
  fixture={meetingId:meeting.value.id,...session.value,collectorId:crypto.randomUUID()};await claim(owner);await writeFile(`${output}/fixture.json`,JSON.stringify(fixture,null,2));
  pass('real-cas-owner-creates-recording');
  const gap=await chunk(owner,2,200);assert.equal(gap.highestContiguousSeq,0);assert.deepEqual(gap.missingSeqs,[1]);pass('out-of-order-gap-is-not-acknowledged');
  assert.equal((await chunk(owner,1,200)).highestContiguousSeq,2);await chunk(owner,1,200);await chunk(owner,1,409,{conflict:true});pass('contiguous-ack-and-exact-replay-conflict');
  await chunk(member,3,403);pass('second-cas-identity-cannot-write');
  assert.equal((await call(owner,`/recordings/${fixture.sessionId}/pause`,'POST',undefined,{'X-Expected-Epoch':String(fixture.writerEpoch),'X-Collector-Id':fixture.collectorId})).status,204);
  await chunk(owner,3,409);pass('pause-persists-before-application-restart');
 }else{
  fixture=JSON.parse(await readFile(`${output}/fixture.json`,'utf8'));await claim(owner);
  assert.equal((await call(owner,`/recordings/${fixture.sessionId}`)).status,200);
  const ack=await call(owner,`/recordings/${fixture.sessionId}/ack?partId=${fixture.partId}`);assert.equal(ack.status,200);assert.equal(ack.value.highestContiguousSeq,2);
  await chunk(owner,3,409);pass('restarted-process-recovers-session-ack-and-pause');
  assert.equal((await call(owner,`/recordings/${fixture.sessionId}/resume`,'POST',undefined,{'X-Expected-Epoch':String(fixture.writerEpoch),'X-Collector-Id':fixture.collectorId})).status,204);
  await chunk(owner,3,409,{epoch:0});assert.equal((await chunk(owner,3,200)).highestContiguousSeq,3);pass('resumed-writer-rejects-stale-epoch');
  const firstPart=fixture.partId;
  assert.equal((await call(owner,`/recordings/${fixture.sessionId}/pause`,'POST',undefined,{'X-Expected-Epoch':String(fixture.writerEpoch),'X-Collector-Id':fixture.collectorId})).status,204);
  const next=await call(owner,`/recordings/${fixture.sessionId}/parts`,'POST',undefined,{'X-Expected-Epoch':String(fixture.writerEpoch),'X-Collector-Id':fixture.collectorId});assert.equal(next.status,200);assert.equal(next.value.writerEpoch,fixture.writerEpoch+1);assert.notEqual(next.value.partId,firstPart);
  fixture={...fixture,...next.value};await chunk(owner,1,409,{epoch:fixture.writerEpoch-1});await chunk(owner,1,200,{epoch:fixture.writerEpoch});
  const headers={'Content-Type':'application/json','X-Expected-Epoch':String(fixture.writerEpoch),'X-Collector-Id':fixture.collectorId};
  assert.equal((await call(owner,`/recordings/${fixture.sessionId}/finish`,'POST',{[fixture.partId]:1},headers)).status,409);
  pass('new-part-fences-old-writer-and-cannot-omit-prior-part');
  const finals={[firstPart]:3,[fixture.partId]:1};
  const finish=await call(owner,`/recordings/${fixture.sessionId}/finish`,'POST',finals,headers);assert.equal(finish.status,200,JSON.stringify(finish));
  assert.equal((await call(owner,`/recordings/${fixture.sessionId}/finish`,'POST',finals,headers)).status,200);await chunk(owner,2,409);pass('finish-replay-and-late-write-rejection');
  assert.equal((await call(member,`/recordings/${fixture.sessionId}/ack?partId=${fixture.partId}`)).status,403);pass('recovery-does-not-bypass-current-acl');
 }
 await writeFile(`${output}/${phase}-report.json`,JSON.stringify({pass:true,phase,base,fixture,checks,requests,qualification:'durable PCM/session protocol only; archive task and playback not asserted'},null,2));
}catch(error){await writeFile(`${output}/${phase}-report.json`,JSON.stringify({pass:false,phase,fixture,checks,requests,error:String(error)},null,2));throw error;}finally{await browser.close();}
