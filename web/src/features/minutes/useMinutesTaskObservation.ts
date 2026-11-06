import {shallowRef,shallowReadonly,computed,watch} from 'vue';
import type {WorkspaceTaskPage} from '../../shared/api/workspace-tasks';
type Task=WorkspaceTaskPage['items'][number];
type Api={currentGeneration?:(meeting:string,signal:AbortSignal)=>Promise<WorkspaceTaskPage>;list:(meeting:string,kind:'MINUTES_GENERATE_V1',cursor:string|null,signal:AbortSignal)=>Promise<WorkspaceTaskPage>};
export function useMinutesTaskObservation(api:Api,actor:()=>string|null,meeting:()=>string,enabled:()=>boolean,completed:()=>void,schedule:(fn:()=>void)=>ReturnType<typeof setTimeout>=fn=>setTimeout(fn,2000)){
 const latest=shallowRef<Task|null>(null),error=shallowRef(''),loading=shallowRef(false),observed=shallowRef(false);
 const running=computed(()=>!!latest.value&&['READY','RUNNING','RECONCILING'].includes(latest.value.state));
 let epoch=0,disposed=false,controller:AbortController|null=null,timer:ReturnType<typeof setTimeout>|null=null;
 const delivered=new Set<string>();let seen=false,previous:Task|null=null;
 function clear(resetHistory=true){epoch++;controller?.abort();if(timer)clearTimeout(timer);timer=null;latest.value=null;error.value='';loading.value=false;observed.value=false;if(resetHistory){delivered.clear();seen=false;previous=null;}}
 async function refresh(){
  if(disposed||!actor()||!enabled())return;
  if(timer)clearTimeout(timer);timer=null;controller?.abort();const request=new AbortController();controller=request;
  const turn=++epoch,who=actor(),id=meeting(),active=()=>!disposed&&turn===epoch&&who===actor()&&id===meeting()&&enabled();
  const deadline=setTimeout(()=>request.abort(),20000);loading.value=true;error.value='';
  try{
   let next:string|null=null,selected:Task|null=null;const cursors=new Set<string>();
   do{const page:WorkspaceTaskPage=api.currentGeneration?await api.currentGeneration(id,request.signal):await api.list(id,'MINUTES_GENERATE_V1',next,request.signal);if(!active())return;
    selected??=page.items[0]??null;const runningTask=page.items.find(t=>['READY','RUNNING','RECONCILING'].includes(t.state));
    if(runningTask){selected=runningTask;break;}next=page.nextCursor;
    if(next&&(cursors.has(next)||cursors.size>=50))throw Error('任务记录暂无法完整核对，请重新读取。');if(next)cursors.add(next);
   }while(next);
   if(!active())return;latest.value=selected;observed.value=true;
   // The first historical result is already covered by the saved-content read.
   // Only a newly observed completion may refresh that content and close editors.
   const changed=seen&&(previous?.taskId!==selected?.taskId||!!previous&&['READY','RUNNING','RECONCILING'].includes(previous.state));
   seen=true;previous=selected;
   if(selected&&!running.value&&!delivered.has(selected.taskId)){delivered.add(selected.taskId);if(changed)completed();}
  }catch(cause){if(active())error.value=request.signal.aborted?'读取生成状态超时，请重试。':cause instanceof Error?cause.message:'生成状态暂不可读取。';}
  finally{clearTimeout(deadline);if(active()){loading.value=false;if(running.value||error.value)timer=schedule(()=>{void refresh();});}}
 }
 const stop=watch(()=>[actor(),meeting(),enabled()],(value,old)=>{clear(value[0]!==old[0]||value[1]!==old[1]);if(actor()&&enabled())void refresh();},{flush:'sync'});
 return {observed:shallowReadonly(observed),latest:shallowReadonly(latest),error:shallowReadonly(error),loading:shallowReadonly(loading),running,refresh,dispose(){disposed=true;stop();clear();}};
}
