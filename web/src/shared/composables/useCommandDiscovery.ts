import {shallowReadonly,shallowRef,watch} from 'vue';
import type {CommandIdentity,CommandLookup} from '../api/command-discovery';
/** Detached lookup state; deliberately has no command mutator, ACK setter, or permission setter. */
export function useCommandDiscovery(read:(identity:CommandIdentity,signal:AbortSignal)=>Promise<CommandLookup>,actor:()=>string|null,command:()=>CommandIdentity|null){
 const result=shallowRef<CommandLookup|null>(null),error=shallowRef<unknown>(null),loading=shallowRef(false);let epoch=0,controller:AbortController|null=null,disposed=false;
 function invalidate(){epoch++;controller?.abort();controller=null;result.value=null;error.value=null;loading.value=false;}
 const stop=watch(()=>[actor(),command()?.actor,command()?.operation,command()?.resource,command()?.key],invalidate,{flush:'sync'});
 async function reload(){
  if(disposed)return;invalidate();const current=command(),who=actor();if(!current||!who||who!==current.actor)return;
  const identity=Object.freeze({actor:current.actor,operation:current.operation,resource:current.resource,key:current.key}),generation=epoch;
  controller=new AbortController();loading.value=true;
  try{const value=await read(identity,controller.signal);if(generation===epoch)result.value=value;}
  catch(cause){if(generation===epoch)error.value=cause;}
  finally{if(generation===epoch){loading.value=false;controller=null;}}
 }
 return {result:shallowReadonly(result),error:shallowReadonly(error),loading:shallowReadonly(loading),reload,invalidate,dispose(){disposed=true;stop();invalidate();}};
}
