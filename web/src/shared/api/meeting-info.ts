import type {components,operations} from './generated';
import type {createBusinessClient} from './business-client';
import type {CommandSnapshot} from '../composables/useCommand';
import {ApiError} from './client';
import {revisionToken} from './intent-contract';
import {fitsUtf8} from '../bounded-text';
export type SavedMeeting=components['schemas']['Saved'];
export type MeetingMutation=components['schemas']['Mutation'];
export type CreatedMeeting=operations['createMeeting']['responses'][201]['content']['application/json'];
export type InfoFields=Required<Pick<components['schemas']['SaveInfo'],'name'|'category'|'summary'|'occurredOn'|'rosterDraftIds'|'externalRosterNames'>>;
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
const object=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v);
const exact=(v:Record<string,unknown>,keys:string)=>Object.keys(v).sort().join(',')===keys;
const numeric=(v:unknown)=>typeof v==='number'&&Number.isInteger(v)&&v>=0; // Observational legacy duplicate; never used for CAS.
const nullableText=(v:unknown)=>v===null||typeof v==='string';
const nullableUuid=(v:unknown)=>v===null||uuid(v);
function date(v:unknown){if(v===null)return true;if(typeof v!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(v))return false;const t=Date.parse(v+'T00:00:00Z');return Number.isFinite(t)&&new Date(t).toISOString().startsWith(v+'T');}
function member(v:unknown):v is components['schemas']['Member']{return object(v)&&exact(v,'displayName,enterpriseId,memberId,memberKind')&&uuid(v.memberId)&&typeof v.displayName==='string'&&(v.memberKind==='EXTERNAL'?v.enterpriseId===null:v.memberKind==='ENTERPRISE'&&typeof v.enterpriseId==='string'&&v.enterpriseId.length>0);}
function roster(v:unknown):v is components['schemas']['Member'][] {return Array.isArray(v)&&v.length<=128&&v.every(member)&&new Set(v.map(x=>x.memberId)).size===v.length;}
function mutation(v:unknown):v is MeetingMutation & Record<string,unknown> {return object(v)&&uuid(v.id)&&numeric(v.revision)&&revisionToken(v.revisionToken);}
function saved(v:unknown):v is SavedMeeting {
 return object(v)&&exact(v,'accessRevision,accessRevisionToken,category,confirmedRoster,confirmedRosterId,id,name,occurredOn,revision,revisionToken,rosterDraft,rosterDraftId,stage,summary')&&mutation(v)&&numeric(v.accessRevision)&&revisionToken(v.accessRevisionToken)
 &&typeof v.stage==='string'&&['ENTRY','TRANSCRIPT_PENDING','TRANSCRIPT_FAILED','MINUTES_PENDING','MINUTES_FAILED','READY_TO_PUBLISH','PUBLISH_FAILED','PUBLISHED'].includes(v.stage)&&nullableText(v.name)&&nullableText(v.category)&&nullableText(v.summary)&&date(v.occurredOn)
 &&nullableUuid(v.confirmedRosterId)&&roster(v.confirmedRoster)&&(v.confirmedRosterId!==null||v.confirmedRoster.length===0)&&nullableUuid(v.rosterDraftId)&&(v.rosterDraftId===null?v.rosterDraft===null:roster(v.rosterDraft));
}
const identity=(v:unknown):v is string=>typeof v==='string'&&fitsUtf8(v,1020)&&[...v].length<=255&&v.trim().length>0;
function fields(v:Record<string,unknown>){return nullableText(v.name)&&nullableText(v.summary)&&(v.category===null||identity(v.category))&&date(v.occurredOn)&&Array.isArray(v.rosterDraftIds)&&v.rosterDraftIds.every(identity)&&new Set(v.rosterDraftIds).size===v.rosterDraftIds.length&&(v.externalRosterNames===null||Array.isArray(v.externalRosterNames)&&v.externalRosterNames.every(x=>typeof x==='string'&&x.trim().length>0));}
export function saveInfoBody(token:string,value:InfoFields){
 if(!revisionToken(token)||!fields(value))throw new TypeError('Invalid meeting information');
 const result={expectedRevisionToken:token,name:value.name,category:value.category,summary:value.summary,occurredOn:value.occurredOn,rosterDraftIds:[...value.rosterDraftIds],externalRosterNames:value.externalRosterNames===null?null:[...value.externalRosterNames]};Object.freeze(result.rosterDraftIds);if(result.externalRosterNames)Object.freeze(result.externalRosterNames);return Object.freeze(result);
}
function freeze<T>(v:T):T{if(typeof v==='object'&&v!==null){Object.values(v).forEach(freeze);Object.freeze(v);}return v;}
/** Real producer routes. This facade confers no owner/editor authority. */
export function createMeetingInfoApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){
 return {
  async saved(id:string,signal:AbortSignal):Promise<SavedMeeting>{if(!uuid(id))throw new TypeError('Invalid meeting');const value=await client.json<unknown>(`/api/v1/meetings/${id}`,{signal,expectedStatus:200});if(!saved(value)||value.id!==id)throw new ApiError('response',200,'会议信息响应不可用，请重新加载。');return freeze(value);},
  async send(command:CommandSnapshot):Promise<MeetingMutation|CreatedMeeting>{
   if(actor()!==command.actor)throw new DOMException('Command actor changed','AbortError');
   const create=command.operation==='CREATE',save=command.operation==='SAVE_INFO',confirm=command.operation==='CONFIRM_ROSTER';
   if((!create&&!save&&!confirm)||(create?command.resource!=='meetings':!uuid(command.resource)))throw new TypeError('Invalid meeting command');
   const path=create?'/api/v1/meetings':`/api/v1/meetings/${command.resource}/${save?'info':'roster-confirmations'}`;
   if(command.path!==path||!command.key.trim()||command.key.length>2048||/[\r\n]/.test(command.key)||!fitsUtf8(command.body,8*1024*1024))throw new TypeError('Invalid command scope');
   const body:unknown=JSON.parse(command.body);if(!object(body))throw new TypeError('Command object required');
   if(create?Object.keys(body).length!==0:!revisionToken(body.expectedRevisionToken)||Object.keys(body).some(k=>!['expectedRevisionToken',...(save?['name','category','summary','occurredOn','rosterDraftIds','externalRosterNames']:[])].includes(k)))throw new TypeError('Invalid command fields');
   if(save&&!fields({name:null,category:null,summary:null,occurredOn:null,externalRosterNames:[],...body}))throw new TypeError('Invalid information fields');
   const value=await client.json<unknown>(path,{method:save?'PUT':'POST',headers:{'Idempotency-Key':command.key},body:command.body,expectedStatus:create?201:200});
   if(!mutation(value)||!exact(value,create?'id,revision,revisionToken,stage':'id,revision,revisionToken')||(create?value.stage!=='ENTRY':value.id!==command.resource))throw new ApiError('response',create?201:200,'操作结果暂无法确认，请使用原请求重试。');return freeze(value);
  }
 };
}
