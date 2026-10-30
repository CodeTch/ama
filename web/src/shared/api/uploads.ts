import {importFormat} from '../document-formats';
import type {components} from './generated';
import type {createBusinessClient} from './business-client';
import type {CommandSnapshot} from '../composables/useCommand';
import {ApiError} from './client';
import {revisionToken} from './intent-contract';
import {fitsUtf8} from '../bounded-text';
import {UPLOAD_FILE_BYTES,UPLOAD_PART_BYTES,UPLOAD_PART_COUNT} from '../../features/import/upload-bytes';
export type UploadAccepted=components['schemas']['Accepted'];
export type UploadReceipt=components['schemas']['UploadReceipt'];
export type UploadPartPage=components['schemas']['PartSummaryPage'];
export type UploadCompleted=components['schemas']['Completed'];
const object=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v);
const exact=(v:Record<string,unknown>,keys:string)=>Object.keys(v).sort().join(',')===keys;
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
const hash=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{64}$/.test(v);
const numeric=(v:unknown):v is number=>typeof v==='number'&&Number.isInteger(v)&&v>=0;
const nullableText=(v:unknown)=>v===null||typeof v==='string';
const timestamp=(v:unknown)=>typeof v==='string'&&v.length<=64&&Number.isFinite(Date.parse(v));
const positiveLong=(v:unknown):v is string=>typeof v==='string'&&v.length<=19&&/^[1-9][0-9]*$/.test(v)&&(v.length<19||v<='9223372036854775807');
function accepted(v:unknown):v is UploadAccepted{return object(v)&&exact(v,'partSize,partSizeToken,uploadId')&&uuid(v.uploadId)&&numeric(v.partSize)&&v.partSize>0&&positiveLong(v.partSizeToken);}
function completed(v:unknown):v is components['schemas']['CompletedFileSummary']{
 return object(v)&&exact(v,'businessReadEligibility,displayName,fileId,fileState,intakeEligibility,originalPurpose,sha256,sizeBytes,sizeBytesToken,uploadId')&&uuid(v.fileId)&&uuid(v.uploadId)&&typeof v.displayName==='string'&&numeric(v.sizeBytes)&&revisionToken(v.sizeBytesToken)&&hash(v.sha256)
 &&typeof v.fileState==='string'&&['AVAILABLE','DELETE_PENDING','DELETED'].includes(v.fileState)&&typeof v.originalPurpose==='string'&&v.businessReadEligibility==='NOT_EVALUATED'&&object(v.intakeEligibility)&&exact(v.intakeEligibility,'reason,state')&&typeof v.intakeEligibility.state==='string'&&['ELIGIBLE','INELIGIBLE','UNAVAILABLE'].includes(v.intakeEligibility.state)&&(v.intakeEligibility.reason===null||typeof v.intakeEligibility.reason==='string'&&['SOURCE_STATE','SOURCE_EPOCH','SOURCE_PURPOSE','SOURCE_ROUTE','SOURCE_PROVENANCE','READ_ONLY'].includes(v.intakeEligibility.reason));
}
function actions(v:unknown,permitted:string[]):boolean{return Array.isArray(v)&&v.length<=permitted.length&&v.every(x=>typeof x==='string'&&permitted.includes(x))&&new Set(v).size===v.length;}
function receipt(v:unknown,id:string):v is UploadReceipt{
 if(!object(v)||!exact(v,'accepted,allowedActions,blockedReason,completed,completedFileId,createdAt,displayName,expectedSha256,expectedSize,expectedSizeToken,idempotentRetries,purpose,state,updatedAt,uploadId')||v.uploadId!==id||typeof v.purpose!=='string'||typeof v.displayName!=='string'||!numeric(v.expectedSize)||!revisionToken(v.expectedSizeToken)||(v.expectedSha256!==null&&!hash(v.expectedSha256))||!timestamp(v.createdAt)||!timestamp(v.updatedAt)||!nullableText(v.blockedReason)||!accepted(v.accepted)||v.accepted.uploadId!==id||typeof v.state!=='string'||!['OPEN','FINALIZING','COMPLETED','ABORTED','FAILED'].includes(v.state))return false;
 if(!actions(v.allowedActions,v.state==='OPEN'?['UPLOAD_PARTS','COMPLETE','ABORT']:v.state==='FINALIZING'?['COMPLETE']:[])||!actions(v.idempotentRetries,v.state==='COMPLETED'?['COMPLETE','ABORT']:v.state==='ABORTED'?['ABORT']:[]))return false;
 if(v.state!=='COMPLETED')return v.completedFileId===null&&v.completed===null;
 return uuid(v.completedFileId)&&completed(v.completed)&&v.completed.fileId===v.completedFileId&&v.completed.uploadId===id&&v.completed.originalPurpose===v.purpose&&v.completed.displayName===v.displayName&&v.completed.sizeBytesToken===v.expectedSizeToken&&(v.expectedSha256===null||v.completed.sha256===v.expectedSha256);
}
function partPage(v:unknown,after:string):v is UploadPartPage{
 if(!object(v)||!exact(v,'items,nextCursor')||!Array.isArray(v.items)||v.items.length>500)return false;
 let previous=BigInt(after);
 for(const item of v.items){if(!object(item)||!exact(item,'part,sha256,sizeBytes,sizeBytesToken')||typeof item.part!=='number'||!Number.isSafeInteger(item.part)||item.part<1||item.part>2147483647||BigInt(item.part)<=previous||!numeric(item.sizeBytes)||!revisionToken(item.sizeBytesToken)||!hash(item.sha256))return false;previous=BigInt(item.part);}
 return v.nextCursor===null||v.items.length>0&&typeof v.nextCursor==='string'&&v.nextCursor===previous.toString();
}
function freeze<T>(v:T):T{if(typeof v==='object'&&v!==null){Object.values(v).forEach(freeze);Object.freeze(v);}return v;}
function response(){return new ApiError('response',200,'上传响应暂无法确认，请保留原文件和操作并重新查询。');}
function requireId(id:string){if(!uuid(id))throw new TypeError('Invalid upload scope');}
/** Actual producer upload/read routes; state/action observations never replace server authorization. */
export function createUploadsApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){
 function requireActor(expected:string){if(!actor()||actor()!==expected)throw new DOMException('Upload actor changed','AbortError');}
 function commandBody(command:CommandSnapshot,operation:string,path:string){requireActor(command.actor);requireId(command.resource);if(command.operation!==operation||command.path!==path||!fitsUtf8(command.body,8*1024*1024))throw new TypeError('Invalid upload command');const value:unknown=JSON.parse(command.body);if(!object(value))throw new TypeError('Object required');return value;}
 return {
  async input(meeting:string,input:string,task:string,signal:AbortSignal):Promise<components['schemas']['InputDetail']>{
   requireId(meeting);requireId(input);requireId(task);const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/processing-inputs/${input}`,{signal,expectedStatus:200,maxResponseBytes:262144});
   if(!object(value)||!exact(value,'baseRevision,batchFingerprint,batchId,batchProfile,cancelEpochToken,createdAt,files,inputFingerprint,inputId,kind,materialMode,revisionToken,taskId')||value.inputId!==input||value.taskId!==task||!uuid(value.batchId)||value.kind!=='IMPORT'||value.materialMode!=='EMPTY'||!numeric(value.baseRevision)||!revisionToken(value.revisionToken)||!revisionToken(value.cancelEpochToken)||value.batchProfile!=='ama.input-batch.v1'||!hash(value.batchFingerprint)||!hash(value.inputFingerprint)||!timestamp(value.createdAt)||!Array.isArray(value.files)||value.files.length<1||value.files.length>16||!value.files.every((f,i)=>object(f)&&exact(f,'fileId,ordinal,sha256,sizeBytes,sizeBytesToken,uploadId')&&f.ordinal===i+1&&uuid(f.fileId)&&uuid(f.uploadId)&&hash(f.sha256)&&typeof f.sizeBytes==='number'&&Number.isSafeInteger(f.sizeBytes)&&f.sizeBytes>=0&&f.sizeBytes<=UPLOAD_FILE_BYTES&&f.sizeBytesToken===String(f.sizeBytes))||new Set(value.files.map(f=>f.fileId)).size!==value.files.length||new Set(value.files.map(f=>f.uploadId)).size!==value.files.length)throw response();return freeze(value as unknown as components['schemas']['InputDetail']);
  },
  async completedFiles(meeting:string,after:string|null,signal:AbortSignal):Promise<components['schemas']['CompletedFileSummaryPage']>{
   requireId(meeting);if(after!==null&&!uuid(after))throw new TypeError('Invalid saved-file cursor');const query=new URLSearchParams({limit:'16'});if(after)query.set('after',after);const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/completed-files?${query}`,{signal,expectedStatus:200,maxResponseBytes:1024*1024});
   if(!object(value)||!exact(value,'items,nextCursor')||!Array.isArray(value.items)||value.items.length>16||!value.items.every(completed)||new Set(value.items.map(x=>x.fileId)).size!==value.items.length||!(value.nextCursor===null||uuid(value.nextCursor))||value.nextCursor!==null&&value.nextCursor!==value.items.at(-1)?.fileId)throw response();return freeze({items:value.items,nextCursor:value.nextCursor});
  },
  async begin(command:CommandSnapshot):Promise<UploadAccepted>{
   const path=`/api/v1/meetings/${command.resource}/uploads`,body=commandBody(command,'UPLOAD_BEGIN',path);
   if(!exact(body,'name,purpose,sha256,size')||body.purpose!=='TRANSCRIPT_INPUT'||typeof body.name!=='string'||!fitsUtf8(body.name,1024*1024)||!importFormat(body.name)||!Number.isSafeInteger(body.size)||typeof body.size!=='number'||body.size<0||body.size>UPLOAD_FILE_BYTES||!hash(body.sha256)||!fitsUtf8(command.key,4096)||!command.key.trim()||/[\r\n]/.test(command.key))throw new TypeError('Invalid upload begin');
   const value=await client.json<unknown>(path,{method:'POST',headers:{'Idempotency-Key':command.key},body:command.body,expectedStatus:201,maxResponseBytes:16384});if(!accepted(value))throw response();return freeze(value);
  },
  async part(expectedActor:string,id:string,part:number,body:Blob,sha256:string,signal:AbortSignal):Promise<void>{
   requireActor(expectedActor);requireId(id);if(!Number.isSafeInteger(part)||part<1||part>2147483647||!(body instanceof Blob)||!Number.isSafeInteger(body.size)||body.size<0||body.size>UPLOAD_PART_BYTES||!hash(sha256))throw new TypeError('Invalid upload part');
   await client.json<void>(`/api/v1/uploads/${id}/parts/${part}`,{method:'PUT',headers:{'Content-Type':'application/octet-stream','X-Checksum-SHA256':sha256},body,signal,expectedStatus:204});
  },
  async complete(command:CommandSnapshot):Promise<UploadCompleted>{
   const path=`/api/v1/uploads/${command.resource}/completion`,body=commandBody(command,'UPLOAD_COMPLETE',path);
   if(!exact(body,'parts,sha256')||!hash(body.sha256)||!Array.isArray(body.parts)||body.parts.length<1||body.parts.length>UPLOAD_PART_COUNT||body.parts.some((p,i)=>!object(p)||!exact(p,'part,sha256')||p.part!==i+1||!hash(p.sha256)))throw new TypeError('Invalid completion manifest');
   const value=await client.json<unknown>(path,{method:'POST',body:command.body,expectedStatus:200,maxResponseBytes:16384});if(!object(value)||!exact(value,'fileId')||!uuid(value.fileId))throw response();return Object.freeze({fileId:value.fileId});
  },
  async abort(expectedActor:string,id:string):Promise<void>{requireActor(expectedActor);requireId(id);await client.json<void>(`/api/v1/uploads/${id}`,{method:'DELETE',expectedStatus:204});},
  async receipt(meeting:string,id:string,signal:AbortSignal):Promise<UploadReceipt>{requireId(meeting);requireId(id);const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/uploads/${id}`,{signal,expectedStatus:200});if(!receipt(value,id))throw response();return freeze(value);},
  async parts(meeting:string,id:string,after:string,signal:AbortSignal):Promise<UploadPartPage>{requireId(meeting);requireId(id);if(!revisionToken(after))throw new TypeError('Invalid part cursor');const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/uploads/${id}/parts?limit=500&afterPart=${after}`,{signal,expectedStatus:200,maxResponseBytes:262144});if(!partPage(value,after))throw response();return freeze(value);}
 };
}
