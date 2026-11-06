import {computed,readonly,shallowRef} from 'vue';
import {useCommand} from '../../shared/composables/useCommand';import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';import type {TabJournalContext} from '../../shared/composables/tab-journal';
import {validCorrectionCommand,type createTranscriptCorrectionsApi,type SpeechCorrections,type SpeechSpeakers,type CorrectionReceipt} from '../../shared/api/transcript-corrections';import type {TranscriptCurrent} from '../../shared/api/transcripts';import {ApiError} from '../../shared/api/client';
type Api=ReturnType<typeof createTranscriptCorrectionsApi>&{current:(meeting:string,signal:AbortSignal)=>Promise<TranscriptCurrent>};
type Options={actor:()=>string|null;meeting:()=>string;eligible:()=>boolean;canMutate:()=>boolean;current:()=>TranscriptCurrent|null;context:TabJournalContext};
/** Persist original intent before sending; a historical ACK never replaces the current draft. */
export function useSpeechCorrections(api:Api,options:Options){
 const journal=createScopedPrivateJournal(options.context,'speech-corrections',validCorrectionCommand),view=shallowRef<SpeechCorrections|null>(null),speakers=shallowRef<SpeechSpeakers|null>(null),available=shallowRef(false),loading=shallowRef(false),reading=shallowRef(false),concealed=shallowRef(false);
 let scope:{actor:string;meeting:string}|null=null,epoch=0,readOrder=0,request:AbortController|null=null;
 const active=(turn:number)=>turn===epoch&&!!scope&&scope.actor===options.actor()&&scope.meeting===options.meeting()&&options.eligible();
 const command=useCommand<CorrectionReceipt>(async c=>{if(!active(epoch)||!scope||!validCorrectionCommand(c,scope.actor,scope.meeting))throw new DOMException('Correction scope changed','AbortError');return api.send(c);});
 const pending=computed(()=>command.snapshot.value!==null),busy=computed(()=>reading.value||command.state.value==='SENDING'),original=computed(()=>!concealed.value&&command.snapshot.value?JSON.parse(command.snapshot.value.body):null);
 function conceal(cause:unknown){if(cause instanceof ApiError&&[401,403].includes(cause.status??0)){view.value=null;speakers.value=null;concealed.value=true;available.value=false;}}
 function clearView(){readOrder++;request?.abort();request=null;view.value=null;speakers.value=null;loading.value=false;}
 let commandRead:AbortController|null=null;
 function dispose(){epoch++;commandRead?.abort();commandRead=null;clearView();scope=null;available.value=false;reading.value=false;command.invalidate();}
 function synchronize(){dispose();const who=options.actor();if(!who||!options.eligible())return;scope={actor:who,meeting:options.meeting()};concealed.value=false;const stored=journal.load(who,scope.meeting);if(['INVALID','UNAVAILABLE'].includes(stored.kind))throw Error('纠错恢复记录暂不可用，请保留页面。');available.value=true;if(stored.kind==='RESTORED')command.restore(stored.value);}
 async function load(){
  const current=options.current(),turn=epoch;
  if(!active(turn)||current?.saved?.kind!=='WORKING'){clearView();return;}
  const source=current.saved.id,token=current.observation.revisionToken,meeting=scope!.meeting;
  const review=current.capabilities.canSave&&current.correction.mode==='CONTROLLED_SPEECH';
  const matches=(result:SpeechSpeakers|SpeechCorrections|null)=>result?.sourceRevisionId===source&&result.observation.revisionToken===token&&result.observation.accessRevisionToken===current.observation.accessRevisionToken;
  if(!review||!matches(view.value))view.value=null;
  if(!matches(speakers.value))speakers.value=null;
  request?.abort();
  const order=++readOrder,controller=new AbortController();request=controller;loading.value=true;
  const valid=()=>active(turn)&&order===readOrder&&!controller.signal.aborted&&options.current()?.saved?.id===source&&options.current()?.observation.revisionToken===token;
  try{
   const reads:Promise<void>[]=[];
   function apply<T extends SpeechSpeakers|SpeechCorrections>(result:T,show:(value:T)=>void){
    if(!valid())return;
    if(!matches(result)){view.value=null;throw Error('转写版本已变化，请重新读取转写再核对。');}
    show(result);
   }
   if(current.speakerSnapshot){
    speakers.value=current.speakerSnapshot;
   }else if(current.saved.segments?.some(row=>row.speakerId!==null)===false){
    speakers.value={meetingId:meeting,sourceRevisionId:source,observation:current.observation,items:[]};
   }else{
    reads.push(api.speakers(meeting,source,controller.signal).then(result=>apply(result,value=>{speakers.value=value;})));
   }
   if(review)reads.push(api.read(meeting,source,controller.signal).then(result=>apply(result,value=>{view.value=value;})));
   // Surface revoked access immediately, even if another concurrent reader fails or stalls.
   const failures=await Promise.all(reads.map(read=>read.then(()=>null,cause=>{
    if(valid()&&cause instanceof ApiError&&[401,403].includes(cause.status??0)){
     controller.abort();conceal(cause);throw cause;
    }
    return cause;
   })));
   if(valid()){const failure=failures.find(cause=>cause!==null);if(failure)throw failure;}
  }finally{if(active(turn)&&order===readOrder)loading.value=false;}
 }
 function source(speakerOperation=false){const current=options.current();if(!active(epoch)||!available.value||!options.canMutate()||pending.value||!current?.capabilities.canSave||current.saved?.kind!=='WORKING'||!speakerOperation&&current.correction.mode!=='CONTROLLED_SPEECH')throw Error('请先保存或处理当前修改，再核对纠错建议。');return current;}
 async function retry(){
  const turn=epoch,selected=scope;if(!active(turn)||!selected||!available.value||!command.snapshot.value||reading.value||command.state.value==='SENDING')throw Error('原纠错操作暂不可恢复。');if(!['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(command.state.value))throw Error('请核对明确拒绝的原操作。');
  if(journal.save(selected.actor,selected.meeting,command.snapshot.value)!=='SAVED')throw Error('无法保存纠错原请求，请保留页面重试。');
  let accepted;try{accepted=command.state.value==='ACKNOWLEDGED'?command.ack.value!:await command.send();}catch(cause){if(active(turn))conceal(cause);throw cause;}
  if(!active(turn))throw new DOMException('Correction scope changed','AbortError');reading.value=true;const controller=new AbortController();commandRead=controller;
  try{const current=await api.current(selected.meeting,controller.signal);if(!active(turn)||controller.signal.aborted)throw new DOMException('Correction scope changed','AbortError');if(current.meetingId!==selected.meeting)throw Error('当前工作稿暂无法核实，请保留原请求。');if(!journal.clear())throw Error('纠错已受理，恢复记录尚未清理，请重试确认。');command.invalidate();return{accepted,current};}
  catch(cause){if(active(turn))conceal(cause);throw cause;}finally{if(active(turn)){commandRead=null;reading.value=false;}}
 }
 function prepare(operation:string,path:string,extra:Record<string,unknown>={}){const current=source(operation==='TRANSCRIPT_SPEAKERS');command.prepare({operation,actor:scope!.actor,resource:scope!.meeting,path:`/api/v1/meetings/${scope!.meeting}/transcript${path}`,key:crypto.randomUUID(),body:{expectedRevisionToken:current.observation.revisionToken,sourceRevisionId:current.saved!.id,...extra}});if(!validCorrectionCommand(command.snapshot.value,scope!.actor,scope!.meeting)){command.invalidate();throw Error('纠错请求无效。');}return retry();}
 function propose(){if(!view.value?.canPropose)return Promise.reject(Error('当前没有可请求的语音增强来源。'));return prepare('TRANSCRIPT_CORRECTIONS_PROPOSE','/correction-proposals');}
 function decide(id:string,decision:'ACCEPT'|'REJECT'|'REOPEN'|'ACCEPT_PUNCTUATION'){const current=source(),item=view.value?.items.find(x=>x.id===id);if(view.value?.sourceRevisionId!==current.saved!.id||view.value?.observation.revisionToken!==current.observation.revisionToken||!item||!(decision==='REOPEN'?item.canReopen:decision==='ACCEPT_PUNCTUATION'?item.punctuationAlternative&&item.canAccept:decision==='ACCEPT'?item.canAccept:item.canReject))return Promise.reject(Error('建议已变化，请重新读取后核对。'));return prepare('TRANSCRIPT_CORRECTION',`/corrections/${id}/decisions`,{candidateId:id,decision});}
 function decideBatch(ids:string[],decision:'ACCEPT'|'REJECT'){
  const current=source(),snapshot=view.value;
  if(!ids.length||ids.length>60||new Set(ids).size!==ids.length||snapshot?.sourceRevisionId!==current.saved!.id||snapshot.observation.revisionToken!==current.observation.revisionToken||ids.some(id=>!snapshot.items.some(item=>item.id===id&&(decision==='ACCEPT'?item.canAccept:item.canReject))))return Promise.reject(Error('批量建议已变化，请重新核对。'));
  return prepare('TRANSCRIPT_CORRECTION',`/corrections/${ids[0]}/decisions`,{candidateId:ids[0],additionalCandidateIds:ids.slice(1),decision});
 }
 function undo(id:string){const current=source(),item=view.value?.items.find(x=>x.id===id);if(!item?.canUndo||item.operationId!==current.saved!.id)return Promise.reject(Error('只能撤销仍为当前版本的接受操作。'));return prepare('TRANSCRIPT_CORRECTION_UNDO',`/operations/${item.operationId}/undo`);}
 function label(segmentIds:string[],label:string,enterpriseId:string|null=null,voiceprintId?:string){return prepare('TRANSCRIPT_SPEAKERS','/speakers',{segmentIds:[...segmentIds],label,enterpriseId,...(voiceprintId?{voiceprintId}:{})});}
 function merge(sourceId:string,targetId:string){const current=source(true),labels=speakers.value;if(!labels||labels.sourceRevisionId!==current.saved!.id||labels.observation.revisionToken!==current.observation.revisionToken||sourceId===targetId)return Promise.reject(Error('说话人版本已变化，请重新核对。'));const from=labels.items.find(x=>x.id===sourceId),to=labels.items.find(x=>x.id===targetId);if(!from||!to)return Promise.reject(Error('请选择当前稿件中的两个说话人。'));return label([...from.segmentIds,...to.segmentIds],to.label,to.enterpriseId);}
 function discardRejected(){if(!['CONFLICT','REJECTED'].includes(command.state.value))throw Error('不能放弃结果未知的纠错操作。');if(!journal.clear())throw Error('原纠错记录暂无法清理。');command.invalidate();clearView();}
 return{view:readonly(view),speakers:readonly(speakers),available:readonly(available),loading:readonly(loading),pending,busy,original,command,synchronize,load,propose,decide,decideBatch,undo,label,merge,retry,discardRejected,clearView,dispose};
}
