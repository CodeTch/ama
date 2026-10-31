import {computed,shallowReadonly,shallowRef,watch} from 'vue';
import type {MeetingPage,MeetingScope,MeetingFilters} from '../../shared/api/meetings';
/** Cursor-only history: every navigation performs a fresh authorized read. No old rows are cached. */
export function useMeetingList(fetchPage:(scope:MeetingScope,cursor:string|null,signal:AbortSignal,filters?:MeetingFilters)=>Promise<MeetingPage>,actor:()=>string|null) {
 const filters=shallowRef<MeetingFilters>({}),scope=shallowRef<MeetingScope>('owned'),page=shallowRef<MeetingPage|null>(null),loading=shallowRef(false),error=shallowRef<unknown>(null);
 type Position={cursors:(string|null)[];index:number;base:number};const initial=():Position=>({cursors:[null],index:0,base:1});
 const positions=shallowRef<Record<MeetingScope,Position>>({owned:initial(),joined:initial()});let generation=0,controller:AbortController|null=null;
 const pageNumber=computed(()=>{const p=positions.value[scope.value];return p.base+p.index;}),canPrevious=computed(()=>positions.value[scope.value].index>0);
 function reset(){positions.value={owned:initial(),joined:initial()};}
 function invalidate(){generation++;controller?.abort();controller=null;page.value=null;loading.value=false;error.value=null;reset();}
 const stop=watch(actor,invalidate,{flush:'sync'});
 async function load(cursor:string|null,navigate:'reload'|'next'|'previous'|'first'='reload'){
  const owner=actor();if(!owner){invalidate();return;}controller?.abort();controller=new AbortController();const turn=++generation,selected=scope.value,previous=positions.value[selected];loading.value=true;error.value=null;
  try{const result=await fetchPage(selected,cursor,controller.signal,filters.value);if(turn!==generation||actor()!==owner)return;
   let position=previous;if(navigate==='first')position=initial();else if(navigate==='previous')position={...previous,index:previous.index-1};else if(navigate==='next'){const cursors=[...previous.cursors.slice(0,previous.index+1),cursor];const drop=Math.max(0,cursors.length-64);position={cursors:cursors.slice(drop),index:cursors.length-1-drop,base:previous.base+drop};}
   positions.value={...positions.value,[selected]:position};page.value=result;
  }catch(cause){if(turn!==generation||actor()!==owner)return;error.value=cause;}
  finally{if(turn===generation){loading.value=false;controller=null;}}
 }
 function reload(){const p=positions.value[scope.value];return load(p.cursors[p.index]!);}
 function selectScope(next:MeetingScope){if(next!==scope.value){controller?.abort();generation++;scope.value=next;page.value=null;}return reload();}
 function next(){if(!page.value?.hasMore||!page.value.nextCursor)throw new Error('No continuation');return load(page.value.nextCursor,'next');}
 function previous(){const p=positions.value[scope.value];if(p.index===0)throw new Error('No previous page');return load(p.cursors[p.index-1]!,'previous');}
 function first(){return load(null,'first');}
 function selectFilters(value:MeetingFilters){filters.value={...value,stages:[...(value.stages??[])],categories:[...(value.categories??[])]};reset();return load(null);}
 function dispose(){invalidate();stop();}
 return {selectFilters,filters:shallowReadonly(filters),scope:shallowReadonly(scope),page:shallowReadonly(page),loading:shallowReadonly(loading),error:shallowReadonly(error),pageNumber,canPrevious,reload,selectScope,next,previous,first,invalidate,dispose};
}
