import type {components} from './generated';
import type {createBusinessClient} from './business-client';
import type {CommandSnapshot} from '../composables/useCommand';
import {revisionToken} from './intent-contract';
import {ApiError} from './client';
import {fitsUtf8} from '../bounded-text';
export type GenerationCurrent=components['schemas']['MinutesCurrent'];
export type GenerationTemplates=components['schemas']['MinutesTemplates'];
export type GenerationReceipt=components['schemas']['MinutesInitialReceipt']|components['schemas']['MinutesGenerationReceipt'];
const object=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v);
const exact=(v:unknown,keys:string):v is Record<string,unknown>=>object(v)&&Object.keys(v).sort().join(',')===keys;
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
const nullableId=(v:unknown)=>v===null||uuid(v),number=(v:unknown)=>typeof v==='number'&&Number.isInteger(v)&&v>=0;
function observation(v:unknown):v is components['schemas']['MinutesObservation']{return exact(v,'accessRevision,accessRevisionToken,currentConfirmationId,currentMinutesRevisionId,revision,revisionToken')&&number(v.revision)&&number(v.accessRevision)&&revisionToken(v.revisionToken)&&revisionToken(v.accessRevisionToken)&&nullableId(v.currentConfirmationId)&&nullableId(v.currentMinutesRevisionId);}
function invalid():never{throw new ApiError('response',200,'纪要生成状态暂无法确认，请重新读取。');}
function freeze<T>(v:T):T{if(v!==null&&typeof v==='object'){Object.values(v).forEach(freeze);Object.freeze(v);}return v;}
export function validGenerationCommand(v:unknown,actor:string,meeting:string):v is CommandSnapshot{
 if(!exact(v,'actor,body,key,operation,path,resource')||v.actor!==actor||v.resource!==meeting||typeof v.key!=='string'||!/^[-a-zA-Z0-9_]{1,128}$/.test(v.key)||typeof v.body!=='string'||!fitsUtf8(v.body,65536))return false;
 const initial=v.operation==='MINUTES_INITIAL';if(!initial&&v.operation!=='MINUTES_GENERATE'||v.path!==`/api/v1/meetings/${meeting}/minutes/${initial?'initial-settings':'generations'}`)return false;
 try{const b:unknown=JSON.parse(v.body);return exact(b,initial?'expectedRevisionToken,templateRevisionId':'expectedRevisionToken,scope')&&revisionToken(b.expectedRevisionToken)&&(initial?uuid(b.templateRevisionId):b.scope==='FULL');}catch{return false;}
}
export function createMinutesGenerationApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){return {
 async current(meeting:string,signal:AbortSignal):Promise<GenerationCurrent>{
  if(!uuid(meeting))throw new TypeError('Invalid meeting');const v=await client.json<unknown>(`/api/v1/meetings/${meeting}/minutes/generation-inputs/current`,{signal,expectedStatus:200,maxResponseBytes:65536});
  if(!exact(v,'activeTaskIds,eligible,materials,observation,reasons,saved,scope')||!observation(v.observation)||!exact(v.saved,'elementSetId,exclusionRevisionId,templateRevisionId,transcriptConfirmationId')||!Object.values(v.saved).every(nullableId)||v.scope!=='FULL'||!['EMPTY','PINNED','PENDING'].includes(v.materials as string)||typeof v.eligible!=='boolean'||!Array.isArray(v.reasons)||v.reasons.length>64||!v.reasons.every(x=>typeof x==='string'&&fitsUtf8(x,256))||!Array.isArray(v.activeTaskIds)||v.activeTaskIds.length>1000||!v.activeTaskIds.every(uuid)||new Set(v.activeTaskIds).size!==v.activeTaskIds.length||v.eligible&&v.reasons.length!==0||v.materials==='PENDING'&&v.eligible)return invalid();return freeze(v as GenerationCurrent);
 },
 async templates(meeting:string,cursor:string|null,signal:AbortSignal):Promise<GenerationTemplates>{
  if(!uuid(meeting)||cursor!==null&&(typeof cursor!=='string'||!fitsUtf8(cursor,4096)))throw new TypeError('Invalid template request');const query=new URLSearchParams({limit:'20'});if(cursor!==null)query.set('cursor',cursor);
  const v=await client.json<unknown>(`/api/v1/meetings/${meeting}/minutes/templates?${query}`,{signal,expectedStatus:200,maxResponseBytes:1024*1024});
  if(!exact(v,'items,nextCursor,observation')||!observation(v.observation)||!(v.nextCursor===null||typeof v.nextCursor==='string'&&fitsUtf8(v.nextCursor,4096))||!Array.isArray(v.items)||v.items.length>50||!v.items.every(x=>exact(x,'default,name,revisionId,selectable,templateId,themeCount,themes')&&uuid(x.templateId)&&uuid(x.revisionId)&&typeof x.name==='string'&&fitsUtf8(x.name,16384)&&typeof x.selectable==='boolean'&&typeof x.default==='boolean'&&x.themeCount===7&&Array.isArray(x.themes)&&x.themes.length===7&&new Set(x.themes).size===7&&x.themes.every(t=>['OVERVIEW','DISCUSSION','CONCLUSION','ACTION','RISK','OPEN_QUESTION','DISAGREEMENT'].includes(t)))||new Set(v.items.map(x=>x.revisionId)).size!==v.items.length)return invalid();return freeze(v as GenerationTemplates);
 },
 async send(command:CommandSnapshot):Promise<GenerationReceipt>{
  if(!actor()||actor()!==command.actor)throw new DOMException('Generation actor changed','AbortError');if(!validGenerationCommand(command,command.actor,command.resource)||!uuid(command.resource))throw new TypeError('Invalid generation command');
  const initial=command.operation==='MINUTES_INITIAL',v=await client.json<unknown>(command.path,{method:'POST',headers:{'Idempotency-Key':command.key},body:command.body,expectedStatus:initial?200:202,maxResponseBytes:65536});
  if(!exact(v,'observation,replayed,result')||!observation(v.observation)||typeof v.replayed!=='boolean'||!object(v.result)||v.result.meetingId!==command.resource)return invalid();
  if(initial?!(exact(v.result,'exclusionRevisionId,kind,meetingId,templateRevisionId')&&v.result.kind==='INITIAL_MINUTES_SETTINGS'&&uuid(v.result.templateRevisionId)&&uuid(v.result.exclusionRevisionId)):!(exact(v.result,'inputFingerprint,inputId,kind,meetingId,taskId')&&v.result.kind==='GENERATION_TASK'&&uuid(v.result.inputId)&&uuid(v.result.taskId)&&typeof v.result.inputFingerprint==='string'&&/^[0-9a-f]{64}$/.test(v.result.inputFingerprint)))return invalid();return freeze(v as GenerationReceipt);
 }
};}
