import {createPcmJournal,PcmRecoverySelectionRequired} from './pcm-journal';
import type {Chunk} from './chunk-store';
const host=globalThis as unknown as {onmessage:((event:MessageEvent)=>void)|null;postMessage:(message:unknown,transfer?:Transferable[])=>void};
let journal:ReturnType<typeof createPcmJournal>|null=null,nextIterator=0;
const readers=new Map<number,AsyncIterator<Chunk>>();
const methods=['begin','load','latest','skipAutomaticRestore','updateSession','finish','reconcile','markPart','stats','put','removeConfirmed'] as const;
host.onmessage=({data})=>{
 void(async()=>{
  let value:unknown,transfer:Transferable[]=[];
  if(data.method==='INIT'){journal=createPcmJournal(data.args[0]);}
  else if(!journal)throw Error('录音缓存尚未初始化');
  else if(data.method==='OPEN_PENDING'){const id=++nextIterator;readers.set(id,journal.pending(data.args[0])[Symbol.asyncIterator]());value=id;}
  else if(data.method==='NEXT_PENDING'){
   const reader=readers.get(data.args[0]);if(!reader)throw Error('录音缓存读取已变化');const chunks:Chunk[]=[];
   for(let i=0;i<8;i++){const item=await reader.next();if(item.done){readers.delete(data.args[0]);break;}chunks.push(item.value);}
   value=chunks;transfer=chunks.map(chunk=>chunk.pcm);
  }else if(data.method==='CLOSE_PENDING'){const reader=readers.get(data.args[0]);readers.delete(data.args[0]);await reader?.return?.();}
  else if(methods.includes(data.method)){
   const method=data.method as typeof methods[number];const invoke=journal[method] as (...args:unknown[])=>Promise<unknown>;
   value=await invoke.apply(journal,data.args);
  }else throw Error('未知录音缓存操作');
  host.postMessage({id:data.id,ok:true,value},transfer);
 })().catch(cause=>host.postMessage({id:data.id,ok:false,name:cause instanceof Error?cause.name:'Error',message:cause instanceof Error?cause.message:'录音缓存操作失败',...(cause instanceof PcmRecoverySelectionRequired?{candidates:cause.candidates}:{})}));
};
