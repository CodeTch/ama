import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
import {spawnSync} from 'node:child_process';
process.env.NO_PROXY=process.env.no_proxy='localhost,127.0.0.1,127.0.0.2';
const {chromium}=createRequire(resolve('web/package.json'))('@playwright/test');
const base=process.env.AMA_LOCAL_URL??'http://127.0.0.1:5174';
const fixtureRoot=process.env.AMA_RECORDING_FIXTURE??'.tools/local-app/recording-cas-files';
const output=process.env.AMA_ARCHIVE_OUTPUT??'.tools/local-app/recording-archive-cas';
const proof=JSON.parse(await readFile(`${fixtureRoot}/actual-file-readback.json`,'utf8'));
assert.equal(proof.pass,true);assert.ok(proof.chunks.every(c=>c.fileBacked&&c.bytes===64000));
const sessionId=proof.sessionId,checks=[],requests=[],errors=[];let meeting,archive,wavProof;
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
await mkdir(output,{recursive:true});const browser=await chromium.launch({headless:true});
async function login(actor){const context=await browser.newContext({viewport:{width:1440,height:900},locale:'zh-CN',timezoneId:'Asia/Shanghai'});const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto(`${base}/login`);await page.locator('#login-username').fill(actor);await page.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'dev-password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(`${base}/meetings`);return page;}
async function call(page,path,method='GET',body){const result=await page.evaluate(async({path,method,body})=>{const session=await(await fetch('/api/v1/session')).json();const response=await fetch('/api/v1'+path,{method,headers:{'X-CSRF-Token':session.csrfToken,...(body===undefined?{}:{'Content-Type':'application/json','Idempotency-Key':crypto.randomUUID()})},...(body===undefined?{}:{body:JSON.stringify(body)})});const text=await response.text();return{status:response.status,value:text?JSON.parse(text):null};},{path,method,body});requests.push({path,method,...result});return result;}
async function audio(page,range){return page.evaluate(async({sessionId,range})=>{const response=await fetch(`/api/v1/recordings/${sessionId}/audio`,{headers:range?{Range:range}:{}});const headers=Object.fromEntries(response.headers);return response.ok?{status:response.status,headers,bytes:Array.from(new Uint8Array(await response.arrayBuffer()))}:{status:response.status,headers,error:await response.json()};},{sessionId,range});}
function pass(name,facts={}){checks.push({name,...facts});console.log('PASS '+name);}
let owner,member;
try{
 owner=await login('dev-owner');member=await login('dev-member');pass('independent-real-fixed-harness-cas-sessions');
 const existing=JSON.parse(await readFile(`${fixtureRoot}/recover-report.json`,'utf8'));meeting=existing.fixture.meetingId;
 await roster([]);assert.equal((await audio(member)).status,403);
 const finals={};for(const c of proof.chunks)finals[c.partId]=Math.max(finals[c.partId]??0,c.seq);
 const current=await call(owner,`/recordings/${sessionId}`);assert.equal(current.status,200);assert.equal(current.value.backendId,proof.chunks[0].backendId);
 // Use the already sealed, process-recovered recording; no synthetic business task or result rows.
 const finished=await owner.evaluate(async({sessionId,epoch,finals})=>{const s=await(await fetch('/api/v1/session')).json();const r=await fetch(`/api/v1/recordings/${sessionId}/finish`,{method:'POST',headers:{'X-CSRF-Token':s.csrfToken,'X-Expected-Epoch':String(epoch),'Content-Type':'application/json'},body:JSON.stringify(finals)});return{status:r.status,value:await r.json()};},{sessionId,epoch:current.value.writerEpoch,finals});
 requests.push({path:`/recordings/${sessionId}/finish`,method:'POST',...finished});assert.equal(finished.status,200,JSON.stringify(finished));assert.match(finished.value.archiveTaskId,/^[0-9a-f-]{36}$/);archive=finished.value.archiveTaskId;pass('finish-admits-real-durable-archive-task',{taskId:archive});
 const observed=[];const deadline=Date.now()+120000;
 for(;;){const state=await call(owner,`/recordings/${sessionId}/archive`);assert.equal(state.status,200,JSON.stringify(state));assert.equal(state.value.taskId,archive);observed.push(state.value.state);if(state.value.state==='SUCCEEDED'){assert.equal(state.value.available,true);break;}assert.ok(['READY','RUNNING'].includes(state.value.state),JSON.stringify(state));assert.equal(state.value.available,false);if(Date.now()>deadline)throw Error('Archive task deadline exceeded');await new Promise(r=>setTimeout(r,250));}
 pass('actual-worker-completes-registered-archive',{observed});
 const full=await audio(owner);assert.equal(full.status,200,JSON.stringify(full.error));assert.match(full.headers['content-type'],/^audio\/wav/);const wav=Buffer.from(full.bytes);
 assert.equal(wav.toString('ascii',0,4),'RIFF');assert.equal(wav.toString('ascii',8,12),'WAVE');assert.equal(wav.readUInt32LE(24),16000);assert.equal(wav.readUInt16LE(22),1);assert.equal(wav.readUInt16LE(34),16);assert.equal(wav.length,44+proof.chunks.reduce((sum,c)=>sum+c.bytes,0));
 let position=44;for(const chunk of proof.chunks){assert.equal(sha(wav.subarray(position,position+chunk.bytes)),chunk.actualSha256);position+=chunk.bytes;}
 wavProof={bytes:wav.length,sha256:sha(wav),sampleCount:(wav.length-44)/2,parts:Object.keys(finals),sourceChunkHashes:proof.chunks.map(c=>c.actualSha256)};await writeFile(`${output}/recording.wav`,wav);pass('real-wav-contains-every-acked-part-in-order',wavProof);
 const range=await audio(owner,'bytes=44-64043');assert.equal(range.status,206);assert.equal(range.headers['content-range'],`bytes 44-64043/${wav.length}`);assert.equal(sha(Buffer.from(range.bytes)),proof.chunks[0].actualSha256);assert.equal((await audio(owner,'bytes=99999999-')).status,416);pass('authenticated-range-playback-and-416');
 const replay=await call(owner,`/recordings/${sessionId}/archive`,'POST');assert.equal(replay.status,200);assert.equal(replay.value.taskId,archive);pass('archive-admission-replay-retains-task');
 async function roster(ids){const saved=await call(owner,`/meetings/${meeting}`);assert.equal(saved.status,200);const result=await call(owner,`/meetings/${meeting}/info`,'PUT',{expectedRevisionToken:saved.value.revisionToken,name:'录音可靠归档联调',category:'project',summary:'真实 CAS、持久任务、完整 WAV、范围回听与当前名单权限。',occurredOn:'2026-10-31',rosterDraftIds:ids,externalRosterNames:[]});assert.equal(result.status,200,JSON.stringify(result));const confirmed=await call(owner,`/meetings/${meeting}/roster-confirmations`,'POST',{expectedRevisionToken:result.value.revisionToken});assert.equal(confirmed.status,200,JSON.stringify(confirmed));}
 await roster(['dev-member']);const granted=await audio(member,'bytes=44-64043');assert.equal(granted.status,206,JSON.stringify(granted.error));assert.equal(sha(Buffer.from(granted.bytes)),proof.chunks[0].actualSha256);assert.equal((await call(member,`/recordings/${sessionId}/archive`)).status,200);assert.equal((await call(member,`/recordings/${sessionId}/archive`,'POST')).status,403);pass('confirmed-member-can-play-but-cannot-admit-archive');
 await roster([]);assert.equal((await audio(member)).status,403);assert.equal((await call(member,`/recordings/${sessionId}/archive`)).status,403);pass('reconfirmation-revokes-existing-session-playback');
 await roster(['dev-member']);assert.equal((await audio(member,'bytes=44-64043')).status,206);const afterInfo=await call(owner,`/recordings/${sessionId}/archive`,'POST');assert.equal(afterInfo.value.taskId,archive);pass('regrant-and-metadata-edits-preserve-same-archive');
 await owner.goto(`${base}/meeting/${meeting}`);await owner.getByRole('heading',{name:'录音可靠归档联调',exact:true}).waitFor();await owner.getByRole('button',{name:'更多操作',exact:true}).click();const taskRow=owner.locator('.task-row').filter({hasText:'录音归档'});await taskRow.first().waitFor();assert.ok((await taskRow.first().innerText()).includes('处理成功'));pass('native-workspace-renders-real-successful-archive-task');await owner.screenshot({path:`${output}/workspace-1440.png`,fullPage:true});assert.equal(errors.length,0,errors.join('\n'));
 const state=JSON.parse(await readFile('.tools/local-app/state.json','utf8'));const source=spawnSync('python3',['-c','import sys;sys.path.insert(0,"tools/dev");import local_app;print(local_app.source_signature())'],{encoding:'utf8'});assert.equal(source.status,0);
 await writeFile(`${output}/report.json`,JSON.stringify({pass:true,base,meetingId:meeting,sessionId,archiveTaskId:archive,checks,requests,wavProof,errors,amaDeployedSourceSignature:state.sourceSignature,amaWorkingSourceSignature:source.stdout.trim(),harnessRevision:spawnSync('git',['-C','/home/tanren/ama-integration-harness','rev-parse','HEAD'],{encoding:'utf8'}).stdout.trim(),qualification:'real fixed-harness CAS plus actual AMA local file storage, durable worker and authorized PCM WAV playback; no real speech-model accuracy or browser microphone qualification'},null,2));
}catch(error){await writeFile(`${output}/report.json`,JSON.stringify({pass:false,base,meetingId:meeting,sessionId,archiveTaskId:archive,checks,requests,wavProof,errors,error:String(error)},null,2));throw error;}finally{await browser.close();}
