import {PcmRecoverySelectionRequired,type createPcmJournal} from './pcm-journal';
import type {Chunk} from './chunk-store';
type Journal=ReturnType<typeof createPcmJournal>;
/** UI-owned transactions must never hold the audio worker's IndexedDB locks. */
export function createPcmJournalClient(actor:string):Journal{
 if(!actor.trim()||actor.length>255)throw new TypeError('录音缓存范围无效');
 let worker:Worker|null=null,ready:Promise<unknown>|null=null,closed=false,id=0;
 const calls=new Map<number,{resolve:(value:unknown)=>void;reject:(cause:Error)=>void;timer:ReturnType<typeof setTimeout>}>();
 function fail(cause:Error){worker?.terminate();worker=null;ready=null;for(const call of calls.values()){clearTimeout(call.timer);call.reject(cause);}calls.clear();}
 function invoke(method:string,args:unknown[]){
  return new Promise<unknown>((resolve,reject)=>{const key=++id,timer=setTimeout(()=>{calls.delete(key);reject(Error('录音缓存响应超时，原音频已保留，请恢复保存'));},15000);calls.set(key,{resolve,reject,timer});worker!.postMessage({id:key,method,args});});
 }
 function connect(){
  if(closed)throw new DOMException('录音缓存作用域已关闭','AbortError');if(ready)return ready;
  worker=new Worker(new URL('./pcm-journal-worker.ts',import.meta.url),{type:'module',name:'ama-recording-journal'});
  worker.onmessage=({data})=>{const call=calls.get(data.id);if(!call)return;calls.delete(data.id);clearTimeout(call.timer);if(data.ok){call.resolve(data.value);return;}const cause=data.candidates?Object.create(PcmRecoverySelectionRequired.prototype) as PcmRecoverySelectionRequired:Error(data.message);cause.message=data.message;cause.name=data.name;if(data.candidates)Object.defineProperty(cause,'candidates',{value:data.candidates});call.reject(cause);};
  worker.onerror=()=>fail(Error('录音缓存线程异常，原音频保留，请恢复保存'));
  ready=invoke('INIT',[actor]);void ready.catch(()=>{});return ready;
 }
 async function call<T>(method:string,...args:unknown[]):Promise<T>{await connect();if(closed)throw new DOMException('录音缓存作用域已关闭','AbortError');return await invoke(method,args) as T;}
 return{
  begin:(...args)=>call('begin',...args),load:(...args)=>call('load',...args),latest:(...args)=>call('latest',...args),
  skipAutomaticRestore:(...args)=>call('skipAutomaticRestore',...args),updateSession:(...args)=>call('updateSession',...args),
  finish:(...args)=>call('finish',...args),reconcile:(...args)=>call('reconcile',...args),markPart:(...args)=>call('markPart',...args),
  stats:(...args)=>call('stats',...args),put:(...args)=>call('put',...args),removeConfirmed:(...args)=>call('removeConfirmed',...args),
  async *pending(sid){const reader=await call<number>('OPEN_PENDING',sid);try{for(;;){const values=await call<Chunk[]>('NEXT_PENDING',reader);for(const chunk of values)yield chunk;if(values.length<8)break;}}finally{await call('CLOSE_PENDING',reader).catch(()=>{});}},
  close(){closed=true;fail(new DOMException('录音缓存作用域已关闭','AbortError'));},
 };
}
