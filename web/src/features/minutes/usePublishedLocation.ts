import {shallowRef,shallowReadonly,watch} from 'vue';
import type {PublicationCurrent} from '../../shared/api/publications';
import type {LibraryContext} from '../../shared/api/meeting-library';
/** Resolves the library through the current publication and provider READ context. */
export function usePublishedLocation(api:{current:(meeting:string,signal:AbortSignal)=>Promise<PublicationCurrent>;context:(meeting:string,publication:string,signal:AbortSignal)=>Promise<LibraryContext>},actor:()=>string|null,meeting:()=>string){
 const value=shallowRef<LibraryContext|null>(null),error=shallowRef<string|null>(null),busy=shallowRef(false);let epoch=0,request:AbortController|null=null,disposed=false;
 function clear(){epoch++;request?.abort();request=null;value.value=null;error.value=null;busy.value=false;}
 const stop=watch(()=>[actor(),meeting()],clear,{flush:'sync'});
 async function reload(){
  clear();const who=actor(),id=meeting(),turn=epoch;if(disposed||!who)return;
  const next=new AbortController();request=next;busy.value=true;const current=()=>!disposed&&turn===epoch&&who===actor()&&id===meeting()&&!next.signal.aborted;
  try{const observed=await api.current(id,next.signal);if(!current())return;const publication=observed.publication;if(!publication||publication.documentViewAvailability!=='READY'||!publication.officialDocumentUrl)throw new Error('原知识库暂不可用');const context=await api.context(id,publication.publicationId,next.signal);if(current())value.value=context;}
  catch(cause){if(current()){value.value=null;error.value='原知识库暂不可用';}}
  finally{if(current()){busy.value=false;request=null;}}
 }
 function dispose(){disposed=true;stop();clear();}
 return{value:shallowReadonly(value),error:shallowReadonly(error),busy:shallowReadonly(busy),reload,dispose};
}
