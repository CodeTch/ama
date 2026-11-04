import type {createBusinessClient} from '../../shared/api/business-client';
import {recordingUuid} from './recording-api';
export type RecordingProcessing={sessionId:string;meetingId:string;state:'NOT_REQUESTED'|'WAITING_ARCHIVE'|'COPYING'|'TRANSCRIBING'|'ENHANCING'|'SUCCEEDED'|'FAILED'|'STALE';materialSnapshotId?:string|null;taskId?:string|null;errorCode?:string|null;materialsChanged?:boolean;materialFiles?:{materialRevisionId:string;name:string;state:'READY'|'FAILED'|'UNSUPPORTED'}[]};
export function recordingProcessingApi(client:ReturnType<typeof createBusinessClient>){
 async function request(session:string,method='GET',body?:object,key?:string):Promise<RecordingProcessing>{
  if(!recordingUuid(session))throw Error('录音标识无效');
  const value=await client.json<RecordingProcessing>(`/api/v1/recordings/${session}/processing${method==='RETRY'?'/retry':''}`,{method:method==='RETRY'?'POST':method,body:body?JSON.stringify(body):undefined,headers:key?{'Idempotency-Key':key}:undefined,expectedStatus:200,maxResponseBytes:131072});
  const allowed=['sessionId','meetingId','state','materialSnapshotId','taskId','errorCode','materialsChanged','materialFiles'];
  if(!value||typeof value!=='object'||Array.isArray(value)||Object.keys(value).some(key=>!allowed.includes(key))||value.sessionId!==session||!recordingUuid(value.meetingId)||!['NOT_REQUESTED','WAITING_ARCHIVE','COPYING','TRANSCRIBING','ENHANCING','SUCCEEDED','FAILED','STALE'].includes(value.state)||value.taskId!=null&&!recordingUuid(value.taskId)||value.materialSnapshotId!=null&&!recordingUuid(value.materialSnapshotId)||value.errorCode!=null&&(typeof value.errorCode!=='string'||!/^[A-Za-z0-9_]{1,128}$/.test(value.errorCode)))throw Error('录音处理状态暂无法核对');
  if(value.materialsChanged!==undefined&&typeof value.materialsChanged!=='boolean'||value.materialFiles!==undefined&&(!Array.isArray(value.materialFiles)||value.materialFiles.length>64||value.materialFiles.some(item=>!item||typeof item!=='object'||Object.keys(item).sort().join(',')!=='materialRevisionId,name,state'||!recordingUuid(item.materialRevisionId)||typeof item.name!=='string'||!item.name.length||item.name.length>1024||!['READY','FAILED','UNSUPPORTED'].includes(item.state))))throw Error('录音材料快照暂无法核对');
  return value;
 }
 return{read:(session:string)=>request(session),start:(session:string,key:string,revision:string,snapshot:string|null)=>request(session,'POST',{expectedRevisionToken:revision,materialSnapshotId:snapshot},key),retry:(session:string,task:string|null)=>request(session,'RETRY',{expectedTaskId:task})};
}
