import type { components } from './generated';
import {intentConflict} from './intent-contract';
import {publicationErrors} from './publication-errors';
import {requestAdmission} from './request-admission';
const admission=requestAdmission();

type DeclaredErrorCode = { [K in keyof components['schemas']]: components['schemas'][K] extends {error: {code: infer C extends string}} ? C : never }[keyof components['schemas']];
const knownCodes: ReadonlySet<string> = new Set(["CATEGORY_DICTIONARY_UNAVAILABLE", "FILE_CONFLICT", "FILE_INVALID_RANGE", "FILE_NOT_FOUND", "FILE_PURPOSE_UNAVAILABLE", "FILE_STORAGE_UNAVAILABLE", "FORBIDDEN", "IDENTITY_PROVIDER_UNAVAILABLE", "IDENTITY_UNAVAILABLE", "INVALID_ARGUMENT", "MEETING_CONFLICT", "TOPIC_NOT_PUBLISHED", "MEETING_FORBIDDEN", "MEETING_UNAVAILABLE", "REQUEST_TOO_LARGE", "UNAUTHENTICATED", "RECORDING_CONFLICT", "RECORDING_GAP", "RECORDING_PAUSED", "RECORDING_UNAVAILABLE", "RECORDING_RATE_LIMITED", "RECORDING_READ_ONLY", "RECORDING_ARCHIVE_NOT_READY", "RECORDING_ARCHIVE_CONFLICT", "RECORDING_ARCHIVE_UNAVAILABLE", "INVALID_RANGE"] satisfies readonly DeclaredErrorCode[]);
const materialMessages:Readonly<Record<string,readonly [number,string]>>={
 MATERIAL_UNAVAILABLE:[503,'材料服务暂不可用，请保留原请求后重试。'],
 MATERIAL_KNOWLEDGE_UNAVAILABLE:[503,'知识库材料服务尚未就绪，请稍后重试。'],
 MATERIAL_KNOWLEDGE_CONTENT_INVALID:[503,'知识库文档内容校验失败，请联系管理员核对文件版本。'],
 MATERIAL_KNOWLEDGE_VERSION_CONFLICT:[409,'知识库文件版本已变化，请先查回原请求。'],
 MATERIAL_BATCH_VERSION_REJECTED:[409,'所选知识库文件版本已变化，本次添加未提交。请移除此操作后重新选择文件。'],
 MATERIAL_KNOWLEDGE_FORBIDDEN:[403,'当前账号无权读取所选知识库文件。'],
 MATERIAL_KNOWLEDGE_NOT_FOUND:[404,'所选知识库文件已不存在或不可用。'],
};
const enhancementCodes=['ENHANCEMENT_INPUT_REJECTED','REVISION_CONFLICT','CURRENT_REVISION_REQUIRED','ENHANCEMENT_SOURCE_CHANGED','ENHANCEMENT_NOT_FOUND','ENHANCEMENT_NOT_READY','ENHANCEMENT_NO_CHANGES','ENHANCEMENT_SOURCE_LIMIT','ENHANCEMENT_MATERIAL_LIMIT','ENHANCEMENT_HISTORY_LIMIT','ENHANCEMENT_PROFILE_UNAVAILABLE','ENHANCEMENT_RECORD_UNAVAILABLE','ENHANCEMENT_RETRY_SAME_REQUEST','MATERIAL_ENHANCEMENT_NOT_INSTALLED','MATERIAL_BYTES_UNAVAILABLE'];
const allKnownCodes:ReadonlySet<string>=new Set([...knownCodes,...Object.keys(materialMessages)]);
const enhancementStatus:Readonly<Record<string,number>>={ENHANCEMENT_INPUT_REJECTED:422,ENHANCEMENT_SOURCE_CHANGED:409,REVISION_CONFLICT:409,CURRENT_REVISION_REQUIRED:409,ENHANCEMENT_NOT_FOUND:404,ENHANCEMENT_NOT_READY:409,ENHANCEMENT_NO_CHANGES:422,ENHANCEMENT_SOURCE_LIMIT:413,ENHANCEMENT_MATERIAL_LIMIT:413,ENHANCEMENT_HISTORY_LIMIT:422,ENHANCEMENT_PROFILE_UNAVAILABLE:503,ENHANCEMENT_RECORD_UNAVAILABLE:503,ENHANCEMENT_RETRY_SAME_REQUEST:503,MATERIAL_ENHANCEMENT_NOT_INSTALLED:503,MATERIAL_BYTES_UNAVAILABLE:503};
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export class ApiError extends Error {
 constructor(public readonly kind:'http'|'network'|'response', public readonly status:number, message:string, public readonly code?:string, public readonly traceId?:string, public readonly details?:unknown) { super(message); this.name='ApiError'; }
}
export interface RequestOptions extends RequestInit { maxResponseBytes?:number; expectedStatus?:number; errorContract?:'saved-intent'|'saved-transcript'|'material-enhancement'|'final-publication' }
const messages:Record<number,string>={400:'请求内容无效，请检查后重新提交。',401:'登录状态不可用，请重新登录。',403:'暂无访问权限。',404:'内容不存在或当前不可用。',409:'内容已发生变化，请保留草稿并核对最新版本。',413:'内容超过当前处理限额。',422:'当前输入格式暂不支持。',429:'录音上传暂时受限，音频已保留，请稍后恢复上传。'};
type TranscriptRejectionCode=components['schemas']['TranscriptCommandRejection']['error']['code'];
const transcriptRejectionStatus:Readonly<Record<TranscriptRejectionCode,number>>={TRANSCRIPT_NOT_FOUND:404,REVISION_CONFLICT:409,CURRENT_REVISION_REQUIRED:409,MEETING_NOT_WRITABLE:409,REVISION_EXHAUSTED:409,IDEMPOTENCY_CONFLICT:409,TRANSCRIPT_CONFLICT:409,REQUEST_TOO_LARGE:413,WORKING_REQUIRED:422,COMPLETE_SEGMENTS_REQUIRED:422,SEGMENT_IDENTITY_CHANGED:422,EMPTY_TRANSCRIPT:422,CORRECTION_NOT_FOUND:404,CORRECTION_ALREADY_HANDLED:409,CORRECTION_STALE:409,TRANSCRIPT_ALREADY_CONFIRMED:409,CORRECTIONS_PENDING:422,CORRECTION_NOT_AVAILABLE:422};
const transcriptRejectionCodes:ReadonlySet<string>=new Set(Object.keys(transcriptRejectionStatus));
function aborted(error:unknown,signal?:AbortSignal|null):boolean {return Boolean(signal?.aborted)||(error instanceof Error&&error.name==='AbortError');}
async function boundedJson(response:Response,maximum:number):Promise<unknown> {
 const length=response.headers.get('Content-Length');
 if(length!==null&&/^\d+$/.test(length)&&BigInt(length)>BigInt(maximum)){await response.body?.cancel();throw new Error('Response exceeds transport bound');}
 if(!response.body)throw new Error('Response body missing');
 const reader=response.body.getReader();let buffer:Uint8Array<ArrayBufferLike>=new Uint8Array(Math.min(maximum,8192));let total=0;
 try {
  while(true){const {done,value}=await reader.read();if(done)break;if(value.byteLength>maximum-total)throw new Error('Response exceeds transport bound');const needed=total+value.byteLength;if(needed>buffer.byteLength){const grown=new Uint8Array(Math.min(maximum,Math.max(needed,buffer.byteLength*2)));grown.set(buffer.subarray(0,total));buffer=grown;}buffer.set(value,total);total=needed;}
 }catch(error){try{await reader.cancel();}catch{/* Preserve the original read/cancellation error. */}throw error;}
 finally{reader.releaseLock();}
 return JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(buffer.subarray(0,total))) as unknown;
}
function safeError(value:unknown,codes:ReadonlySet<string>=allKnownCodes):{code?:string;traceId?:string} {
 if(typeof value!=='object'||value===null||Array.isArray(value)||Object.keys(value).length!==1||!('error' in value))return {};
 const error=value.error;if(typeof error!=='object'||error===null||Array.isArray(error))return {};
 if(Object.keys(error).sort().join(',')!=='code,message,traceId'||!('code' in error)||!('message' in error)||!('traceId' in error))return {};
 if(typeof error.code!=='string'||!codes.has(error.code)||typeof error.message!=='string'||error.message.length>4096||typeof error.traceId!=='string'||!UUID.test(error.traceId))return {};
 return {code:error.code,traceId:error.traceId};
}
/** The single JSON transport boundary; business facade adds same-origin route and current-session requirements. */
export async function request<T>(path:string, init:RequestOptions={}):Promise<T> {
 const {maxResponseBytes=8*1024*1024,expectedStatus,errorContract,...options}=init;
 const intentMeeting=errorContract==='saved-intent'&&(options.method??'GET').toUpperCase()==='POST'?new URL(path,'https://local.invalid').pathname.match(/^\/api\/v1\/meetings\/([0-9a-f-]{36})\/minutes\/editor-intents$/)?.[1]:undefined;
 const method=(options.method??'GET').toUpperCase(),transcriptRoute=errorContract==='saved-transcript'?new URL(path,'https://local.invalid').pathname.match(/^\/api\/v1\/meetings\/([0-9a-f-]{36})\/transcript(\/marks|\/speakers|\/restorations|\/confirmations|\/replacements|\/correction-proposals|\/operations\/([0-9a-f-]{36})\/undo|\/corrections\/([0-9a-f-]{36})\/decisions)?$/):null;
 const transcriptCommand=transcriptRoute&&UUID.test(transcriptRoute[1]!)&&(method==='PUT'&&(!transcriptRoute[2]||transcriptRoute[2]==='/marks')||method==='POST'&&(['/speakers','/restorations','/confirmations','/replacements','/correction-proposals'].includes(transcriptRoute[2]??'')||!!transcriptRoute[3]&&UUID.test(transcriptRoute[3])||!!transcriptRoute[4]&&UUID.test(transcriptRoute[4])));
 const enhancementCommand=errorContract==='material-enhancement'&&method==='POST'&&/^\/api\/v1\/meetings\/[0-9a-f-]{36}\/transcript\/material-enhancements(?:\/[0-9a-f-]{36}\/adoption)?$/.test(new URL(path,'https://local.invalid').pathname);
 const publicationRoute=errorContract==='final-publication'?new URL(path,'https://local.invalid').pathname.match(/^\/api\/v1\/meetings\/([0-9a-f-]{36})\/(final-confirmations|publication-configuration|publications(?:\/([0-9a-f-]{36})\/(?:zero-effect-retries|resubmissions|local-recoveries))?)$/):null;
 const publicationCommand=publicationRoute&&UUID.test(publicationRoute[1]!)&&(!publicationRoute[3]||UUID.test(publicationRoute[3]))&&(publicationRoute[2]==='publication-configuration'?method==='PUT':method==='POST');
 if(errorContract&&!intentMeeting&&!transcriptCommand&&!enhancementCommand&&!publicationCommand)throw new TypeError('Error contract requires the matching product command');
 if(!Number.isSafeInteger(maxResponseBytes)||maxResponseBytes<1||maxResponseBytes>8*1024*1024)throw new TypeError('Invalid response limit');
 if(expectedStatus!==undefined&&(!Number.isInteger(expectedStatus)||expectedStatus<200||expectedStatus>299))throw new TypeError('Invalid expected status');
 const readDeadline=method==='GET'&&/\/(minutes|workspace|final-confirmations|publications|info-suggestions)(?:[/?]|$)/.test(path)?AbortSignal.timeout(20000):null;
 const originalSignal=options.signal;if(readDeadline)options.signal=originalSignal?AbortSignal.any([originalSignal,readDeadline]):readDeadline;
 const checkReadDeadline=()=>{if(readDeadline?.aborted&&!originalSignal?.aborted)throw new ApiError('network',0,'读取会议内容超时，请重新读取。');options.signal?.throwIfAborted();};
 const headers=new Headers(options.headers);if(!headers.has('Accept'))headers.set('Accept','application/json');
 let release=()=>{};
 try {
 let response:Response;
 const resource=new URL(path,'https://local.invalid');
 const displayRenewal=method==='GET'&&!resource.search&&!resource.hash&&/^\/api\/v1\/meetings\/[0-9a-f-]{36}\/workspace\/access$/.test(resource.pathname);
 try {if(!displayRenewal){const slot=admission.acquire(options.signal);release=typeof slot==='function'?slot:await slot;}response=await fetch(path,{...options,...(displayRenewal?{priority:'high' as const}:{}),headers,credentials:'include',redirect:'error'});}
 catch(error){checkReadDeadline();if(aborted(error,options.signal))throw error;throw new ApiError('network',0,'网络连接不可用，请检查网络后重试。');}
 checkReadDeadline();
 if(!response.ok) {
  let safe:{code?:string;traceId?:string}={};let details:unknown;
  try{const body=await boundedJson(response,16384);safe=safeError(body);if(publicationCommand){const declared=safeError(body,new Set(Object.keys(publicationErrors)));if(declared.code&&publicationErrors[declared.code]?.[0]===response.status)safe=declared;}if(enhancementCommand){const declared=safeError(body,new Set(enhancementCodes));if(declared.code&&enhancementStatus[declared.code]===response.status)safe=declared;}if(transcriptCommand){const declared=safeError(body,transcriptRejectionCodes);if(declared.code&&transcriptRejectionStatus[declared.code as TranscriptRejectionCode]===response.status)safe=declared;}if(response.status===409&&intentMeeting&&intentConflict(body,intentMeeting)){safe={code:'REVISION_CONFLICT'};details=body;}}catch(error){checkReadDeadline();if(aborted(error,options.signal))throw error;}
  throw new ApiError(safe.code?'http':'response',response.status,publicationCommand&&safe.code&&publicationErrors[safe.code]?.[0]===response.status?publicationErrors[safe.code]![1]:safe.code&&materialMessages[safe.code]?.[0]===response.status?materialMessages[safe.code]![1]:safe.code==='TOPIC_NOT_PUBLISHED'&&response.status===422?'仅本人负责的已发布会议可归入专题。':messages[response.status]??'服务暂不可用，请稍后重试。',safe.code,safe.traceId,details);
 }
 if(expectedStatus!==undefined&&response.status!==expectedStatus){try{await response.body?.cancel();}catch{/* Keep the unexpected-success failure. */}throw new ApiError('response',response.status,'操作响应状态异常，请保留原请求并核对结果。');}
 checkReadDeadline();
 if(response.status===204||response.status===205)return undefined as T;
 try{const value=await boundedJson(response,maxResponseBytes);checkReadDeadline();return value as T;}
 catch(error){checkReadDeadline();if(aborted(error,options.signal))throw error;throw new ApiError('response',response.status,'服务响应格式异常，请稍后重试。');}
 } finally {release();}
}
