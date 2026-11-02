import {shallowRef,onScopeDispose,watch} from 'vue';
import type {TranscriptCurrent,TranscriptDetail,TranscriptSegment} from '../../shared/api/transcripts';
type Ports={revision:(meeting:string,id:string,signal:AbortSignal)=>Promise<TranscriptDetail>};
type Options={actor:()=>string|null;meeting:()=>string;active:()=>boolean;canMutate:()=>boolean;current:()=>TranscriptCurrent|null;undo:()=>Promise<unknown>;denied:(cause:unknown)=>void};
function unchanged(a:TranscriptSegment,b:TranscriptSegment){return a.segmentId===b.segmentId&&a.ordinal===b.ordinal&&a.sourceFileId===b.sourceFileId&&a.sourceParagraph===b.sourceParagraph&&a.startMs===b.startMs&&a.endMs===b.endMs&&a.body===b.body&&a.marks.length===b.marks.length&&a.marks.every(mark=>b.marks.includes(mark));}
/** A speaker control may undo only a proven current speaker change, never a newer body or mark edit. */
export function useSpeakerUndo(ports:Ports,options:Options){
 const checking=shallowRef(false),message=shallowRef<string|null>(null);let epoch=0,disposed=false,request:AbortController|null=null;
 function cancel(){epoch++;request?.abort();request=null;checking.value=false;message.value=null;}
 async function run(){
  if(checking.value)return;cancel();const who=options.actor(),meeting=options.meeting(),saved=options.current();
  if(disposed||!who||!options.active()||!options.canMutate()||saved?.saved?.kind!=='WORKING')return;
  const turn=epoch,source=saved.saved.id,token=saved.observation.revisionToken;request=new AbortController();const signal=request.signal;checking.value=true;
  const valid=()=>!disposed&&turn===epoch&&!signal.aborted&&options.actor()===who&&options.meeting()===meeting&&options.active()&&options.current()?.saved?.id===source&&options.current()?.observation.revisionToken===token;
  try{
   const current=await ports.revision(meeting,source,signal);if(!valid())return;
   if(current.observation.revisionToken!==token||current.observation.currentSavedRevisionId!==source){message.value='转写版本已变化，请重新读取后再撤销。';return;}
   const parentId=current.selected.parentId;if(!parentId||parentId===source){message.value='当前没有可撤销的说话人变更。';return;}
   const parent=await ports.revision(meeting,parentId,signal);if(!valid())return;
   if(parent.observation.revisionToken!==token||parent.observation.currentSavedRevisionId!==source){message.value='转写版本已变化，请重新读取后再撤销。';return;}
   const now=current.selected.segments,before=parent.selected.segments;
   if(now.length!==before.length||!now.every((segment,index)=>unchanged(segment,before[index]!))){message.value='最近的变更不是说话人标注，未执行撤销。';return;}
   if(!now.some((segment,index)=>segment.speakerId!==before[index]!.speakerId)){message.value='当前没有可撤销的说话人变更。';return;}
   if(!options.canMutate()){message.value='读取期间草稿或当前操作已变化，未执行撤销。';return;}
   await options.undo();
  }catch(cause){if(!valid())return;if(cause&&typeof cause==='object'&&'status' in cause&&[401,403].includes(Number(cause.status))){cancel();options.denied(cause);}else message.value='说话人变更暂无法核实，未执行撤销，请重新读取。';}
  finally{if(valid()){checking.value=false;request=null;}}
 }
 const stop=watch(()=>[options.actor(),options.meeting(),options.active(),options.current()?.saved?.id,options.current()?.observation.revisionToken],cancel,{flush:'sync'});
 onScopeDispose(()=>{disposed=true;stop();cancel();});return{checking,message,cancel,run};
}
