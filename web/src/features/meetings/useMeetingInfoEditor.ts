import {shallowReadonly,shallowRef,watch} from 'vue';
import {useDraft,type DraftTicket} from '../../shared/composables/useDraft';
import {useCommand,type CommandSnapshot} from '../../shared/composables/useCommand';
import {saveInfoBody,type SavedMeeting,type MeetingMutation,type InfoFields} from '../../shared/api/meeting-info';
import {ApiError} from '../../shared/api/client';
import {createInfoJournal} from './info-journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
type Api={saved:(meeting:string,signal:AbortSignal)=>Promise<SavedMeeting>;send:(command:CommandSnapshot)=>Promise<MeetingMutation>};
type Draft=ReturnType<typeof useDraft<InfoFields>>;
type Scope={actor:string;meeting:string};
type Pending={draft:Draft;ticket:DraftTicket<InfoFields>;key:string};
function fields(saved:SavedMeeting):InfoFields{
 const roster=saved.rosterDraft??saved.confirmedRoster;
 return {name:saved.name,category:saved.category,summary:saved.summary,occurredOn:saved.occurredOn,
  rosterDraftIds:roster.flatMap(member=>member.memberKind==='ENTERPRISE'&&member.enterpriseId!==null?[member.enterpriseId]:[]),
  externalRosterNames:roster.filter(member=>member.memberKind==='EXTERNAL').map(member=>member.displayName)};
}
/** In-memory information draft/command coordinator. Persistence and UI adoption are separate work.
 * Caller eligibility only controls this editor; the real producer authorizes every command.
 */
export function useMeetingInfoEditor(api:Api,actor:()=>string|null,meeting:()=>string,eligible:()=>boolean,journals?:TabJournalContext){
 const draft=shallowRef<Draft|null>(null),saved=shallowRef<SavedMeeting|null>(null),loading=shallowRef(false),busy=shallowRef(false),error=shallowRef<unknown>(null),command=useCommand(api.send);
 const persistence=journals?createInfoJournal(journals):null,storageStatus=shallowRef('EMPTY'),recoveryBlocked=shallowRef(false);
 let epoch=0,readEpoch=0,controller:AbortController|null=null,flight:Promise<void>|null=null,disposed=false,resolving=false,invalidating=0;
 let pending:Pending|null=null;
 let scope:Scope|null=null;
 let retained:{scope:Scope;draft:Draft;pending:Pending|null;snapshot:CommandSnapshot|null}|null=null;
 function persist(){
  if(recoveryBlocked.value)return false;
  if(!persistence||!scope||!draft.value)return true;
  const current=draft.value;
  storageStatus.value=persistence.save(scope.actor,scope.meeting,{base:current.baseToken.value,saved:current.saved.value,value:current.value.value,dirty:current.dirty.value,pending:pending?command.snapshot.value:null});
  return storageStatus.value==='SAVED';
 }
 function invalidate(){
  invalidating++;
  try{
   const generation=++epoch,turn=++readEpoch;const current=()=>generation===epoch&&turn===readEpoch;
   // Null identity and temporary denial freeze this scope. A definite new identity/resource destroys it.
   if(draft.value&&scope&&!recoveryBlocked.value)retained={scope,draft:draft.value,pending,snapshot:command.snapshot.value};
   if(retained&&(retained.scope.meeting!==meeting()||actor()!==null&&retained.scope.actor!==actor())){retained=null;if(persistence&&!persistence.clear())recoveryBlocked.value=true;}
   if(disposed)retained=null;
   const old=controller;controller=null;pending=null;scope=null;flight=null;old?.abort();
   draft.value=null;if(!current())return;saved.value=null;if(!current())return;
   command.invalidate();if(!current())return;error.value=null;if(!current())return;loading.value=false;if(!current())return;busy.value=false;
  }finally{invalidating--;}
 }
 const stop=watch(()=>[actor(),meeting(),eligible()],invalidate,{flush:'sync'});
 async function read(afterSave=false){
  if(invalidating>0){await Promise.resolve();return read(afterSave);}
  if(disposed||flight&&!afterSave)return;const who=actor(),id=meeting();if(!who||!eligible()){invalidate();return;}
  const generation=epoch,turn=++readEpoch,previous=controller,current=new AbortController();controller=current;previous?.abort();
  const active=()=>!disposed&&generation===epoch&&turn===readEpoch&&actor()===who&&meeting()===id&&eligible()&&!current.signal.aborted;
  try{if(!active())return;saved.value=null;if(!active())return;error.value=null;if(!active())return;loading.value=true;if(!active())return;
   const result=await api.saved(id,current.signal);if(!active())return;saved.value=result;if(!active())return;
   if(!draft.value||recoveryBlocked.value){
    const frozen=retained;
    invalidating++;
    try{
     if(frozen&&frozen.scope.actor===who&&frozen.scope.meeting===id){
      // Restore the actual ticket and immutable bytes only after a fresh authorized read.
      if(frozen.pending){if(!frozen.snapshot)throw new Error('Original information command missing');command.restore(frozen.snapshot);if(!active())return;}
      pending=frozen.pending;scope=frozen.scope;draft.value=frozen.draft;if(!active())return;retained=null;
      if(!pending&&(!frozen.draft.dirty.value||frozen.draft.baseToken.value!==result.revisionToken))frozen.draft.observe(fields(result),result.revisionToken);
     }else{
      scope={actor:who,meeting:id};draft.value=useDraft(fields(result),result.revisionToken,()=>!disposed&&actor()===who&&meeting()===id&&eligible());
      if(persistence){
       const recovered=persistence.load(who,id);storageStatus.value=recovered.kind;recoveryBlocked.value=['INVALID','UNAVAILABLE'].includes(recovered.kind);
       if(recovered.kind==='RESTORED'){
        const record=recovered.value,current=draft.value;current.reset(record.saved,record.base);
        if(record.pending){
         const {expectedRevisionToken,...bodyFields}=JSON.parse(record.pending.body) as InfoFields&{expectedRevisionToken:string};
         const original=record.pending.operation==='SAVE_INFO'?bodyFields:record.saved;
         current.edit(original);const ticket=current.capture();command.restore(record.pending);pending={draft:current,ticket,key:record.pending.key};
         if(JSON.stringify(record.value)!==JSON.stringify(original))current.edit(record.value);
        }else{if(record.dirty)current.edit(record.value);if(!record.dirty||record.base!==result.revisionToken)current.observe(fields(result),result.revisionToken);}
       }
      }
     }
    }finally{invalidating--;}
   }
   else if(!pending||['CONFLICT','REJECTED'].includes(command.state.value)){
    const currentDraft=draft.value;if(!currentDraft.dirty.value||currentDraft.baseToken.value!==result.revisionToken)currentDraft.observe(fields(result),result.revisionToken);
   }
  }catch(cause){if(active()){if(cause instanceof ApiError&&cause.kind==='http'&&[401,403].includes(cause.status??0)){const deniedEpoch=epoch+1,deniedRead=readEpoch+1;invalidate();if(epoch===deniedEpoch&&readEpoch===deniedRead)error.value=cause;}else error.value=cause;}}
  finally{if(active()){controller=null;loading.value=false;}}
 }
 function save(operation:'SAVE_INFO'|'CONFIRM_ROSTER'='SAVE_INFO'):Promise<void>{
  if(disposed||resolving||invalidating>0)return Promise.resolve();if(flight)return flight;const who=actor(),id=meeting(),generation=epoch;
  const active=()=>!disposed&&generation===epoch&&actor()===who&&meeting()===id&&eligible();
  const run=async()=>{
   try{if(!who||!active()||!draft.value||recoveryBlocked.value)throw new Error('Current editable information and recovery required');error.value=null;if(!active())return;busy.value=true;if(!active())return;
    if(command.state.value==='EMPTY'||command.state.value==='ACKNOWLEDGED'){
     const current=draft.value;
     if(operation==='CONFIRM_ROSTER'&&(current.dirty.value||current.conflict.value||!saved.value?.rosterDraftId))throw new Error('请先保存名单草稿，再单独确认。');
     const ticket=current.capture(),body=operation==='SAVE_INFO'?saveInfoBody(ticket.baseToken,ticket.value):{expectedRevisionToken:ticket.baseToken},key=crypto.randomUUID();pending={draft:current,ticket,key};
     command.prepare({actor:who,operation,resource:id,path:`/api/v1/meetings/${id}/${operation==='SAVE_INFO'?'info':'roster-confirmations'}`,key,body});
    }
    if(command.snapshot.value?.operation!==operation)throw new Error('请先恢复原操作。');
    if(!persist())throw new Error('无法保存恢复记录，请保持页面打开并重试。');
    if(!active())return;const sent=pending;if(!sent||command.snapshot.value?.key!==sent.key)throw new Error('Original information command missing');
    const ack=await command.send();if(!active()||pending!==sent||draft.value!==sent.draft)return;
    // ACK is known: accept consumes the ticket before publishing reactive draft fields.
    // A synchronous scope freeze during that publication must not archive it as pending.
    pending=null;sent.draft.accept(sent.ticket,sent.ticket.value,ack.revisionToken);if(!active())return;persist();await read(true);
   }catch(cause){if(active()){
    if(cause instanceof ApiError&&cause.kind==='http'&&[401,403].includes(cause.status??0)){
     const deniedEpoch=epoch+1,deniedRead=readEpoch+1;invalidate();if(epoch===deniedEpoch&&readEpoch===deniedRead)error.value=cause;
    }else error.value=cause;
   }}
   finally{if(active())busy.value=false;}
  };
  let finish!:()=>void;const result=new Promise<void>(resolve=>{finish=resolve;});flight=result;void result.then(()=>{if(generation===epoch)flight=null;});void run().then(finish,finish);return result;
 }
 function resolveLocal(action:'discard'|'reapply'){
  if(resolving||flight||!['EMPTY','ACKNOWLEDGED','CONFLICT','REJECTED'].includes(command.state.value))throw new Error('Resolve the original save first');
  const current=draft.value,generation=epoch;if(!current)return;if(action==='reapply'&&!current.conflict.value)throw new Error('No current conflict');
  resolving=true;try{pending=null;command.invalidate();if(generation===epoch&&draft.value===current){current[action]();persist();}}finally{resolving=false;}
 }
 function reapply(){resolveLocal('reapply');}
 function discard(){resolveLocal('discard');}
 return {draft:shallowReadonly(draft),saved:shallowReadonly(saved),loading:shallowReadonly(loading),busy:shallowReadonly(busy),error:shallowReadonly(error),command,storageStatus:shallowReadonly(storageStatus),recoveryBlocked:shallowReadonly(recoveryBlocked),
  reload:()=>read(),save:()=>save(),confirmRoster:()=>save('CONFIRM_ROSTER'),edit(value:InfoFields){if(!draft.value||recoveryBlocked.value)throw new Error('Information not loaded or recovery blocked');draft.value.edit(value);persist();},reapply,discard,
  logout(){retained=null;if(persistence)recoveryBlocked.value=!persistence.clear();invalidate();retained=null;},
  dispose(){disposed=true;stop();invalidate();}};
}
