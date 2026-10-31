import type { components } from './generated';
import {intentConflict} from './intent-contract';

type DeclaredErrorCode = { [K in keyof components['schemas']]: components['schemas'][K] extends {error: {code: infer C extends string}} ? C : never }[keyof components['schemas']];
const knownCodes: ReadonlySet<string> = new Set(["CATEGORY_DICTIONARY_UNAVAILABLE", "FILE_CONFLICT", "FILE_INVALID_RANGE", "FILE_NOT_FOUND", "FILE_PURPOSE_UNAVAILABLE", "FILE_STORAGE_UNAVAILABLE", "FORBIDDEN", "IDENTITY_PROVIDER_UNAVAILABLE", "IDENTITY_UNAVAILABLE", "INVALID_ARGUMENT", "MEETING_CONFLICT", "MEETING_FORBIDDEN", "MEETING_UNAVAILABLE", "REQUEST_TOO_LARGE", "UNAUTHENTICATED", "RECORDING_CONFLICT", "RECORDING_GAP", "RECORDING_PAUSED", "RECORDING_UNAVAILABLE", "RECORDING_RATE_LIMITED", "RECORDING_READ_ONLY", "RECORDING_ARCHIVE_NOT_READY", "RECORDING_ARCHIVE_CONFLICT", "RECORDING_ARCHIVE_UNAVAILABLE", "INVALID_RANGE"] satisfies readonly DeclaredErrorCode[]);
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export class ApiError extends Error {
 constructor(public readonly kind:'http'|'network'|'response', public readonly status:number, message:string, public readonly code?:string, public readonly traceId?:string, public readonly details?:unknown) { super(message); this.name='ApiError'; }
}
export interface RequestOptions extends RequestInit { maxResponseBytes?:number; expectedStatus?:number; errorContract?:'saved-intent'|'saved-transcript' }
const messages:Record<number,string>={400:'请求内容无效，请检查后重新提交。',401:'登录状态不可用，请重新登录。',403:'暂无访问权限。',404:'内容不存在或当前不可用。',409:'内容已发生变化，请保留草稿并核对最新版本。',413:'内容超过当前处理限额。',422:'当前输入格式暂不支持。',429:'录音上传暂时受限，音频已保留，请稍后恢复上传。'};
type TranscriptRejectionCode=components['schemas']['TranscriptCommandRejection']['error']['code'];
const transcriptRejectionStatus:Readonly<Record<TranscriptRejectionCode,number>>={TRANSCRIPT_NOT_FOUND:404,REVISION_CONFLICT:409,CURRENT_REVISION_REQUIRED:409,MEETING_NOT_WRITABLE:409,REVISION_EXHAUSTED:409,IDEMPOTENCY_CONFLICT:409,TRANSCRIPT_CONFLICT:409,REQUEST_TOO_LARGE:413,WORKING_REQUIRED:422,COMPLETE_SEGMENTS_REQUIRED:422,SEGMENT_IDENTITY_CHANGED:422,EMPTY_TRANSCRIPT:422};
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
function safeError(value:unknown,codes:ReadonlySet<string>=knownCodes):{code?:string;traceId?:string} {
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
 const method=(options.method??'GET').toUpperCase(),transcriptRoute=errorContract==='saved-transcript'?new URL(path,'https://local.invalid').pathname.match(/^\/api\/v1\/meetings\/([0-9a-f-]{36})\/transcript(\/marks|\/restorations|\/confirmations|\/replacements|\/operations\/([0-9a-f-]{36})\/undo)?$/):null;
 const transcriptCommand=transcriptRoute&&UUID.test(transcriptRoute[1]!)&&(method==='PUT'&&(!transcriptRoute[2]||transcriptRoute[2]==='/marks')||method==='POST'&&(['/restorations','/confirmations','/replacements'].includes(transcriptRoute[2]??'')||!!transcriptRoute[3]&&UUID.test(transcriptRoute[3])));
 if(errorContract&&!intentMeeting&&!transcriptCommand)throw new TypeError('Error contract requires the matching product command');
 if(!Number.isSafeInteger(maxResponseBytes)||maxResponseBytes<1||maxResponseBytes>8*1024*1024)throw new TypeError('Invalid response limit');
 if(expectedStatus!==undefined&&(!Number.isInteger(expectedStatus)||expectedStatus<200||expectedStatus>299))throw new TypeError('Invalid expected status');
 const headers=new Headers(options.headers);if(!headers.has('Accept'))headers.set('Accept','application/json');
 let response:Response;
 try {response=await fetch(path,{...options,headers,credentials:'include',redirect:'error'});}
 catch(error){if(aborted(error,options.signal))throw error;throw new ApiError('network',0,'网络连接不可用，请检查网络后重试。');}
 if(!response.ok) {
  let safe:{code?:string;traceId?:string}={};let details:unknown;
  try{const body=await boundedJson(response,16384);safe=safeError(body);if(transcriptCommand){const declared=safeError(body,transcriptRejectionCodes);if(declared.code&&transcriptRejectionStatus[declared.code as TranscriptRejectionCode]===response.status)safe=declared;}if(response.status===409&&intentMeeting&&intentConflict(body,intentMeeting)){safe={code:'REVISION_CONFLICT'};details=body;}}catch(error){if(aborted(error,options.signal))throw error;}
  throw new ApiError(safe.code?'http':'response',response.status,messages[response.status]??'服务暂不可用，请稍后重试。',safe.code,safe.traceId,details);
 }
 if(expectedStatus!==undefined&&response.status!==expectedStatus){try{await response.body?.cancel();}catch{/* Keep the unexpected-success failure. */}throw new ApiError('response',response.status,'操作响应状态异常，请保留原请求并核对结果。');}
 if(response.status===204||response.status===205)return undefined as T;
 try{return await boundedJson(response,maxResponseBytes) as T;}
 catch(error){if(aborted(error,options.signal))throw error;throw new ApiError('response',response.status,'服务响应格式异常，请稍后重试。');}
}
