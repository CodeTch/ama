import {shallowRef,shallowReadonly} from 'vue';
import type {Feedback} from '../../shared/api/feedback';
export function useFeedbackList(api:{list:(cursor:string|null,signal:AbortSignal)=>Promise<{items:readonly Feedback[];nextCursor:string|null}>},actor:()=>string|null){
 const items=shallowRef<readonly Feedback[]>([]),next=shallowRef<string|null>(null),error=shallowRef<string|null>(null),loading=shallowRef(false);let epoch=0,controller:AbortController|null=null;
 function invalidate(){epoch++;controller?.abort();controller=null;items.value=[];next.value=null;error.value=null;loading.value=false;}
 async function load(cursor:string|null=null){
  const who=actor();if(!who){invalidate();return;}const turn=++epoch;controller?.abort();controller=new AbortController();const signal=controller.signal;loading.value=true;error.value=null;
  const active=()=>turn===epoch&&actor()===who&&!signal.aborted;
  try{const page=await api.list(cursor,signal);if(active()){items.value=page.items;next.value=page.nextCursor;}}
  catch(cause){if(active()){items.value=[];next.value=null;error.value=cause instanceof Error?cause.message:'反馈列表暂不可用。';}}
  finally{if(active())loading.value=false;}
 }
 return {items:shallowReadonly(items),next:shallowReadonly(next),error:shallowReadonly(error),loading:shallowReadonly(loading),load,invalidate};
}
