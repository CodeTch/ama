import {createJournal} from '../../shared/composables/journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
import type {CommandSnapshot} from '../../shared/composables/useCommand';
import {saveInfoBody,type InfoFields} from '../../shared/api/meeting-info';
import {fitsUtf8} from '../../shared/bounded-text';
export type InfoRecovery={base:string;saved:InfoFields;value:InfoFields;dirty:boolean;pending:CommandSnapshot|null};
const object=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v);
const exact=(v:Record<string,unknown>,keys:string)=>Object.keys(v).sort().join(',')===keys;
function info(v:unknown,token:string):v is InfoFields {
 if(!object(v)||!exact(v,'category,externalRosterNames,name,occurredOn,rosterDraftIds,summary'))return false;
 try{saveInfoBody(token,v as InfoFields);return true;}catch{return false;}
}
/** One bounded owner pointer per tab; account/resource switches delete the prior private payload. */
export function createInfoJournal(context:TabJournalContext){
 const storage=context.storage,ownerKey='ama.info-owner.v1:'+context.tab,cleanupKey='ama.info-cleanup.v1:'+context.tab;
 let owner:{actor:string;meeting:string}|null=null;
 let cleanupRequired=false;
 let requestedActor:string|undefined,requestedMeeting:string|null|undefined;
 function noteScope(actor:string|null,meeting?:string|null){
  if(actor!==null){if(owner&&owner.actor!==actor||requestedActor!==undefined&&requestedActor!==actor)cleanupRequired=true;requestedActor=actor;}
  if(meeting!==undefined){if(owner&&owner.meeting!==meeting||requestedMeeting!==undefined&&requestedMeeting!==meeting)cleanupRequired=true;requestedMeeting=meeting;}
  if(cleanupRequired)markCleanup();
 }
 function markCleanup(){cleanupRequired=true;try{storage?.setItem(cleanupKey,'required');}catch{/* A denied write cannot persist a marker; the live barrier remains. */}}
 const valid=(v:unknown,actor:string,meeting:string):v is InfoRecovery=>{
  if(!object(v)||!exact(v,'base,dirty,pending,saved,value')||typeof v.base!=='string'||typeof v.dirty!=='boolean'||!info(v.saved,v.base)||!info(v.value,v.base))return false;
  if(v.pending===null)return true;
  const c=v.pending;if(!object(c)||!exact(c,'actor,body,key,operation,path,resource')||c.actor!==actor||c.resource!==meeting||!['SAVE_INFO','CONFIRM_ROSTER'].includes(String(c.operation))||c.path!==`/api/v1/meetings/${meeting}/${c.operation==='SAVE_INFO'?'info':'roster-confirmations'}`||typeof c.key!=='string'||!/^[-a-zA-Z0-9_]{1,128}$/.test(c.key)||typeof c.body!=='string')return false;
  try{const b:unknown=JSON.parse(c.body);if(!object(b)||b.expectedRevisionToken!==v.base)return false;if(c.operation==='CONFIRM_ROSTER')return exact(b,'expectedRevisionToken');if(!exact(b,'category,expectedRevisionToken,externalRosterNames,name,occurredOn,rosterDraftIds,summary'))return false;const {expectedRevisionToken,...fields}=b;return info(fields,v.base);}catch{return false;}
 };
 function journal(actor:string,meeting:string){return createJournal<InfoRecovery>(storage!,{actor,meeting,kind:'info-editor',tab:context.tab},(v):v is InfoRecovery=>valid(v,actor,meeting),8*1024*1024);}
 function readOwner(){
  if(!storage)throw new Error('Recovery storage unavailable');const marker=storage.getItem(cleanupKey);if(marker!==null){if(marker!=='required')throw new Error('Invalid cleanup marker');cleanupRequired=true;}const raw=storage.getItem(ownerKey);if(raw===null){owner=null;return;}
  if(!fitsUtf8(raw,4096))throw new Error('Invalid recovery owner');const value:unknown=JSON.parse(raw);
  if(!object(value)||!(exact(value,'actor,meeting')||exact(value,'actor,meeting,revoked')&&value.revoked===true)||typeof value.actor!=='string'||!value.actor||!fitsUtf8(value.actor,1020)||typeof value.meeting!=='string'||!/^[0-9a-f-]{36}$/.test(value.meeting))throw new Error('Invalid recovery owner');
  owner={actor:value.actor,meeting:value.meeting};
  if(value.revoked===true)cleanupRequired=true;
 }
 function clear(){
  markCleanup();
  try{readOwner();if(owner){
   // Persist the obligation before deleting payload. Failed deletion must not revive it on reload.
   try{storage!.setItem(ownerKey,JSON.stringify({...owner,revoked:true}));}catch{/* Keep the in-memory barrier even if all storage writes are unavailable. */}
   if(journal(owner.actor,owner.meeting).clear().kind!=='CLEARED')return false;
  }storage!.removeItem(ownerKey);storage!.removeItem(cleanupKey);owner=null;cleanupRequired=false;return true;}catch{return false;}
 }
 function load(actor:string,meeting:string){
  noteScope(actor,meeting);
  if(context.recovery==='UNAVAILABLE'||!storage)return {kind:'UNAVAILABLE' as const};
  try{readOwner();if((cleanupRequired||owner&&(owner.actor!==actor||owner.meeting!==meeting))&&!clear())return {kind:'UNAVAILABLE' as const};
   const found=journal(actor,meeting).load();if(found.kind==='RESTORED'&&!owner||found.kind==='EMPTY'&&owner)return {kind:'INVALID' as const};return found;
  }catch{return {kind:'UNAVAILABLE' as const};}
 }
 function save(actor:string,meeting:string,value:InfoRecovery){
  noteScope(actor,meeting);
  if(!storage)return 'UNAVAILABLE' as const;
  try{readOwner();if((cleanupRequired||owner&&(owner.actor!==actor||owner.meeting!==meeting))&&!clear())return 'UNAVAILABLE' as const;
   storage.setItem(ownerKey,JSON.stringify({actor,meeting}));owner={actor,meeting};return journal(actor,meeting).save(value).kind;
  }catch{return 'UNAVAILABLE' as const;}
 }
 function synchronize(actor:string|null,meeting?:string|null){
  noteScope(actor,meeting);
  try{readOwner();if(cleanupRequired||owner&&(actor!==null&&owner.actor!==actor||meeting!==undefined&&owner.meeting!==meeting))return clear();return true;}catch{return false;}
 }
 return {load,save,clear,synchronize};
}
