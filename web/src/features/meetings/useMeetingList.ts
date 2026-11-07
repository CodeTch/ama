import {computed,reactive,shallowReadonly,shallowRef,watch} from 'vue';
import {ApiError} from '../../shared/api/client';
import type {MeetingPage,MeetingScope,MeetingFilters} from '../../shared/api/meetings';
const emptyView=()=>({keyword:'',stageFilters:[] as string[],categoryFilters:[] as string[],fromDate:'',toDate:'',groupView:false,topicFilter:'',selectedMeetingIds:[] as string[],scrollTop:0,scrollLeft:0});
function normalize(value:MeetingFilters,scope:MeetingScope):MeetingFilters{
 return {q:value.q?.trim()??'',stages:[...new Set(value.stages??[])].sort(),categories:[...new Set(value.categories??[])].sort(),from:value.from??'',to:value.to??'',topic:scope==='owned'?value.topic||undefined:undefined};
}
/** View state survives route changes. Every navigation still makes a fresh authorized read.
 * Commit scope, rows and cursor together; a pending read never erases the displayed table. */
export function useMeetingList(fetchPage:(scope:MeetingScope,cursor:string|null,signal:AbortSignal,filters?:MeetingFilters)=>Promise<MeetingPage>,actor:()=>string|null) {
 const view=reactive(emptyView()),filters=shallowRef<MeetingFilters>({}),scope=shallowRef<MeetingScope>('owned'),requestedScope=shallowRef<MeetingScope|null>(null),page=shallowRef<MeetingPage|null>(null),loading=shallowRef(false),error=shallowRef<unknown>(null);
 type Position={cursors:(string|null)[];index:number;base:number};const initial=():Position=>({cursors:[null],index:0,base:1});
 const positions=shallowRef<Record<MeetingScope,Position>>({owned:initial(),joined:initial()});let generation=0,controller:AbortController|null=null,flight:{key:string;promise:Promise<void>}|null=null,pendingFilters:MeetingFilters|null=null;
 const pageNumber=computed(()=>{const p=positions.value[scope.value];return p.base+p.index;}),canPrevious=computed(()=>positions.value[scope.value].index>0);
 function reset(){positions.value={owned:initial(),joined:initial()};}
 function invalidate(){generation++;controller?.abort();controller=null;flight=null;pendingFilters=null;page.value=null;loading.value=false;error.value=null;requestedScope.value=null;filters.value={};scope.value='owned';Object.assign(view,emptyView());reset();}
 const stop=watch(actor,invalidate,{flush:'sync'});
 function load(cursor:string|null,navigate:'reload'|'next'|'previous'|'first'='reload',selected=scope.value,value=filters.value,resetAll=false):Promise<void>{
  const owner=actor();if(!owner){invalidate();return Promise.resolve();}
  const selectedFilters=normalize(value,selected),key=JSON.stringify([owner,selected,cursor,selectedFilters,navigate,resetAll]);
  if(flight?.key===key)return flight.promise;
  controller?.abort();const request=new AbortController();controller=request;pendingFilters=selectedFilters;const turn=++generation,previous=positions.value[selected];loading.value=true;error.value=null;requestedScope.value=selected;
  const promise=(async()=>{
   try{const result=await fetchPage(selected,cursor,request.signal,selectedFilters);if(turn!==generation||actor()!==owner||request.signal.aborted)return;
    let position=previous;if(navigate==='first')position=initial();else if(navigate==='previous')position={...previous,index:previous.index-1};else if(navigate==='next'){const cursors=[...previous.cursors.slice(0,previous.index+1),cursor];const drop=Math.max(0,cursors.length-64);position={cursors:cursors.slice(drop),index:cursors.length-1-drop,base:previous.base+drop};}
    positions.value={...(resetAll?{owned:initial(),joined:initial()}:positions.value),[selected]:position};scope.value=selected;filters.value=selectedFilters;
    if(JSON.stringify(page.value)!==JSON.stringify(result))page.value=result;
    const ids=new Set(result.items.map(item=>item.id));view.selectedMeetingIds=view.selectedMeetingIds.filter(id=>ids.has(id));
   }catch(cause){if(turn!==generation||actor()!==owner||request.signal.aborted)return;if(cause instanceof ApiError&&[401,403,404,410].includes(cause.status)){page.value=null;view.selectedMeetingIds=[];}error.value=cause;}
   finally{if(turn===generation){loading.value=false;requestedScope.value=null;controller=null;flight=null;pendingFilters=null;}}
  })();
  flight={key,promise};return promise;
 }
 function reload(){const p=positions.value[scope.value];return load(p.cursors[p.index]!);}
 function selectScope(next:MeetingScope){if(!actor()){invalidate();scope.value=next;return Promise.resolve();}const p=positions.value[next],value={...(pendingFilters??filters.value),topic:next==='owned'?view.topicFilter||filters.value.topic:undefined},changed=JSON.stringify(normalize(value,'joined'))!==JSON.stringify(normalize(filters.value,'joined'));return load(changed?null:p.cursors[p.index]!,changed?'first':'reload',next,value,changed);}
 function next(){if(!page.value?.hasMore||!page.value.nextCursor)throw new Error('No continuation');return load(page.value.nextCursor,'next');}
 function previous(){const p=positions.value[scope.value];if(p.index===0)throw new Error('No previous page');return load(p.cursors[p.index-1]!,'previous');}
 function first(){return load(null,'first');}
 function selectFilters(value:MeetingFilters){const selected=requestedScope.value??scope.value,normalized=normalize(value,selected);if(selected===scope.value&&JSON.stringify(normalized)===JSON.stringify(normalize(filters.value,selected)))return reload();return load(null,'first',selected,normalized,true);}
 function dispose(){invalidate();stop();}
 return {view,selectFilters,filters:shallowReadonly(filters),scope:shallowReadonly(scope),requestedScope:shallowReadonly(requestedScope),page:shallowReadonly(page),loading:shallowReadonly(loading),error:shallowReadonly(error),pageNumber,canPrevious,reload,selectScope,next,previous,first,invalidate,dispose};
}
