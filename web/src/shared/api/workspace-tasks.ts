import type {components} from './generated';
import type {createBusinessClient} from './business-client';
import {ApiError} from './client';
import {revisionToken} from './intent-contract';
export type WorkspaceTaskPage=components['schemas']['WorkspaceTaskPage'];
export type WorkspaceTaskKind=components['schemas']['WorkspaceTaskItem']['kind']|'ALL';
const kinds=['TRANSCRIPT_IMPORT_V1','MINUTES_GENERATE_V1','KNOWLEDGE_PUBLISH_V1'];
const uuid=(value:unknown):value is string=>typeof value==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value);
const cursor=(value:unknown):value is string=>typeof value==='string'&&/^[A-Za-z0-9_-]{1,1536}$/.test(value);
function shape(value:unknown,keys:string):value is Record<string,unknown>{return typeof value==='object'&&value!==null&&!Array.isArray(value)&&Object.keys(value).sort().join(',')===keys;}
function item(value:unknown,meeting:string,kind:WorkspaceTaskKind):value is components['schemas']['WorkspaceTaskItem']{
 if(!shape(value,'attemptToken,businessRevisionToken,detailPath,inputId,kind,state,taskId')||!uuid(value.taskId)||!uuid(value.inputId)||typeof value.kind!=='string'||!kinds.includes(value.kind)||kind!=='ALL'&&value.kind!==kind||typeof value.state!=='string'||!['READY','RUNNING','RECONCILING','SUCCEEDED','FAILED','CANCELLED','STALE'].includes(value.state)||typeof value.attemptToken!=='string'||!(/^[0-3]$/).test(value.attemptToken)||!revisionToken(value.businessRevisionToken))return false;
 const suffix=value.kind==='TRANSCRIPT_IMPORT_V1'?`processing-tasks/${value.taskId}`:value.kind==='MINUTES_GENERATE_V1'?`minutes/generation-tasks/${value.taskId}`:`publications/${value.inputId}`;
 return value.detailPath===`/api/v1/meetings/${meeting}/${suffix}`;
}
function page(value:unknown,meeting:string,kind:WorkspaceTaskKind):value is WorkspaceTaskPage{
 return shape(value,'hasMore,items,nextCursor,verification')&&value.verification==='PERSISTED_RELATION'&&Array.isArray(value.items)&&value.items.length<=20&&value.items.every(row=>item(row,meeting,kind))&&new Set(value.items.map(row=>row.taskId)).size===value.items.length&&typeof value.hasMore==='boolean'&&(value.nextCursor===null||cursor(value.nextCursor))&&value.hasMore===(value.nextCursor!==null);
}
/** Owner-only task navigation observation; task states and links convey no command authority. */
export function createWorkspaceTasksApi(client:ReturnType<typeof createBusinessClient>){
 return {async list(meeting:string,kind:WorkspaceTaskKind,next:string|null,signal:AbortSignal):Promise<WorkspaceTaskPage>{
  if(!uuid(meeting)||kind!=='ALL'&&!kinds.includes(kind)||next!==null&&!cursor(next))throw new TypeError('Invalid task query');const query=new URLSearchParams({kind,limit:'20'});if(next!==null)query.set('cursor',next);
  const result=await client.json<unknown>(`/api/v1/meetings/${meeting}/tasks?${query}`,{signal,expectedStatus:200,maxResponseBytes:131072});if(!page(result,meeting,kind))throw new ApiError('response',200,'任务列表响应暂无法确认，请重新加载。');
  result.items.forEach(Object.freeze);Object.freeze(result.items);return Object.freeze(result);
 }};
}
