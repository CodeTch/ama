import type {components} from '../../shared/api/generated';import type {createBusinessClient} from '../../shared/api/business-client';import {responseMatches} from '../../shared/api/response-schema';import {ApiError} from '../../shared/api/client';import {recordingUuid} from './recording-api';
export type SubtitlePage=components['schemas']['RecordingSubtitles'];export type SubtitleEvent=components['schemas']['RecordingSubtitleEvent'];
const token=(v:string)=>/^(0|[1-9][0-9]{0,18})$/.test(v)&&BigInt(v)<=9223372036854775807n;
const invalid=():never=>{throw new ApiError('response',200,'实时字幕结果暂无法核实，音频保存独立进行。');};
function freeze<T>(v:T):T{if(v!==null&&typeof v==='object'){Object.values(v).forEach(freeze);Object.freeze(v);}return v;}
export function createSubtitleApi(client:ReturnType<typeof createBusinessClient>){return{
 async retry(session:string){if(!recordingUuid(session))throw Error('录音标识无效');await client.json(`/api/v1/recordings/${session}/subtitles/retry`,{method:'POST',body:'{}',expectedStatus:204});},
 async read(meeting:string,session:string,after:string,signal:AbortSignal):Promise<SubtitlePage>{
  if(!recordingUuid(meeting)||!recordingUuid(session)||!token(after))throw new TypeError('Invalid recording subtitle scope');
  const value=await client.json<unknown>(`/api/v1/recordings/${session}/subtitles?${new URLSearchParams({afterEventId:after,limit:'64'})}`,{signal,expectedStatus:200,maxResponseBytes:8*1024*1024});
  if(!responseMatches('recording.yaml#/RecordingSubtitles',value))return invalid();const result=value as SubtitlePage;if(result.meetingId!==meeting||result.sessionId!==session||!token(result.nextCursor)||result.hasMore&&!result.events.length||['DELAYED','DEFERRED'].includes(result.state)&&!result.delayed)return invalid();let cursor=BigInt(after);
  for(const event of result.events){if(!token(event.eventId)||BigInt(event.eventId)!==++cursor||event.sessionId!==session||event.endMs<=event.startMs||event.status==='stable'&&event.revision<2)return invalid();}
  if(result.nextCursor!==String(cursor)||result.state==='NOT_STARTED'&&(result.events.length>0||cursor!==0n))return invalid();return freeze(result);
 }
};}
