import {shallowRef,shallowReadonly,watch} from 'vue';
import {ApiError} from '../../shared/api/client';
import type {MaterialListing} from '../../shared/api/materials';
/** A read-only published source view. Each opening requires a fresh actor/meeting read. */
export function usePublishedMaterials(list:(meeting:string,signal:AbortSignal)=>Promise<MaterialListing>,actor:()=>string|null,meeting:()=>string,onDenied:(cause:unknown)=>void){
 const opened=shallowRef(false),value=shallowRef<MaterialListing|null>(null),error=shallowRef<string|null>(null),busy=shallowRef(false);
 let epoch=0,request:AbortController|null=null,disposed=false;
 function close(){epoch++;request?.abort();request=null;opened.value=false;value.value=null;error.value=null;busy.value=false;}
 const stop=watch(()=>[actor(),meeting()],close,{flush:'sync'});
 async function reload(){
  const turn=++epoch,who=actor(),id=meeting();request?.abort();request=null;value.value=null;error.value=null;
  if(disposed||!opened.value||!who){busy.value=false;if(opened.value&&!who)error.value='请登录后读取背景材料。';return;}
  const next=new AbortController();request=next;busy.value=true;
  const current=()=>!disposed&&turn===epoch&&who===actor()&&id===meeting()&&opened.value&&!next.signal.aborted;
  try{const result=await list(id,next.signal);if(current())value.value=result;}
  catch(cause){if(current()){value.value=null;error.value=cause instanceof Error?cause.message:'背景材料暂不可读取，请重试。';if(cause instanceof ApiError&&cause.kind==='http'&&[401,403].includes(cause.status))onDenied(cause);}}
  finally{if(current()){busy.value=false;request=null;}}
 }
 function open(){if(disposed)return Promise.resolve();opened.value=true;return reload();}
 function dispose(){disposed=true;stop();close();}
 return{opened:shallowReadonly(opened),value:shallowReadonly(value),error:shallowReadonly(error),busy:shallowReadonly(busy),open,reload,close,dispose};
}
