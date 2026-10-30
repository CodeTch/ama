import {IMPORT_FORMATS,type ImportFormat} from '../document-formats';
import type {components} from './generated';
import type {createBusinessClient} from './business-client';
import type {CommandSnapshot} from '../composables/useCommand';
import {ApiError} from './client';
import {revisionToken} from './intent-contract';
import {fitsUtf8} from '../bounded-text';
export type ImportAccepted=components['schemas']['ImportAccepted'];
export type ProcessingCapabilities=components['schemas']['Capabilities'];
const shape=(v:unknown,keys:string):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v)&&Object.keys(v).sort().join(',')===keys;
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
function invalid():never{throw new ApiError('response',200,'导入状态暂无法确认，请保留原请求并重试。');}
export function createTranscriptImportApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){return {
 async capabilities(meeting:string,signal:AbortSignal):Promise<ProcessingCapabilities>{
  if(!uuid(meeting))throw new TypeError('Invalid meeting');const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/processing-capabilities`,{signal,expectedStatus:200,maxResponseBytes:65536});
  if(!shape(value,'accessRevision,accessRevisionToken,budgets,businessReady,canSubmit,execution,formats,intake,materialMode,purpose,readOnlyReason,revision,revisionToken')||typeof value.revision!=='number'||!Number.isInteger(value.revision)||value.revision<0||!revisionToken(value.revisionToken)||typeof value.accessRevision!=='number'||!Number.isInteger(value.accessRevision)||value.accessRevision<0||!revisionToken(value.accessRevisionToken)||typeof value.canSubmit!=='boolean'||typeof value.businessReady!=='boolean'||!(value.readOnlyReason===null||typeof value.readOnlyReason==='string')||!(value.intake==='AVAILABLE'||value.intake==='UNAVAILABLE')||!(value.execution==='INSTALLED'||value.execution==='NOT_INSTALLED'||value.execution==='UNAVAILABLE')||!Array.isArray(value.formats)||value.formats.length>IMPORT_FORMATS.length||new Set(value.formats).size!==value.formats.length||!value.formats.every(x=>IMPORT_FORMATS.some(f=>f===x))||value.purpose!=='TRANSCRIPT_INPUT'||value.materialMode!=='EMPTY'||typeof value.budgets!=='object'||value.budgets===null||Array.isArray(value.budgets))return invalid();
  const budgets:Record<string,string|number>={};for(const [key,limit] of Object.entries(value.budgets)){if(!(typeof limit==='string'&&fitsUtf8(limit,1024)||typeof limit==='number'&&Number.isFinite(limit)))return invalid();budgets[key]=limit;}
  return Object.freeze({revision:value.revision,revisionToken:value.revisionToken,accessRevision:value.accessRevision,accessRevisionToken:value.accessRevisionToken,canSubmit:value.canSubmit,businessReady:value.businessReady,readOnlyReason:value.readOnlyReason,intake:value.intake,execution:value.execution,formats:Object.freeze(value.formats.map(x=>x as ImportFormat)) as ImportFormat[],purpose:value.purpose,materialMode:value.materialMode,budgets:Object.freeze(budgets)});
 },
 async send(command:CommandSnapshot):Promise<ImportAccepted>{
  if(!actor()||actor()!==command.actor)throw new DOMException('Import actor changed','AbortError');
  if(command.operation!=='TRANSCRIPT_IMPORT'||!uuid(command.resource)||command.path!==`/api/v1/meetings/${command.resource}/transcript-imports`||!command.key.trim()||!fitsUtf8(command.key,4096)||/[\r\n]/.test(command.key)||!fitsUtf8(command.body,65536))throw new TypeError('Invalid import command');
  const body:unknown=JSON.parse(command.body);if(!shape(body,'expectedRevisionToken,orderedFileIds')||!revisionToken(body.expectedRevisionToken)||!Array.isArray(body.orderedFileIds)||body.orderedFileIds.length<1||body.orderedFileIds.length>16||!body.orderedFileIds.every(uuid)||new Set(body.orderedFileIds).size!==body.orderedFileIds.length)throw new TypeError('Invalid import files');
  const value=await client.json<unknown>(command.path,{method:'POST',body:command.body,headers:{'Idempotency-Key':command.key},expectedStatus:202,maxResponseBytes:16384});
  if(!shape(value,'batchId,inputFingerprint,inputId,meetingId,revision,revisionToken,taskId')||value.meetingId!==command.resource||!uuid(value.batchId)||!uuid(value.inputId)||!uuid(value.taskId)||typeof value.revision!=='number'||!Number.isInteger(value.revision)||value.revision<0||!revisionToken(value.revisionToken)||typeof value.inputFingerprint!=='string'||!/^[0-9a-f]{64}$/.test(value.inputFingerprint))return invalid();
  return Object.freeze({meetingId:value.meetingId,batchId:value.batchId,inputId:value.inputId,taskId:value.taskId,revision:value.revision,revisionToken:value.revisionToken,inputFingerprint:value.inputFingerprint});
 }
};}
