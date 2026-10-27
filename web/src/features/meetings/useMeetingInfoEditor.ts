import {shallowReadonly,shallowRef,watch} from 'vue';
import {useDraft,type DraftTicket} from '../../shared/composables/useDraft';
import {useCommand,type CommandSnapshot} from '../../shared/composables/useCommand';
import {saveInfoBody,type SavedMeeting,type MeetingMutation,type InfoFields} from '../../shared/api/meeting-info';
import {ApiError} from '../../shared/api/client';
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
export function useMeetingInfoEditor(api:Api,actor:()=>string|null,meeting:()=>string,eligible:()=>boolean){
 const draft=shallowRef<Draft|null>(null),saved=shallowRef<SavedMeeting|null>(null),loading=shallowRef(false),busy=shallowRef(false),error=shallowRef<unknown>(null),command=useCommand(api.send);
 let epoch=0,readEpoch=0,controller:AbortController|null=null,flight:Promise<void>|null=null,disposed=false,resolving=false,invalidating=0;
 let pending:Pending|null=null;
 let scope:Scope|null=null;
 let retained:{scope:Scope;draft:Draft;pending:Pending|null;snapshot:CommandSnapshot|null}|null=null;
 function invalidate(){
  invalidating++;
  try{
   const generation=++epoch,turn=++readEpoch;const current=()=>generation===epoch&&turn===readEpoch;
   // Null identity and temporary denial freeze this scope. A definite new identity/resource destroys it.
   if(draft.value&&scope)retained={scope,draft:draft.value,pending,snapshot:command.snapshot.value};
   if(disposed||retained&&(retained.scope.meeting!==meeting()||actor()!==null&&retained.scope.actor!==actor()))retained=null;
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
   if(!draft.value){
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
     }
    }finally{invalidating--;}
   }
   else if(!pending||['CONFLICT','REJECTED'].includes(command.state.value)){
    const currentDraft=draft.value;if(!currentDraft.dirty.value||currentDraft.baseToken.value!==result.revisionToken)currentDraft.observe(fields(result),result.revisionToken);
   }
  }catch(cause){if(active()){if(cause instanceof ApiError&&cause.kind==='http'&&[401,403].includes(cause.status??0)){const deniedEpoch=epoch+1,deniedRead=readEpoch+1;invalidate();if(epoch===deniedEpoch&&readEpoch===deniedRead)error.value=cause;}else error.value=cause;}}
  finally{if(active()){controller=null;loading.value=false;}}
 }
 function save():Promise<void>{
  if(disposed||resolving||invalidating>0)return Promise.resolve();if(flight)return flight;const who=actor(),id=meeting(),generation=epoch;
  const active=()=>!disposed&&generation===epoch&&actor()===who&&meeting()===id&&eligible();
  const run=async()=>{
   try{if(!who||!active()||!draft.value)throw new Error('Current editable information required');error.value=null;if(!active())return;busy.value=true;if(!active())return;
    if(command.state.value==='EMPTY'||command.state.value==='ACKNOWLEDGED'){
     const current=draft.value,ticket=current.capture(),body=saveInfoBody(ticket.baseToken,ticket.value),key=crypto.randomUUID();pending={draft:current,ticket,key};
     command.prepare({actor:who,operation:'SAVE_INFO',resource:id,path:`/api/v1/meetings/${id}/info`,key,body});
    }
    if(!active())return;const sent=pending;if(!sent||command.snapshot.value?.key!==sent.key)throw new Error('Original information command missing');
    const ack=await command.send();if(!active()||pending!==sent||draft.value!==sent.draft)return;
    // ACK is known: accept consumes the ticket before publishing reactive draft fields.
    // A synchronous scope freeze during that publication must not archive it as pending.
    pending=null;sent.draft.accept(sent.ticket,sent.ticket.value,ack.revisionToken);if(!active())return;await read(true);
   }catch(cause){if(active())error.value=cause;}
   finally{if(active())busy.value=false;}
  };
  let finish!:()=>void;const result=new Promise<void>(resolve=>{finish=resolve;});flight=result;void result.then(()=>{if(generation===epoch)flight=null;});void run().then(finish,finish);return result;
 }
 function resolveLocal(action:'discard'|'reapply'){
  if(resolving||flight||!['EMPTY','ACKNOWLEDGED','CONFLICT','REJECTED'].includes(command.state.value))throw new Error('Resolve the original save first');
  const current=draft.value,generation=epoch;if(!current)return;if(action==='reapply'&&!current.conflict.value)throw new Error('No current conflict');
  resolving=true;try{pending=null;command.invalidate();if(generation===epoch&&draft.value===current)current[action]();}finally{resolving=false;}
 }
 function reapply(){resolveLocal('reapply');}
 function discard(){resolveLocal('discard');}
 return {draft:shallowReadonly(draft),saved:shallowReadonly(saved),loading:shallowReadonly(loading),busy:shallowReadonly(busy),error:shallowReadonly(error),command,
  reload:()=>read(),save,edit(value:InfoFields){if(!draft.value)throw new Error('Information not loaded');draft.value.edit(value);},reapply,discard,
  dispose(){disposed=true;stop();invalidate();}};
}
