import {onUnmounted,ref,shallowRef,watch} from 'vue';
import {useRoute,useRouter} from 'vue-router';
import {useApplication} from '../../application';
type Page={items:unknown[];counts:Record<string,number>;total:number;nextCursor:string|null};
/** Each module owns its read sequence; an old response cannot replace a new filter. */
export function useGovernanceList<T extends Page>(module:string){
 const app=useApplication(),route=useRoute(),router=useRouter(),page=shallowRef<T|null>(null),loading=ref(false),error=ref(''),query=ref('');let epoch=0;
 const actor=()=>app.session.current.value?.actor.enterpriseId??null;
 const defaultState=['corpus','voiceprints'].includes(module)?'PENDING':'';
 const value=(name:string)=>name==='state'?(route.query.state==='ALL'?'':typeof route.query.state==='string'?route.query.state:defaultState):typeof route.query[name]==='string'?route.query[name] as string:'';
 async function load(){const turn=++epoch,who=actor();page.value=null;error.value='';if(!who||!app.operationsCommands.available.value)return;loading.value=true;
  const filters=new URLSearchParams({limit:route.query.limit==='50'?'50':'20'});for(const key of ['q','state','cursor','type','usage','model','from','to'])if(value(key))filters.set(key,value(key));
  try{const result=await app.operationsCommands.read<T>(`/api/v1/operations/${module}/page?${filters}`);if(turn===epoch&&actor()===who)page.value=result;}catch(cause){if(turn===epoch&&actor()===who)error.value=cause instanceof Error?cause.message:'当前记录暂不可用。';}finally{if(turn===epoch)loading.value=false;}
 }
 function filter(key:string,next:string){return router.replace({query:{...route.query,[key]:key==='state'?(next||'ALL'):next||undefined,cursor:undefined,selected:undefined}});}
 const stop=watch(()=>[actor(),app.operationsCommands.available.value,route.path,route.query.q,route.query.state,route.query.cursor,route.query.type,route.query.usage,route.query.model,route.query.from,route.query.to,route.query.limit],()=>{query.value=value('q');void load();},{immediate:true,flush:'sync'});
 watch(()=>app.operationsCommands.pending.value,(pending,was)=>{if(was&&!pending)void load();});
 onUnmounted(()=>{epoch++;stop();});
 return{page,loading,error,query,load,filter,state:()=>value('state'),search:()=>filter('q',query.value.trim()),next:()=>page.value?.nextCursor&&router.push({query:{...route.query,cursor:page.value.nextCursor,selected:undefined}}),first:()=>filter('cursor',''),hasCursor:()=>!!value('cursor')};
}
