import type {components} from '../../shared/api/generated';
import type {createBusinessClient} from '../../shared/api/business-client';
import {responseMatches} from '../../shared/api/response-schema';
import {ApiError} from '../../shared/api/client';
import {recordingUuid} from './recording-api';
export type RecordingTranscriptSource=components['schemas']['RecordingTranscriptSource'];
function path(meeting:string,session:string|null){if(!recordingUuid(meeting)||session!==null&&!recordingUuid(session))throw new TypeError('Invalid recording transcription scope');return session===null?`/api/v1/meetings/${meeting}/recording-transcription-source`:`/api/v1/recordings/${session}/transcription-source`;}
function verify(value:unknown,meeting:string,session:string|null,status:number):RecordingTranscriptSource{
 const invalid=():never=>{throw new ApiError('response',status,'录音转写来源暂无法核实，请保留当前记录并重试。');};
 if(!responseMatches('recording.yaml#/RecordingTranscriptSource',value))return invalid();const result=value as RecordingTranscriptSource;
 if(result.meetingId!==meeting||session!==null&&result.sessionId!==session)return invalid();
 if((result.transcriptionTaskId===undefined)!==(result.transcriptionState===undefined)||result.transcriptionTaskId!==undefined&&result.state!=='READY')return invalid();
 const origin=[result.sourceId,result.sourceSha256,result.sourceBytes,result.sourceSamples];
 if(result.state==='NOT_RECORDED'){if(result.sessionId!==undefined||result.archiveAvailable||origin.some(x=>x!==undefined)||result.fileId!==undefined)return invalid();}
 else if(result.state==='NOT_REQUESTED'){if(!result.sessionId||origin.some(x=>x!==undefined)||result.fileId!==undefined)return invalid();}
 else {
  if(!result.sessionId||origin.some(x=>x===undefined)||result.sourceBytes!==44+2*result.sourceSamples!)return invalid();
  if(result.state==='READY'){if(!result.fileId||!result.archiveAvailable)return invalid();}else if(result.fileId!==undefined)return invalid();
 }
 return Object.freeze(result);
}
export function createRecordingTranscriptionApi(client:ReturnType<typeof createBusinessClient>){return{
 async read(meeting:string,session:string|null,signal:AbortSignal):Promise<RecordingTranscriptSource>{const value=await client.json<unknown>(path(meeting,session),{signal,expectedStatus:200,maxResponseBytes:16384});return verify(value,meeting,session,200);},
 async prepare(meeting:string,session:string|null,key:string,signal:AbortSignal):Promise<RecordingTranscriptSource>{if(!recordingUuid(key))throw new TypeError('Stable source preparation key required');const value=await client.json<unknown>(path(meeting,session),{method:'POST',body:'{}',headers:{'Idempotency-Key':key},signal,expectedStatus:202,maxResponseBytes:16384});return verify(value,meeting,session,202);}
};}
