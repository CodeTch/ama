import {createPcmJournal} from './pcm-journal';
import {createPcmPipeline} from './pcm-pipeline';
import type {RecordingSession} from './recording-api';

// The worklet's MessagePort is transferred here. No audio block or disk credit
// crosses the UI thread, including while Vue is rendering a long transcript.
const host=globalThis as unknown as {onmessage:((event:MessageEvent)=>void)|null;postMessage:(message:unknown)=>void};
let port:MessagePort|null=null,journal:ReturnType<typeof createPcmJournal>|null=null,pipeline:ReturnType<typeof createPcmPipeline>|null=null;
let session:RecordingSession|null=null,rate=0,lastUpdate=0,persisted=0,maximum=Infinity,limiting=false,limitReason='',pendingBudget=0,chunks=0;
let ordered:Promise<unknown>=Promise.resolve();
const pending=new Map<number,{resolve:()=>void;reject:(error:Error)=>void}>();let flushId=0;
function snapshot(force=false){const now=Date.now();if(!force&&now-lastUpdate<250)return;lastUpdate=now;host.postMessage({type:'SNAPSHOT',samples:pipeline?.samples()??0,persisted,pendingBudget});}
function fail(cause:unknown){host.postMessage({type:'ERROR',message:cause instanceof Error?cause.message:'本地录音保存失败'});}
function pause(){if(!port)throw Error('麦克风未连接');const id=++flushId;const promise=new Promise<void>((resolve,reject)=>pending.set(id,{resolve,reject}));port.postMessage({type:'PAUSE',flushId:id});return promise;}
function limit(reason:string){if(limiting)return;limiting=true;limitReason=reason;void pause().then(async()=>{await pipeline?.flush();snapshot(true);host.postMessage({type:'LIMIT',message:limitReason});}).catch(fail);}
async function configure(actor:string,value:RecordingSession){
 session=value;journal=createPcmJournal(actor);const record=await journal.load(value.sessionId);
 if(!record||record.session.partId!==value.partId||record.session.writerEpoch!==value.writerEpoch)throw Error('录音采集会话已变化');
 const part=record.parts.at(-1)!;persisted=part.finalSequence;
 chunks=record.parts.reduce((n,p)=>n+p.finalSequence,0);maximum=Math.min(115200000-record.parts.reduce((n,p)=>n+p.sampleCount,0),(4096-chunks)*32000);
 if(maximum<=0)throw Error('已达到录音时长或分片上限，请结束保存');
 if((await journal.stats(value.sessionId)).bytes>=19200000-64000)throw Error('本地未确认音频接近缓存上限，请先恢复上传');
 pipeline=createPcmPipeline(rate,async pcm=>{
  const current=await journal!.load(value.sessionId);if(!current||current.session.partId!==value.partId||current.session.writerEpoch<value.writerEpoch||current.session.collectorId!==value.collectorId)throw Error('录音采集权限已变化');
  const part=current.parts.at(-1)!,sha256=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',pcm)),b=>b.toString(16).padStart(2,'0')).join('');
  await journal!.put({sessionId:value.sessionId,partId:value.partId,seq:part.finalSequence+1,sampleStart:part.sampleCount,sampleCount:pcm.byteLength/2,sha256,pcm});
  persisted=part.finalSequence+1;chunks=current.parts.reduce((n,p)=>n+p.finalSequence,0)+1;pendingBudget=(await journal!.stats(value.sessionId)).bytes;snapshot(true);
 },{maxSamples:maximum});
}
function receive(message:any){
 if(message?.type==='FLUSHED'){pending.get(message.flushId)?.resolve();pending.delete(message.flushId);return;}
 if(message?.type==='OVERRUN'){fail(Error('本地保存无法跟上麦克风采集，已保存范围保留，请恢复保存'));return;}
 if(message?.type!=='PCM'||!(message.pcm instanceof ArrayBuffer)||message.sampleRate!==rate){fail(Error('麦克风返回无效分片'));return;}
 ordered=ordered.then(async()=>{
  if(!pipeline)throw Error('录音采样管线尚未就绪');await pipeline.process(new Float32Array(message.pcm));snapshot();
  if(pipeline.samples()>=maximum||chunks>=4095)limit('已达到录音时长或分片上限，采集已自动暂停，请结束保存');
  else if(pendingBudget>=19200000-64000)limit('未确认音频接近十分钟缓存上限，采集已自动暂停，请恢复上传后开启新段');
  port!.postMessage({type:'ACK'});
 }).catch(cause=>{fail(cause);});
}
host.onmessage=({data})=>{
 if(data.type==='CONNECT'){port=data.port;rate=data.sampleRate;port!.onmessage=event=>receive(event.data);port!.start();return;}
 const run=async()=>{
  switch(data.type){
   case 'CONFIGURE':await configure(data.actor,data.session);break;
   case 'RESUME':if(limiting)throw Error(limitReason);port!.postMessage({type:'RESUME'});break;
   case 'PAUSE':await pause();await ordered;break;
   case 'FLUSH':await ordered;await pipeline?.flush();break;
   case 'RETRY':await ordered;await pipeline?.retry();break;
   case 'DISCONNECT':port?.close();port=null;break;
   default:throw Error('未知录音采集命令');
  }
  snapshot(true);host.postMessage({id:data.id,ok:true});
 };
 void run().catch(cause=>host.postMessage({id:data.id,ok:false,message:cause instanceof Error?cause.message:'录音采集命令失败'}));
};
