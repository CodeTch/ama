import {shallowReadonly,shallowRef,watch} from 'vue';
import type {WorkspaceTaskPage,WorkspaceTaskKind} from '../../shared/api/workspace-tasks';
type FetchPage=(meeting:string,kind:WorkspaceTaskKind,cursor:string|null,signal:AbortSignal)=>Promise<WorkspaceTaskPage>;
const kinds:readonly WorkspaceTaskKind[]=['ALL','TRANSCRIPT_IMPORT_V1','MINUTES_GENERATE_V1','KNOWLEDGE_PUBLISH_V1','RECORDING_ARCHIVE_V1'];
/** A bounded, explicitly refreshed observation. Never grants producer permission or proves task absence. */
export function useWorkspaceTasks(fetchPage:FetchPage,actor:()=>string|null,meeting:()=>string){
 const kind=shallowRef<WorkspaceTaskKind>('ALL'),page=shallowRef<WorkspaceTaskPage|null>(null),loading=shallowRef(false),error=shallowRef<unknown>(null);
 let epoch=0,controller:AbortController|null=null,position:string|null=null,disposed=false;
 function invalidate(){
  const generation=++epoch,previous=controller;controller=null;position=null;previous?.abort();
  if(generation!==epoch)return;page.value=null;if(generation!==epoch)return;error.value=null;if(generation!==epoch)return;loading.value=false;
 }
 const stop=watch(()=>[actor(),meeting()],invalidate,{flush:'sync'});
 async function load(cursor:string|null){
  if(disposed)return;const who=actor(),id=meeting();if(!who){invalidate();return;}
  const generation=++epoch,previous=controller,current=new AbortController(),selected=kind.value;controller=current;previous?.abort();
  const active=()=>!disposed&&generation===epoch&&!current.signal.aborted&&actor()===who&&meeting()===id&&kind.value===selected;
  try{
   if(!active())return;position=cursor;page.value=null;if(!active())return;error.value=null;if(!active())return;loading.value=true;if(!active())return;
   const result=await fetchPage(id,selected,cursor,current.signal);if(!active())return;
   page.value=result;
  }catch(cause){if(active())error.value=cause;}
  finally{if(active()){controller=null;loading.value=false;}}
 }
 async function selectKind(next:WorkspaceTaskKind){
  if(!kinds.includes(next))throw new TypeError('Invalid task kind');if(disposed)return;
  if(next===kind.value)return load(position);
  const generation=epoch+1;invalidate();if(generation!==epoch||disposed)return;kind.value=next;if(generation!==epoch||disposed)return;return load(null);
 }
 return {kind:shallowReadonly(kind),page:shallowReadonly(page),loading:shallowReadonly(loading),error:shallowReadonly(error),
  reload:()=>load(position),first:()=>load(null),selectKind,
  next(){if(loading.value||!page.value?.hasMore||!page.value.nextCursor)return Promise.resolve();return load(page.value.nextCursor);},
  invalidate,dispose(){disposed=true;stop();invalidate();}};
}
