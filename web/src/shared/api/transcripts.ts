import type {components} from './generated';
import type {createBusinessClient} from './business-client';
import type {CommandSnapshot} from '../composables/useCommand';
import {ApiError} from './client';
import {revisionToken} from './intent-contract';
import {fitsUtf8} from '../bounded-text';
export type TranscriptCurrent=components['schemas']['Current'];
export type TranscriptDetail=components['schemas']['OwnerDetail'];
export type TranscriptReceipt=components['schemas']['Receipt'];
export type TranscriptSegment=components['schemas']['Segment'];
export type TranscriptHistory=components['schemas']['History'];
export type TranscriptSource={fileId:string;sha256:string;sizeBytes:number;revisionId:string|null};
const object=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v);
const exact=(v:Record<string,unknown>,keys:string)=>Object.keys(v).sort().join(',')===keys;
const shape=(v:unknown,keys:string):v is Record<string,unknown>=>object(v)&&exact(v,keys);
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
const nullableId=(v:unknown)=>v===null||uuid(v),number=(v:unknown):v is number=>typeof v==='number'&&Number.isInteger(v)&&v>=0;
const nullableNumber=(v:unknown)=>v===null||number(v),text=(v:unknown):v is string=>typeof v==='string',nullableText=(v:unknown)=>v===null||text(v);
const hash=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{64}$/.test(v),timestamp=(v:unknown)=>text(v)&&Number.isFinite(Date.parse(v));
const marks=(v:unknown):v is TranscriptSegment['marks']=>Array.isArray(v)&&v.length<=4&&v.every(x=>['KEY','CONCLUSION','DISPUTE','GENERATION_FOCUS'].includes(x))&&new Set(v).size===v.length;
function observation(v:unknown):v is components['schemas']['Observation']{return shape(v,'accessRevision,accessRevisionToken,currentConfirmationId,currentSavedRevisionId,meetingRevision,revisionToken')&&number(v.meetingRevision)&&number(v.accessRevision)&&revisionToken(v.revisionToken)&&revisionToken(v.accessRevisionToken)&&nullableId(v.currentSavedRevisionId)&&nullableId(v.currentConfirmationId);}
function confirmation(v:unknown):v is components['schemas']['Confirmation']{return shape(v,'confirmedAt,confirmedBy,contentHash,id,isCurrent,profile,revisionId')&&uuid(v.id)&&uuid(v.revisionId)&&hash(v.contentHash)&&text(v.confirmedBy)&&timestamp(v.confirmedAt)&&v.profile==='ama.transcript-confirmation.v1'&&typeof v.isCurrent==='boolean';}
function segment(v:unknown):v is TranscriptSegment{return shape(v,'body,endMs,marks,ordinal,segmentId,sourceFileId,sourceParagraph,speakerId,startMs')&&uuid(v.segmentId)&&number(v.ordinal)&&text(v.body)&&uuid(v.sourceFileId)&&nullableNumber(v.sourceParagraph)&&nullableNumber(v.startMs)&&nullableNumber(v.endMs)&&nullableText(v.speakerId)&&marks(v.marks);}
function saved(v:unknown,owner=false):v is components['schemas']['OwnerSaved']|components['schemas']['MemberSaved']{
 if(!object(v)||!uuid(v.id)||!(v.kind==='RAW'||v.kind==='WORKING')||!number(v.revision)||!revisionToken(v.revisionToken)||!Array.isArray(v.segments)||v.segments.length>100000||!v.segments.every(segment)||new Set(v.segments.map(x=>x.segmentId)).size!==v.segments.length)return false;
 if(!owner&&exact(v,'id,kind,revision,revisionToken,segments'))return true;
 return exact(v,'batchId,createdAt,createdBy,id,inputFingerprint,kind,parentId,processingInputId,producerTaskId,revision,revisionToken,segments')&&uuid(v.batchId)&&timestamp(v.createdAt)&&text(v.createdBy)&&hash(v.inputFingerprint)&&nullableId(v.parentId)&&uuid(v.processingInputId)&&nullableId(v.producerTaskId);
}
function common(v:Record<string,unknown>,meeting:string){
 return v.meetingId===meeting&&observation(v.observation)&&v.storedInspection==='AVAILABLE'&&typeof v.needsGeneration==='boolean'
 &&shape(v.correction,'mode,qualificationId,readiness')&&['DISABLED_NOT_INSTALLED','UNAVAILABLE'].includes(String(v.correction.mode))&&['NOT_APPLICABLE','UNAVAILABLE'].includes(String(v.correction.readiness))&&nullableText(v.correction.qualificationId)
 &&shape(v.capabilities,'canConfirm,canReadHistory,canReadSources,canRestore,canSave')&&Object.values(v.capabilities).every(x=>typeof x==='boolean')
 &&(v.confirmation===null||confirmation(v.confirmation))&&Array.isArray(v.sources)&&v.sources.length<=4096&&v.sources.every(s=>shape(s,'fileId,ordinal,sha256,sizeBytes,sizeBytesToken')&&uuid(s.fileId)&&number(s.ordinal)&&hash(s.sha256)&&number(s.sizeBytes)&&revisionToken(s.sizeBytesToken))&&new Set(v.sources.map(s=>s.fileId)).size===v.sources.length
 &&Array.isArray(v.sourceBytes)&&v.sourceBytes.length===v.sources.length&&v.sourceBytes.every(s=>shape(s,'eligibility,fileId,reason')&&uuid(s.fileId)&&['ELIGIBLE','INELIGIBLE','UNAVAILABLE'].includes(String(s.eligibility))&&['AVAILABLE','SOURCE_EPOCH_CHANGED','FILE_UNAVAILABLE','ROUTE_UNAVAILABLE'].includes(String(s.reason)))&&new Set(v.sourceBytes.map(s=>s.fileId)).size===v.sourceBytes.length;
}
function current(v:unknown,meeting:string):v is TranscriptCurrent{
 if(!object(v)||!common(v,meeting)||!observation(v.observation)||!(v.saved===null||saved(v.saved)))return false;
 const required=['capabilities','confirmation','correction','meetingId','needsGeneration','observation','saved','sourceBytes','sources','storedInspection'];
 const optional=['parserVersion','processingConfigVersion','confirmationHashProfile','selectedConfirmation'];
 if(!required.every(k=>k in v)||Object.keys(v).some(k=>!required.includes(k)&&!optional.includes(k))||'parserVersion' in v&&!text(v.parserVersion)||'processingConfigVersion' in v&&!text(v.processingConfigVersion)||'confirmationHashProfile' in v&&v.confirmationHashProfile!=='ama.transcript-confirmation.v1'||'selectedConfirmation' in v&&v.selectedConfirmation!==null&&!confirmation(v.selectedConfirmation))return false;
 if(v.saved===null)return v.observation.currentSavedRevisionId===null&&v.confirmation===null;
 return v.saved.kind==='WORKING'&&v.saved.id===v.observation.currentSavedRevisionId&&(v.confirmation===null?v.observation.currentConfirmationId===null:confirmation(v.confirmation)&&v.confirmation.id===v.observation.currentConfirmationId&&v.confirmation.revisionId===v.saved.id&&v.confirmation.isCurrent);
}
function detail(v:unknown,meeting:string,revision:string):v is TranscriptDetail{return object(v)&&exact(v,'capabilities,confirmation,confirmationHashProfile,correction,meetingId,needsGeneration,observation,parserVersion,processingConfigVersion,selected,selectedConfirmation,sourceBytes,sources,storedInspection')&&common(v,meeting)&&text(v.parserVersion)&&text(v.processingConfigVersion)&&v.confirmationHashProfile==='ama.transcript-confirmation.v1'&&(v.selectedConfirmation===null||confirmation(v.selectedConfirmation))&&saved(v.selected,true)&&v.selected.id===revision;}
function receipt(v:unknown,meeting:string,confirm:boolean):v is TranscriptReceipt{
 if(!shape(v,'observation,replayed,result,resultIsCurrent')||typeof v.replayed!=='boolean'||typeof v.resultIsCurrent!=='boolean'||!observation(v.observation)||!object(v.result)||v.result.meetingId!==meeting||!uuid(v.result.revisionId))return false;
 return confirm?exact(v.result,'confirmationId,contentHash,kind,meetingId,revisionId')&&v.result.kind==='TRANSCRIPT_CONFIRMATION'&&uuid(v.result.confirmationId)&&hash(v.result.contentHash):exact(v.result,'historyRevision,historyRevisionToken,kind,meetingId,revisionId')&&v.result.kind==='SAVED_REVISION'&&number(v.result.historyRevision)&&revisionToken(v.result.historyRevisionToken);
}
function freeze<T>(v:T):T{if(v!==null&&typeof v==='object'){Object.values(v).forEach(freeze);Object.freeze(v);}return v;}
function invalid():never{throw new ApiError('response',200,'转写响应暂无法确认，请保留草稿并重新读取。');}
export function createTranscriptsApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){return {
 async source(meeting:string,source:TranscriptSource,signal:AbortSignal):Promise<{blob:Blob;name:string|null}>{
  if(!uuid(meeting)||!uuid(source.fileId)||source.revisionId!==null&&!uuid(source.revisionId)||!hash(source.sha256)||!Number.isSafeInteger(source.sizeBytes)||source.sizeBytes<0||source.sizeBytes>32*1024*1024)throw new TypeError('Exact original source required');
  const who=actor();if(!who)throw new DOMException('Source actor unavailable','AbortError');const path=`/api/v1/meetings/${meeting}/transcript${source.revisionId===null?'':`/revisions/${source.revisionId}`}/sources/${source.fileId}`;
  const value=await client.bytes(path,{signal,size:source.sizeBytes});const actual=[...new Uint8Array(await crypto.subtle.digest('SHA-256',value.bytes))].map(x=>x.toString(16).padStart(2,'0')).join('');if(signal.aborted||actor()!==who)throw new DOMException('Source actor changed','AbortError');if(actual!==source.sha256)throw new ApiError('response',200,'原文件校验失败，请重新读取会议。');
  const p=new Uint8Array(value.bytes),name=value.name?.toLowerCase()??'';let type='application/octet-stream';
  if(p.length>=12&&String.fromCharCode(...p.subarray(0,4))==='RIFF'&&String.fromCharCode(...p.subarray(8,12))==='WAVE')type='audio/wav';else if(p.length>=3&&String.fromCharCode(...p.subarray(0,3))==='ID3'||p.length>=2&&p[0]===255&&((p[1]??0)&224)===224)type='audio/mpeg';else if(p.length>=8&&String.fromCharCode(...p.subarray(4,8))==='ftyp')type=name.endsWith('.m4a')?'audio/mp4':name.endsWith('.mov')?'video/quicktime':'video/mp4';else if(p.length>=4&&p[0]===26&&p[1]===69&&p[2]===223&&p[3]===163)type=name.endsWith('.mkv')?'video/x-matroska':'audio/webm';
  return{blob:new Blob([value.bytes],{type}),name:value.name};
 },
 async current(meeting:string,signal:AbortSignal):Promise<TranscriptCurrent>{if(!uuid(meeting))throw new TypeError('Invalid meeting');const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/transcript`,{signal,expectedStatus:200,maxResponseBytes:8*1024*1024});if(!current(value,meeting))return invalid();return freeze(value);},
 async revision(meeting:string,id:string,signal:AbortSignal):Promise<TranscriptDetail>{if(!uuid(meeting)||!uuid(id))throw new TypeError('Invalid revision');const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/transcript/revisions/${id}`,{signal,expectedStatus:200,maxResponseBytes:8*1024*1024});if(!detail(value,meeting,id))return invalid();return freeze(value);},
 async history(meeting:string,before:string|null,signal:AbortSignal):Promise<TranscriptHistory>{
  if(!uuid(meeting)||before!==null&&!revisionToken(before))throw new TypeError('Invalid history request');const query=new URLSearchParams({limit:'20'});if(before!==null)query.set('beforeRevisionToken',before);
  const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/transcript/history?${query}`,{signal,expectedStatus:200,maxResponseBytes:1024*1024});
  if(!shape(value,'hasMore,items,nextCursor,observation')||!observation(value.observation)||!Array.isArray(value.items)||value.items.length>50||!value.items.every(x=>shape(x,'createdAt,createdBy,id,kind,revision,revisionToken')&&uuid(x.id)&&(x.kind==='RAW'||x.kind==='WORKING')&&number(x.revision)&&revisionToken(x.revisionToken)&&text(x.createdBy)&&timestamp(x.createdAt))||new Set(value.items.map(x=>x.id)).size!==value.items.length||!(value.nextCursor===null||revisionToken(value.nextCursor))||value.hasMore!==(value.nextCursor!==null))return invalid();
  const items:TranscriptHistory['items']=value.items.map(x=>({id:x.id,kind:x.kind,revision:x.revision,revisionToken:x.revisionToken,createdBy:x.createdBy,createdAt:x.createdAt}));return freeze({items,hasMore:value.nextCursor!==null,nextCursor:value.nextCursor,observation:value.observation});
 },
 async send(command:CommandSnapshot):Promise<TranscriptReceipt>{
  if(!actor()||actor()!==command.actor)throw new DOMException('Transcript actor changed','AbortError');const operations:Record<string,{suffix:string;method:string}>={TRANSCRIPT_SAVE:{suffix:'',method:'PUT'},TRANSCRIPT_MARKS:{suffix:'/marks',method:'PUT'},TRANSCRIPT_RESTORE:{suffix:'/restorations',method:'POST'},TRANSCRIPT_CONFIRM:{suffix:'/confirmations',method:'POST'}},operation=operations[command.operation];
  if(!operation||!uuid(command.resource)||command.path!==`/api/v1/meetings/${command.resource}/transcript${operation.suffix}`||!command.key.trim()||!fitsUtf8(command.key,4096)||/[\r\n]/.test(command.key)||!fitsUtf8(command.body,8*1024*1024))throw new TypeError('Invalid transcript command');
  const body:unknown=JSON.parse(command.body);if(!object(body)||!revisionToken(body.expectedRevisionToken))throw new TypeError('Exact transcript revision required');
  if(command.operation==='TRANSCRIPT_SAVE'||command.operation==='TRANSCRIPT_MARKS'){
   const keys=command.operation==='TRANSCRIPT_SAVE'?'body,marks,segmentId':'marks,segmentId';if(!exact(body,'expectedRevisionToken,segments,sourceRevisionId')||!uuid(body.sourceRevisionId)||!Array.isArray(body.segments)||body.segments.length>100000||!body.segments.every(s=>shape(s,keys)&&uuid(s.segmentId)&&marks(s.marks)&&(command.operation!=='TRANSCRIPT_SAVE'||text(s.body)))||new Set(body.segments.map(s=>s.segmentId)).size!==body.segments.length)throw new TypeError('Complete transcript segments required');
  }else{const key=command.operation==='TRANSCRIPT_CONFIRM'?'savedRevisionId':'sourceRevisionId';if(!exact(body,`expectedRevisionToken,${key}`)||!uuid(body[key]))throw new TypeError('Invalid selected transcript');}
  const value=await client.json<unknown>(command.path,{method:operation.method,headers:{'Idempotency-Key':command.key},body:command.body,expectedStatus:200,errorContract:'saved-transcript',maxResponseBytes:65536});if(!receipt(value,command.resource,command.operation==='TRANSCRIPT_CONFIRM'))return invalid();return freeze(value);
 }
};}
