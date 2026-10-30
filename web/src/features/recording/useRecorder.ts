import {ref,shallowRef,computed,shallowReadonly} from 'vue';
import {RECORDING_BYTES,RECORDING_CHUNKS,RECORDING_PARTS,RECORDING_MS,type RecordingStore,type CaptureFactory,type Capture,type RecordingSession,type RecordingPart,type PartState} from './recording-types';
/** The application owns capture; route components only render it. Persisted checkpoints never claim an unsaved tail. */
export function useRecorder(store:RecordingStore,factory:CaptureFactory,actor:()=>string|null,authorize:(meeting:string)=>Promise<void>,now:()=>number=()=>performance.now()){
 const record=shallowRef<RecordingSession|null>(null),phase=ref('IDLE'),error=ref<string|null>(null),clock=ref(now());let local:RecordingSession|null=null,capture:Capture|null=null,liveId:string|null=null,started:number|null=null,epoch=0,disposed=false,opening=false,sequence=0,flushFlight:Promise<void>|null=null,completion:PartState|null=null,suppressData=false;
 let retained:RecordingSession|null=null,openingController:AbortController|null=null;
 let disk:Promise<unknown>=Promise.resolve();
 function ordered<T>(work:()=>Promise<T>):Promise<T>{const result=disk.then(work);disk=result.then(()=>{},()=>{});return result;}
 const pending:Array<{id:string;part:string;blob:Blob;elapsed:number}>=[];const clone=<T>(v:T):T=>structuredClone(v);
 const part=()=>local?.parts.find(p=>p.id===liveId)??null;
 function milliseconds(){const p=part();return p?Math.min(RECORDING_MS,p.elapsedMs+(started===null?0:Math.max(0,now()-started))):local?.parts.at(-1)?.elapsedMs??0;}
 const partElapsed=computed(()=>{clock.value;record.value;phase.value;return Math.floor(milliseconds());});
 const elapsed=computed(()=>{clock.value;record.value;phase.value;return Math.floor((local?.parts.reduce((sum,p)=>sum+(p.id===liveId?0:p.elapsedMs),0)??0)+(liveId?milliseconds():0));});
 function snapshot(value:RecordingSession){const result=clone(value),active=result.parts.find(p=>p.id===liveId);if(active)active.elapsedMs=Math.floor(milliseconds());return result;}
 function publish(){record.value=local?snapshot(local):null;clock.value=now();}
 function current(){if(disposed||!local||!actor()||local.actor!==actor())throw new DOMException('录音会话已变化','AbortError');return local;}
 function freeze(){const p=part();if(p){p.elapsedMs=Math.floor(milliseconds());started=null;}clock.value=now();}
 function failed(){freeze();if(capture?.state()==='recording')capture.pause();phase.value='ERROR';error.value='本地保存失败，音频仍保留在当前页面，请重试保存。';publish();}
 async function persist(){const value=current(),turn=epoch;try{await ordered(async()=>{if(turn!==epoch)throw new DOMException('录音会话已变化','AbortError');await store.save(snapshot(value));});if(turn!==epoch)throw new DOMException('录音会话已变化','AbortError');}catch(cause){if(turn===epoch)failed();throw cause;}}
 function flush():Promise<void>{
  if(flushFlight)return flushFlight;const turn=epoch;
  const run=async()=>{try{while(pending.length){const item=pending[0]!;await ordered(async()=>{const value=current(),next=snapshot(value),p=next.parts.find(p=>p.id===item.part);if(!p||p.chunks.includes(item.id))throw Error('录音分片顺序已变化');p.chunks.push(item.id);p.bytes+=item.blob.size;p.savedElapsedMs=Math.max(p.savedElapsedMs,item.elapsed);await store.append(next,item.id,item.blob);if(turn!==epoch)throw new DOMException('录音会话已变化','AbortError');const live=value.parts.find(p=>p.id===item.part)!;live.chunks=p.chunks;live.bytes=p.bytes;live.savedElapsedMs=p.savedElapsedMs;pending.shift();publish();});}}catch(cause){if(turn===epoch)failed();throw cause;}};
  const result=run();flushFlight=result;void result.then(()=>{if(flushFlight===result)flushFlight=null;},()=>{if(flushFlight===result)flushFlight=null;});return result;
 }
 function receive(blob:Blob,turn:number){
  if(turn!==epoch||disposed||suppressData||!blob.size)return;const p=part();if(!p)return;const bytes=(local?.parts.reduce((n,p)=>n+p.bytes,0)??0)+pending.reduce((n,p)=>n+p.blob.size,0),count=(local?.parts.reduce((n,p)=>n+p.chunks.length,0)??0)+pending.length;
  if(blob.size>RECORDING_BYTES-bytes||count>=RECORDING_CHUNKS){suppressData=true;freeze();completion='SYSTEM_INTERRUPTED';const old=capture;capture=null;void old?.stop().catch(()=>{}).finally(()=>old.release());phase.value='ERROR';p.incomplete=true;error.value='录音达到处理限额，请重试保存可恢复范围，再结束当前会议。';publish();return;}
  pending.push({id:p.id+':'+(++sequence),part:p.id,blob,elapsed:Math.floor(milliseconds())});void flush().catch(()=>{});
 }
 async function attach(meeting:string){
  if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(meeting))throw Error('会议编号无效');if(local?.meeting===meeting&&local.actor===actor())return;if(capture||opening)throw Error('请先结束当前录音');const who=actor();if(!who)throw Error('请先登录');const turn=++epoch;await authorize(meeting);if(turn!==epoch||who!==actor())throw new DOMException('录音会话已变化','AbortError');const previous=await store.load(who,meeting);if(turn!==epoch||who!==actor())throw new DOMException('录音会话已变化','AbortError');local=previous??{schema:1,actor:who,meeting,finished:false,parts:[]};liveId=null;started=null;pending.length=0;completion=null;suppressData=false;error.value=null;
  for(const p of local.parts)if(['RECORDING','PAUSED','SAVING'].includes(p.state)){p.elapsedMs=p.savedElapsedMs;p.state='SYSTEM_INTERRUPTED';p.incomplete=true;error.value='录音已中断，仅保留已落盘范围，尾段可能缺失。';}
  phase.value=local.finished?'FINISHED':local.parts.length?'INTERRUPTED':'IDLE';await persist();publish();
 }
 async function start(){
  const value=current();if(opening||capture||value.finished||phase.value==='SAVING'||phase.value==='ERROR'&&pending.length)throw Error('请先处理当前录音');if(value.parts.length>=RECORDING_PARTS||value.parts.reduce((n,p)=>n+p.bytes,0)>=RECORDING_BYTES||elapsed.value>=RECORDING_MS)throw Error('录音达到当前处理限额，请结束并保存');const turn=epoch,request=new AbortController();openingController=request;opening=true;phase.value='AUTHORIZING';error.value=null;
  let device:Capture|null=null;try{await authorize(value.meeting);if(turn!==epoch||actor()!==value.actor)throw new DOMException('录音会话已变化','AbortError');device=await factory(blob=>receive(blob,turn),()=>{if(turn===epoch&&capture)void finishPart('SYSTEM_INTERRUPTED').catch(()=>{});},request.signal);if(turn!==epoch||actor()!==value.actor){throw new DOMException('录音会话已变化','AbortError');}
   const extension=device.mime.includes('mp4')?'m4a':device.mime.includes('webm')?'webm':null;if(!extension){throw Error('当前浏览器没有受支持的录音编码');}
   const index=value.parts.length+1,id=crypto.randomUUID();value.parts.push({id,index,name:`录音片段 ${String(index).padStart(2,'0')}.${extension}`,mime:device.mime,state:'RECORDING',elapsedMs:0,savedElapsedMs:0,bytes:0,chunks:[],incomplete:false});liveId=id;capture=device;sequence=0;suppressData=false;completion=null;await persist();device.start();started=now();phase.value='RECORDING';publish();
  }catch(cause){device?.release();if(turn===epoch){const failedPart=value.parts.find(p=>p.id===liveId);if(failedPart){failedPart.state='SYSTEM_INTERRUPTED';failedPart.incomplete=true;failedPart.elapsedMs=failedPart.savedElapsedMs;}liveId=null;capture=null;started=null;phase.value='ERROR';error.value=cause instanceof DOMException&&cause.name==='NotAllowedError'?'无法访问麦克风，请允许麦克风权限并检查设备。':cause instanceof Error?cause.message:'无法启动录音，请检查麦克风。';publish();}throw cause;}finally{if(openingController===request)openingController=null;if(turn===epoch)opening=false;}
 }
 function pause(){current();if(capture?.state()!=='recording')return;freeze();capture.pause();const p=part();if(p)p.state='PAUSED';phase.value='PAUSED';capture.requestData();publish();void persist().catch(()=>{});}
 async function resume(){const value=current();if(phase.value!=='PAUSED'||capture?.state()!=='paused')return;const turn=epoch,device=capture;phase.value='AUTHORIZING';try{await authorize(value.meeting);if(turn!==epoch||actor()!==value.actor||capture!==device)throw new DOMException('录音会话已变化','AbortError');device.resume();started=now();const p=part();if(p)p.state='RECORDING';phase.value='RECORDING';await persist();publish();}catch(cause){if(turn===epoch){freeze();phase.value='ERROR';error.value=cause instanceof Error?cause.message:'当前会议不允许继续录音';publish();}throw cause;}}

 async function complete(){const p=part();if(!p||!completion)return;p.state=completion;if(completion==='SYSTEM_INTERRUPTED'){p.incomplete=true;p.elapsedMs=p.savedElapsedMs;}await persist();completion=null;liveId=null;started=null;phase.value='INTERRUPTED';error.value=null;publish();}
 async function finishPart(reason:PartState){
  current();if(phase.value==='ERROR')throw Error('请先重试保存');if(!liveId)return;freeze();completion=reason;phase.value='SAVING';const p=part();if(p)p.state='SAVING';publish();const old=capture;capture=null;
  try{if(old)await old.stop();await flush();await complete();}catch(cause){failed();throw cause;}finally{old?.release();}
 }
 async function interrupt(){await finishPart('INTERRUPTED');}
 async function end(){if(!local?.parts.length)throw Error('尚无录音可保存');await finishPart('SAVED');}
 async function retry(){current();if(phase.value!=='ERROR')return;error.value=null;phase.value='SAVING';try{await flush();if(completion){await complete();return;}const p=part();if(p)p.state=capture?'PAUSED':'SYSTEM_INTERRUPTED';phase.value=capture?'PAUSED':'INTERRUPTED';await persist();publish();}catch(cause){failed();throw cause;}}
 async function files(){const value=current();if(capture||opening||pending.length||phase.value==='ERROR')throw Error('请先保存全部录音分片');const result:File[]=[];for(const p of value.parts)if(p.bytes>0)result.push(await store.file(clone(value),clone(p)));if(!result.length)throw Error('尚无可恢复的录音文件');return result;}
 async function finish(){const value=current();if(capture||pending.length||phase.value==='ERROR')throw Error('录音尚未完整保存');await authorize(value.meeting);value.finished=true;phase.value='FINISHED';await persist();publish();}
 async function invalidate(){if(local)retained=clone(local);epoch++;openingController?.abort();openingController=null;capture?.release();capture=null;local=null;liveId=null;started=null;opening=false;pending.length=0;completion=null;phase.value='IDLE';error.value=null;publish();}
 async function logout(){const previous=local?clone(local):retained;await invalidate();if(previous)await ordered(()=>store.clear(previous.actor,previous.meeting));retained=null;}
 function tick(){clock.value=now();if(capture?.state()==='recording'&&elapsed.value>=RECORDING_MS)void finishPart('SYSTEM_INTERRUPTED').catch(()=>{});}
 function dispose(){disposed=true;void invalidate();}
 return{record:shallowReadonly(record),phase:shallowReadonly(phase),error:shallowReadonly(error),partElapsed,elapsed,attach,start,pause,resume,interrupt,end,retry,tick,files,finish,invalidate,logout,dispose};
}
