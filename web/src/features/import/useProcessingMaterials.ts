import {onScopeDispose,ref,shallowReadonly,shallowRef,watch} from 'vue';
import type {MaterialListing} from '../../shared/api/materials';
/** Read-only current material directory. An unavailable read never means an empty directory. */
export function useProcessingMaterials(read:(meeting:string,signal:AbortSignal)=>Promise<MaterialListing>,actor:()=>string|null,meeting:()=>string,active:()=>boolean){
 const value=shallowRef<MaterialListing|null>(null),busy=ref(false),error=ref<string|null>(null);let epoch=0,request:AbortController|null=null,disposed=false;
 function invalidate(){epoch++;request?.abort();request=null;value.value=null;busy.value=false;error.value=null;}
 async function reload(){
  invalidate();const who=actor(),id=meeting(),turn=epoch;if(disposed||!who||!id||!active())return;
  const controller=new AbortController();request=controller;busy.value=true;const current=()=>!disposed&&epoch===turn&&!controller.signal.aborted&&actor()===who&&meeting()===id&&active();
  try{const result=await read(id,controller.signal);if(current())value.value=result;}
  catch(cause){if(current())error.value=cause instanceof Error?cause.message:'背景材料目录暂不可读取，请重试。';}
  finally{if(current()){busy.value=false;request=null;}}
 }
 const stop=watch(()=>[actor(),meeting(),active()],()=>{void reload();},{immediate:true,flush:'sync'});
 onScopeDispose(()=>{disposed=true;stop();invalidate();});
 return{value:shallowReadonly(value),busy:shallowReadonly(busy),error:shallowReadonly(error),reload};
}
