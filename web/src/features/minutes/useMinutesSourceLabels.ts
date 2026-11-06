import {shallowRef,watch,onScopeDispose} from 'vue';
import type {MinutesCurrent} from '../../shared/api/minutes-saved';import type {Source} from '../../shared/api/minutes-inspection';
type Ports={source:(meeting:string,locator:string,target:{kind:'BODY'|'ELEMENT'|'SET';id:string},signal:AbortSignal)=>Promise<Source>};
export type MinutesSourceLabel={label:string;kind:'recording'|'transcript'|'material'};
/** Read-only labels from current evidence and its exact immutable transcript version. */
export function useMinutesSourceLabels(ports:Ports,actor:()=>string|null,meeting:()=>string,active:()=>boolean,current:()=>MinutesCurrent|null,denied:(cause:unknown)=>void,visibleElements?:()=>readonly string[]){
 const labels=shallowRef<Record<string,MinutesSourceLabel>>({}),errors=shallowRef<Record<string,string>>({}),loading=shallowRef(false);let epoch=0,controller:AbortController|null=null;
 let observation='',completePage:string|null=null;
 const resolved=new Map<string,MinutesSourceLabel>();
 const key=()=>JSON.stringify([actor(),meeting(),current()?.observation.revisionToken,current()?.observation.accessRevisionToken,current()?.body?.id,current()?.setId]);
 function clear(){epoch++;controller?.abort();labels.value={};errors.value={};loading.value=false;resolved.clear();completePage=null;}
 async function reload(refresh=true){
  const nextKey=key();
  if(refresh||observation!==nextKey){clear();observation=nextKey;}
  else {epoch++;controller?.abort();errors.value={};loading.value=false;}
  const who=actor(),id=meeting(),v=current();
  if(!who||!v){labels.value={};return;}
  if(!active())return;
  const pageKey=JSON.stringify(visibleElements?.()??null);
  if(!refresh&&completePage===pageKey)return;
  completePage=null;
  const turn=epoch;controller=new AbortController();const signal=controller.signal;loading.value=true;
  const valid=()=>turn===epoch&&!signal.aborted&&who===actor()&&id===meeting()&&active();
  const ready:Record<string,MinutesSourceLabel>=Object.fromEntries((v.presentation?.sourceLabels??[]).map(({locatorId,kind,label})=>[locatorId,{kind,label}]));
  const elements=new Set(visibleElements?.()??Object.values(v.elements).flatMap(items=>items.slice(0,20).map(item=>item.id)));
  type Target={kind:'ELEMENT'|'SET';id:string};
  const targets=new Map<string,Target>();
  const targetKey=(locator:string,target:Target)=>[target.kind,target.id,locator].join('/');
  function add(locator:string,target:Target){
   if(ready[locator]||targets.has(locator))return;
   const cached=resolved.get(targetKey(locator,target));
   if(cached)ready[locator]=cached;
   else targets.set(locator,target);
  }
  // This panel displays element and directory chips. Body-only evidence must never
  // consume the budget ahead of a visible element on a later page.
  const visibleLocators=new Map<string,Set<string>>();
  for(const evidence of v.evidence){
   const element=evidence.element_id;
   if(!element||!elements.has(element))continue;
   const locators=visibleLocators.get(element)??new Set<string>();
   if(locators.size===20||locators.has(evidence.locator_id))continue;
   locators.add(evidence.locator_id);visibleLocators.set(element,locators);
   add(evidence.locator_id,{kind:'ELEMENT',id:element});
  }
  if(v.setId)for(const entry of [...v.sensitive.slice(0,20),...v.background.slice(0,20)])for(const locator of entry.locatorIds.slice(0,20))add(locator,{kind:'SET',id:v.setId});
  labels.value=ready;
  const jobs=[...targets];let next=0;
  async function worker(){while(valid()&&next<jobs.length){const [locator,target]=jobs[next++]!;
   try{
    const source=await ports.source(id,locator,target,signal);if(!valid())return;if(source.observation.revisionToken!==v!.observation.revisionToken||source.observation.accessRevisionToken!==v!.observation.accessRevisionToken)throw Error('来源或访问版本已变化');
    let result:MinutesSourceLabel;
    if(source.locator.kind==='TRANSCRIPT'){
     const version=source.sourceVersion;if(version!==source.locator.transcript_revision_id)throw Error('来源版本无法核实');
     const segment=source.transcript;if(!segment||segment.revisionId!==version||segment.segmentId!==source.locator.segment_id)throw Error('来源段落无法核实');
     if(segment.startMs===null)result={kind:'transcript',label:'转写文件'};
     else{
      const speaker=segment.speakerLabel??'匿名说话人';
      const seconds=Math.floor(segment.startMs/1000);result={kind:'recording',label:`${String(Math.floor(seconds/60)).padStart(2,'0')}:${String(seconds%60).padStart(2,'0')} ${speaker}`};
     }
    }else result={kind:'material',label:'背景材料'+(source.locator.page_noToken?' · 第'+source.locator.page_noToken+'页':'')};
    if(valid()){
     if(resolved.size>=2048)resolved.delete(resolved.keys().next().value!);
     resolved.set(targetKey(locator,target),result);
     labels.value={...labels.value,[locator]:result};
    }
   }catch(cause){if(!valid())return;if(cause&&typeof cause==='object'&&'status' in cause&&[401,403].includes(Number(cause.status))){clear();denied(cause);return;}errors.value={...errors.value,[locator]:cause instanceof Error?cause.message.slice(0,160):'来源标签暂不可读取，请打开来源核对。'};}
  }}
  try{await Promise.all(Array.from({length:Math.min(2,jobs.length)},()=>worker()));}finally{if(valid()){loading.value=false;if(!Object.keys(errors.value).length)completePage=pageKey;}}
 }
 const stop=watch(()=>JSON.stringify([actor(),meeting(),active(),current()?.observation.revisionToken,current()?.observation.accessRevisionToken,current()?.body?.id,current()?.setId,visibleElements?.()]),()=>{void reload(false);},{immediate:true,flush:'sync'});
 onScopeDispose(()=>{stop();clear();});return{labels,errors,loading,reload};
}
