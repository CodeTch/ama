import {computed,ref,shallowRef,shallowReadonly} from 'vue';
import type {Session} from '../../shared/api/business-client';
import {ApiError} from '../../shared/api/client';
import {createRecordingApi,recordingUuid,type RecordingSession} from './recording-api';
import {createPcmJournal,type PcmRecord} from './pcm-journal';
import {openPcmCapture,type PcmCapture} from './browser-pcm-capture';
import {createPcmPipeline} from './pcm-pipeline';
import {connectRecordingSocket,type RecordingSocket} from './recording-socket';
import {copyChunk,chunkKey,type Chunk} from './chunk-store';
type Sdk=ReturnType<typeof createRecordingApi>;
type Dependencies={journal?:(actor:string)=>ReturnType<typeof createPcmJournal>;capture?:typeof openPcmCapture;connect?:typeof connectRecordingSocket;waitForRetry?:(milliseconds:number)=>Promise<void>};
/** Application lifetime owns capture. Route disposal cannot stop it or erase unconfirmed audio. */
export function usePcmRecorder(sdk:Sdk,identity:()=>Session|null,collectorId:string,dependencies:Dependencies={}){
 const raw=shallowRef<PcmRecord|null>(null),phase=ref('IDLE'),error=ref<string|null>(null),connected=ref(false),pendingCount=ref(0),pendingBytes=ref(0),meetingId=ref<string|null>(null);
 let owner:string|null=null,journal:ReturnType<typeof createPcmJournal>|null=null,device:PcmCapture|null=null,pipeline:ReturnType<typeof createPcmPipeline>|null=null,socket:RecordingSocket|null=null,turn=0,busy=false,disposed=false,draining:Promise<void>|null=null,request:AbortController|null=null,renewTimer:ReturnType<typeof setTimeout>|null=null;
 let captureVersion=0,transportChanging=false;
 let disk:Promise<unknown>=Promise.resolve();const receipts=new Map<string,Chunk>();
 function local<T>(work:()=>Promise<T>){const result=disk.then(work);disk=result.then(()=>{},()=>{});return result;}
 function actor(){return identity()?.actor.enterpriseId??null;}
 function current(){if(disposed||!journal||!owner||actor()!==owner)throw new DOMException('录音身份已变化','AbortError');return journal;}
 function active(generation:number){return !disposed&&generation===turn&&owner!==null&&actor()===owner;}
 function check(generation:number){if(!active(generation))throw new DOMException('录音会话已变化','AbortError');}
 async function refresh(){const store=current();if(raw.value){const loaded=await store.load(raw.value.session.sessionId);if(!loaded)throw Error('录音缓存记录不可用');raw.value=loaded;}}
 async function recount(){let count=0,bytes=0;if(raw.value)for await(const c of current().pending(raw.value.session.sessionId)){count++;bytes+=c.pcm.byteLength;}pendingCount.value=count;pendingBytes.value=bytes;}
 async function command<T>(work:()=>Promise<T>){if(busy)throw Error('录音操作仍在进行');busy=true;error.value=null;try{return await work();}catch(cause){error.value=cause instanceof Error?cause.message:'录音操作未完成，音频已保留';if(['SAVING','AUTHORIZING'].includes(phase.value))phase.value='ERROR';if(denied(cause))await failed(cause);throw cause;}finally{busy=false;}}
 function closeSocket(){socket?.close();socket=null;connected.value=false;}
 async function stopDevice(){request?.abort();request=null;const old=device;device=null;if(old)await old.release();}
 async function failed(cause:unknown){if(!owner||actor()!==owner)return;phase.value='ERROR';error.value=cause instanceof Error?cause.message:'录音已中断，音频保留';closeSocket();await stopDevice();}
 function denied(cause:unknown){return cause instanceof ApiError&&[401,403,409].includes(cause.status);}
 function scheduleRenew(){
  if(renewTimer)clearTimeout(renewTimer);const generation=turn;
  renewTimer=setTimeout(async()=>{
   renewTimer=null;const expected=raw.value?{...raw.value.session}:null;
   if(!expected||!active(generation)||raw.value?.finished)return;
   const matches=()=>active(generation)&&raw.value?.session.sessionId===expected.sessionId&&raw.value.session.partId===expected.partId&&raw.value.session.writerEpoch===expected.writerEpoch&&raw.value.session.collectorId===expected.collectorId;
   try{
    const renewed=await sdk.renewWriter(expected);if(!matches())return;
    await local(async()=>{if(!matches())return;await current().updateSession(renewed);await refresh();});
   }catch(cause){if(matches()){if(denied(cause))await failed(cause);else{connected.value=false;error.value='连接暂不可用，未确认音频保留在本地；请恢复上传';}}}
   if(matches()&&phase.value!=='ERROR')scheduleRenew();
  },10000);
 }
 async function bind(){const session=raw.value?.session,generation=turn;if(!session)throw Error('尚无录音采集会话');closeSocket();const who=identity();if(!who)throw Error('请先登录');try{const opened=await(dependencies.connect??connectRecordingSocket)(globalThis.location?.origin??'http://localhost',session,who.csrfToken,()=>active(generation));if(!active(generation)){opened.close();check(generation);}socket=opened;connected.value=true;}catch(cause){if(denied(cause))throw cause;check(generation);socket=null;connected.value=false;}}
 function drain():Promise<void>{
  if(draining)return draining;if(transportChanging)return Promise.resolve();
  const generation=turn,session=raw.value?{...raw.value.session}:null;if(!session)return Promise.resolve();
  const run=async()=>{try{
   let observed:number;
   do{
    observed=captureVersion;
    for await(const value of current().pending(session.sessionId)){
     check(generation);if(transportChanging)return;
     if(value.partId!==session.partId)throw Error('原设备仍有未确认分段，请保留缓存并核对原录音');
     const chunk=copyChunk(value);let ack;
     for(let retries=0;;){
      try{ack=socket?await socket.put(chunk):await sdk.put(session,chunk);break;}
      catch(cause){
       closeSocket();if(!(cause instanceof ApiError)||cause.status!==429||retries++>=10)throw cause;
       error.value='录音上传暂时受限，音频保留在本地，正在等待重试';
       await(dependencies.waitForRetry??(milliseconds=>new Promise<void>(resolve=>setTimeout(resolve,milliseconds))))(1000);
       check(generation);if(transportChanging)return;
      }
     }
     check(generation);receipts.set(chunkKey(chunk),chunk);
     await local(async()=>{check(generation);for(const[key,receipt]of receipts)if(receipt.partId===chunk.partId&&receipt.seq<=ack.highestContiguousSeq){await current().removeConfirmed(receipt);receipts.delete(key);}await recount();});
     connected.value=true;
    }
   }while(active(generation)&&!transportChanging&&captureVersion!==observed);
   if(active(generation))error.value=null;
  }catch(cause){if(active(generation)){connected.value=false;error.value=cause instanceof Error?cause.message:'录音未确认，已保留本地音频';if(denied(cause))await failed(cause);}throw cause;}};
  const flight=run();draining=flight;void flight.then(()=>{if(draining===flight)draining=null;},()=>{if(draining===flight)draining=null;});return flight;
 }
 function uploadLater(){void drain().catch(()=>{});}
 async function claim(){
  if(!raw.value)throw Error('尚无录音会话');const generation=turn;transportChanging=true;
  try{
   await draining?.catch(()=>{});check(generation);closeSocket();
   const latest=await sdk.current(raw.value.session.sessionId);check(generation);
   const claimed=await sdk.acquireWriter(latest,collectorId,crypto.randomUUID());check(generation);
   await local(async()=>{check(generation);await current().updateSession(claimed);await refresh();});
  }finally{transportChanging=false;}
  scheduleRenew();await bind();
 }
 async function recover(){await claim();await sdk.resume(raw.value!.session);await drain();if(pendingCount.value)throw Error('仍有未确认音频，请先恢复上传');if(phase.value!=='RECORDING')await sdk.pause(raw.value!.session);}
 async function attach(id:string){if(!recordingUuid(id))throw new TypeError('会议标识无效');return command(async()=>{if(meetingId.value===id&&owner===actor())return;if(device||busy&&phase.value==='RECORDING')throw Error('请先结束当前录音');const who=actor();if(!who)throw Error('请先登录');await invalidate();owner=who;meetingId.value=id;journal=(dependencies.journal??createPcmJournal)(who);const generation=turn;const saved=await journal.latest(id);check(generation);raw.value=saved;await recount();phase.value=saved?.finished?'FINISHED':saved?'INTERRUPTED':'IDLE';});}
 async function restore(){const who=actor();if(!who)return null;const store=(dependencies.journal??createPcmJournal)(who);try{const saved=await store.latest();if(actor()!==who)throw new DOMException('录音身份已变化','AbortError');if(saved)await attach(saved.meetingId);return saved;}finally{if(store!==journal)store.close();}}
 async function initialize(){if(raw.value)return;const id=meetingId.value;if(!id)throw Error('请先创建会议');const generation=turn;const issued=await sdk.create(id);check(generation);const claimed=await sdk.acquireWriter(issued,collectorId,crypto.randomUUID());check(generation);const manifest=await sdk.manifest(claimed);check(generation);if(manifest.finished||manifest.parts.some(p=>p.highestContiguousSeq!==p.highestReceivedSeq))throw Error('原录音存在缺口，请使用原设备恢复上传');await current().begin(id,claimed,manifest.parts.map(p=>({partId:p.partId,finalSequence:p.highestReceivedSeq,sampleCount:p.sampleCount})));raw.value=await current().load(issued.sessionId);await recount();scheduleRenew();await bind();}
 async function start(){return command(async()=>{current();if(device||raw.value?.finished)throw Error('当前不可开启录音');if(phase.value==='ERROR')throw Error('请先重试保存未确认音频');phase.value='AUTHORIZING';try{const existed=Boolean(raw.value);await initialize();if(existed)await recover();const session=raw.value!.session;if(existed||raw.value!.parts.some(p=>p.finalSequence>0)){await sdk.pause(session);const next=await sdk.nextPart(session);await local(async()=>{await current().updateSession(next);await refresh();});scheduleRenew();await bind();}const generation=turn;request=new AbortController();const opened=await(dependencies.capture??openPcmCapture)(async input=>{check(generation);try{if(!pipeline)throw Error('录音采样管线尚未就绪');await pipeline.process(input);}catch(cause){await failed(cause);throw cause;}},cause=>{if(active(generation))void failed(cause);},request.signal);if(!active(generation)){await opened.release();check(generation);}device=opened;pipeline=createPcmPipeline(opened.sampleRate,async pcm=>{check(generation);const captured={...raw.value!.session},bytes=pcm.slice(0),sha256=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),b=>b.toString(16).padStart(2,'0')).join('');check(generation);await local(async()=>{check(generation);if(raw.value!.session.partId!==captured.partId)throw Error('录音分段已变化');const p=raw.value!.parts.at(-1)!;const chunk={sessionId:captured.sessionId,partId:captured.partId,seq:p.finalSequence+1,sampleStart:p.sampleCount,sampleCount:bytes.byteLength/2,sha256,pcm:bytes};await current().put(chunk);await refresh();pendingCount.value++;pendingBytes.value+=bytes.byteLength;captureVersion++;});uploadLater();});await opened.resume();check(generation);phase.value='RECORDING';}catch(cause){await failed(cause);throw cause;}});}
 async function pause(){return command(async()=>{current();if(phase.value!=='RECORDING'||!device)return;phase.value='SAVING';await device.pause();await pipeline?.flush();try{await drain();if(!pendingCount.value)await sdk.pause(raw.value!.session);}catch(cause){if(denied(cause)){await failed(cause);throw cause;}error.value='服务端暂停未确认，音频已保留；请恢复连接';}phase.value='PAUSED';});}
 async function resume(){return command(async()=>{current();if(phase.value!=='PAUSED'||!device)throw Error('请开启新的录音分段');await sdk.resume(raw.value!.session);await device.resume();phase.value='RECORDING';});}
 async function interrupt(){return command(async()=>{current();if(device){phase.value='SAVING';await device.pause();await pipeline?.flush();await stopDevice();}await drain();if(pendingCount.value)throw Error('请先恢复未确认音频');await sdk.pause(raw.value!.session);phase.value='INTERRUPTED';});}
 async function reconnect(){return command(async()=>{current();await recover();});}
 async function recoverSealed(){
  const generation=turn;if(!raw.value)return false;const latest=await sdk.current(raw.value.session.sessionId);check(generation);const manifest=await sdk.manifest(latest);check(generation);if(!manifest.finished)return false;
  if(pendingCount.value||pipeline&&pipeline.samples()!==raw.value.parts.at(-1)!.sampleCount||manifest.parts.length!==raw.value.parts.length||manifest.parts.some((p,i)=>{const local=raw.value!.parts[i]!;return p.partId!==local.partId||p.highestContiguousSeq!==local.finalSequence||p.highestReceivedSeq!==local.finalSequence||p.sampleCount!==local.sampleCount;}))throw Error('服务端录音已结束，本机仍有未确认范围，请保留原音频');
  const accepted=await sdk.finish({...latest,collectorId:raw.value.session.collectorId},Object.fromEntries(raw.value.parts.map(p=>[p.partId,p.finalSequence])));check(generation);
  await local(async()=>{check(generation);await current().finish(latest.sessionId,accepted.archiveTaskId);await refresh();});await stopDevice();closeSocket();if(renewTimer)clearTimeout(renewTimer);renewTimer=null;phase.value='FINISHED';return true;
 }
 async function retry(){return command(async()=>{current();if(await recoverSealed())return;await claim();await sdk.resume(raw.value!.session);await drain();await pipeline?.retry();await pipeline?.flush();await drain();if(pendingCount.value)throw Error('仍有未确认音频，请恢复上传');await sdk.pause(raw.value!.session);phase.value=device?'PAUSED':'INTERRUPTED';});}
 async function finish(){return command(async()=>{current();if(!raw.value)throw Error('尚无录音可保存');if(device){phase.value='SAVING';await device.pause();await pipeline?.flush();await stopDevice();}await drain();if(pendingCount.value)throw Error('仍有未确认音频，请恢复上传');const finals=Object.fromEntries(raw.value.parts.map(p=>[p.partId,p.finalSequence]));const accepted=await sdk.finish(raw.value.session,finals);await local(async()=>{await current().finish(raw.value!.session.sessionId,accepted.archiveTaskId);await refresh();});phase.value='FINISHED';closeSocket();if(renewTimer)clearTimeout(renewTimer);renewTimer=null;return accepted;});}
 async function invalidate(){turn++;if(renewTimer)clearTimeout(renewTimer);renewTimer=null;closeSocket();await stopDevice();await disk;receipts.clear();journal?.close();journal=null;owner=null;raw.value=null;pipeline=null;meetingId.value=null;phase.value='IDLE';error.value=null;pendingCount.value=0;pendingBytes.value=0;}
 async function dispose(){await invalidate();disposed=true;}
 const elapsed=computed(()=>(raw.value?.parts.reduce((sum,p)=>sum+p.sampleCount,0)??0)/16),partElapsed=computed(()=>(raw.value?.parts.at(-1)?.sampleCount??0)/16);
 return{record:shallowReadonly(raw),meetingId:shallowReadonly(meetingId),phase:shallowReadonly(phase),error:shallowReadonly(error),connected:shallowReadonly(connected),pendingCount:shallowReadonly(pendingCount),pendingBytes:shallowReadonly(pendingBytes),elapsed,partElapsed,attach,restore,start,pause,resume,interrupt,reconnect,retry,finish,invalidate,dispose};
}
