import {ref,readonly,type Ref} from 'vue';
import type {Chunk,ChunkStore} from './chunk-store';
import {chunkKey,copyChunk} from './chunk-store';

export type RecordingSessionState='IDLE'|'RECORDING'|'PAUSED'|'FINISHED'|'ERROR';
type Uploader={put(chunk:Chunk):Promise<unknown>};
export function createRecordingSession(sessionId:string,store:ChunkStore,uploader?:Uploader){
 if(!sessionId.trim())throw new TypeError('录音会话无效');
 const state=ref<RecordingSessionState>('IDLE'),error=ref<string|null>(null),connected=ref(true);
 const receipts=new Map<string,Chunk>();
 return {
  state:readonly(state) as Readonly<Ref<RecordingSessionState>>,error:readonly(error),connected:readonly(connected),
  async start(){if(state.value!=='IDLE')throw new Error('录音已开始');state.value='RECORDING';},
  pause(){if(state.value!=='RECORDING')throw new Error('当前不可暂停');state.value='PAUSED';},
  async resume(){if(state.value!=='PAUSED')throw new Error('当前不可恢复');state.value='RECORDING';},
  async finish(){if(!['RECORDING','PAUSED'].includes(state.value))throw new Error('当前不可结束');for await(const _ of store.pending(sessionId))throw new Error('仍有未确认保存的录音，请恢复上传');state.value='FINISHED';},
  async reconnect(){connected.value=true;for await(const chunk of store.pending(sessionId))await upload(chunk);},
  disconnect(){connected.value=false;},
  async put(value:Chunk){const chunk=copyChunk(value);if(chunk.sessionId!==sessionId)throw new Error('录音分片不属于当前会话');await store.put(chunk);if(!connected.value)return;await upload(chunk);},
 };
 async function upload(chunk:Chunk){
  if(!uploader)return;
  try{
   const ack=await uploader.put(chunk);
   if(!ack||typeof ack!=='object'||!('highestContiguousSeq' in ack)||!Number.isSafeInteger(ack.highestContiguousSeq)||Number(ack.highestContiguousSeq)<0||Number(ack.highestContiguousSeq)>4096)throw new Error('服务端保存确认无效，已保留本地音频');
   receipts.set(chunkKey(chunk),copyChunk(chunk));
   for(const [key,receipt] of receipts)if(receipt.partId===chunk.partId&&receipt.seq<=Number(ack.highestContiguousSeq)){await store.removeConfirmed(receipt);receipts.delete(key);}
   error.value=null;
  }catch(cause){error.value=cause instanceof Error?cause.message:'录音上传未确认，已保留本地音频';throw cause;}
 }
}
