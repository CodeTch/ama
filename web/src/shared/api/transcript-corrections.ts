import type {components} from './generated';
import type {createBusinessClient} from './business-client';import type {CommandSnapshot} from '../composables/useCommand';
import {ApiError} from './client';import {revisionToken} from './intent-contract';import {responseMatches} from './response-schema';
export type SpeechSpeakers=components['schemas']['SpeechSpeakers'];
export type SpeechCorrections=components['schemas']['SpeechCorrections'];
export type SpeechCorrectionItem=components['schemas']['SpeechCorrectionItem'];
export type CorrectionReceipt=components['schemas']['CorrectionProposalReceipt']|components['schemas']['TranscriptOperationReceipt'];
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v)&&v!=='00000000-0000-0000-0000-000000000000';
const shape=(v:unknown,keys:string):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v)&&Object.keys(v).sort().join(',')===keys;
export function validCorrectionCommand(v:unknown,actor:string,meeting:string):v is CommandSnapshot{
 if(!shape(v,'actor,body,key,operation,path,resource')||v.actor!==actor||v.resource!==meeting||!uuid(meeting)||!uuid(v.key)||typeof v.body!=='string'||v.body.length>256*1024)return false;
 try{const body:unknown=JSON.parse(v.body),root=`/api/v1/meetings/${meeting}/transcript`;
  if(v.operation==='TRANSCRIPT_CORRECTIONS_PROPOSE')return shape(body,'expectedRevisionToken,sourceRevisionId')&&revisionToken(body.expectedRevisionToken)&&uuid(body.sourceRevisionId)&&v.path===root+'/correction-proposals';
  if(v.operation==='TRANSCRIPT_SPEAKERS')return (shape(body,'enterpriseId,expectedRevisionToken,label,segmentIds,sourceRevisionId')||shape(body,'enterpriseId,expectedRevisionToken,label,segmentIds,sourceRevisionId,voiceprintId')&&uuid(body.voiceprintId)&&typeof body.enterpriseId==='string')&&revisionToken(body.expectedRevisionToken)&&uuid(body.sourceRevisionId)&&v.path===root+'/speakers'&&typeof body.label==='string'&&body.label.length>0&&body.label.length<=256&&body.label.trim()===body.label&&!/[\ud800-\udfff]/u.test(body.label)&&Array.isArray(body.segmentIds)&&body.segmentIds.length>0&&body.segmentIds.length<=4096&&body.segmentIds.every(uuid)&&new Set(body.segmentIds).size===body.segmentIds.length&&(body.enterpriseId===null||typeof body.enterpriseId==='string'&&body.enterpriseId.length>0&&body.enterpriseId.length<=255&&!/[\ud800-\udfff]/u.test(body.enterpriseId));
  if(v.operation==='TRANSCRIPT_CORRECTION')return (shape(body,'candidateId,decision,expectedRevisionToken,sourceRevisionId')||shape(body,'additionalCandidateIds,candidateId,decision,expectedRevisionToken,sourceRevisionId')&&Array.isArray(body.additionalCandidateIds)&&body.additionalCandidateIds.length<=59&&body.additionalCandidateIds.every(uuid)&&uuid(body.candidateId)&&!body.additionalCandidateIds.includes(body.candidateId)&&new Set(body.additionalCandidateIds).size===body.additionalCandidateIds.length)&&revisionToken(body.expectedRevisionToken)&&uuid(body.sourceRevisionId)&&uuid(body.candidateId)&&['ACCEPT','REJECT'].includes(String(body.decision))&&v.path===root+`/corrections/${body.candidateId}/decisions`;
  return v.operation==='TRANSCRIPT_CORRECTION_UNDO'&&shape(body,'expectedRevisionToken,sourceRevisionId')&&revisionToken(body.expectedRevisionToken)&&uuid(body.sourceRevisionId)&&v.path===root+`/operations/${body.sourceRevisionId}/undo`;
 }catch{return false;}
}
const invalid=():never=>{throw new ApiError('response',200,'纠错结果暂无法核实，请保留原请求。');};
function freeze<T>(v:T):T{if(v!==null&&typeof v==='object'){Object.values(v).forEach(freeze);Object.freeze(v);}return v;}
export function createTranscriptCorrectionsApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){return{
 async speakers(meeting:string,source:string,signal:AbortSignal):Promise<SpeechSpeakers>{
  if(!uuid(meeting)||!uuid(source))throw new TypeError('Invalid speaker scope');const who=actor();if(!who)throw new DOMException('Speaker actor unavailable','AbortError');
  const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/transcript/speakers?${new URLSearchParams({sourceRevisionId:source})}`,{signal,expectedStatus:200,maxResponseBytes:4*1024*1024});
  if(signal.aborted||who!==actor())throw new DOMException('Speaker scope changed','AbortError');if(!responseMatches('transcripts.yaml#/SpeechSpeakers',value))return invalid();const result=value as SpeechSpeakers;
  const segments=result.items.flatMap(x=>x.segmentIds);if(result.meetingId!==meeting||result.sourceRevisionId!==source||new Set(result.items.map(x=>x.id)).size!==result.items.length||new Set(segments).size!==segments.length)return invalid();return freeze(result);
 },
 async read(meeting:string,source:string,signal:AbortSignal):Promise<SpeechCorrections>{
  if(!uuid(meeting)||!uuid(source))throw new TypeError('Invalid correction scope');const who=actor();if(!who)throw new DOMException('Correction actor unavailable','AbortError');
  const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/transcript/corrections?${new URLSearchParams({sourceRevisionId:source})}`,{signal,expectedStatus:200,maxResponseBytes:8*1024*1024});
  if(signal.aborted||who!==actor())throw new DOMException('Correction scope changed','AbortError');
  if(!responseMatches('transcripts.yaml#/SpeechCorrections',value))return invalid();const result=value as SpeechCorrections;
  if(result.meetingId!==meeting||result.sourceRevisionId!==source||result.pendingCount!==result.items.filter(x=>x.status==='PENDING').length||new Set(result.items.map(x=>x.id)).size!==result.items.length||result.items.some(x=>x.source===x.replacement||(x.status!=='PENDING'&&(x.canAccept||x.canReject))||(x.canUndo&&(x.status!=='ACCEPTED'||x.operationId!==source))))return invalid();if(result.items.some(item=>{
   if(!item.range)return false;
   const {startScalar:start,endScalar:end,original,replacement}=item.range,points=Array.from(item.source);
   return end<start||end>points.length||points.slice(start,end).join('')!==original||points.slice(0,start).join('')+replacement+points.slice(end).join('')!==item.replacement;
  }))return invalid();return freeze(result);
 },
 async send(command:CommandSnapshot):Promise<CorrectionReceipt>{
  const who=actor();if(!who||who!==command.actor)throw new DOMException('Correction actor changed','AbortError');if(!validCorrectionCommand(command,who,command.resource))throw new TypeError('Invalid original correction command');
  const value=await client.json<unknown>(command.path,{method:'POST',headers:{'Idempotency-Key':command.key},body:command.body,expectedStatus:200,errorContract:'saved-transcript',maxResponseBytes:65536});
  const proposal=command.operation==='TRANSCRIPT_CORRECTIONS_PROPOSE';if(!responseMatches(`transcripts.yaml#/${proposal?'CorrectionProposalReceipt':'TranscriptOperationReceipt'}`,value))return invalid();
  const result=value as CorrectionReceipt;
  if('reviewBatchId' in result.result){if(result.result.sourceRevisionId!==JSON.parse(command.body).sourceRevisionId||result.result.createdCount>result.result.candidateIds.length)return invalid();}
  else if(result.result.meetingId!==command.resource||result.result.operationId!==result.result.revisionId)return invalid();return freeze(result);
 }
};}
