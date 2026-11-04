import {shallowRef,shallowReadonly,computed,watch} from 'vue';
import type {WorkspaceTaskPage} from '../../shared/api/workspace-tasks';
type Task=WorkspaceTaskPage['items'][number];
type Api={list:(meeting:string,kind:'MINUTES_GENERATE_V1',cursor:string|null,signal:AbortSignal)=>Promise<WorkspaceTaskPage>};
export function useMinutesTaskObservation(api:Api,actor:()=>string|null,meeting:()=>string,enabled:()=>boolean,completed:()=>void,schedule:(fn:()=>void)=>ReturnType<typeof setTimeout>=fn=>setTimeout(fn,2000)){
 const latest=shallowRef<Task|null>(null),error=shallowRef(''),loading=shallowRef(false),observed=shallowRef(false);
 const running=computed(()=>!!latest.value&&['READY','RUNNING','RECONCILING'].includes(latest.value.state));
 let epoch=0,disposed=false,controller:AbortController|null=null,timer:ReturnType<typeof setTimeout>|null=null;
 const delivered=new Set<string>();
 function clear(){epoch++;controller?.abort();if(timer)clearTimeout(timer);timer=null;latest.value=null;error.value='';loading.value=false;observed.value=false;delivered.clear();}
 async function refresh(){
  if(disposed||!actor()||!enabled())return;
  if(timer)clearTimeout(timer);timer=null;controller?.abort();const request=new AbortController();controller=request;
  const turn=++epoch,who=actor(),id=meeting(),active=()=>!disposed&&turn===epoch&&who===actor()&&id===meeting()&&enabled();
  const deadline=setTimeout(()=>request.abort(),20000);loading.value=true;error.value='';
  try{
   let next:string|null=null,selected:Task|null=null;const cursors=new Set<string>();
   do{const page=await api.list(id,'MINUTES_GENERATE_V1',next,request.signal);if(!active())return;
    selected??=page.items[0]??null;const runningTask=page.items.find(t=>['READY','RUNNING','RECONCILING'].includes(t.state));
    if(runningTask){selected=runningTask;break;}next=page.nextCursor;
    if(next&&(cursors.has(next)||cursors.size>=50))throw Error('任务记录暂无法完整核对，请重新读取。');if(next)cursors.add(next);
   }while(next);
   if(!active())return;latest.value=selected;observed.value=true;
   if(selected&&!running.value&&!delivered.has(selected.taskId)){delivered.add(selected.taskId);completed();}
  }catch(cause){if(active())error.value=request.signal.aborted?'读取生成状态超时，请重试。':cause instanceof Error?cause.message:'生成状态暂不可读取。';}
  finally{clearTimeout(deadline);if(active()){loading.value=false;if(running.value||error.value)timer=schedule(()=>{void refresh();});}}
 }
 const stop=watch(()=>[actor(),meeting(),enabled()],()=>{clear();if(actor()&&enabled())void refresh();},{flush:'sync'});
 return {observed:shallowReadonly(observed),latest:shallowReadonly(latest),error:shallowReadonly(error),loading:shallowReadonly(loading),running,refresh,dispose(){disposed=true;stop();clear();}};
}
