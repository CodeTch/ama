import {shallowReadonly,shallowRef,watch} from 'vue';
import type {MeetingPage,MeetingScope} from '../../shared/api/meetings';
/** One bounded visible page. Scope/actor changes abort reads; no elapsed-time or client ACL inference. */
export function useMeetingList(fetchPage:(scope:MeetingScope,cursor:string|null,signal:AbortSignal)=>Promise<MeetingPage>,actor:()=>string|null) {
 const scope=shallowRef<MeetingScope>('owned'),page=shallowRef<MeetingPage|null>(null),loading=shallowRef(false),error=shallowRef<unknown>(null);
 let generation=0,controller:AbortController|null=null;const positions:Record<MeetingScope,string|null>={owned:null,joined:null};
 function invalidate(){generation++;controller?.abort();controller=null;page.value=null;loading.value=false;error.value=null;positions.owned=null;positions.joined=null;}
 const stop=watch(actor,()=>invalidate(),{flush:'sync'});
 async function load(cursor:string|null){
  const owner=actor();if(!owner){invalidate();return;}controller?.abort();controller=new AbortController();const turn=++generation,selected=scope.value;loading.value=true;error.value=null;
  try{const result=await fetchPage(selected,cursor,controller.signal);if(turn!==generation||actor()!==owner)return;positions[selected]=cursor;page.value=result;}
  catch(cause){if(turn!==generation||actor()!==owner)return;error.value=cause;}
  finally{if(turn===generation){loading.value=false;controller=null;}}
 }
 function reload(){return load(positions[scope.value]);}
 function selectScope(next:MeetingScope){if(next!==scope.value){controller?.abort();generation++;scope.value=next;page.value=null;}return reload();}
 function next(){if(!page.value?.hasMore||!page.value.nextCursor)throw new Error('No continuation');return load(page.value.nextCursor);}
 function first(){return load(null);}
 function dispose(){invalidate();stop();}
 return {scope:shallowReadonly(scope),page:shallowReadonly(page),loading:shallowReadonly(loading),error:shallowReadonly(error),reload,selectScope,next,first,invalidate,dispose};
}
