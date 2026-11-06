import {fitsUtf8} from '../../shared/bounded-text';
import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';import type {CommandSnapshot} from '../../shared/composables/useCommand';
import {exact,uuid,text,nullableText} from '../../shared/api/minutes-wire';import {revisionToken} from '../../shared/api/intent-contract';import {validMinutesCommand} from '../../shared/api/minutes-saved';import type {EvidenceAnchor} from './minutes-anchors';
export type MinutesTarget={kind:'BODY'|'ELEMENT'|'ACTION';id:string;category?:string;ordinalToken?:string;ownerEnterpriseId?:string|null};export type MinutesText={title:string;body:string};
export type MinutesRecovery={target:MinutesTarget;sourceId:string;base:string;saved:MinutesText;value:MinutesText;dirty:boolean;anchors:readonly EvidenceAnchor[];pending:CommandSnapshot|null};
export function validText(v:unknown):v is MinutesText{return exact(v,'body,title')&&text(v.title)&&text(v.body);}
function validRecovery(v:unknown,actor:string,meeting:string):v is MinutesRecovery{
 if(!exact(v,'anchors,base,dirty,pending,saved,sourceId,target,value')||!(exact(v.target,'id,kind')&&v.target.kind==='BODY'||exact(v.target,'category,id,kind,ordinalToken')&&v.target.kind==='ELEMENT'&&text(v.target.category)&&revisionToken(v.target.ordinalToken)||exact(v.target,'category,id,kind,ordinalToken,ownerEnterpriseId')&&v.target.kind==='ACTION'&&v.target.category==='ACTION'&&revisionToken(v.target.ordinalToken)&&nullableText(v.target.ownerEnterpriseId))||!['BODY','ELEMENT','ACTION'].includes(String(v.target.kind))||!uuid(v.target.id)||!uuid(v.sourceId)||!revisionToken(v.base)||!validText(v.saved)||!validText(v.value)||typeof v.dirty!=='boolean'||!Array.isArray(v.anchors)||v.anchors.length>10000||!v.anchors.every(a=>exact(a,'endToken,evidenceId,quote,startToken')&&uuid(a.evidenceId)&&text(a.quote)&&revisionToken(a.startToken)&&revisionToken(a.endToken)&&BigInt(a.endToken)>BigInt(a.startToken))||new Set(v.anchors.map(a=>a.evidenceId)).size!==v.anchors.length)return false;
 if(v.pending===null)return true;if(!validMinutesCommand(v.pending,actor,meeting))return false;const b=JSON.parse(v.pending.body);return b.expectedRevisionToken===v.base&&(v.target.kind==='BODY'?v.pending.operation==='MINUTES_BODY_SAVE'&&b.sourceRevisionId===v.sourceId:(v.target.kind==='ACTION'?v.pending.operation==='MINUTES_ACTION_FIELDS'&&b.ownerEnterpriseId===v.target.ownerEnterpriseId&&v.pending.path.endsWith('/'+v.target.id+'/action-fields'):v.pending.operation==='MINUTES_ELEMENT_EDIT'&&v.pending.path.endsWith('/'+v.target.id))&&b.sourceSetId===v.sourceId);
}
type Checkpoint={generation:string;record:MinutesRecovery};
type DraftChange={generation:string;start:number;end:number;insert:string;title:string;dirty:boolean};
function isCheckpoint(v:unknown,actor:string,meeting:string):v is Checkpoint{
 return exact(v,'generation,record')&&uuid(v.generation)&&validRecovery(v.record,actor,meeting);
}
function isChange(v:unknown):v is DraftChange{
 return exact(v,'dirty,end,generation,insert,start,title')&&uuid(v.generation)&&Number.isSafeInteger(v.start)&&Number.isSafeInteger(v.end)&&
  Number(v.start)>=0&&Number(v.end)>=Number(v.start)&&text(v.title)&&text(v.insert)&&typeof v.dirty==='boolean';
}
/** The full checkpoint commits first. An old edit can never apply to a new checkpoint,
 * even if storage fails between writes. Existing single-record journals remain readable. */
export function createMinutesEditorJournal(context:TabJournalContext){
 const full=createScopedPrivateJournal<MinutesRecovery|Checkpoint>(context,'minutes-editor',
  (v,actor,meeting):v is MinutesRecovery|Checkpoint=>validRecovery(v,actor,meeting)||isCheckpoint(v,actor,meeting));
 const changes=createScopedPrivateJournal<DraftChange>(context,'minutes-editor-change',isChange);
 let active:{actor:string;meeting:string;checkpoint:Checkpoint}|null=null;
 function load(actor:string,meeting:string){
  active=null;
  const found=full.load(actor,meeting);
  if(found.kind!=='RESTORED')return found;
  if(!('generation' in found.value))return {kind:'RESTORED' as const,value:found.value};
  const checkpoint=found.value,record=checkpoint.record,edit=changes.load(actor,meeting);
  if(edit.kind==='INVALID'||edit.kind==='UNAVAILABLE')return {kind:edit.kind};
  active={actor,meeting,checkpoint};
  if(edit.kind!=='RESTORED'||edit.value.generation!==checkpoint.generation)return {kind:'RESTORED' as const,value:record};
  const patch=edit.value,original=record.value.body;
  if(patch.end>original.length||original.length-(patch.end-patch.start)+patch.insert.length>8*1024*1024)return {kind:'INVALID' as const};
  const body=original.slice(0,patch.start)+patch.insert+original.slice(patch.end);
  if(!fitsUtf8(body,8*1024*1024))return {kind:'INVALID' as const};
  return {kind:'RESTORED' as const,value:Object.freeze({...record,dirty:patch.dirty,value:Object.freeze({title:patch.title,body})})};
 }
 function save(actor:string,meeting:string,record:MinutesRecovery){
  const checkpoint=active?.actor===actor&&active.meeting===meeting?active.checkpoint:null,prior=checkpoint?.record;
  if(checkpoint&&prior&&record.sourceId===prior.sourceId&&record.base===prior.base&&record.target===prior.target&&record.saved===prior.saved&&record.anchors===prior.anchors&&record.pending===prior.pending){
   const before=prior.value.body,after=record.value.body,limit=Math.min(before.length,after.length);
   let start=0,suffix=0;
   while(start<limit&&before.charCodeAt(start)===after.charCodeAt(start))start++;
   while(suffix<limit-start&&before.charCodeAt(before.length-1-suffix)===after.charCodeAt(after.length-1-suffix))suffix++;
   return changes.save(actor,meeting,{generation:checkpoint.generation,start,end:before.length-suffix,insert:after.slice(start,after.length-suffix),title:record.value.title,dirty:record.dirty});
  }
  if(record.value.body.length<16384&&record.anchors.length<128){
   const status=full.save(actor,meeting,record);
   if(status==='SAVED')active=null;
   return status;
  }
  const next={generation:crypto.randomUUID(),record},status=full.save(actor,meeting,next);
  if(status==='SAVED')active={actor,meeting,checkpoint:next};
  return status;
 }
 function clear(){active=null;const edits=changes.clear();return full.clear()&&edits;}
 function synchronize(actor:string|null,meeting?:string|null){
  if(active&&(actor===null||actor!==active.actor||meeting!==undefined&&meeting!==active.meeting))active=null;
  const edits=changes.synchronize(actor,meeting);
  return full.synchronize(actor,meeting)&&edits;
 }
 return {load,save,clear,synchronize};
}
