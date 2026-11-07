import {PCM_WORKLET_PROCESSOR,PCM_WORKLET_SOURCE} from './pcm-worklet';
import type {RecordingSession} from './recording-api';
export type CapturePipeline={samples():number;flush():Promise<void>;retry():Promise<void>;process?:(input:Float32Array)=>Promise<void>;dispose?():void};
export type PersistentCapture={actor:string;session:RecordingSession;snapshot:(value:{samples:number;persisted:number;pendingBudget:number})=>void;limit:(message:string)=>void};
export type PcmCapture={sampleRate:number;resume():Promise<void>;pause():Promise<void>;release():Promise<void>;released():boolean;configure?:(options:PersistentCapture)=>Promise<void>;pipeline?:CapturePipeline};
/** Requested explicitly by the application. Native worklet credit waits only for bounded local persistence. */
export async function openPcmCapture(data:(input:Float32Array)=>Promise<void>,lost:(cause:Error)=>void,signal?:AbortSignal,persistent?:boolean):Promise<PcmCapture>{
 const cancelled=()=>{if(signal?.aborted)throw new DOMException('麦克风请求已取消','AbortError');};cancelled();
 if(!globalThis.isSecureContext||!navigator.mediaDevices?.getUserMedia||typeof AudioContext==='undefined')throw Error('当前浏览器无法使用安全麦克风录音，请导入音频文件');
 const context=new AudioContext({latencyHint:'interactive'});let stream:MediaStream|null=null,node:AudioWorkletNode|null=null,source:MediaStreamAudioSourceNode|null=null,gain:GainNode|null=null,closed=false,running=false,id=0;
 let worker:Worker|null=null,configuration:PersistentCapture|null=null,workerSamples=0;const calls=new Map<number,{resolve:()=>void;reject:(cause:Error)=>void;timer:ReturnType<typeof setTimeout>}>();
 function rpc(type:string,extra:Record<string,unknown>={}){const key=++id;return new Promise<void>((resolve,reject)=>{calls.set(key,{resolve,reject,timer:setTimeout(()=>{calls.delete(key);reject(Error('录音采集命令超时，已保存范围保留'));},10000)});worker!.postMessage({type,id:key,...extra});});}
 function disposeWorker(){worker?.terminate();worker=null;for(const call of calls.values()){clearTimeout(call.timer);call.reject(Error('录音采集已释放'));}calls.clear();}
 let flushing:{id:number;promise:Promise<void>;resolve:()=>void;reject:(cause:Error)=>void;timer:ReturnType<typeof setTimeout>}|null=null;
 const abort=()=>{void release();};
 async function release(){for(const track of stream?.getTracks()??[])track.stop();if(closed)return;closed=true;running=false;signal?.removeEventListener('abort',abort);if(flushing){clearTimeout(flushing.timer);flushing.reject(Error('麦克风已释放，尾段未确认'));flushing=null;}if(worker)void rpc('DISCONNECT').catch(()=>{});if(node){node.port.onmessage=null;node.port.close();node.disconnect();}source?.disconnect();gain?.disconnect();for(const track of stream?.getTracks()??[]){track.removeEventListener('ended',ended);track.removeEventListener('mute',ended);track.stop();}context.removeEventListener('statechange',stateChanged);await context.close().catch(()=>{});}
 function fail(cause:Error){if(closed)return;void release();lost(cause);}
 function ended(){fail(Error('麦克风已中断，已保存范围保留，继续时请开启新段'));}
 function stateChanged(){if(running&&context.state!=='running')ended();}
 signal?.addEventListener('abort',abort,{once:true});
 try{
  const acquiring=navigator.mediaDevices.getUserMedia({audio:{channelCount:{ideal:1},echoCancellation:false,noiseSuppression:false,autoGainControl:false}});
  // Browsers do not abort their permission prompt. Cancel logically now and stop
  // any stream that arrives after cancellation without waiting for that prompt.
  void acquiring.then(value=>{if(closed||signal?.aborted)for(const track of value.getTracks())track.stop();},()=>{});
  let cancelRequest:()=>void=()=>{};
  try{stream=await Promise.race([acquiring,new Promise<never>((_,reject)=>{cancelRequest=()=>reject(new DOMException('麦克风请求已取消','AbortError'));signal?.addEventListener('abort',cancelRequest,{once:true});if(signal?.aborted)cancelRequest();})]);}
  finally{signal?.removeEventListener('abort',cancelRequest);}cancelled();
  if(!context.audioWorklet)throw Error('当前浏览器不支持 AudioWorklet 录音');
  const url=URL.createObjectURL(new Blob([PCM_WORKLET_SOURCE],{type:'text/javascript'}));try{await context.audioWorklet.addModule(url);}finally{URL.revokeObjectURL(url);}cancelled();
  source=context.createMediaStreamSource(stream);node=new AudioWorkletNode(context,PCM_WORKLET_PROCESSOR,{numberOfInputs:1,numberOfOutputs:1,outputChannelCount:[1]});gain=context.createGain();gain.gain.value=0;source.connect(node);node.connect(gain);gain.connect(context.destination);
  if(persistent){
   worker=new Worker(new URL('./pcm-capture-worker.ts',import.meta.url),{type:'module',name:'ama-recording-persistence'});
   worker.onmessage=({data:message})=>{if(message.type==='SNAPSHOT'){workerSamples=message.samples;configuration?.snapshot(message);return;}if(message.type==='ERROR'){fail(Error(message.message));return;}if(message.type==='LIMIT'){running=false;configuration?.limit(message.message);return;}const call=calls.get(message.id);if(call){calls.delete(message.id);clearTimeout(call.timer);message.ok?call.resolve():call.reject(Error(message.message));}};
   worker.onerror=()=>{const cause=Error('录音保存线程异常，已保存范围保留，尾段完整性未知；请恢复保存');cause.name='PcmWorkerError';fail(cause);};
   worker.postMessage({type:'CONNECT',port:node.port,sampleRate:context.sampleRate},[node.port]);
  }else node.port.onmessage=async({data:message})=>{if(closed)return;if(message?.type==='OVERRUN'){fail(Error('本地保存无法跟上麦克风采集，录音已中断，请先恢复保存'));return;}if(message?.type==='FLUSHED'){if(flushing&&flushing.id===message.flushId){const pending=flushing;flushing=null;clearTimeout(pending.timer);pending.resolve();}return;}if(message?.type!=='PCM'||!(message.pcm instanceof ArrayBuffer)||message.pcm.byteLength<4||message.pcm.byteLength>16384||message.pcm.byteLength%4!==0||message.sampleRate!==context.sampleRate){fail(Error('麦克风返回无效分片'));return;}try{await data(new Float32Array(message.pcm));if(!closed)node?.port.postMessage({type:'ACK'});}catch(cause){fail(cause instanceof Error?cause:Error('本地录音保存失败，请保留当前页面'));}};
  node.addEventListener('processorerror',ended);for(const track of stream.getAudioTracks()){track.addEventListener('ended',ended);track.addEventListener('mute',ended);}context.addEventListener('statechange',stateChanged);signal?.addEventListener('abort',abort,{once:true});cancelled();
  return{sampleRate:context.sampleRate,released:()=>closed,
   async resume(){cancelled();if(closed)throw Error('麦克风已释放，请开启新段');let timer:ReturnType<typeof setTimeout>|undefined;try{await Promise.race([context.resume(),new Promise<never>((_,reject)=>{timer=setTimeout(()=>reject(Error('麦克风启动超时，请重新授权')),5000);})]);}finally{clearTimeout(timer);}cancelled();if(closed||context.state!=='running')throw Error('麦克风未运行');if(worker)await rpc('RESUME');else node!.port.postMessage({type:'RESUME'});running=true;},
   async pause(){if(closed)throw Error('麦克风已中断');running=false;if(worker)return rpc('PAUSE');if(flushing)return flushing.promise;let resolve:()=>void=()=>{},reject:(cause:Error)=>void=()=>{};const promise=new Promise<void>((yes,no)=>{resolve=yes;reject=no;});const flushId=++id;flushing={id:flushId,promise,resolve,reject,timer:setTimeout(()=>fail(Error('麦克风尾段保存超时，已保存范围保留')),5000)};node!.port.postMessage({type:'PAUSE',flushId});return promise;},
   ...(worker?{configure:async(options:PersistentCapture)=>{configuration=options;await rpc('CONFIGURE',{actor:options.actor,session:options.session});},pipeline:{samples:()=>workerSamples,flush:()=>rpc('FLUSH'),retry:()=>rpc('RETRY'),dispose:disposeWorker}}:{}),
   release,
  };
 }catch(cause){await release();disposeWorker();throw cause;}
}
