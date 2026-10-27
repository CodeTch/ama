import {shallowReadonly,shallowRef,watch} from 'vue';
import type {WorkspaceNavigation} from '../../shared/api/workspace';
/** Fresh navigation observation only. Producer reads and mutations still recheck their own authority. */
export function useWorkspaceNavigation(fetchValue:(meeting:string,signal:AbortSignal)=>Promise<WorkspaceNavigation>,actor:()=>string|null,meeting:()=>string){
 const value=shallowRef<WorkspaceNavigation|null>(null),loading=shallowRef(false),error=shallowRef<unknown>(null);
 let epoch=0,controller:AbortController|null=null,disposed=false;
 function invalidate(){const generation=++epoch,old=controller;controller=null;old?.abort();if(generation!==epoch)return;value.value=null;if(generation!==epoch)return;error.value=null;if(generation!==epoch)return;loading.value=false;}
 const stop=watch(()=>[actor(),meeting()],invalidate,{flush:'sync'});
 async function reload(){
  if(disposed)return;const who=actor(),id=meeting();if(!who){invalidate();return;}
  const generation=++epoch,old=controller,current=new AbortController();controller=current;old?.abort();
  const active=()=>!disposed&&generation===epoch&&actor()===who&&meeting()===id&&!current.signal.aborted;
  try{if(!active())return;value.value=null;if(!active())return;error.value=null;if(!active())return;loading.value=true;if(!active())return;
   const result=await fetchValue(id,current.signal);if(!active())return;value.value=result;
  }catch(cause){if(active())error.value=cause;}
  finally{if(active()){controller=null;loading.value=false;}}
 }
 return {value:shallowReadonly(value),loading:shallowReadonly(loading),error:shallowReadonly(error),reload,invalidate,dispose(){disposed=true;stop();invalidate();}};
}
