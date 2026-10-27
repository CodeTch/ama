import {shallowRef,shallowReadonly,watch} from 'vue';
import {useDraft,type DraftTicket} from '../../shared/composables/useDraft';
import {useCommand,type CommandSnapshot} from '../../shared/composables/useCommand';
import type {TranscriptCurrent,TranscriptReceipt,TranscriptDetail} from '../../shared/api/transcripts';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
import {ApiError} from '../../shared/api/client';
import {createTranscriptJournal,edits,type TranscriptEdits} from './transcript-journal';
type Api={authorize?:(meeting:string,signal:AbortSignal)=>Promise<boolean>;revision?:(meeting:string,id:string,signal:AbortSignal)=>Promise<TranscriptDetail>;current:(id:string,signal:AbortSignal)=>Promise<TranscriptCurrent>;send:(command:CommandSnapshot)=>Promise<TranscriptReceipt>};
type Draft=ReturnType<typeof useDraft<TranscriptEdits>>;
type Pending={ticket:DraftTicket<TranscriptEdits>;draft:Draft;operation:string};
const content=(value:TranscriptCurrent):TranscriptEdits=>value.saved?.segments.map(({segmentId,body,marks})=>({segmentId,body,marks}))??[];
const denied=(e:unknown)=>e instanceof ApiError&&e.kind==='http'&&[401,403].includes(e.status??0);
/** Original commands remain immutable; a historical ACK is never used as the current root revision. */
export function useTranscriptEditor(api:Api,actor:()=>string|null,meeting:()=>string,eligible:()=>boolean,journals?:TabJournalContext){
 const saved=shallowRef<TranscriptCurrent|null>(null),draft=shallowRef<Draft|null>(null),loading=shallowRef(false),busy=shallowRef(false),error=shallowRef<unknown>(null),recoveryBlocked=shallowRef(false),storageStatus=shallowRef('EMPTY');
 const command=useCommand(api.send),journal=journals?createTranscriptJournal(journals):null;
 let authorizedOwner=false,restoring=false;
 let epoch=0,readTurn=0,disposed=false,controller:AbortController|null=null,flight:Promise<void>|null=null,pending:Pending|null=null,sourceRevisionId:string|null=null,scope:{actor:string;meeting:string}|null=null;
 let retained:{draft:Draft;pending:Pending|null;source:string;scope:{actor:string;meeting:string};snapshot:CommandSnapshot|null}|null=null;
 function persist(){if(recoveryBlocked.value)return false;if(!journal)return true;if(!scope||!draft.value||!sourceRevisionId)return false;storageStatus.value=journal.save(scope.actor,scope.meeting,{base:draft.value.baseToken.value,sourceRevisionId,saved:draft.value.saved.value,value:draft.value.value.value,dirty:draft.value.dirty.value,pending:pending?command.snapshot.value:null});return storageStatus.value==='SAVED';}
 function invalidate(){
  authorizedOwner=false;++epoch;++readTurn;controller?.abort();controller=null;flight=null;
  if(draft.value&&sourceRevisionId&&scope&&!recoveryBlocked.value)retained={draft:draft.value,pending,source:sourceRevisionId,scope,snapshot:command.snapshot.value};
  if(retained&&(retained.scope.meeting!==meeting()||actor()!==null&&retained.scope.actor!==actor()))retained=null;
  if(journal&&!journal.synchronize(actor(),meeting()))recoveryBlocked.value=true;
  if(disposed)retained=null;pending=null;sourceRevisionId=null;scope=null;draft.value=null;saved.value=null;command.invalidate();busy.value=false;loading.value=false;error.value=null;
 }
 const stop=watch(()=>[actor(),meeting(),eligible()],invalidate,{flush:'sync'});
 function canEdit(){return !disposed&&!!actor()&&eligible()&&!!saved.value?.capabilities.canSave&&!recoveryBlocked.value;}
 function canUseDraft(){return !disposed&&!!actor()&&eligible()&&(!!saved.value?.capabilities.canReadHistory||restoring&&authorizedOwner)&&!recoveryBlocked.value;}
 function restoreDraft(value:TranscriptCurrent|null,who:string,id:string){
  restoring=true;try{
   if(!draft.value||recoveryBlocked.value){
    const frozen=retained;
    if(frozen&&frozen.scope.actor===who&&frozen.scope.meeting===id){scope=frozen.scope;sourceRevisionId=frozen.source;pending=frozen.pending;if(frozen.snapshot&&pending&&command.state.value==='EMPTY')command.restore(frozen.snapshot);draft.value=frozen.draft;retained=null;}
    else {
     const recovered=journal?.load(who,id)??{kind:'EMPTY' as const};storageStatus.value=recovered.kind;recoveryBlocked.value=['INVALID','UNAVAILABLE'].includes(recovered.kind);if(recoveryBlocked.value)return;
     scope={actor:who,meeting:id};
     if(recovered.kind==='RESTORED'){
      const record=recovered.value,d=useDraft(record.saved,record.base,canUseDraft);sourceRevisionId=record.sourceRevisionId;
      if(record.pending){const body=JSON.parse(record.pending.body);const original=record.pending.operation==='TRANSCRIPT_SAVE'?body.segments:record.saved;if(record.pending.operation==='TRANSCRIPT_SAVE')d.edit(original);const ticket=d.capture();if(command.state.value==='EMPTY')command.restore(record.pending);pending={draft:d,ticket,operation:record.pending.operation};if(JSON.stringify(record.value)!==JSON.stringify(original))d.edit(record.value);}
      else if(record.dirty)d.edit(record.value);draft.value=d;
     }else if(value?.saved){sourceRevisionId=value.saved.id;draft.value=useDraft(content(value),value.observation.revisionToken,canUseDraft);}
    }
   }
  }finally{restoring=false;}
 }
 async function reload(afterSend=false){
  if(disposed||flight&&!afterSend)return;const who=actor(),id=meeting();if(!who||!eligible()){invalidate();return;}
  const generation=epoch,turn=++readTurn;controller?.abort();const request=new AbortController();controller=request;
  const active=()=>!disposed&&generation===epoch&&turn===readTurn&&actor()===who&&meeting()===id&&eligible()&&!request.signal.aborted;
  loading.value=true;error.value=null;
  try{
   if(api.authorize){const allowed=await api.authorize(id,request.signal);if(!active())return;authorizedOwner=allowed;if(allowed)restoreDraft(null,who,id);else{const nextEpoch=epoch+1;invalidate();if(epoch===nextEpoch){const publicRequest=new AbortController();controller=publicRequest;loading.value=true;const publicActive=()=>!disposed&&epoch===nextEpoch&&actor()===who&&meeting()===id&&eligible()&&!publicRequest.signal.aborted;try{const readOnly=await api.current(id,publicRequest.signal);if(publicActive())saved.value=readOnly;}catch(cause){if(publicActive())error.value=cause;}finally{if(publicActive()){controller=null;loading.value=false;}}}return;}if(!active())return;}
   const value=await api.current(id,request.signal);if(!active())return;saved.value=value;authorizedOwner=value.capabilities.canReadHistory;if(!active())return;
   if(!value.capabilities.canReadHistory){const nextEpoch=epoch+1;invalidate();if(epoch===nextEpoch)saved.value=value;return;}
   restoreDraft(value,who,id);
   if(!active())return;
   if(draft.value&&(!pending||['CONFLICT','REJECTED'].includes(command.state.value))){if(['CONFLICT','REJECTED'].includes(command.state.value))pending=null;const d=draft.value;if(!d.dirty.value||d.baseToken.value!==value.observation.revisionToken)d.observe(content(value),value.observation.revisionToken);if(!d.dirty.value)sourceRevisionId=value.saved?.id??null;persist();}
  }catch(cause){if(active()){if(denied(cause))invalidate();error.value=cause;}}
  finally{if(active()){loading.value=false;controller=null;}}
 }
 function mutate(operation:'TRANSCRIPT_SAVE'|'TRANSCRIPT_CONFIRM'|'TRANSCRIPT_RESTORE',selected?:string):Promise<void>{
  if(disposed)return Promise.resolve();if(flight)return flight;const who=actor(),id=meeting(),generation=epoch;
  const active=()=>!disposed&&generation===epoch&&actor()===who&&meeting()===id&&eligible();
  const run=async()=>{try{
   if(!who||!active()||recoveryBlocked.value||!authorizedOwner||!pending&&(!saved.value||(!draft.value||!sourceRevisionId)&&operation!=='TRANSCRIPT_RESTORE'))throw new Error('请先读取当前转写并恢复原操作。');error.value=null;busy.value=true;
   if(!pending){
    if(!['EMPTY','ACKNOWLEDGED'].includes(command.state.value))throw new Error('请先处理原操作。');
    if(operation==='TRANSCRIPT_RESTORE'){
     if(!selected||!api.revision)throw new Error('请先选择可恢复的历史版本。');
     const selectedVersion=await api.revision(id,selected,new AbortController().signal);if(!active())return;if(!selectedVersion.capabilities.canRestore)throw new Error('所选历史版本当前不可恢复。');
     if(!draft.value){sourceRevisionId=selected;draft.value=useDraft<TranscriptEdits>([],saved.value!.observation.revisionToken,canUseDraft);}
    }
    const d=draft.value!;if(d.conflict.value)throw new Error('请先处理版本冲突。');
    if(operation==='TRANSCRIPT_SAVE'&&!canEdit()||operation==='TRANSCRIPT_CONFIRM'&&!saved.value?.capabilities.canConfirm)throw new Error('当前操作不可用，请重新读取。');
    if(operation!=='TRANSCRIPT_SAVE'&&d.dirty.value)throw new Error('请先保存或放弃本地修改。');
    const ticket=d.capture(),suffix=operation==='TRANSCRIPT_SAVE'?'':operation==='TRANSCRIPT_CONFIRM'?'/confirmations':'/restorations';
    const body=operation==='TRANSCRIPT_SAVE'?{expectedRevisionToken:ticket.baseToken,sourceRevisionId,segments:ticket.value}:operation==='TRANSCRIPT_CONFIRM'?{expectedRevisionToken:ticket.baseToken,savedRevisionId:sourceRevisionId}:{expectedRevisionToken:ticket.baseToken,sourceRevisionId:selected};
    command.prepare({operation,actor:who,resource:id,path:`/api/v1/meetings/${id}/transcript${suffix}`,key:crypto.randomUUID(),body});pending={draft:d,ticket,operation};
   }
   if(pending.operation!==operation)throw new Error('请先恢复原操作。');if(!persist())throw new Error('恢复记录无法保存，请保留页面并重试。');if(!active())return;
   const original=pending,ack=command.state.value==='ACKNOWLEDGED'?command.ack.value!:await command.send();if(!active()||pending!==original)return;
   // Keep the ticket and original command until this authorized observation succeeds.
   await reload(true);if(!active()||pending!==original||!saved.value||error.value)return;
   const current=saved.value,d=original.draft;
   if(ack.result.revisionId===current.saved?.id){
    pending=null;sourceRevisionId=current.saved.id;d.accept(original.ticket,content(current),current.observation.revisionToken);
   }else{pending=null;d.observe(content(current),current.observation.revisionToken);if(!d.dirty.value)sourceRevisionId=current.saved?.id??null;}
   if(active())persist();
  }catch(cause){if(active()){if(denied(cause))invalidate();error.value=cause;}}finally{if(active())busy.value=false;}};
  let finish!:()=>void;const result=new Promise<void>(r=>{finish=r;});flight=result;void result.then(()=>{if(generation===epoch)flight=null;});void run().then(finish,finish);return result;
 }
 function resolve(action:'discard'|'reapply'){
  if(flight||!['EMPTY','ACKNOWLEDGED','CONFLICT','REJECTED'].includes(command.state.value))throw new Error('请先查清原操作结果。');const d=draft.value;if(!d||!saved.value?.saved)throw new Error('当前转写不可用。');
  if(action==='reapply'){
   if(!d.conflict.value)throw new Error('没有待处理的冲突。');const ids=d.conflict.value.value.map(x=>x.segmentId);if(ids.length!==d.value.value.length||ids.some((id,i)=>id!==d.value.value[i]?.segmentId))throw new Error('段落结构已变化，请复制需要保留的文字后读取最新版本。');
  }
  pending=null;command.invalidate();d[action]();sourceRevisionId=saved.value.saved.id;persist();
 }
 return {saved:shallowReadonly(saved),draft:shallowReadonly(draft),loading:shallowReadonly(loading),busy:shallowReadonly(busy),error:shallowReadonly(error),storageStatus:shallowReadonly(storageStatus),recoveryBlocked:shallowReadonly(recoveryBlocked),command,
  reload:()=>reload(),save:()=>mutate('TRANSCRIPT_SAVE'),confirm:()=>mutate('TRANSCRIPT_CONFIRM'),restore:(id:string)=>mutate('TRANSCRIPT_RESTORE',id),retry:()=>{const operation=command.snapshot.value?.operation;if(operation==='TRANSCRIPT_SAVE'||operation==='TRANSCRIPT_CONFIRM'||operation==='TRANSCRIPT_RESTORE')return mutate(operation);return Promise.resolve();},
  edit(value:TranscriptEdits){if(!draft.value||!canEdit()||!edits(value)||value.length!==draft.value.value.value.length||value.some((x,i)=>x.segmentId!==draft.value!.value.value[i]?.segmentId))throw new Error('只能编辑当前完整段落。');draft.value.edit(value);persist();},
  deny(cause:unknown){invalidate();error.value=cause;},discard:()=>resolve('discard'),reapply:()=>resolve('reapply'),logout(){retained=null;if(journal&&!journal.clear())recoveryBlocked.value=true;invalidate();retained=null;},dispose(){disposed=true;stop();invalidate();}
 };
}
