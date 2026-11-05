import type {createBusinessClient} from '../../shared/api/business-client';
import {recordingUuid} from './recording-api';

export type ProcessingMaterial={
 materialRevisionId:string;name:string;state:'READY'|'FAILED'|'UNSUPPORTED';
 source?:'LOCAL'|'KNOWLEDGE';sourceLibraryName?:string|null;sourceLibraryId?:string|null;sourceVersion?:string|null;error?:string|null;
};
export type RecordingProcessing={
 sessionId:string;meetingId:string;state:'NOT_REQUESTED'|'WAITING_ARCHIVE'|'COPYING'|'TRANSCRIBING'|'ENHANCING'|'SUCCEEDED'|'FAILED'|'STALE'|'CANCELLED';
 runId?:string;materialSnapshotId?:string|null;taskId?:string|null;errorCode?:string|null;materialsChanged?:boolean;materialFiles?:ProcessingMaterial[];
};
export function validMaterial(item:ProcessingMaterial){
 return item&&typeof item==='object'&&Object.keys(item).every(key=>['materialRevisionId','name','state','source','sourceLibraryId','sourceLibraryName','sourceVersion','error'].includes(key))
  &&recordingUuid(item.materialRevisionId)&&typeof item.name==='string'&&item.name.length>0&&item.name.length<=1024&&['READY','FAILED','UNSUPPORTED'].includes(item.state)
  &&(item.source===undefined||item.source==='LOCAL'||item.source==='KNOWLEDGE')
  &&(item.sourceVersion==null||typeof item.sourceVersion==='string'&&/^[0-9a-f]{64}$/.test(item.sourceVersion))
  &&(item.sourceLibraryName==null||typeof item.sourceLibraryName==='string'&&item.sourceLibraryName.length<=1024)
  &&(item.sourceLibraryId==null||typeof item.sourceLibraryId==='string'&&/^[A-Za-z0-9_-]{1,128}$/.test(item.sourceLibraryId))
  &&(item.error==null||typeof item.error==='string'&&/^[A-Z0-9_]{1,128}$/.test(item.error));
}
export function recordingProcessingApi(client:ReturnType<typeof createBusinessClient>){
 async function request(session:string,method='GET',body?:object,key?:string,signal?:AbortSignal):Promise<RecordingProcessing>{
  if(!recordingUuid(session))throw Error('录音标识无效');
  const value=await client.json<RecordingProcessing>(`/api/v1/recordings/${session}/processing${['RETRY','CANCEL','RESTART'].includes(method)?'/'+method.toLowerCase():''}`,{signal,method:['RETRY','CANCEL','RESTART'].includes(method)?'POST':method,body:body?JSON.stringify(body):undefined,headers:key?{'Idempotency-Key':key}:undefined,expectedStatus:200,maxResponseBytes:524288});
  const allowed=['sessionId','meetingId','state','runId','materialSnapshotId','taskId','errorCode','materialsChanged','materialFiles'];
  if(!value||typeof value!=='object'||Array.isArray(value)||Object.keys(value).some(key=>!allowed.includes(key))||value.sessionId!==session||!recordingUuid(value.meetingId)||!['NOT_REQUESTED','WAITING_ARCHIVE','COPYING','TRANSCRIBING','ENHANCING','SUCCEEDED','FAILED','STALE','CANCELLED'].includes(value.state)||value.runId!==undefined&&!recordingUuid(value.runId)||value.taskId!=null&&!recordingUuid(value.taskId)||value.materialSnapshotId!=null&&!recordingUuid(value.materialSnapshotId)||value.errorCode!=null&&(typeof value.errorCode!=='string'||!/^[A-Za-z0-9_]{1,128}$/.test(value.errorCode)))throw Error('录音处理状态暂无法核对');
  if(value.materialsChanged!==undefined&&typeof value.materialsChanged!=='boolean'||value.materialFiles!==undefined&&(!Array.isArray(value.materialFiles)||value.materialFiles.length>64||!value.materialFiles.every(validMaterial)||new Set(value.materialFiles.map(item=>item.materialRevisionId)).size!==value.materialFiles.length))throw Error('录音材料快照暂无法核对');
  return value;
 }
 return{change:(session:string,action:'CANCEL'|'RESTART',runId:string,revision:string,key:string)=>request(session,action,{runId,expectedRevisionToken:revision},key),read:(session:string,signal?:AbortSignal)=>request(session,'GET',undefined,undefined,signal),start:(session:string,key:string,revision:string,snapshot:string|null)=>request(session,'POST',{expectedRevisionToken:revision,materialSnapshotId:snapshot},key),retry:(session:string,task:string|null)=>request(session,'RETRY',{expectedTaskId:task})};
}
