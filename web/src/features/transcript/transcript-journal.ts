import {createJournal} from '../../shared/composables/journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
import type {CommandSnapshot} from '../../shared/composables/useCommand';
import {revisionToken} from '../../shared/api/intent-contract';
import type {TranscriptSegment} from '../../shared/api/transcripts';
import {fitsUtf8} from '../../shared/bounded-text';
export type TranscriptEdits=Array<Pick<TranscriptSegment,'segmentId'|'body'|'marks'>>;
export type TranscriptRecovery={base:string;sourceRevisionId:string;saved:TranscriptEdits;value:TranscriptEdits;dirty:boolean;pending:CommandSnapshot|null};
const object=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v);
const exact=(v:Record<string,unknown>,keys:string)=>Object.keys(v).sort().join(',')===keys;
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
export function edits(v:unknown):v is TranscriptEdits{return Array.isArray(v)&&v.length<=100000&&v.every(x=>object(x)&&exact(x,'body,marks,segmentId')&&uuid(x.segmentId)&&typeof x.body==='string'&&Array.isArray(x.marks)&&x.marks.length<=4&&new Set(x.marks).size===x.marks.length&&x.marks.every(m=>['KEY','CONCLUSION','DISPUTE','GENERATION_FOCUS'].includes(m)))&&new Set(v.map(x=>x.segmentId)).size===v.length;}
/** One bounded owner pointer per tab; account/resource switches delete the prior private payload. */
export function createTranscriptJournal(context:TabJournalContext){
 const storage=context.storage,ownerKey='ama.transcript-owner.v1:'+context.tab,cleanupKey='ama.transcript-cleanup.v1:'+context.tab;
 let owner:{actor:string;meeting:string}|null=null;
 let cleanupRequired=false;
 let requestedActor:string|undefined,requestedMeeting:string|null|undefined;
 function noteScope(actor:string|null,meeting?:string|null){
  if(actor!==null){if(owner&&owner.actor!==actor||requestedActor!==undefined&&requestedActor!==actor)cleanupRequired=true;requestedActor=actor;}
  if(meeting!==undefined){if(owner&&owner.meeting!==meeting||requestedMeeting!==undefined&&requestedMeeting!==meeting)cleanupRequired=true;requestedMeeting=meeting;}
  if(cleanupRequired)markCleanup();
 }
 function markCleanup(){cleanupRequired=true;try{storage?.setItem(cleanupKey,'required');}catch{/* A denied write cannot persist a marker; the live barrier remains. */}}
 const valid=(v:unknown,actor:string,meeting:string):v is TranscriptRecovery=>{
  if(!object(v)||!exact(v,'base,dirty,pending,saved,sourceRevisionId,value')||!revisionToken(v.base)||typeof v.dirty!=='boolean'||!uuid(v.sourceRevisionId)||!edits(v.saved)||!edits(v.value))return false;
  if(v.pending===null)return true;
  const c=v.pending;if(!object(c)||!exact(c,'actor,body,key,operation,path,resource')||c.actor!==actor||c.resource!==meeting||typeof c.key!=='string'||!/^[-a-zA-Z0-9_]{1,128}$/.test(c.key)||typeof c.body!=='string')return false;
  const suffix:Record<string,string>={TRANSCRIPT_SAVE:'',TRANSCRIPT_MARKS:'/marks',TRANSCRIPT_RESTORE:'/restorations',TRANSCRIPT_CONFIRM:'/confirmations'};
  if(typeof c.operation!=='string'||!(c.operation in suffix)||c.path!==`/api/v1/meetings/${meeting}/transcript${suffix[c.operation]}`)return false;
  try{const b:unknown=JSON.parse(c.body);if(!object(b)||b.expectedRevisionToken!==v.base)return false;
   if(c.operation==='TRANSCRIPT_CONFIRM')return exact(b,'expectedRevisionToken,savedRevisionId')&&b.savedRevisionId===v.sourceRevisionId;
   if(c.operation==='TRANSCRIPT_RESTORE')return exact(b,'expectedRevisionToken,sourceRevisionId')&&uuid(b.sourceRevisionId);
   return exact(b,'expectedRevisionToken,segments,sourceRevisionId')&&b.sourceRevisionId===v.sourceRevisionId&&edits(b.segments);
  }catch{return false;}
 };
 function journal(actor:string,meeting:string){return createJournal<TranscriptRecovery>(storage!,{actor,meeting,kind:'transcript-editor',tab:context.tab},(v):v is TranscriptRecovery=>valid(v,actor,meeting),8*1024*1024);}
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
 function save(actor:string,meeting:string,value:TranscriptRecovery){
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
