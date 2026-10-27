import type {components,operations} from './generated';
import type {createBusinessClient} from './business-client';
import type {CommandSnapshot} from '../composables/useCommand';
import {ApiError} from './client';
import {revisionToken} from './intent-contract';
import {fitsUtf8} from '../bounded-text';
export type CommandLookup=components['schemas']['CommandLookup'];
export type CommandIdentity=Pick<CommandSnapshot,'actor'|'operation'|'resource'|'key'>;
type Query=operations['discoverMeetingCommand']['requestBody']['content']['application/json'];
const object=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v);
const exact=(v:Record<string,unknown>,keys:string)=>Object.keys(v).sort().join(',')===keys;
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
const operation=(v:unknown):v is Query['operation']=>v==='CREATE'||v==='SAVE_INFO'||v==='CONFIRM_ROSTER'||v==='UPLOAD_BEGIN';
function observation(v:unknown):v is components['schemas']['CommandObservation']{
 return object(v)&&exact(v,'accessRevisionToken,meetingId,revisionToken,stage')&&uuid(v.meetingId)&&revisionToken(v.revisionToken)&&revisionToken(v.accessRevisionToken)&&typeof v.stage==='string'&&['ENTRY','TRANSCRIPT_PENDING','TRANSCRIPT_FAILED','MINUTES_PENDING','MINUTES_FAILED','READY_TO_PUBLISH','PUBLISH_FAILED','PUBLISHED'].includes(v.stage);
}
function lookup(v:unknown,query:Query):v is CommandLookup{
 if(!object(v)||!exact(v,'association,current,status'))return false;
 const create=query.operation==='CREATE',upload=query.operation==='UPLOAD_BEGIN';
 if(v.status==='ABSENT')return v.association===null&&(create?v.current===null:observation(v.current)&&v.current.meetingId===query.resource);
 if(v.status!=='FOUND'||!object(v.association)||!observation(v.current))return false;
 const a=v.association;
 return exact(a,'acceptedRevisionToken,meetingId,operation,originalHttpStatus,resultId,resultKind')&&a.operation===query.operation&&uuid(a.meetingId)&&uuid(a.resultId)
 &&a.meetingId===v.current.meetingId&&(create||a.meetingId===query.resource)
 &&a.resultKind===(upload?'UPLOAD_SESSION':query.operation==='CONFIRM_ROSTER'?'ROSTER_REVISION':'INFO_REVISION')
 &&a.originalHttpStatus===(create||upload?201:200)&&(upload?a.acceptedRevisionToken===null:revisionToken(a.acceptedRevisionToken)&&(!create||a.acceptedRevisionToken==='0'));
}
/** Discovery is observational. FOUND is not an ACK and ABSENT cannot replace the original key/body. */
export function createCommandDiscoveryApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){
 return {async lookup(identity:CommandIdentity,signal:AbortSignal):Promise<CommandLookup>{
  if(!actor()||actor()!==identity.actor)throw new DOMException('Command actor changed','AbortError');
  if(!operation(identity.operation)||(identity.operation==='CREATE'?identity.resource!=='meetings':!uuid(identity.resource))||typeof identity.key!=='string'||!fitsUtf8(identity.key,4096)||!identity.key.trim())throw new TypeError('Invalid command lookup');
  const query:Query={operation:identity.operation,resource:identity.resource,key:identity.key};
  const body=JSON.stringify(query);if(!fitsUtf8(body,16384))throw new TypeError('Command lookup exceeds wire bound');
  const value=await client.json<unknown>('/api/v1/meetings/command-discovery',{method:'POST',body,signal,maxResponseBytes:16384});
  if(!lookup(value,query))throw new ApiError('response',200,'原操作关联暂无法确认，请保留原请求并重试查询。');
  if(value.association)Object.freeze(value.association);if(value.current)Object.freeze(value.current);return Object.freeze(value);
 }};
}
