import {ApiError} from './client';import type {createBusinessClient} from './business-client';import type {CommandSnapshot} from '../composables/useCommand';import type {components} from './generated';
export type ExportFormat=components['schemas']['MinutesExportRequest']['format'];export type ExportJob=components['schemas']['MinutesExportStatus'];
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
const shape=(v:unknown,keys:string):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v)&&Object.keys(v).sort().join(',')===keys;
function job(v:unknown):v is ExportJob{return shape(v,'error,exportJobId,fileId,format,sourceRevision,state')&&uuid(v.exportJobId)&&uuid(v.sourceRevision)&&['md','pdf','docx'].includes(v.format as string)&&['READY','RUNNING','RECONCILING','SUCCEEDED','FAILED','CANCELLED','STALE'].includes(v.state as string)&&(v.fileId===null||uuid(v.fileId)&&v.state==='SUCCEEDED')&&(v.error===null||typeof v.error==='string'&&/^[A-Z][A-Z0-9_]{0,63}$/.test(v.error));}
export function validMinutesExportCommand(v:unknown,actor:string,meeting:string):v is CommandSnapshot{
 if(!shape(v,'actor,body,key,operation,path,resource')||!uuid(meeting)||v.actor!==actor||v.resource!==meeting||v.operation!=='MINUTES_EXPORT'||v.path!==`/api/v1/meetings/${meeting}/exports`||!uuid(v.key)||typeof v.body!=='string'||v.body.length>256)return false;
 try{const body:unknown=JSON.parse(v.body);return JSON.stringify(body)===v.body&&shape(body,'format,sourceRevision')&&uuid(body.sourceRevision)&&['md','pdf','docx'].includes(body.format as string);}catch{return false;}
}
export function createMinutesExportsApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){
 const invalid=()=>new ApiError('response',200,'导出结果暂无法核实，请查回原请求。');
 function who(){const value=actor();if(!value)throw new ApiError('http',401,'请重新登录。');return value;}
 function active(value:string,signal?:AbortSignal){if(actor()!==value||signal?.aborted)throw new DOMException('Export scope changed','AbortError');}
 return {
  async send(original:CommandSnapshot):Promise<ExportJob>{const owner=who();if(!validMinutesExportCommand(original,owner,original.resource))throw new TypeError('Invalid original export request');const expected=JSON.parse(original.body);const value=await client.json<unknown>(original.path,{method:'POST',headers:{'Idempotency-Key':original.key},body:original.body,expectedStatus:202,maxResponseBytes:4096});active(owner);if(!job(value)||value.sourceRevision!==expected.sourceRevision||value.format!==expected.format)throw invalid();return Object.freeze(value);},
  async read(id:string,signal:AbortSignal):Promise<ExportJob>{const owner=who();if(!uuid(id))throw new TypeError('Invalid export task');const value=await client.json<unknown>(`/api/v1/exports/${id}`,{signal,expectedStatus:200,maxResponseBytes:4096});active(owner,signal);if(!job(value)||value.exportJobId!==id)throw invalid();return Object.freeze(value);},
 };
}
