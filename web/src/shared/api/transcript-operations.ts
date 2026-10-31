import type {components} from './generated';
import type {createBusinessClient} from './business-client';
import type {CommandSnapshot} from '../composables/useCommand';
import {ApiError} from './client';import {revisionToken} from './intent-contract';import {fitsUtf8} from '../bounded-text';import {responseMatches} from './response-schema';
export type TranscriptReplacementSearch=components['schemas']['TranscriptReplacementSearch'];
export type TranscriptOperationReceipt=components['schemas']['TranscriptOperationReceipt'];
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v)&&v!=='00000000-0000-0000-0000-000000000000';
const shape=(v:unknown,keys:string):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v)&&Object.keys(v).sort().join(',')===keys;
const literal=(v:unknown,maximum:number):v is string=>typeof v==='string'&&v.length<=maximum&&!/[\ud800-\udfff]/u.test(v);
export function validTranscriptOperationCommand(value:unknown,actor:string,meeting:string):value is CommandSnapshot{
 if(!shape(value,'actor,body,key,operation,path,resource')||value.actor!==actor||value.resource!==meeting||!uuid(meeting)||!uuid(value.key)||typeof value.body!=='string'||!fitsUtf8(value.body,8*1024*1024))return false;
 try{
  const body:unknown=JSON.parse(value.body);const root=`/api/v1/meetings/${meeting}/transcript`;
  if(value.operation==='TRANSCRIPT_UNDO')return shape(body,'expectedRevisionToken,sourceRevisionId')&&revisionToken(body.expectedRevisionToken)&&uuid(body.sourceRevisionId)&&value.path===root+`/operations/${body.sourceRevisionId}/undo`;
  return value.operation==='TRANSCRIPT_REPLACE'&&value.path===root+'/replacements'&&shape(body,'expectedRevisionToken,find,occurrenceIds,replacement,sourceRevisionId')&&revisionToken(body.expectedRevisionToken)&&uuid(body.sourceRevisionId)&&literal(body.find,1024)&&body.find.length>0&&literal(body.replacement,65536)&&Array.isArray(body.occurrenceIds)&&body.occurrenceIds.length>0&&body.occurrenceIds.length<=10000&&body.occurrenceIds.every(id=>typeof id==='string'&&/^[0-9a-f]{64}$/.test(id))&&new Set(body.occurrenceIds).size===body.occurrenceIds.length;
 }catch{return false;}
}
function invalid():never{throw new ApiError('response',200,'替换与撤销结果暂无法核实，请保留原请求。');}
function freeze<T>(value:T):T{if(value!==null&&typeof value==='object'){Object.values(value).forEach(freeze);Object.freeze(value);}return value;}
export function createTranscriptOperationsApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){return{
 async search(meeting:string,source:string,query:string,signal:AbortSignal):Promise<TranscriptReplacementSearch>{
  if(!uuid(meeting)||!uuid(source)||!literal(query,1024)||!query.length)throw new TypeError('Invalid transcript search');const who=actor();if(!who)throw new DOMException('Transcript actor unavailable','AbortError');
  const args=new URLSearchParams({sourceRevisionId:source,query});const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/transcript/search?${args}`,{signal,expectedStatus:200,maxResponseBytes:4*1024*1024});
  if(signal.aborted||actor()!==who)throw new DOMException('Transcript search scope changed','AbortError');
  if(!responseMatches('transcripts.yaml#/TranscriptReplacementSearch',value))return invalid();const result=value as TranscriptReplacementSearch;
  if(result.meetingId!==meeting||result.sourceRevisionId!==source||result.query!==query||new Set(result.occurrences.map(x=>x.occurrenceId)).size!==result.occurrences.length||result.occurrences.some(x=>x.endScalar<=x.startScalar))return invalid();return freeze(result);
 },
 async send(command:CommandSnapshot):Promise<TranscriptOperationReceipt>{
  const who=actor();if(!who||command.actor!==who)throw new DOMException('Transcript operation actor changed','AbortError');if(!validTranscriptOperationCommand(command,who,command.resource))throw new TypeError('Invalid original transcript operation');
  const value=await client.json<unknown>(command.path,{method:'POST',headers:{'Idempotency-Key':command.key},body:command.body,expectedStatus:200,errorContract:'saved-transcript',maxResponseBytes:65536});
  if(!responseMatches('transcripts.yaml#/TranscriptOperationReceipt',value))return invalid();const result=value as TranscriptOperationReceipt;if(result.result.meetingId!==command.resource||result.result.operationId!==result.result.revisionId)return invalid();return freeze(result);
 }
};}
