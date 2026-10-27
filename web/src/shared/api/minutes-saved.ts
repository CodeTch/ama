import type {components} from './generated';
import type {createBusinessClient} from './business-client';
import type {CommandSnapshot} from '../composables/useCommand';
import {ApiError} from './client';
import {revisionToken,intentAck} from './intent-contract';
import {fitsUtf8} from '../bounded-text';
import {scalarOffset} from '../scalar-anchors';
import {object,exact,uuid,text,nullableText,observation,evidence,body,memberElements,directories,freeze} from './minutes-wire';
export type MinutesCurrent=components['schemas']['SavedCurrent'];
export type MinutesReceipt=components['schemas']['SavedCommandAck'];
export type MinutesOperation=MinutesReceipt['operation'];
const operations:Record<MinutesOperation,{method:string;suffix:string;result:string}>={
 MINUTES_BODY_SAVE:{method:'PUT',suffix:'body',result:'MINUTES_REVISION'},MINUTES_BODY_RESTORE:{method:'POST',suffix:'body-restorations',result:'MINUTES_REVISION'},MINUTES_SET_RESTORE:{method:'POST',suffix:'element-set-restorations',result:'ELEMENT_SET'},MINUTES_ELEMENT_EDIT:{method:'PUT',suffix:'elements/:id',result:'ELEMENT_SET'},MINUTES_ACTION_FIELDS:{method:'PUT',suffix:'elements/:id/action-fields',result:'ELEMENT_SET'},MINUTES_OPEN_QUESTION:{method:'POST',suffix:'elements/:id/open-question',result:'ELEMENT_SET'},MINUTES_TEMPLATE:{method:'PUT',suffix:'settings/template',result:'TEMPLATE_REVISION'},MINUTES_EXCLUSIONS:{method:'PUT',suffix:'settings/exclusions',result:'EXCLUSION_REVISION'},MINUTES_ADOPT:{method:'POST',suffix:'candidates/:id/adoptions',result:'GENERATION_CANDIDATE'},MINUTES_REJECT:{method:'POST',suffix:'candidates/:id/rejections',result:'GENERATION_CANDIDATE'},MINUTES_DISPOSE:{method:'POST',suffix:'candidates/:id/disposals',result:'GENERATION_CANDIDATE'},MINUTES_REVIEW:{method:'POST',suffix:'reviews',result:'RESULT_REVIEW'}};
export function minutesPath(meeting:string,operation:MinutesOperation,selected?:string){if(!uuid(meeting)||!Object.hasOwn(operations,operation)||operations[operation].suffix.includes(':id')&&!uuid(selected))throw new TypeError('Invalid minutes target');return `/api/v1/meetings/${meeting}/minutes/${operations[operation].suffix.replace(':id',selected??'')}`;}
function anchors(v:unknown){return Array.isArray(v)&&v.length<=10000&&v.every(a=>exact(a,'endToken,evidenceId,startToken')&&uuid(a.evidenceId)&&revisionToken(a.startToken)&&revisionToken(a.endToken)&&BigInt(a.endToken)>BigInt(a.startToken))&&new Set(v.map(a=>a.evidenceId)).size===v.length;}
export function validMinutesCommand(v:unknown,actor:string,meeting:string):v is CommandSnapshot{
 if(!exact(v,'actor,body,key,operation,path,resource')||v.actor!==actor||v.resource!==meeting||typeof v.operation!=='string'||!Object.hasOwn(operations,v.operation)||!text(v.path)||!text(v.key)||!/^[-a-zA-Z0-9_]{1,128}$/.test(v.key)||!text(v.body)||!fitsUtf8(v.body,8*1024*1024))return false;
 try{const operation=v.operation as MinutesOperation,parts=v.path.split('/'),selected=operations[operation].suffix.includes(':id')?parts[7]:undefined;if(v.path!==minutesPath(meeting,operation,selected))return false;const b:unknown=JSON.parse(v.body);if(!object(b)||!revisionToken(b.expectedRevisionToken))return false;
  switch(operation){
   case 'MINUTES_BODY_SAVE':if(!exact(b,'body,evidenceAnchors,expectedRevisionToken,sourceRevisionId,title')||!uuid(b.sourceRevisionId)||!text(b.title)||!text(b.body)||!anchors(b.evidenceAnchors))return false;scalarOffset(b.body,b.body.length);return true;
   case 'MINUTES_ELEMENT_EDIT':case 'MINUTES_OPEN_QUESTION':if(!exact(b,'body,evidenceAnchors,expectedRevisionToken,sourceSetId')||!uuid(b.sourceSetId)||!text(b.body)||!anchors(b.evidenceAnchors))return false;scalarOffset(b.body,b.body.length);return true;
   case 'MINUTES_ACTION_FIELDS':return exact(b,'dueOn,expectedRevisionToken,ownerDisplay,ownerEnterpriseId,sourceSetId')&&uuid(b.sourceSetId)&&nullableText(b.ownerEnterpriseId)&&nullableText(b.ownerDisplay)&&(b.dueOn===null||text(b.dueOn)&&/^\d{4}-\d{2}-\d{2}$/.test(b.dueOn));
   case 'MINUTES_BODY_RESTORE':return exact(b,'expectedRevisionToken,sourceRevisionId')&&uuid(b.sourceRevisionId);
   case 'MINUTES_SET_RESTORE':return exact(b,'expectedRevisionToken,sourceSetId')&&uuid(b.sourceSetId);
   case 'MINUTES_TEMPLATE':return exact(b,'expectedRevisionToken,templateRevisionId')&&uuid(b.templateRevisionId);
   case 'MINUTES_ADOPT':return exact(b,'expectedRevisionToken,inputFingerprint,inputId')&&uuid(b.inputId)&&text(b.inputFingerprint)&&/^[0-9a-f]{64}$/.test(b.inputFingerprint);
   case 'MINUTES_REJECT':case 'MINUTES_DISPOSE':return exact(b,'expectedRevisionToken');
   case 'MINUTES_EXCLUSIONS':return exact(b,'expectedRevisionToken,items,sourceExclusionRevisionId')&&uuid(b.sourceExclusionRevisionId)&&Array.isArray(b.items)&&b.items.length<=10000&&b.items.every(i=>exact(i,'category,locatorId,reason')&&uuid(i.locatorId)&&['POLITICAL_SENSITIVE','INTERNAL_BUSINESS_DATA'].includes(String(i.category))&&nullableText(i.reason));
   case 'MINUTES_REVIEW':{if(!exact(b,b.scope==='SPAN'?'endToken,expectedRevisionToken,outcome,reviewKind,scope,startToken,target':'expectedRevisionToken,outcome,reviewKind,scope,target')||!exact(b.target,'id,kind')||!uuid(b.target.id)||!['BODY','ELEMENT'].includes(String(b.target.kind)))return false;return b.reviewKind==='ACTION_FIELDS'?b.target.kind==='ELEMENT'&&b.scope===null&&b.outcome==='CONFIRMED':b.reviewKind==='SOURCE_SUPPORT'&&['SUPPORTED','PARTIAL','UNSUPPORTED'].includes(String(b.outcome))&&(b.scope==='WHOLE'||b.scope==='SPAN'&&revisionToken(b.startToken)&&revisionToken(b.endToken)&&BigInt(b.endToken)>BigInt(b.startToken));}
  }
 }catch{return false;}
}
function invalid():never{throw new ApiError('response',200,'纪要响应暂无法确认，请保留草稿并重新读取。');}
export function createMinutesSavedApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){return {
 async current(meeting:string,signal:AbortSignal):Promise<MinutesCurrent>{if(!uuid(meeting))throw new TypeError('Invalid meeting');const v=await client.json<unknown>(`/api/v1/meetings/${meeting}/minutes`,{signal,expectedStatus:200,maxResponseBytes:8*1024*1024});
  if(!exact(v,'background,body,elements,evidence,observation,sensitive,setId')||!observation(v.observation)||!(v.body===null||body(v.body))||!(v.setId===null||uuid(v.setId))||!memberElements(v.elements)||!evidence(v.evidence)||!directories(v.sensitive,true)||!directories(v.background,false)||v.observation.currentBodyId!==(v.body===null?null:v.body.id)||v.observation.currentSetId!==v.setId)return invalid();
  const currentBodyId=v.observation.currentBodyId;const elementIds=new Set(Object.values(v.elements).flat().map(e=>e.id));if(v.setId===null&&elementIds.size||v.evidence.some(e=>e.element_id!==null?!elementIds.has(e.element_id):e.minutes_revision_id!==currentBodyId))return invalid();return freeze(v as MinutesCurrent);
 },
 async intent(command:CommandSnapshot){if(actor()!==command.actor||!actor()||command.operation!=='EDIT_INTENT'||command.path!==`/api/v1/meetings/${command.resource}/minutes/editor-intents`||!uuid(command.resource))throw new TypeError('Invalid editor intent');const request:unknown=JSON.parse(command.body);if(!exact(request,'expectedRevisionToken')||!revisionToken(request.expectedRevisionToken))throw new TypeError('Exact intent revision required');const v=await client.json<unknown>(command.path,{method:'POST',headers:{'Idempotency-Key':command.key},body:command.body,expectedStatus:200,maxResponseBytes:65536});if(!intentAck(v,command.resource))return invalid();return freeze(v);},
 async send(command:CommandSnapshot):Promise<MinutesReceipt>{if(!actor()||actor()!==command.actor)throw new DOMException('Minutes actor changed','AbortError');if(!validMinutesCommand(command,command.actor,command.resource))throw new TypeError('Invalid minutes command');const operation=operations[command.operation as MinutesOperation],v=await client.json<unknown>(command.path,{method:operation.method,headers:{'Idempotency-Key':command.key},body:command.body,expectedStatus:200,maxResponseBytes:65536});
  if(!exact(v,'httpStatus,observation,operation,replayed,resultId,resultKind')||v.operation!==command.operation||v.resultKind!==operation.result||!uuid(v.resultId)||v.httpStatus!==200||typeof v.replayed!=='boolean'||!observation(v.observation,true))return invalid();return freeze(v as MinutesReceipt);
 }
};}
