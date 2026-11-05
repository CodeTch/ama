import {validMaterial,type ProcessingMaterial} from '../../features/recording/processing-api';
import type {components} from './generated';
import type {createBusinessClient} from './business-client';
import {ApiError} from './client';
import {revisionToken} from './intent-contract';
export type WorkspaceTaskPage=components['schemas']['WorkspaceTaskPage'];
export type WorkspaceTaskKind=components['schemas']['WorkspaceTaskItem']['kind']|'ALL';
const kinds=['TRANSCRIPT_IMPORT_V1','MINUTES_GENERATE_V1','KNOWLEDGE_PUBLISH_V1','RECORDING_ARCHIVE_V1','MINUTES_EXPORT_V1'];
const uuid=(value:unknown):value is string=>typeof value==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value);
const cursor=(value:unknown):value is string=>typeof value==='string'&&/^[A-Za-z0-9_-]{1,1536}$/.test(value);
function shape(value:unknown,keys:string):value is Record<string,unknown>{return typeof value==='object'&&value!==null&&!Array.isArray(value)&&Object.keys(value).sort().join(',')===keys;}
function item(value:unknown,meeting:string,kind:WorkspaceTaskKind):value is components['schemas']['WorkspaceTaskItem']{
 if(!shape(value,'attemptToken,businessRevisionToken,detailPath,inputId,kind,state,taskId')||!uuid(value.taskId)||!uuid(value.inputId)||typeof value.kind!=='string'||!kinds.includes(value.kind)||kind!=='ALL'&&value.kind!==kind||typeof value.state!=='string'||!['READY','RUNNING','RECONCILING','SUCCEEDED','FAILED','CANCELLED','STALE'].includes(value.state)||typeof value.attemptToken!=='string'||!(/^[0-3]$/).test(value.attemptToken)||!revisionToken(value.businessRevisionToken))return false;
 if(value.kind==='MINUTES_EXPORT_V1')return value.detailPath===`/api/v1/exports/${value.taskId}`;
 const suffix=value.kind==='TRANSCRIPT_IMPORT_V1'?`processing-tasks/${value.taskId}`:value.kind==='MINUTES_GENERATE_V1'?`minutes/generation-tasks/${value.taskId}`:value.kind==='RECORDING_ARCHIVE_V1'?`recording-archives/${value.inputId}`:`publications/${value.inputId}`;
 return value.detailPath===`/api/v1/meetings/${meeting}/${suffix}`;
}
export function workspaceTaskPage(value:unknown,meeting:string,kind:WorkspaceTaskKind):value is WorkspaceTaskPage{
 return shape(value,'hasMore,items,nextCursor,verification')&&value.verification==='PERSISTED_RELATION'&&Array.isArray(value.items)&&value.items.length<=20&&value.items.every(row=>item(row,meeting,kind))&&new Set(value.items.map(row=>row.taskId)).size===value.items.length&&typeof value.hasMore==='boolean'&&(value.nextCursor===null||cursor(value.nextCursor))&&value.hasMore===(value.nextCursor!==null);
}
/** Owner-only task navigation observation; task states and links convey no command authority. */
export function createWorkspaceTasksApi(client:ReturnType<typeof createBusinessClient>){
 return {
  async importTask(meeting:string,task:string,signal:AbortSignal){
   if(!uuid(meeting)||!uuid(task))throw new TypeError('Invalid import task');
   const row=await client.json<unknown>(`/api/v1/meetings/${meeting}/processing-tasks/${task}`,{signal,expectedStatus:200,maxResponseBytes:524288});
   const extended=typeof row==='object'&&row!==null&&'materialStage' in row;
   if(!shape(row,'attempt,attemptToken,availableAt,batchId,businessVersion,businessVersionToken,cancelEpochToken,createdAt,errorCode,inputId,'+(extended?'materialFiles,materialStage,':'')+'maxAttempts,meetingId,output,pool,state,taskId,type')
    ||row.taskId!==task||row.meetingId!==meeting||row.type!=='TRANSCRIPT_IMPORT_V1'||row.pool!=='TRANSCRIPT_IMPORT'||!uuid(row.inputId)||!uuid(row.batchId)
    ||typeof row.state!=='string'||!['READY','RUNNING','SUCCEEDED','FAILED','CANCELLED','STALE'].includes(row.state)
    ||!revisionToken(row.businessVersionToken)||!revisionToken(row.cancelEpochToken)||!revisionToken(row.attemptToken)
    ||row.attempt!==Number(row.attemptToken)||row.businessVersion!==Number(row.businessVersionToken)
    ||typeof row.maxAttempts!=='number'||!Number.isInteger(row.maxAttempts)||row.maxAttempts<1||row.attempt>row.maxAttempts
    ||typeof row.createdAt!=='string'||!Number.isFinite(Date.parse(row.createdAt))||typeof row.availableAt!=='string'||!Number.isFinite(Date.parse(row.availableAt))
    ||row.errorCode!==null&&(typeof row.errorCode!=='string'||!/^[A-Z0-9_]{1,128}$/.test(row.errorCode))
    ||(row.state==='SUCCEEDED'?(!shape(row.output,'id,kind')||row.output.kind!=='TRANSCRIPT_REVISION'||!uuid(row.output.id)):row.output!==null))
    throw new ApiError('response',200,'原处理任务响应暂无法核对，请重新读取。');
   if(extended&&(!Array.isArray(row.materialFiles)||row.materialFiles.length>64||!row.materialFiles.every(validMaterial)))throw new ApiError('response',200,'锁定材料无效');
   if(extended&&!['TRANSCRIBING','ENHANCING','ADOPTING','SUCCEEDED','FAILED','CANCELLED','STALE'].includes(String(row.materialStage)))throw new ApiError('response',200,'增强阶段无效');
   return {inputId:row.inputId,state:extended?String(row.materialStage):row.state,errorCode:row.errorCode,...(extended?{materialStage:String(row.materialStage),materialFiles:row.materialFiles as ProcessingMaterial[]}:{})};
  },
  async enhancementCommand(meeting:string,task:string,action:'retry'|'cancel'){
   if(!uuid(meeting)||!uuid(task))throw new TypeError('Invalid task');
   return client.json('/api/v1/meetings/'+meeting+'/processing-tasks/'+task+'/enhancement/'+action,{method:'POST',expectedStatus:200});
  },
  async list(meeting:string,kind:WorkspaceTaskKind,next:string|null,signal:AbortSignal):Promise<WorkspaceTaskPage>{
  if(!uuid(meeting)||kind!=='ALL'&&!kinds.includes(kind)||next!==null&&!cursor(next))throw new TypeError('Invalid task query');const query=new URLSearchParams({kind,limit:'20'});if(next!==null)query.set('cursor',next);
  const result=await client.json<unknown>(`/api/v1/meetings/${meeting}/tasks?${query}`,{signal,expectedStatus:200,maxResponseBytes:131072});if(!workspaceTaskPage(result,meeting,kind))throw new ApiError('response',200,'任务列表响应暂无法确认，请重新加载。');
  result.items.forEach(Object.freeze);Object.freeze(result.items);return Object.freeze(result);
 }};
}
