import {test} from 'node:test';import assert from 'node:assert/strict';import {useRecorder} from '../../src/features/recording/useRecorder';import type {RecordingStore,RecordingSession,CaptureFactory,Capture} from '../../src/features/recording/recording-types';
const actor='owner',meeting='00000000-0000-4000-8000-000000000001';
function fixture(){let value:RecordingSession|null=null,fail=false,clock=0,emit:(blob:Blob)=>void=()=>{},lost:()=>void=()=>{},capture:Capture|null=null,released=0,requests=0,authorized=0;const blobs=new Map<string,Blob>();
 const copy=(v:RecordingSession)=>structuredClone(v);const store:RecordingStore={async load(){return value&&copy(value);},async save(v){if(fail)throw Error('quota');value=copy(v);},async append(v,id,blob){if(fail)throw Error('quota');blobs.set(id,blob);value=copy(v);},async file(v,p){const chunks=p.chunks.map(id=>{const blob=blobs.get(id);assert(blob);return blob;});return new File(chunks,p.name,{type:p.mime});},async clear(){value=null;blobs.clear();}};
 const factory:CaptureFactory=async(data,onLost)=>{requests++;emit=data;lost=onLost;let state:'recording'|'paused'|'inactive'='inactive';capture={mime:'audio/webm;codecs=opus',state:()=>state,start(){state='recording';},pause(){state='paused';},resume(){state='recording';},requestData(){},async stop(){state='inactive';emit(new Blob(['tail']));},release(){released++;state='inactive';}};return capture;};
 const controller=useRecorder(store,factory,()=>actor,async m=>{assert.equal(m,meeting);authorized++;},()=>clock);
 return{controller,store,advance(n:number){clock+=n;controller.tick();},emit(text='chunk'){emit(new Blob([text]));},fail(v:boolean){fail=v;},lost(){lost();},get released(){return released;},get requests(){return requests;},get authorized(){return authorized;},get saved(){return value;}};
}
const flush=()=>new Promise<void>(resolve=>setImmediate(resolve));
test('capture requires a user start; pause time is excluded and interrupt produces ordered original files',async()=>{
 const f=fixture(),r=f.controller;try{await r.attach(meeting);assert.equal(f.requests,0);await r.start();f.advance(1250);assert.equal(r.partElapsed.value,1250);r.pause();f.advance(5000);assert.equal(r.partElapsed.value,1250);await r.resume();f.advance(750);f.emit();await flush();await r.interrupt();assert.equal(r.phase.value,'INTERRUPTED');assert.equal(r.record.value?.parts.length,1);assert.equal(r.record.value?.parts[0]?.elapsedMs,2000);assert.equal(f.released,1);await r.start();f.advance(1000);f.emit('second');await flush();await r.end();const files=await r.files();assert.equal(files.length,2);assert.deepEqual(files.map(f=>f.name),['录音片段 01.webm','录音片段 02.webm']);assert.equal(await files[0]!.text(),'chunktail');assert.equal(await files[1]!.text(),'secondtail');assert.equal(r.elapsed.value,3000);assert(f.authorized>=3);}finally{r.dispose();}
});
test('a failed local write retains the actual chunk for retry and does not claim it was saved',async()=>{
 const f=fixture(),r=f.controller;try{await r.attach(meeting);await r.start();f.advance(1200);f.fail(true);f.emit('retained');await flush();assert.equal(r.phase.value,'ERROR');assert.equal(r.record.value?.parts[0]?.chunks.length,0);assert.match(r.error.value??'',/保存/);f.fail(false);await r.retry();assert.equal(r.phase.value,'PAUSED');assert.equal(r.record.value?.parts[0]?.chunks.length,1);await r.end();assert.equal(await (await r.files())[0]!.text(),'retainedtail');}finally{r.dispose();}
});
test('refresh restores only persisted ranges and never requests a microphone or invents missing audio',async()=>{
 const f=fixture(),r=f.controller;await r.attach(meeting);await r.start();f.advance(1500);f.emit('persisted');await flush();f.advance(1200);r.dispose();let requested=0;const restored=useRecorder(f.store,async()=>{requested++;throw Error('no device');},()=>actor,async()=>{},()=>0);
 try{await restored.attach(meeting);assert.equal(requested,0);assert.equal(restored.phase.value,'INTERRUPTED');assert.equal(restored.record.value?.parts[0]?.incomplete,true);assert.equal(restored.record.value?.parts[0]?.elapsedMs,1500);assert.equal(await (await restored.files())[0]!.text(),'persisted');}finally{restored.dispose();}
});
test('microphone rejection preserves the meeting without a fake audio part',async()=>{
 const f=fixture(),r=useRecorder(f.store,async()=>{throw new DOMException('denied','NotAllowedError');},()=>actor,async()=>{},()=>0);try{await r.attach(meeting);await assert.rejects(r.start());assert.equal(r.record.value?.parts.length,0);assert.equal(r.phase.value,'ERROR');assert.match(r.error.value??'',/麦克风/);}finally{r.dispose();}
});
test('pause metadata cannot overwrite a chunk whose transaction committed before its completion callback',async()=>{
 const f=fixture(),r=f.controller,append=f.store.append;let release:()=>void=()=>{};const gate=new Promise<void>(resolve=>{release=resolve;});f.store.append=async(...args)=>{await append(...args);await gate;};
 try{await r.attach(meeting);await r.start();f.advance(1000);f.emit('committed');await flush();r.pause();release();await flush();assert.equal(f.saved?.parts[0]?.chunks.length,1);assert.equal(f.saved?.parts[0]?.bytes,9);}finally{r.dispose();}
});
test('a changed actor before permission acquisition cannot request a device',async()=>{
 const f=fixture();let who:string|null='owner',authorized:()=>void=()=>{},requests=0;let ready=false;const r=useRecorder(f.store,async()=>{requests++;throw Error('must not acquire');},()=>who,async()=>{if(ready)await new Promise<void>(resolve=>{authorized=resolve;});},()=>0);
 try{await r.attach(meeting);ready=true;const pending=r.start();who=null;authorized();await assert.rejects(pending);assert.equal(requests,0);}finally{r.dispose();}
});
test('logout after invalidation releases hardware and clears its own persisted recording',async()=>{
 const f=fixture(),r=f.controller;try{await r.attach(meeting);await r.start();f.advance(1200);f.emit('private');await flush();await r.invalidate();assert.equal(r.record.value,null);assert.equal(f.released,1);await r.logout();assert.equal(f.saved,null);}finally{r.dispose();}
});
test('resume requires fresh writable authorization and cannot restart a published meeting',async()=>{
 const f=fixture();let allowed=true,resumed=0;let state:'recording'|'paused'|'inactive'='inactive';const r=useRecorder(f.store,async()=>({mime:'audio/webm',state:()=>state,start(){state='recording';},pause(){state='paused';},resume(){resumed++;state='recording';},requestData(){},async stop(){},release(){}}),()=>actor,async()=>{if(!allowed)throw Error('published read-only');},()=>0);
 try{await r.attach(meeting);await r.start();r.pause();allowed=false;await assert.rejects(async()=>r.resume());assert.equal(resumed,0);}finally{r.dispose();}
});

test('invalidation aborts a pending device factory and releases a late stream before creating a part',async()=>{
 const f=fixture();let signal:AbortSignal|undefined,release:()=>void=()=>{},freed=0;const gate=new Promise<void>(resolve=>{release=resolve;});const r=useRecorder(f.store,async(_data,_lost,cancel)=>{signal=cancel;await gate;return{mime:'audio/webm',state:()=> 'inactive',start(){throw Error('late device must never start');},pause(){},resume(){},requestData(){},async stop(){},release(){freed++;}};},()=>actor,async()=>{},()=>0);let started:Promise<void>|null=null;
 try{await r.attach(meeting);started=r.start();await flush();await r.invalidate();assert.equal(signal?.aborted,true);release();await assert.rejects(started);assert.equal(r.record.value,null);assert.equal(freed,1);}finally{release();await started?.catch(()=>{});r.dispose();}
});

test('capture start failure after a durable checkpoint can recover without deleting the stored part identity',async()=>{
 const f=fixture(),original=f.store.save;f.store.save=async next=>{for(const old of f.saved?.parts??[])if(!next.parts.some(p=>p.id===old.id))throw Error('录音保存范围已变化');await original(next);};let attempts=0;
 const r=useRecorder(f.store,async()=>({mime:'audio/webm',state:()=> 'inactive',start(){if(++attempts===1)throw Error('device start failed');},pause(){},resume(){},requestData(){},async stop(){},release(){}}),()=>actor,async()=>{},()=>0);
 try{await r.attach(meeting);await assert.rejects(r.start(),/device start failed/);await r.retry();assert.equal(r.phase.value,'INTERRUPTED');await r.start();assert.equal(r.phase.value,'RECORDING');assert.equal(f.saved?.parts.length,2);assert.equal(f.saved?.parts[0]?.incomplete,true);assert.equal(f.saved?.parts[0]?.bytes,0);}finally{r.dispose();}
});
