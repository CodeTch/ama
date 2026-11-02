import {shallowRef,onScopeDispose,watch} from 'vue';
import type {InfoFields} from '../../shared/api/meeting-info';
import type {TranscriptCurrent} from '../../shared/api/transcripts';
import type {SpeechSpeakers} from '../../shared/api/transcript-corrections';
import type {Person} from '../../shared/api/meeting-options';
type Ports={current:(meeting:string,signal:AbortSignal)=>Promise<TranscriptCurrent>;speakers:(meeting:string,source:string,signal:AbortSignal)=>Promise<SpeechSpeakers>;people:(query:string,cursor:string|null,signal:AbortSignal)=>Promise<{items:readonly Person[];nextCursor:string|null}>};
/** Explicit read into a local draft; never a save, identity provision or access grant. */
export function useSpeakerRosterSource(ports:Ports,actor:()=>string|null,meeting:()=>string,active:()=>boolean,draft:()=>InfoFields|null,apply:(value:InfoFields)=>void){
 const busy=shallowRef(false),error=shallowRef<string|null>(null),message=shallowRef<string|null>(null);
 let epoch=0,controller:AbortController|null=null;
 function clear(){epoch++;controller?.abort();controller=null;busy.value=false;error.value=null;message.value=null;}
 async function load(){
  if(busy.value)return;clear();const who=actor(),id=meeting(),initial=draft();if(!who||!active()||!initial)return;
  const turn=epoch,original=JSON.stringify(initial);controller=new AbortController();const signal=controller.signal;busy.value=true;
  const valid=()=>turn===epoch&&!signal.aborted&&who===actor()&&id===meeting()&&active();
  try{
   const before=await ports.current(id,signal);if(!valid())return;
   if(!before.saved){message.value='当前没有已保存的转写，请先保存转写或手动添加参会人。';return;}
   const source=before.saved.id,snapshot=await ports.speakers(id,source,signal);if(!valid())return;
   const ids=new Set<string>(),names=new Set<string>();let skipped=0;const lookups=new Map<string,Person|null>();
   if(snapshot.items.length>128)throw Error('说话人过多，请手动选择参会人。');
   for(const person of snapshot.items){
    if(!valid())return;if(person.source!=='HUMAN'){skipped++;continue;}
    if(person.enterpriseId!==null){
     const key=person.enterpriseId;
     if(!lookups.has(key)){
      let cursor:string|null=null,found:Person|null=null;const seen=new Set<string>();
      for(let page=0;page<10;page++){
       const result=await ports.people(key,cursor,signal);if(!valid())return;found=result.items.find(p=>p.stableId===key)??null;
       if(found||result.nextCursor===null)break;
       if(seen.has(result.nextCursor)||page===9)throw Error('目录分页暂无法核实，请重试。');seen.add(result.nextCursor);cursor=result.nextCursor;
      }
      lookups.set(key,found);
     }
     const match=lookups.get(key);if(match?.active)ids.add(key);else skipped++;
    }else if(person.label.trim())names.add(person.label.trim());
   }
   const after=await ports.current(id,signal);if(!valid())return;
   if(after.saved?.id!==source||after.observation.revisionToken!==before.observation.revisionToken)throw Error('转写或会议版本已变化，请重新读取发言人。');
   const latest=draft();if(!latest||JSON.stringify(latest)!==original)throw Error('读取期间草稿已修改，已保留人工输入，请重新获取。');
   const internal=[...new Set([...latest.rosterDraftIds,...ids])],external=[...new Set([...(latest.externalRosterNames??[]),...names])];
   if(internal.length+external.length>128)throw Error('参会人超过128人，请手动选择。');
   const added=internal.length-latest.rosterDraftIds.length,guests=external.length-(latest.externalRosterNames??[]).length;
   if(added||guests)apply({...latest,rosterDraftIds:internal,externalRosterNames:external});
   message.value=added||guests?`已加入草稿：${added} 位企业人员、${guests} 位外部人员。${skipped?'另有身份未核实或已停用，未加入。':''}保存后仍需确认名单。`:'未发现可新增的人工发言人标签，可手动添加参会人。';
  }catch(cause){if(valid())error.value=cause instanceof Error?cause.message:'发言人来源暂不可读取，请重试。';}
  finally{if(valid()){busy.value=false;controller=null;}}
 }
 const stop=watch(()=>[actor(),meeting(),active()],clear,{flush:'sync'});onScopeDispose(()=>{stop();clear();});
 return{busy,error,message,load};
}
