import {computed,shallowRef,watch,onScopeDispose} from 'vue';
import type {Person,Department} from '../../shared/api/meeting-options';
type Ports={people:(query:string,cursor:string|null,signal:AbortSignal,departmentId?:string|null)=>Promise<{items:readonly Person[];nextCursor:string|null}>;departments:(signal:AbortSignal)=>Promise<{items:readonly Department[]}>};
/** The full catalogue and a filtered people page have separate read lifetimes; neither grants access. */
export function usePeopleDirectory(ports:Ports,actor:()=>string|null,meeting:()=>string,active:()=>boolean){
 const people=shallowRef<readonly Person[]>([]),departments=shallowRef<readonly Department[]>([]),peopleReady=shallowRef(false),catalogueReady=shallowRef(false),peopleBusy=shallowRef(false),catalogueBusy=shallowRef(false),peopleError=shallowRef<string|null>(null),catalogueError=shallowRef<string|null>(null),nextCursor=shallowRef<string|null>(null);
 let epoch=0,peopleEpoch=0,catalogueEpoch=0,peopleRequest:AbortController|null=null,catalogueRequest:AbortController|null=null,query='',department:string|null=null,disposed=false;
 const enabled=()=>!disposed&&!!actor()&&active();
 function reset(){epoch++;peopleEpoch++;catalogueEpoch++;peopleRequest?.abort();catalogueRequest?.abort();peopleRequest=null;catalogueRequest=null;people.value=[];departments.value=[];peopleReady.value=false;catalogueReady.value=false;peopleBusy.value=false;catalogueBusy.value=false;peopleError.value=null;catalogueError.value=null;nextCursor.value=null;query='';department=null;}
 const message=(cause:unknown)=>cause instanceof Error?cause.message:'人员目录暂不可用，请重新读取。';
 function denied(cause:unknown){if(cause&&typeof cause==='object'&&'status'in cause&&[401,403].includes(Number(cause.status))){reset();peopleError.value=message(cause);return true;}return false;}
 async function catalogue(force=false){
  if(!enabled()||catalogueBusy.value||catalogueReady.value&&!force)return;const turn=epoch,generation=++catalogueEpoch,who=actor(),id=meeting();catalogueRequest?.abort();const request=new AbortController();catalogueRequest=request;catalogueBusy.value=true;catalogueReady.value=false;departments.value=[];catalogueError.value=null;
  const valid=()=>enabled()&&epoch===turn&&generation===catalogueEpoch&&!request.signal.aborted&&actor()===who&&meeting()===id;
  try{const page=await ports.departments(request.signal);if(valid()){departments.value=page.items;catalogueReady.value=true;}}catch(cause){if(valid()&&!denied(cause))catalogueError.value=message(cause);}finally{if(valid()){catalogueBusy.value=false;catalogueRequest=null;}}
 }
 async function search(q='',dep:string|null=null,more=false){
  if(!enabled())return;const cursor=more&&q===query&&dep===department?nextCursor.value:null;if(more&&q===query&&dep===department&&cursor===null&&peopleReady.value)return;
  const turn=epoch,generation=++peopleEpoch,who=actor(),id=meeting();peopleRequest?.abort();const request=new AbortController();peopleRequest=request;query=q;department=dep;people.value=[];nextCursor.value=null;peopleReady.value=false;peopleBusy.value=true;peopleError.value=null;
  const valid=()=>enabled()&&turn===epoch&&generation===peopleEpoch&&!request.signal.aborted&&actor()===who&&meeting()===id;
  try{const page=await ports.people(q,cursor,request.signal,dep);if(valid()){if(page.nextCursor!==null&&page.nextCursor===cursor)throw Error('人员目录分页未前进，请重新读取。');people.value=page.items;nextCursor.value=page.nextCursor;peopleReady.value=true;}}catch(cause){if(valid()&&!denied(cause))peopleError.value=message(cause);}finally{if(valid()){peopleBusy.value=false;peopleRequest=null;}}
 }
 async function retry(){await Promise.allSettled([catalogue(true),search(query,department)]);}
 const stop=watch(()=>[actor(),meeting(),active()],reset,{flush:'sync'});onScopeDispose(()=>{disposed=true;stop();reset();});
 return{people,departments,peopleReady,catalogueReady,busy:computed(()=>peopleBusy.value||catalogueBusy.value),error:computed(()=>peopleError.value??catalogueError.value),nextCursor,more:computed(()=>nextCursor.value!==null),catalogue,search,retry};
}
