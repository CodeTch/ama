import {ref,shallowRef,computed,watch,onScopeDispose} from 'vue';
import type {TranscriptSource} from '../../shared/api/transcripts';
export type MediaSource=TranscriptSource&{ordinal:number};
export type MediaScope={meeting:string;revisionId:string|null;accessRevisionToken:string;sources:MediaSource[]};
export type SourceReader=(meeting:string,source:TranscriptSource,signal:AbortSignal)=>Promise<{blob:Blob;name:string|null}>;
/** Own one verified media Blob. Neither a prior URL nor a late source read survives its actor/revision scope. */
export function useSourceMedia(read:SourceReader,actor:()=>string|null,scope:()=>MediaScope|null,urls:Pick<typeof URL,'createObjectURL'|'revokeObjectURL'>=URL){
 const url=ref<string|null>(null),selected=ref<string|null>(null),busy=ref(false),error=ref<string|null>(null),name=ref<string|null>(null),type=ref(''),names=shallowRef<Record<string,string>>({});let epoch=0,controller:AbortController|null=null;
 const sources=computed(()=>actor()?scope()?.sources??[]:[]);
 function clear(){epoch++;controller?.abort();controller=null;if(url.value)urls.revokeObjectURL(url.value);url.value=null;name.value=null;type.value='';busy.value=false;}
 async function select(fileId:string){
  const snapshot=scope(),who=actor(),source=sources.value.find(s=>s.fileId===fileId);if(!snapshot||!who||!source)return;
  clear();selected.value=fileId;error.value=null;busy.value=true;const scopeKey=JSON.stringify(snapshot),turn=epoch,request=new AbortController();controller=request;const active=()=>epoch===turn&&who===actor()&&JSON.stringify(scope())===scopeKey&&!request.signal.aborted;
  try{const result=await read(snapshot.meeting,source,request.signal);if(!active())return;url.value=urls.createObjectURL(result.blob);name.value=result.name;type.value=result.blob.type;if(result.name)names.value={...names.value,[fileId]:result.name};}
  catch(cause){if(active()){error.value=cause instanceof Error?cause.message:'原文件暂不可用，请重试。';name.value=null;}}
  finally{if(active()){busy.value=false;controller=null;}}
 }
 const stop=watch(()=>JSON.stringify([actor(),scope()]),()=>{clear();selected.value=null;error.value=null;names.value={};const first=sources.value[0];if(first)void select(first.fileId);},{immediate:true,flush:'sync'});
 function dispose(){stop();clear();selected.value=null;error.value=null;names.value={};}
 onScopeDispose(dispose);return{url,selected,busy,error,name,type,names,sources,select,dispose};
}
