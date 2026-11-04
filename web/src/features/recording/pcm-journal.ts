import {copyChunk,sameChunk,type Chunk,type ChunkStore} from './chunk-store';
import {recordingUuid,type RecordingSession,type RecordingManifest} from './recording-api';
export type PcmPart={partId:string;finalSequence:number;sampleCount:number;confirmedSequence?:number;confirmedSamples?:number;endReason?:'ACTIVE'|'MANUAL'|'SYSTEM'|'FINISHED';tailIntegrity?:'COMPLETE'|'UNKNOWN'};
export type PcmRecord={schema:1;createdAt?:number;updatedAt?:number;actor:string;meetingId:string;session:RecordingSession;parts:PcmPart[];finished:boolean;archiveTaskId?:string;skipAutomaticRestore?:true};
export class PcmRecoverySelectionRequired extends Error {readonly candidates:ReadonlyArray<{meetingId:string;sessionId:string;samples:number;parts:number}>;constructor(records:PcmRecord[]){super('存在多份缺少时间信息的旧录音，请选择要恢复的会话；原音频已保留');this.candidates=records.map(r=>({meetingId:r.meetingId,sessionId:r.session.sessionId,samples:r.parts.reduce((n,p)=>n+p.sampleCount,0),parts:r.parts.length}));}}
/** An actor-wide ten-minute pending budget. Cursor and chunk commit together; receipts remove exact bytes only. */
export function createPcmJournal(actor:string,options:{limitBytes?:number}={}):ChunkStore&{begin(meetingId:string,session:RecordingSession,parts?:PcmPart[]):Promise<void>;load(sid:string):Promise<PcmRecord|null>;latest(meetingId?:string):Promise<PcmRecord|null>;skipAutomaticRestore(sid:string):Promise<void>;updateSession(session:RecordingSession):Promise<void>;finish(sid:string,taskId:string):Promise<void>;reconcile(manifest:RecordingManifest):Promise<void>;markPart(sid:string,reason:NonNullable<PcmPart['endReason']>,integrity:NonNullable<PcmPart['tailIntegrity']>):Promise<void>;close():void}{
 const limit=options.limitBytes??19200000;if(!actor.trim()||actor.length>255||!Number.isSafeInteger(limit)||limit<1||limit>19200000)throw new TypeError('录音缓存范围或限额无效');
 let connection:Promise<IDBDatabase>|null=null;
 function database(){if(connection)return connection;connection=new Promise<IDBDatabase>((resolve,reject)=>{if(typeof indexedDB==='undefined'){reject(Error('浏览器无法使用持久录音缓存'));return;}const r=indexedDB.open('ama.pcm.recording.v1',1);r.onupgradeneeded=()=>{r.result.createObjectStore('chunks');const records=r.result.createObjectStore('records');records.createIndex('actor','actor');records.createIndex('meeting',['actor','meetingId']);r.result.createObjectStore('budgets');};r.onerror=()=>reject(Error('无法打开录音缓存，请保留当前页面'));r.onblocked=()=>reject(Error('录音缓存升级受阻，请关闭旧页面后重试'));r.onsuccess=()=>{r.result.onversionchange=()=>r.result.close();resolve(r.result);};});void connection.catch(()=>{connection=null;});return connection;}
 const request=<T>(r:IDBRequest<T>)=>new Promise<T>((resolve,reject)=>{r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error??Error('录音缓存操作失败'));});
 async function transaction<T>(mode:IDBTransactionMode,names:string[],work:(tx:IDBTransaction)=>Promise<T>){const db=await database(),tx=db.transaction(names,mode),done=new Promise<void>((resolve,reject)=>{tx.oncomplete=()=>resolve();tx.onabort=tx.onerror=()=>reject(Error('录音缓存事务未完成，音频仍需保存'));});try{const result=await work(tx);await done;return result;}catch(cause){try{tx.abort();}catch{/* May already have aborted. */}await done.catch(()=>{});throw cause;}}
 const recordKey=(sid:string)=>[actor,sid],chunkKey=(c:Chunk)=>[actor,c.sessionId,c.partId,c.seq];
 const range=(sid:string)=>IDBKeyRange.bound([actor,sid],[actor,sid,[]]);
 function validSession(s:RecordingSession){if(!recordingUuid(s.sessionId)||!recordingUuid(s.partId)||!Number.isSafeInteger(s.writerEpoch)||s.writerEpoch<1||!(/^[0-9a-f]{64}$/).test(s.backendId)||!recordingUuid(s.collectorId))throw new TypeError('录音采集会话无效');}
 function checked(raw:unknown,sid?:string):PcmRecord{const r=raw as PcmRecord;if(!r||r.schema!==1||r.actor!==actor||!recordingUuid(r.meetingId)||sid&&r.session?.sessionId!==sid||!Array.isArray(r.parts)||r.parts.length<1||r.parts.length>16||typeof r.finished!=='boolean')throw Error('录音缓存记录无效，请保留本地数据');validSession(r.session);for(const time of [r.createdAt,r.updatedAt])if(time!==undefined&&(!Number.isSafeInteger(time)||time<0))throw Error('录音记录时间无效');const ids=new Set();let count=0,samples=0;for(const p of r.parts){if(!recordingUuid(p.partId)||ids.has(p.partId)||!Number.isSafeInteger(p.finalSequence)||p.finalSequence<0||!Number.isSafeInteger(p.sampleCount)||p.sampleCount<0||(p.finalSequence===0)!==(p.sampleCount===0))throw Error('录音缓存清单无效');if((p.confirmedSequence===undefined)!==(p.confirmedSamples===undefined)||p.confirmedSequence!==undefined&&(!Number.isSafeInteger(p.confirmedSequence)||p.confirmedSequence<0||p.confirmedSequence>p.finalSequence||!Number.isSafeInteger(p.confirmedSamples)||p.confirmedSamples!<0||p.confirmedSamples!>p.sampleCount))throw Error('录音确认范围无效');if(p.endReason!==undefined&&!['ACTIVE','MANUAL','SYSTEM','FINISHED'].includes(p.endReason)||p.tailIntegrity!==undefined&&!['COMPLETE','UNKNOWN'].includes(p.tailIntegrity)||p.confirmedSequence!==undefined&&(p.confirmedSequence===0)!==(p.confirmedSamples===0))throw Error('录音状态记录无效');ids.add(p.partId);count+=p.finalSequence;samples+=p.sampleCount;}if(count>4096||samples>115200000||r.parts.at(-1)?.partId!==r.session.partId||r.finished&&!recordingUuid(r.archiveTaskId))throw Error('录音缓存范围无效');return r;}
 async function record(tx:IDBTransaction,sid:string){const raw:unknown=await request(tx.objectStore('records').get(recordKey(sid)));return checked(raw,sid);}
 async function budget(tx:IDBTransaction){const raw:unknown=await request(tx.objectStore('budgets').get(actor));if(raw===undefined)return 0;if(typeof raw!=='number'||!Number.isSafeInteger(raw)||raw<0||raw>19200000)throw Error('录音缓存用量无法核对');return raw;}
 return{
  async begin(meetingId,session,parts){session={...session};validSession(session);if(!recordingUuid(meetingId))throw new TypeError('录音会议无效');const value=checked({schema:1,createdAt:Date.now(),updatedAt:Date.now(),actor,meetingId,session,parts:parts?.map(p=>({...p}))??[{partId:session.partId,finalSequence:0,sampleCount:0,confirmedSequence:0,confirmedSamples:0}],finished:false});await transaction('readwrite',['records'],async tx=>{const store=tx.objectStore('records');if(await request(store.get(recordKey(session.sessionId)))!==undefined)throw Error('原录音缓存已存在，请恢复原会话');const previous=await request(store.index('actor').getAll(actor)) as PcmRecord[];const latest=previous.reduce((n,p)=>Math.max(n,p.updatedAt??0),0);value.createdAt=value.updatedAt=Math.max(Date.now(),latest+1);await request(store.add(value,recordKey(session.sessionId)));});},
  async load(sid){if(!recordingUuid(sid))throw new TypeError('录音编号无效');return transaction('readonly',['records'],async tx=>{const raw:unknown=await request(tx.objectStore('records').get(recordKey(sid)));return raw===undefined?null:checked(raw,sid);});},
  async latest(meetingId){
   return transaction('readonly',['records'],async tx=>{
    const index=tx.objectStore('records').index(meetingId?'meeting':'actor');
    const values=(await request(index.getAll(meetingId?[actor,meetingId]:actor))).map(v=>checked(v));
    const eligible=values.filter(v=>!v.finished&&(meetingId||!v.skipAutomaticRestore));
    const candidates=eligible.length?eligible:meetingId?values:[];
    if(candidates.length>1&&candidates.some(v=>v.updatedAt===undefined))throw new PcmRecoverySelectionRequired(candidates);
    const dated=candidates.filter(v=>v.updatedAt!==undefined).sort((a,b)=>b.updatedAt!-a.updatedAt!);
    if(dated.length)return dated[0]!;

    return candidates[0]??null;
   });
  },
  async skipAutomaticRestore(sid){
   return transaction('readwrite',['records'],async tx=>{
    const saved=await record(tx,sid);
    await request(tx.objectStore('records').put({...saved,skipAutomaticRestore:true},recordKey(sid)));
   });
  },
  async updateSession(session){session={...session};validSession(session);await transaction('readwrite',['records','chunks'],async tx=>{const r=await record(tx,session.sessionId);if(r.finished||r.session.backendId!==session.backendId||session.writerEpoch<r.session.writerEpoch||session.writerEpoch===r.session.writerEpoch&&(session.partId!==r.session.partId||session.collectorId!==r.session.collectorId))throw Error('录音缓存版本已变化');if(session.partId!==r.session.partId){if(r.parts.length===16||r.parts.some(p=>p.partId===session.partId)||await request(tx.objectStore('chunks').count(range(session.sessionId)))>0)throw Error('请先恢复并确认原录音分片');r.parts.push({partId:session.partId,finalSequence:0,sampleCount:0,confirmedSequence:0,confirmedSamples:0});}r.session=session;r.updatedAt=Math.max(Date.now(),(r.updatedAt??0)+1);await request(tx.objectStore('records').put(checked(r),recordKey(session.sessionId)));});},
  async put(value){const c=copyChunk(value);if(!recordingUuid(c.sessionId)||!recordingUuid(c.partId)||!Number.isSafeInteger(c.seq)||c.seq<1||c.seq>4096||!Number.isSafeInteger(c.sampleStart)||c.sampleStart<0||!Number.isSafeInteger(c.sampleCount)||c.sampleCount<1||c.sampleCount>32000||c.pcm.byteLength!==c.sampleCount*2||!(/^[0-9a-f]{64}$/).test(c.sha256))throw new TypeError('录音缓存分片无效');const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',c.pcm)),b=>b.toString(16).padStart(2,'0')).join('');if(hash!==c.sha256)throw Error('录音缓存校验不匹配');await transaction('readwrite',['records','chunks','budgets'],async tx=>{const r=await record(tx,c.sessionId),chunks=tx.objectStore('chunks'),old=await request(chunks.get(chunkKey(c))) as Chunk|undefined;if(old){if(!sameChunk(old,c))throw Error('本地录音分片冲突，原数据已保留');return;}if(r.finished||r.session.partId!==c.partId)throw Error('录音分段已封存');const part=r.parts.at(-1)!;if(c.seq!==part.finalSequence+1||c.sampleStart!==part.sampleCount)throw Error('录音采集范围不连续');const used=await budget(tx);if(c.pcm.byteLength>limit-used)throw Error('未确认录音已达到十分钟缓存限额，请恢复上传');part.finalSequence=c.seq;part.sampleCount+=c.sampleCount;r.updatedAt=Math.max(Date.now(),(r.updatedAt??0)+1);checked(r);await request(chunks.add(c,chunkKey(c)));await request(tx.objectStore('budgets').put(used+c.pcm.byteLength,actor));await request(tx.objectStore('records').put(r,recordKey(c.sessionId)));});},
  async removeConfirmed(value){
   const c=copyChunk(value);
   await transaction('readwrite',['records','chunks','budgets'],async tx=>{
    const chunks=tx.objectStore('chunks'),old=await request(chunks.get(chunkKey(c))) as Chunk|undefined;
    if(!old||!sameChunk(old,c))return;
    const r=await record(tx,c.sessionId),part=r.parts.find(p=>p.partId===c.partId)!;
    if(part.confirmedSequence===undefined)throw Error('旧录音确认范围尚未核对，请恢复连接');
    if(c.seq>part.confirmedSequence){
     if(c.seq!==part.confirmedSequence+1||c.sampleStart!==part.confirmedSamples)throw Error('录音确认范围不连续，原音频已保留');
     part.confirmedSequence=c.seq;part.confirmedSamples=c.sampleStart+c.sampleCount;
    }
    const used=await budget(tx);if(used<old.pcm.byteLength)throw Error('录音缓存用量冲突');
    await request(chunks.delete(chunkKey(c)));await request(tx.objectStore('budgets').put(used-old.pcm.byteLength,actor));
    await request(tx.objectStore('records').put(checked(r),recordKey(c.sessionId)));
   });
  },
  async reconcile(manifest){
   await transaction('readwrite',['records'],async tx=>{
    const r=await record(tx,manifest.sessionId);
    if(r.parts.length!==manifest.parts.length||r.parts.some((p,i)=>p.partId!==manifest.parts[i]!.partId))throw Error('录音恢复范围已变化，请保留原音频');
    for(let i=0;i<r.parts.length;i++){
     const local=r.parts[i]!,server=manifest.parts[i]!;
     // A total received count is a continuous frontier only when no gap exists.
     if(server.highestContiguousSeq!==server.highestReceivedSeq)continue;
     if(server.highestReceivedSeq>local.finalSequence||server.sampleCount>local.sampleCount||server.highestContiguousSeq<(local.confirmedSequence??0)||server.sampleCount<(local.confirmedSamples??0))throw Error('录音保存范围无法核对，请保留原音频');
     local.confirmedSequence=server.highestContiguousSeq;local.confirmedSamples=server.sampleCount;
    }
    await request(tx.objectStore('records').put(checked(r),recordKey(manifest.sessionId)));
   });
  },
  async markPart(sid,reason,integrity){await transaction('readwrite',['records'],async tx=>{const r=await record(tx,sid),part=r.parts.at(-1)!;part.endReason=reason;part.tailIntegrity=integrity;r.updatedAt=Math.max(Date.now(),(r.updatedAt??0)+1);await request(tx.objectStore('records').put(r,recordKey(sid)));});},
  async *pending(sid){if(!recordingUuid(sid))throw new TypeError('录音编号无效');const values=await transaction('readonly',['records','chunks'],async tx=>{const raw:unknown=await request(tx.objectStore('records').get(recordKey(sid)));if(raw===undefined)return[];const r=checked(raw,sid),values=await request(tx.objectStore('chunks').getAll(range(sid),4097)) as Chunk[];if(values.length>4096)throw Error('录音缓存分片超限');return values.sort((a,b)=>r.parts.findIndex(p=>p.partId===a.partId)-r.parts.findIndex(p=>p.partId===b.partId)||a.seq-b.seq);});for(const c of values)yield copyChunk(c);},
  async finish(sid,taskId){if(!recordingUuid(taskId))throw new TypeError('录音归档任务无效');await transaction('readwrite',['records','chunks'],async tx=>{const r=await record(tx,sid);if(await request(tx.objectStore('chunks').count(range(sid)))>0)throw Error('仍有未确认音频，请恢复上传');if(r.finished&&r.archiveTaskId!==taskId)throw Error('录音归档任务已变化');r.finished=true;r.archiveTaskId=taskId;r.updatedAt=Math.max(Date.now(),(r.updatedAt??0)+1);r.parts.at(-1)!.tailIntegrity??='UNKNOWN';await request(tx.objectStore('records').put(r,recordKey(sid)));});},
  close(){if(connection)void connection.then(db=>db.close()).catch(()=>{});connection=null;},
 };
}
