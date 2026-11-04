import {test,expect} from '@playwright/test';
test('sealing confirmed PCM cannot turn an unknown interrupted tail into a complete capture',async({page})=>{
 await page.goto('/login');const result=await page.evaluate(async()=>{
  const path='/src/features/recording/pcm-journal.ts';const {createPcmJournal}=await import(path),j=createPcmJournal(crypto.randomUUID()),sid=crypto.randomUUID(),partId=crypto.randomUUID();
  try{await j.begin(crypto.randomUUID(),{sessionId:sid,partId,writerEpoch:2,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()},[{partId,finalSequence:1,sampleCount:16000,confirmedSequence:1,confirmedSamples:16000,endReason:'SYSTEM',tailIntegrity:'UNKNOWN'}]);await j.finish(sid,crypto.randomUUID());const record=await j.load(sid);return{finished:record.finished,integrity:record.parts[0].tailIntegrity};}finally{j.close();}
 });expect(result).toEqual({finished:true,integrity:'UNKNOWN'});
});
test('legacy confirmation is unknown until manifest reconciliation and ambiguous old sessions require selection',async({page})=>{
 await page.goto('/login');const result=await page.evaluate(async()=>{
  const path='/src/features/recording/pcm-journal.ts';const {createPcmJournal}=await import(path);const actor=crypto.randomUUID(),meeting=crypto.randomUUID(),j=createPcmJournal(actor),ids=[crypto.randomUUID(),crypto.randomUUID()];
  for(const sessionId of ids)await j.begin(meeting,{sessionId,partId:crypto.randomUUID(),writerEpoch:2,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()});
  const db=await new Promise<IDBDatabase>((resolve,reject)=>{const r=indexedDB.open('ama.pcm.recording.v1',1);r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
  await new Promise<void>((resolve,reject)=>{const tx=db.transaction('records','readwrite'),store=tx.objectStore('records');for(const id of ids){const r=store.get([actor,id]);r.onsuccess=()=>{const old=r.result;delete old.createdAt;delete old.updatedAt;delete old.parts[0].confirmedSequence;delete old.parts[0].confirmedSamples;store.put(old,[actor,id]);};}tx.oncomplete=()=>resolve();tx.onabort=()=>reject(tx.error);});db.close();
  const old=await j.load(ids[0]);let choices:string[]=[];try{await j.latest(meeting);}catch(error){choices=(error as any).candidates.map((x:any)=>x.sessionId);}
  await j.reconcile({sessionId:ids[0],parts:[{partId:old.session.partId,highestContiguousSeq:0,highestReceivedSeq:0,sampleCount:0}]});const fresh=await j.load(ids[0]);j.close();return{unknown:old.parts[0].confirmedSamples===undefined,choices:choices.sort(),ids:ids.sort(),confirmed:fresh.parts[0].confirmedSamples};
 });expect(result.unknown).toBe(true);expect(result.choices).toEqual(result.ids);expect(result.confirmed).toBe(0);
});
test('an aborted confirmation transaction retains bytes, budget and original frontier together',async({page})=>{
 await page.goto('/login');const result=await page.evaluate(async()=>{
  const path='/src/features/recording/pcm-journal.ts';const {createPcmJournal}=await import(path);const actor=crypto.randomUUID(),j=createPcmJournal(actor),sessionId=crypto.randomUUID(),partId=crypto.randomUUID();await j.begin(crypto.randomUUID(),{sessionId,partId,writerEpoch:2,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()});const pcm=new Uint8Array([5,9]).buffer,sha256=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',pcm)),b=>b.toString(16).padStart(2,'0')).join(''),c={sessionId,partId,seq:1,sampleStart:0,sampleCount:1,sha256,pcm};await j.put(c);
  const original=IDBObjectStore.prototype.put;let aborted=false;IDBObjectStore.prototype.put=function(...args:any[]){if(this.name==='records'){this.transaction.abort();aborted=true;throw Error('controlled transaction abort');}return original.apply(this,args as any);};try{await j.removeConfirmed(c);}catch{}finally{IDBObjectStore.prototype.put=original;}const before=await j.load(sessionId),pending=[];for await(const chunk of j.pending(sessionId))pending.push(chunk.seq);await j.removeConfirmed(c);const after=await j.load(sessionId);j.close();return{aborted,pending,before:before.parts[0].confirmedSamples,after:after.parts[0].confirmedSamples};
 });expect(result).toEqual({aborted:true,pending:[1],before:0,after:1});
});
test('confirmed audio remains distinct from local audio across reload and duplicate receipts',async({page})=>{
 await page.goto('/login');
 const result=await page.evaluate(async()=>{
  const path='/src/features/recording/pcm-journal.ts';const {createPcmJournal}=await import(path);
  const sid=crypto.randomUUID(),partId=crypto.randomUUID(),actor=crypto.randomUUID(),j=createPcmJournal(actor);
  await j.begin(crypto.randomUUID(),{sessionId:sid,partId,writerEpoch:2,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()});
  const pcm=new Uint8Array(64000).buffer,sha256=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',pcm)),b=>b.toString(16).padStart(2,'0')).join('');
  const chunks=[1,2,3].map(seq=>({sessionId:sid,partId,seq,sampleStart:(seq-1)*32000,sampleCount:32000,sha256,pcm}));
  for(const c of chunks)await j.put(c);
  await j.removeConfirmed(chunks[0]);await j.removeConfirmed(chunks[0]);j.close();
  const restored=createPcmJournal(actor);try{const p=(await restored.load(sid)).parts[0],pending=[];for await(const c of restored.pending(sid))pending.push(c.seq);return{local:p.sampleCount,confirmed:p.confirmedSamples,sequence:p.confirmedSequence,pending};}finally{restored.close();}
 });expect(result).toEqual({local:96000,confirmed:32000,sequence:1,pending:[2,3]});
});
test('latest recording follows creation order instead of UUID lexical order',async({page})=>{
 await page.goto('/login');const result=await page.evaluate(async()=>{
  const path='/src/features/recording/pcm-journal.ts';const {createPcmJournal}=await import(path);const j=createPcmJournal(crypto.randomUUID()),meeting=crypto.randomUUID();
  try{for(const id of ['ffffffff-ffff-4fff-8fff-ffffffffffff','00000000-0000-4000-8000-000000000001'])await j.begin(meeting,{sessionId:id,partId:crypto.randomUUID(),writerEpoch:2,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()});return (await j.latest(meeting)).session.sessionId;}finally{j.close();}
 });expect(result).toBe('00000000-0000-4000-8000-000000000001');
});
test('a receipt outside the confirmed prefix cannot delete audio or overstate the saved range',async({page})=>{
 await page.goto('/login');const result=await page.evaluate(async()=>{
  const path='/src/features/recording/pcm-journal.ts';const {createPcmJournal}=await import(path);const j=createPcmJournal(crypto.randomUUID()),sid=crypto.randomUUID(),partId=crypto.randomUUID();
  try{await j.begin(crypto.randomUUID(),{sessionId:sid,partId,writerEpoch:2,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()});const pcm=new Uint8Array([1,2]).buffer,sha256=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',pcm)),b=>b.toString(16).padStart(2,'0')).join('');const chunk=(seq:number)=>({sessionId:sid,partId,seq,sampleStart:seq-1,sampleCount:1,sha256,pcm});await j.put(chunk(1));await j.put(chunk(2));let rejected=false;try{await j.removeConfirmed(chunk(2));}catch{rejected=true;}const pending=[];for await(const c of j.pending(sid))pending.push(c.seq);return{rejected,pending,confirmed:(await j.load(sid)).parts[0].confirmedSamples};}finally{j.close();}
 });expect(result).toEqual({rejected:true,pending:[1,2],confirmed:0});
});
