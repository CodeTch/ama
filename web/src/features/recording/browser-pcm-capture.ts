import {PCM_WORKLET_PROCESSOR,PCM_WORKLET_SOURCE} from './pcm-worklet';
export type PcmCapture={sampleRate:number;resume():Promise<void>;pause():Promise<void>;release():Promise<void>;released():boolean};
/** Requested explicitly by the application. Native worklet credit waits only for bounded local persistence. */
export async function openPcmCapture(data:(input:Float32Array)=>Promise<void>,lost:(cause:Error)=>void,signal?:AbortSignal):Promise<PcmCapture>{
 const cancelled=()=>{if(signal?.aborted)throw new DOMException('麦克风请求已取消','AbortError');};cancelled();
 if(!globalThis.isSecureContext||!navigator.mediaDevices?.getUserMedia||typeof AudioContext==='undefined')throw Error('当前浏览器无法使用安全麦克风录音，请导入音频文件');
 const context=new AudioContext({latencyHint:'interactive'});let stream:MediaStream|null=null,node:AudioWorkletNode|null=null,source:MediaStreamAudioSourceNode|null=null,gain:GainNode|null=null,closed=false,running=false,id=0;
 let flushing:{id:number;promise:Promise<void>;resolve:()=>void;reject:(cause:Error)=>void;timer:ReturnType<typeof setTimeout>}|null=null;
 const abort=()=>{void release();};
 async function release(){for(const track of stream?.getTracks()??[])track.stop();if(closed)return;closed=true;running=false;signal?.removeEventListener('abort',abort);if(flushing){clearTimeout(flushing.timer);flushing.reject(Error('麦克风已释放，尾段未确认'));flushing=null;}if(node){node.port.onmessage=null;node.port.close();node.disconnect();}source?.disconnect();gain?.disconnect();for(const track of stream?.getTracks()??[]){track.removeEventListener('ended',ended);track.removeEventListener('mute',ended);track.stop();}context.removeEventListener('statechange',stateChanged);await context.close().catch(()=>{});}
 function fail(cause:Error){if(closed)return;void release();lost(cause);}
 function ended(){fail(Error('麦克风已中断，已保存范围保留，继续时请开启新段'));}
 function stateChanged(){if(running&&context.state!=='running')ended();}
 signal?.addEventListener('abort',abort,{once:true});
 try{
  stream=await navigator.mediaDevices.getUserMedia({audio:{channelCount:{ideal:1},echoCancellation:false,noiseSuppression:false,autoGainControl:false}});cancelled();
  if(!context.audioWorklet)throw Error('当前浏览器不支持 AudioWorklet 录音');
  const url=URL.createObjectURL(new Blob([PCM_WORKLET_SOURCE],{type:'text/javascript'}));try{await context.audioWorklet.addModule(url);}finally{URL.revokeObjectURL(url);}cancelled();
  source=context.createMediaStreamSource(stream);node=new AudioWorkletNode(context,PCM_WORKLET_PROCESSOR,{numberOfInputs:1,numberOfOutputs:1,outputChannelCount:[1]});gain=context.createGain();gain.gain.value=0;source.connect(node);node.connect(gain);gain.connect(context.destination);
  node.port.onmessage=async({data:message})=>{if(closed)return;if(message?.type==='OVERRUN'){fail(Error('本地保存无法跟上麦克风采集，录音已中断，请先恢复保存'));return;}if(message?.type==='FLUSHED'){if(flushing&&flushing.id===message.flushId){const pending=flushing;flushing=null;clearTimeout(pending.timer);pending.resolve();}return;}if(message?.type!=='PCM'||!(message.pcm instanceof ArrayBuffer)||message.pcm.byteLength<4||message.pcm.byteLength>16384||message.pcm.byteLength%4!==0||message.sampleRate!==context.sampleRate){fail(Error('麦克风返回无效分片'));return;}try{await data(new Float32Array(message.pcm));if(!closed)node?.port.postMessage({type:'ACK'});}catch(cause){fail(cause instanceof Error?cause:Error('本地录音保存失败，请保留当前页面'));}};
  node.addEventListener('processorerror',ended);for(const track of stream.getAudioTracks()){track.addEventListener('ended',ended);track.addEventListener('mute',ended);}context.addEventListener('statechange',stateChanged);signal?.addEventListener('abort',abort,{once:true});cancelled();
  return{sampleRate:context.sampleRate,released:()=>closed,
   async resume(){cancelled();if(closed)throw Error('麦克风已释放，请开启新段');let timer:ReturnType<typeof setTimeout>|undefined;try{await Promise.race([context.resume(),new Promise<never>((_,reject)=>{timer=setTimeout(()=>reject(Error('麦克风启动超时，请重新授权')),5000);})]);}finally{clearTimeout(timer);}cancelled();if(closed||context.state!=='running')throw Error('麦克风未运行');node!.port.postMessage({type:'RESUME'});running=true;},
   async pause(){if(closed)throw Error('麦克风已中断');running=false;if(flushing)return flushing.promise;let resolve:()=>void=()=>{},reject:(cause:Error)=>void=()=>{};const promise=new Promise<void>((yes,no)=>{resolve=yes;reject=no;});const flushId=++id;flushing={id:flushId,promise,resolve,reject,timer:setTimeout(()=>fail(Error('麦克风尾段保存超时，已保存范围保留')),5000)};node!.port.postMessage({type:'PAUSE',flushId});return promise;},
   release,
  };
 }catch(cause){await release();throw cause;}
}
