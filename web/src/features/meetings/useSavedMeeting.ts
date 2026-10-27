import {shallowReadonly,shallowRef,watch} from 'vue';
import type {SavedMeeting} from '../../shared/api/meeting-info';
/** Saved server content only. No roster shape or route grants ownership or edit authority. */
export function useSavedMeeting(read:(id:string,signal:AbortSignal)=>Promise<SavedMeeting>,actor:()=>string|null,meeting:()=>string){
 const saved=shallowRef<SavedMeeting|null>(null),error=shallowRef<unknown>(null),loading=shallowRef(false);let epoch=0,controller:AbortController|null=null;
 function invalidate(){epoch++;controller?.abort();controller=null;saved.value=null;error.value=null;loading.value=false;}
 const stop=watch(()=>[actor(),meeting()],invalidate,{flush:'sync'});
 async function reload(){invalidate();const currentActor=actor(),id=meeting();if(!currentActor)return;const generation=epoch;controller=new AbortController();loading.value=true;
  try{const value=await read(id,controller.signal);if(generation!==epoch||actor()!==currentActor||meeting()!==id)return;saved.value=value;}
  catch(cause){if(generation===epoch)error.value=cause;}
  finally{if(generation===epoch){loading.value=false;controller=null;}}
 }
 return {saved:shallowReadonly(saved),error:shallowReadonly(error),loading:shallowReadonly(loading),reload,invalidate,dispose(){stop();invalidate();}};
}
