import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,writeFile,readFile} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
process.env.NO_PROXY=process.env.no_proxy='localhost,127.0.0.1,127.0.0.2';
const {chromium}=createRequire(resolve('web/package.json'))('@playwright/test');
const base=process.env.AMA_LOCAL_URL??'http://127.0.0.1:5174',output=process.env.AMA_SOCKET_OUTPUT??'.tools/local-app/recording-socket-cas';
await mkdir(output,{recursive:true});const browser=await chromium.launch({headless:true});const errors=[];let proof;
try{
 const page=await browser.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto(`${base}/login`);await page.locator('#login-username').fill('dev-owner');await page.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'dev-password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(`${base}/meetings`);
 proof=await page.evaluate(async()=>{
  const {createBusinessClient}=await import('/src/shared/api/business-client.ts'),{createRecordingApi}=await import('/src/features/recording/recording-api.ts'),{connectRecordingSocket}=await import('/src/features/recording/recording-socket.ts');
  const identity=await(await fetch('/api/v1/session')).json(),client=createBusinessClient(location.origin,()=>identity,()=>{throw Error('Current identity rejected');}),sdk=createRecordingApi(client),checks=[];
  const check=(yes,name)=>{if(!yes)throw Error(name);checks.push(name);},reject=async(work,name)=>{try{await work();}catch{checks.push(name);return;}throw Error(name);};
  const meeting=await client.json('/api/v1/meetings',{method:'POST',headers:{'Idempotency-Key':crypto.randomUUID()},body:'{}',expectedStatus:201});
  let current=await sdk.create(meeting.id);const collector=crypto.randomUUID();current=await sdk.acquireWriter(current,collector,crypto.randomUUID());check(current.writerEpoch===2,'real-cas-session-claims-database-writer');const first=current.partId;
  const hash=async pcm=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',pcm)),b=>b.toString(16).padStart(2,'0')).join('');
  const chunk=async(seq,fill)=>{const pcm=new Uint8Array(64000).fill(fill).buffer;return{sessionId:current.sessionId,partId:current.partId,seq,sampleStart:(seq-1)*32000,sampleCount:32000,sha256:await hash(pcm),pcm};};
  const firstSocket=await connectRecordingSocket(location.origin,current,identity.csrfToken,()=>true);const one=await chunk(1,1);check((await firstSocket.put(one)).highestContiguousSeq===1,'standard-browser-websocket-persists-first-frame');check((await firstSocket.put(one)).highestContiguousSeq===1,'exact-binary-replay-has-one-receipt');
  await reject(()=>sdk.acquireWriter(current,crypto.randomUUID(),crypto.randomUUID()),'competing-collector-cannot-steal-live-lease');
  await reject(()=>connectRecordingSocket(location.origin,current,'A'.repeat(43),()=>true,{timeoutMs:2000}),'wrong-csrf-subprotocol-cannot-open-stream');
  current=await sdk.acquireWriter(current,collector,crypto.randomUUID());check(current.writerEpoch===3,'same-collector-reconnect-advances-writer-epoch');const two=await chunk(2,2);await reject(()=>firstSocket.put(two),'old-socket-cannot-ack-after-new-claim');check((await sdk.ack(current)).highestContiguousSeq===1,'rejected-old-frame-did-not-advance-ack');
  const secondSocket=await connectRecordingSocket(location.origin,current,identity.csrfToken,()=>true);check((await secondSocket.put(two)).highestContiguousSeq===2,'new-socket-resends-original-unconfirmed-bytes');await sdk.pause(current);const old=current;current=await sdk.nextPart(current);check(current.writerEpoch===4&&current.partId!==first,'paused-device-reopen-issues-new-part-and-epoch');
  const thirdSocket=await connectRecordingSocket(location.origin,current,identity.csrfToken,()=>true);secondSocket.close();const three=await chunk(1,3);check((await thirdSocket.put(three)).highestContiguousSeq===1,'late-old-close-cannot-release-new-part-socket');
  await sdk.renewWriter(current);const finish=await sdk.finish(current,{[first]:2,[current.partId]:1});thirdSocket.close();check(Boolean(finish.archiveTaskId),'finished-pcm-admits-real-archive-task');
  const deadline=Date.now()+90000;let archive;for(;;){archive=await sdk.archive(current);if(archive.available)break;if(!['READY','RUNNING'].includes(archive.state)||Date.now()>deadline)throw Error('Archive not completed: '+archive.state);await new Promise(r=>setTimeout(r,200));}
  const response=await fetch(sdk.audioUrl(current)),wav=await response.arrayBuffer(),bytes=new Uint8Array(wav),view=new DataView(wav);check(response.status===200&&wav.byteLength===192044&&view.getUint32(24,true)===16000,'actual-file-backed-worker-registers-canonical-wav');for(const [index,source]of[one,two,three].entries())check(await hash(bytes.slice(44+index*64000,44+(index+1)*64000))===source.sha256,'archive-verifies-browser-chunk-'+(index+1));
  const range=await fetch(sdk.audioUrl(current),{headers:{Range:'bytes=44-64043'}});check(range.status===206&&await hash(await range.arrayBuffer())===one.sha256,'same-origin-cookie-range-playback');
  return{checks,meetingId:meeting.id,sessionId:current.sessionId,taskId:finish.archiveTaskId,backendId:current.backendId,writerEpoch:current.writerEpoch,parts:[first,current.partId],wav:{bytes:wav.byteLength,sampleCount:96000,sha256:await hash(wav),chunkSha256:[one.sha256,two.sha256,three.sha256]}};
 });
 assert.equal(errors.length,0,errors.join('\n'));const state=JSON.parse(await readFile('.tools/local-app/state.json','utf8'));const source=spawnSync('python3',['-c','import sys;sys.path.insert(0,"tools/dev");import local_app;print(local_app.source_signature())'],{encoding:'utf8'});assert.equal(source.status,0);
 await writeFile(`${output}/report.json`,JSON.stringify({pass:true,base,...proof,errors,amaDeployedSourceSignature:state.sourceSignature,amaWorkingSourceSignature:source.stdout.trim(),qualification:'Actual fixed-port sibling CAS, product SDK, standard browser WebSocket and actual file-backed durable PCM/archive; synthetic PCM, microphone/device and real speech-model accuracy not asserted'},null,2));console.log('PASS '+proof.checks.length+' real CAS/browser WebSocket/archive checks');
}catch(error){await writeFile(`${output}/report.json`,JSON.stringify({pass:false,base,proof,errors,error:String(error)},null,2));throw error;}finally{await browser.close();}
