import {shallowRef,watch,onScopeDispose} from 'vue';
import type {MinutesCurrent} from '../../shared/api/minutes-saved';import type {Source} from '../../shared/api/minutes-inspection';
type Ports={source:(meeting:string,locator:string,target:{kind:'BODY'|'ELEMENT'|'SET';id:string},signal:AbortSignal)=>Promise<Source>};
export type MinutesSourceLabel={label:string;kind:'recording'|'transcript'|'material'};
/** Read-only labels from current evidence and its exact immutable transcript version. */
export function useMinutesSourceLabels(ports:Ports,actor:()=>string|null,meeting:()=>string,active:()=>boolean,current:()=>MinutesCurrent|null,denied:(cause:unknown)=>void){
 const labels=shallowRef<Record<string,MinutesSourceLabel>>({}),errors=shallowRef<Record<string,string>>({}),loading=shallowRef(false);let epoch=0,controller:AbortController|null=null;
 function clear(){epoch++;controller?.abort();labels.value={};errors.value={};loading.value=false;}
 async function reload(){
  clear();const who=actor(),id=meeting(),v=current();if(!who||!active()||!v)return;
  const turn=epoch;controller=new AbortController();const signal=controller.signal;loading.value=true;
  const valid=()=>turn===epoch&&!signal.aborted&&who===actor()&&id===meeting()&&active();
  const elements=new Set(Object.values(v.elements).flatMap(items=>items.slice(0,20).map(item=>item.id)));
  const targets=new Map<string,{kind:'BODY'|'ELEMENT'|'SET';id:string}>();
  for(const evidence of v.evidence){if(targets.size>=128)break;if(evidence.element_id&&elements.has(evidence.element_id))targets.set(evidence.locator_id,{kind:'ELEMENT',id:evidence.element_id});else if(evidence.minutes_revision_id===v.body?.id)targets.set(evidence.locator_id,{kind:'BODY',id:evidence.minutes_revision_id!});}
  if(v.setId)for(const entry of [...v.sensitive,...v.background])for(const locator of entry.locatorIds){if(targets.size<128&&!targets.has(locator))targets.set(locator,{kind:'SET',id:v.setId});}
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
    if(valid())labels.value={...labels.value,[locator]:result};
   }catch(cause){if(!valid())return;if(cause&&typeof cause==='object'&&'status' in cause&&[401,403].includes(Number(cause.status))){clear();denied(cause);return;}errors.value={...errors.value,[locator]:cause instanceof Error?cause.message.slice(0,160):'来源标签暂不可读取，请打开来源核对。'};}
  }}
  try{await Promise.all(Array.from({length:Math.min(2,jobs.length)},()=>worker()));}finally{if(valid())loading.value=false;}
 }
 const stop=watch(()=>JSON.stringify([actor(),meeting(),active(),current()?.observation.revisionToken,current()?.observation.accessRevisionToken,current()?.body?.id,current()?.setId]),()=>{void reload();},{immediate:true,flush:'sync'});
 onScopeDispose(()=>{stop();clear();});return{labels,errors,loading,reload};
}
