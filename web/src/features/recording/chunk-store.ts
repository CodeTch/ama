export type Chunk={sessionId:string;partId:string;seq:number;sampleStart:number;sampleCount:number;sha256:string;pcm:ArrayBuffer};
export interface ChunkStore{put(chunk:Chunk):Promise<void>;removeConfirmed(chunk:Chunk):Promise<void>;pending(sessionId:string):AsyncIterable<Chunk>;}
export const copyChunk=(chunk:Chunk):Chunk=>({...chunk,pcm:chunk.pcm.slice(0)});
export const chunkKey=(chunk:Chunk)=>`${chunk.sessionId}/${chunk.partId}/${chunk.seq}`;
function equalBytes(a:ArrayBuffer,b:ArrayBuffer){const left=new Uint8Array(a),right=new Uint8Array(b);return left.every((byte,index)=>byte===right[index]);}
export function sameChunk(a:Chunk,b:Chunk):boolean{return a.sessionId===b.sessionId&&a.partId===b.partId&&a.seq===b.seq&&a.sampleStart===b.sampleStart&&a.sampleCount===b.sampleCount&&a.sha256===b.sha256&&a.pcm.byteLength===b.pcm.byteLength&&equalBytes(a.pcm,b.pcm);}

/** Bounded in-memory fallback used by the demo and replaced by IndexedDB in the browser shell. */
export function createChunkStore(limitBytes=20*1024*1024):ChunkStore{
 const values=new Map<string,Chunk>();let bytes=0;
 if(!Number.isSafeInteger(limitBytes)||limitBytes<1)throw new TypeError('录音缓存限额无效');
 return {async put(value){const chunk=copyChunk(value),key=chunkKey(chunk),old=values.get(key);if(old){if(!sameChunk(old,chunk))throw new Error('本地录音分片冲突，原音频已保留');return;}if(chunk.pcm.byteLength>limitBytes||bytes+chunk.pcm.byteLength>limitBytes)throw new Error('录音缓存已满，请先恢复上传');values.set(key,chunk);bytes+=chunk.pcm.byteLength;},async removeConfirmed(chunk){const key=chunkKey(chunk),old=values.get(key);if(old&&sameChunk(old,chunk)){values.delete(key);bytes-=old.pcm.byteLength;}},async *pending(sessionId){for(const chunk of [...values.values()].filter(c=>c.sessionId===sessionId).sort((a,b)=>a.partId.localeCompare(b.partId)||a.seq-b.seq))yield copyChunk(chunk);}};
}
