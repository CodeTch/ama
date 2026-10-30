import {shallowReadonly,shallowRef,watch} from 'vue';import type {Person} from '../../shared/api/meeting-options';
type People={items:readonly Person[];nextCursor:string|null};
/** Only current API Person objects are selectable; these observations confer no server authority. */
export function useActionOwnerSelection(fetchPeople:(query:string,cursor:string|null,signal:AbortSignal)=>Promise<People>,actor:()=>string|null,scope:()=>readonly string[],enabled:()=>boolean){
 const people=shallowRef<readonly Person[]>([]),selected=shallowRef<Person|null>(null),nextCursor=shallowRef<string|null>(null),busy=shallowRef(false),error=shallowRef<unknown>(null);let epoch=0,controller:AbortController|null=null,lastQuery='',disposed=false;
 function invalidate(){const turn=++epoch,old=controller;controller=null;old?.abort();if(turn!==epoch)return;people.value=[];if(turn!==epoch)return;selected.value=null;if(turn!==epoch)return;nextCursor.value=null;if(turn!==epoch)return;busy.value=false;if(turn!==epoch)return;error.value=null;if(turn!==epoch)return;lastQuery='';}
 const stop=watch(()=>[actor(),...scope(),enabled()],invalidate,{flush:'sync'});
 async function search(query:string,more=false){
  if(disposed||!actor()||!enabled())return;const who=actor(),where=JSON.stringify(scope()),turn=++epoch,cursor=more&&query===lastQuery?nextCursor.value:null;const old=controller,request=new AbortController();controller=request;old?.abort();
  const active=()=>!disposed&&epoch===turn&&actor()===who&&JSON.stringify(scope())===where&&enabled()&&!request.signal.aborted;
  try{if(!active())return;selected.value=null;if(!active())return;people.value=[];if(!active())return;nextCursor.value=null;if(!active())return;busy.value=true;if(!active())return;error.value=null;if(!active())return;const value=await fetchPeople(query,cursor,request.signal);if(!active())return;people.value=value.items;if(!active())return;nextCursor.value=value.nextCursor;if(!active())return;lastQuery=query;}
  catch(cause){if(active())error.value=cause;}finally{if(active()){busy.value=false;if(active())controller=null;}}
 }
 function choose(person:Person){if(disposed||busy.value||!enabled()||!actor()||!person.active||!people.value.includes(person))throw new Error('请从本次目录返回的在职人员中选择。');selected.value=person;}
 return {people:shallowReadonly(people),selected:shallowReadonly(selected),nextCursor:shallowReadonly(nextCursor),busy:shallowReadonly(busy),error:shallowReadonly(error),search,choose,invalidate,dispose(){disposed=true;stop();invalidate();}};
}
