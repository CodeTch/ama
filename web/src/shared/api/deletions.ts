import type {components} from './generated';import type {CommandSnapshot} from '../composables/useCommand';import type {createBusinessClient} from './business-client';import {ApiError} from './client';import {revisionToken} from './intent-contract';
export type DeletionJob=components['schemas']['DeletionJob'];
// One private outstanding deletion per browser tab. This is a journal namespace, never a meeting authority.
export const deletionJournalScope='00000000-0000-4000-8000-00000000d007';
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
const shape=(v:unknown,keys:string):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v)&&Object.keys(v).sort().join(',')===keys;
export function deletionMeeting(command:CommandSnapshot){const m=/^\/api\/v1\/meetings\/([0-9a-f-]{36})$/.exec(command.path);if(!m||!uuid(m[1]))throw new TypeError('Invalid deletion target');return m[1];}
export function validDeletionCommand(v:unknown,actor:string,scope:string):v is CommandSnapshot{
 if(!shape(v,'actor,body,key,operation,path,resource')||scope!==deletionJournalScope||v.actor!==actor||v.resource!==scope||v.operation!=='MEETING_DELETE'||!uuid(v.key)||typeof v.path!=='string'||typeof v.body!=='string'||v.body.length>256)return false;
 try{deletionMeeting(v as CommandSnapshot);const b:unknown=JSON.parse(v.body);return shape(b,'expectedRevisionToken')&&revisionToken(b.expectedRevisionToken)&&JSON.stringify(b)===v.body;}catch{return false;}
}
function job(v:unknown):v is DeletionJob{return shape(v,'attempts,deletionTaskId,errorCode,meetingId,phase,revisionToken,state')&&uuid(v.deletionTaskId)&&uuid(v.meetingId)&&revisionToken(v.revisionToken)&&['PENDING','RUNNING','FAILED','COMPLETE'].includes(v.state as string)&&['INVENTORY','STORAGE','PURGE','COMPLETE'].includes(v.phase as string)&&(v.state==='COMPLETE')===(v.phase==='COMPLETE')&&typeof v.attempts==='number'&&Number.isSafeInteger(v.attempts)&&v.attempts>=0&&(v.errorCode===null||v.errorCode==='CLEANUP_UNAVAILABLE');}
export function createDeletionsApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){
 const invalid=()=>new ApiError('response',200,'删除回执暂无法核实，请保留原请求查回。');
 function owner(){const who=actor();if(!who)throw new ApiError('http',401,'请重新登录。');return who;}
 function active(who:string,signal?:AbortSignal){if(actor()!==who||signal?.aborted)throw new DOMException('Deletion identity changed','AbortError');}
 return {
  async send(original:CommandSnapshot):Promise<DeletionJob>{const who=owner();if(!validDeletionCommand(original,who,deletionJournalScope))throw new TypeError('Original deletion required');const value=await client.json<unknown>(original.path,{method:'DELETE',headers:{'Idempotency-Key':original.key},body:original.body,expectedStatus:202,maxResponseBytes:4096});active(who);if(!job(value)||value.meetingId!==deletionMeeting(original)||BigInt(value.revisionToken)!==BigInt(JSON.parse(original.body).expectedRevisionToken)+1n)throw invalid();return Object.freeze(value);},
  async read(original:DeletionJob,signal:AbortSignal):Promise<DeletionJob>{const who=owner();if(!job(original))throw new TypeError('Deletion receipt required');const value=await client.json<unknown>(`/api/v1/deletions/${original.deletionTaskId}`,{signal,expectedStatus:200,maxResponseBytes:4096});active(who,signal);if(!job(value)||value.meetingId!==original.meetingId||value.deletionTaskId!==original.deletionTaskId||value.revisionToken!==original.revisionToken||original.state==='COMPLETE'&&value.state!=='COMPLETE')throw invalid();return Object.freeze(value);}
 };
}
