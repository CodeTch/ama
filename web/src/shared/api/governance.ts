import type {components} from './generated';
import {ApiError} from './client';import type {createBusinessClient} from './business-client';import type {CommandSnapshot} from '../composables/useCommand';import {fitsUtf8} from '../bounded-text';
export type GovernanceAction='WITHDRAW'|'OBSOLETE';
export type GovernanceJob=components['schemas']['GovernanceJob'];
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
const shape=(v:unknown,keys:string):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v)&&Object.keys(v).sort().join(',')===keys;
const version=(v:unknown):v is string=>typeof v==='string'&&/^(0|[1-9][0-9]{0,18})$/.test(v)&&BigInt(v)<9223372036854775807n;
export const validGovernanceReason=(v:unknown):v is string=>typeof v==='string'&&v.trim().length>0&&Array.from(v).length<=2048&&fitsUtf8(v,8192);
export function governanceTarget(original:CommandSnapshot){const found=/^\/api\/v1\/meetings\/([0-9a-f-]{36})\/publications\/([0-9a-f-]{36})\/(withdrawal|obsolescence)$/.exec(original.path);if(!found||!uuid(found[1])||!uuid(found[2]))throw new TypeError('Invalid governance target');return {meetingId:found[1],publicationId:found[2],action:(found[3]==='withdrawal'?'WITHDRAW':'OBSOLETE') as GovernanceAction};}
export function validGovernanceCommand(v:unknown,actor:string,meeting:string):v is CommandSnapshot{
 if(!shape(v,'actor,body,key,operation,path,resource')||v.actor!==actor||v.resource!==meeting||!uuid(meeting)||v.operation!=='PUBLICATION_GOVERNANCE'||!uuid(v.key)||typeof v.path!=='string'||typeof v.body!=='string'||!fitsUtf8(v.body,32768))return false;
 try{const target=governanceTarget(v as CommandSnapshot),body:unknown=JSON.parse(v.body);return target.meetingId===meeting&&shape(body,'expectedRevisionToken,reason')&&JSON.stringify(body)===v.body&&version(body.expectedRevisionToken)&&validGovernanceReason(body.reason);}catch{return false;}
}
function job(v:unknown):v is GovernanceJob{return shape(v,'action,createdAt,finishedAt,governanceJobId,lastError,meetingId,publicationId,reason,state')&&uuid(v.governanceJobId)&&uuid(v.meetingId)&&uuid(v.publicationId)&&['WITHDRAW','OBSOLETE'].includes(v.action as string)&&['PENDING','RECONCILING','SUCCEEDED','CONFLICT'].includes(v.state as string)&&validGovernanceReason(v.reason)&&typeof v.createdAt==='string'&&v.createdAt.length<=40&&(v.finishedAt===null||typeof v.finishedAt==='string'&&v.finishedAt.length<=40)&&(v.lastError===null||typeof v.lastError==='string'&&/^[A-Z][A-Z0-9_]{0,63}$/.test(v.lastError));}
export function createGovernanceApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){
 const invalid=()=>new ApiError('response',200,'治理结果暂无法核实，请保留原请求查回。');
 function who(){const current=actor();if(!current)throw new ApiError('http',401,'请重新登录。');return current;}
 function active(owner:string,signal?:AbortSignal){if(actor()!==owner||signal?.aborted)throw new DOMException('Governance scope changed','AbortError');}
 function base(meeting:string,publication:string){if(!uuid(meeting)||!uuid(publication))throw new TypeError('Invalid publication');return `/api/v1/meetings/${meeting}/publications/${publication}/governance`;}
 return {
  async send(original:CommandSnapshot):Promise<GovernanceJob>{const owner=who();if(!validGovernanceCommand(original,owner,original.resource))throw new TypeError('Invalid original governance request');const target=governanceTarget(original),body=JSON.parse(original.body);const value=await client.json<unknown>(original.path,{method:'POST',headers:{'Idempotency-Key':original.key},body:original.body,expectedStatus:202,maxResponseBytes:16384});active(owner);if(!job(value)||value.meetingId!==target.meetingId||value.publicationId!==target.publicationId||value.action!==target.action||value.reason!==body.reason)throw invalid();return Object.freeze(value);},
  async read(meeting:string,publication:string,id:string,signal:AbortSignal):Promise<GovernanceJob>{const owner=who();if(!uuid(id))throw new TypeError('Invalid governance job');const value=await client.json<unknown>(base(meeting,publication)+`/${id}`,{signal,expectedStatus:200,maxResponseBytes:16384});active(owner,signal);if(!job(value)||value.meetingId!==meeting||value.publicationId!==publication||value.governanceJobId!==id)throw invalid();return Object.freeze(value);},
  async history(meeting:string,publication:string,signal:AbortSignal):Promise<readonly GovernanceJob[]>{const owner=who();const value=await client.json<unknown>(base(meeting,publication),{signal,expectedStatus:200,maxResponseBytes:262144});active(owner,signal);if(!Array.isArray(value)||value.length>20||value.some(x=>!job(x)||x.meetingId!==meeting||x.publicationId!==publication))throw invalid();return Object.freeze(value.map(x=>Object.freeze(x)));},
 };
}
