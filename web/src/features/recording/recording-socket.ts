import type {RecordingSession} from './recording-api';
import type {Chunk} from './chunk-store';
import {ApiError} from '../../shared/api/client';
const uuid=(value:string)=>/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value);
const integer=(value:number,min:number,max:number)=>Number.isSafeInteger(value)&&value>=min&&value<=max;
/** Immutable AMA1 wire snapshot; connection authentication and the database writer fence remain server-owned. */
export async function encodeRecordingFrame(session:RecordingSession,chunk:Chunk):Promise<ArrayBuffer>{
 session={sessionId:session.sessionId,partId:session.partId,writerEpoch:session.writerEpoch,backendId:session.backendId};chunk={sessionId:chunk.sessionId,partId:chunk.partId,seq:chunk.seq,sampleStart:chunk.sampleStart,sampleCount:chunk.sampleCount,sha256:chunk.sha256,pcm:chunk.pcm};
 if(!uuid(session.sessionId)||!uuid(session.partId)||chunk.sessionId!==session.sessionId||chunk.partId!==session.partId||!integer(session.writerEpoch,1,Number.MAX_SAFE_INTEGER))throw new TypeError('录音分片不属于当前采集会话');
 if(!integer(chunk.seq,1,4096)||!integer(chunk.sampleStart,0,115200000)||!integer(chunk.sampleCount,1,32000)||chunk.sampleStart+chunk.sampleCount>115200000||!(chunk.pcm instanceof ArrayBuffer)||chunk.pcm.byteLength!==chunk.sampleCount*2||!(/^[0-9a-f]{64}$/).test(chunk.sha256))throw new TypeError('录音分片范围或长度无效');
 const pcm=chunk.pcm.slice(0);const digest=new Uint8Array(await crypto.subtle.digest('SHA-256',pcm));const hash=Array.from(digest,b=>b.toString(16).padStart(2,'0')).join('');if(hash!==chunk.sha256)throw new TypeError('录音音频校验失败，已保留本地数据');
 const frame=new ArrayBuffer(80+pcm.byteLength),bytes=new Uint8Array(frame),view=new DataView(frame);bytes.set([0x41,0x4d,0x41,0x31]);const part=session.partId.replaceAll('-','');for(let i=0;i<16;i++)bytes[4+i]=Number.parseInt(part.slice(i*2,i*2+2),16);
 view.setBigInt64(20,BigInt(session.writerEpoch),false);view.setBigInt64(28,BigInt(chunk.seq),false);view.setBigInt64(36,BigInt(chunk.sampleStart),false);view.setUint32(44,chunk.sampleCount,false);bytes.set(digest,48);bytes.set(new Uint8Array(pcm),80);return frame;
}

import {recordingUuid,validateRecordingAck,type RecordingAck} from './recording-api';
type SocketPort=Pick<WebSocket,'readyState'|'protocol'|'send'|'close'|'addEventListener'|'removeEventListener'>;
export type RecordingSocket={put(chunk:Chunk):Promise<RecordingAck>;close():void};
/** One ordered in-flight frame. Errors and timeouts never manufacture a receipt or clear a cache. */
export async function connectRecordingSocket(origin:string,current:RecordingSession,csrfToken:string,active:()=>boolean,options:{create?:(url:string,protocols:string[])=>SocketPort;timeoutMs?:number}={}):Promise<RecordingSocket>{
 const scope={...current},base=new URL(origin),timeout=options.timeoutMs??10000;
 if(!['http:','https:'].includes(base.protocol)||base.pathname!=='/'||base.username||base.password||base.search||base.hash||!recordingUuid(scope.sessionId)||!recordingUuid(scope.partId)||!recordingUuid(scope.collectorId)||!Number.isSafeInteger(scope.writerEpoch)||scope.writerEpoch<1||!(/^[A-Za-z0-9_-]{43}$/).test(csrfToken)||!Number.isSafeInteger(timeout)||timeout<10||timeout>30000||!active())throw new TypeError('录音连接凭据或范围无效');
 base.protocol=base.protocol==='https:'?'wss:':'ws:';base.pathname=`/api/v1/recordings/${scope.sessionId}/stream`;
 const socket=(options.create??((url,protocols)=>new WebSocket(url,protocols)))(base.href,['ama.recording.v1',`ama.csrf.${csrfToken}`]);
 let closed=false,ready=false,busy=false,readyResolve:(client:RecordingSocket)=>void=()=>{},readyReject:(cause:Error)=>void=()=>{},waiting:{resolve:(ack:RecordingAck)=>void;reject:(cause:Error)=>void;timer:ReturnType<typeof setTimeout>}|undefined;
 const handshake=new Promise<RecordingSocket>((resolve,reject)=>{readyResolve=resolve;readyReject=reject;});
 const handshakeTimer=setTimeout(()=>fail(new Error('录音连接未确认，请恢复上传')),timeout);
 function fail(cause:Error){if(closed)return;closed=true;clearTimeout(handshakeTimer);if(waiting){clearTimeout(waiting.timer);waiting.reject(cause);waiting=undefined;}readyReject(cause);socket.removeEventListener('open',open);socket.removeEventListener('message',message);socket.removeEventListener('close',end);socket.removeEventListener('error',end);try{socket.close();}catch{/* The pending audio remains in its store. */}}
 function check(){if(!active()){fail(new Error('登录或录音范围已变化，已保留本地音频'));return false;}return !closed;}
 function open(){if(!check())return;if(socket.protocol!=='ama.recording.v1'){fail(new Error('录音连接协议未确认'));return;}try{socket.send(JSON.stringify({type:'HELLO',collectorId:scope.collectorId,writerEpoch:scope.writerEpoch}));}catch{fail(new Error('录音连接不可用，已保留本地音频'));}}
 function end(){fail(new Error('录音连接已断开，已保留本地音频'));}
 function message(event:Event){if(!check())return;try{
  const data=(event as MessageEvent<unknown>).data;if(typeof data!=='string'||new TextEncoder().encode(data).byteLength>8192)throw new Error('录音连接响应超限');
  const body=JSON.parse(data) as Record<string,unknown>;if(!body||typeof body!=='object'||Array.isArray(body))throw new Error('录音连接响应无效');
  if(body.type==='ERROR'){
   if(Object.keys(body).sort().join(',')==='code,type'&&body.code==='RATE_LIMITED')throw new ApiError('http',429,'录音上传暂时受限，音频已保留，正在等待重试。','RECORDING_RATE_LIMITED');
   throw new Error('服务端未确认录音保存，已保留本地音频');
  }
  if(body.sessionId!==scope.sessionId||body.partId!==scope.partId||body.writerEpoch!==scope.writerEpoch)throw new Error('录音连接响应范围已变化');
  if(body.type==='READY'&&!ready&&Object.keys(body).sort().join(',')==='partId,sessionId,type,writerEpoch'){ready=true;clearTimeout(handshakeTimer);readyResolve(client);return;}
  if(body.type!=='ACK'||!ready||!waiting||Object.keys(body).sort().join(',')!=='highestContiguousSeq,missingSeqs,partId,sessionId,type,writerEpoch')throw new Error('录音连接返回非预期确认');
  const ack=validateRecordingAck({highestContiguousSeq:body.highestContiguousSeq,missingSeqs:body.missingSeqs}),pending=waiting;waiting=undefined;clearTimeout(pending.timer);pending.resolve(ack);
 }catch(cause){fail(cause instanceof Error?cause:new Error('录音连接响应无效'));}}
 const client:RecordingSocket={
  async put(chunk){if(!check()||!ready)throw new Error('录音连接尚未就绪，已保留本地音频');if(busy)throw new Error('上一录音分片尚未确认');busy=true;try{const frame=await encodeRecordingFrame(scope,chunk);if(!check()||socket.readyState!==1)throw new Error('录音连接已断开，已保留本地音频');return await new Promise<RecordingAck>((resolve,reject)=>{waiting={resolve,reject,timer:setTimeout(()=>fail(new Error('录音保存确认超时，已保留本地音频')),timeout)};try{socket.send(frame);}catch{fail(new Error('录音发送失败，已保留本地音频'));}});}finally{busy=false;}},
  close(){fail(new Error('录音连接已关闭，已保留未确认音频'));},
 };
 socket.addEventListener('open',open);socket.addEventListener('message',message);socket.addEventListener('close',end);socket.addEventListener('error',end);return handshake;
}
