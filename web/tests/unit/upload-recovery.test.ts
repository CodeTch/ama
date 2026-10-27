import {test} from 'node:test';import assert from 'node:assert/strict';import {shallowRef,watch} from 'vue';
import {useUploadRecovery} from '../../src/features/import/useUploadRecovery.js';
import type {UploadReceipt} from '../../src/shared/api/uploads.js';
import type {components} from '../../src/shared/api/generated.js';
const meeting='00000000-0000-4000-8000-000000000001',upload='00000000-0000-4000-8000-000000000002',fileId='00000000-0000-4000-8000-000000000003';
const hash=async(value:string)=>Buffer.from(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value))).toString('hex');
async function fixture(){
 const sum=await hash('abcdef'),parts:components['schemas']['PartSummary'][]=[];let phase:UploadReceipt['state']='OPEN',lostPart=false,lostComplete=false,partCalls:number[]=[],completeBodies:string[]=[],abortCalls=0;
 const api={
  async receipt():Promise<UploadReceipt>{return {uploadId:upload,purpose:'TRANSCRIPT_INPUT',displayName:'x.txt',state:phase,expectedSize:6,expectedSizeToken:'6',expectedSha256:sum,createdAt:'2026-10-27T00:00:00Z',updatedAt:'2026-10-27T00:00:00Z',completedFileId:phase==='COMPLETED'?fileId:null,accepted:{uploadId:upload,partSize:3,partSizeToken:'3'},allowedActions:phase==='OPEN'?['UPLOAD_PARTS','COMPLETE','ABORT']:phase==='FINALIZING'?['COMPLETE']:[],idempotentRetries:phase==='COMPLETED'?['COMPLETE','ABORT']:[],blockedReason:null,completed:phase==='COMPLETED'?{fileId,uploadId:upload,displayName:'x.txt',sizeBytes:6,sizeBytesToken:'6',sha256:sum,fileState:'AVAILABLE',originalPurpose:'TRANSCRIPT_INPUT',intakeEligibility:{state:'ELIGIBLE',reason:null},businessReadEligibility:'NOT_EVALUATED'}:null};},
  async parts(){return {items:[...parts],nextCursor:null};},
  async part(_actor:string,_id:string,part:number,body:Blob,sha256:string){partCalls.push(part);assert.equal(sha256,await hash(await body.text()));parts.push({part,sizeBytes:body.size,sizeBytesToken:String(body.size),sha256});if(lostPart){lostPart=false;throw new Error('lost part response');}},
  async complete(command:{body:string}){completeBodies.push(command.body);phase='COMPLETED';if(lostComplete){lostComplete=false;throw new Error('lost completion');}return {fileId};},
  async abort(){abortCalls++;phase='ABORTED';}
 };
 return {api,parts,partCalls,completeBodies,get abortCalls(){return abortCalls;},setPhase(value:UploadReceipt['state']){phase=value;},losePart(){lostPart=true;},loseComplete(){lostComplete=true;}};
}
test('lost accepted part response recovers receipts and sends only remaining exact bytes',async()=>{
 const f=await fixture(),state=useUploadRecovery(f.api,()=> 'owner',()=>meeting,()=>upload),file=new File(['abcdef'],'x.txt');f.losePart();await assert.rejects(state.resume(file));assert.deepEqual(f.partCalls,[1]);assert.equal(f.completeBodies.length,0);await state.resume(file);assert.deepEqual(f.partCalls,[1,2]);assert.equal(f.completeBodies.length,1);assert.equal(state.stage.value,'OBSERVED_COMPLETED');assert.equal(state.completion.ack.value?.fileId,fileId);state.dispose();
});
test('lost completion ACK is resolved as a distinct observed result without inventing an ACK',async()=>{
 const f=await fixture(),state=useUploadRecovery(f.api,()=> 'owner',()=>meeting,()=>upload);f.loseComplete();await assert.rejects(state.resume(new File(['abcdef'],'x.txt')));assert.equal(state.completion.state.value,'UNCERTAIN');const original=state.completion.snapshot.value;await state.inspect();assert.equal(state.stage.value,'OBSERVED_COMPLETED');assert.equal(state.completion.ack.value,null);assert.deepEqual(state.completion.snapshot.value,original);assert.equal(f.completeBodies.length,1);state.dispose();
});
test('FINALIZING resumes exact completion with existing parts and never transfers or aborts',async()=>{
 const f=await fixture();f.parts.push({part:1,sizeBytes:3,sizeBytesToken:'3',sha256:await hash('abc')},{part:2,sizeBytes:3,sizeBytesToken:'3',sha256:await hash('def')});f.setPhase('FINALIZING');const state=useUploadRecovery(f.api,()=> 'owner',()=>meeting,()=>upload);await assert.rejects(state.abort());assert.equal(f.abortCalls,0);await state.resume(new File(['abcdef'],'x.txt'));assert.deepEqual(f.partCalls,[]);assert.equal(f.completeBodies.length,1);state.dispose();
});
test('actor loss during a part suppresses all later automatic transfer/completion and old observations',async()=>{
 const f=await fixture(),actor=shallowRef<string|null>('owner');let release!:()=>void,entered!:()=>void;const started=new Promise<void>(r=>entered=r),original=f.api.part;f.api.part=async(...args)=>{entered();await new Promise<void>(r=>release=r);return original(...args);};const state=useUploadRecovery(f.api,()=>actor.value,()=>meeting,()=>upload),pending=state.resume(new File(['abcdef'],'x.txt'));await started;actor.value=null;release();await assert.rejects(pending,{name:'AbortError'});assert.equal(f.partCalls.length,1);assert.equal(f.completeBodies.length,0);assert.equal(state.receipt.value,null);assert.equal(state.completion.ack.value,null);state.dispose();
});
test('wrong reselected bytes and FINALIZING missing receipts never cause mutation',async()=>{
 const f=await fixture(),state=useUploadRecovery(f.api,()=> 'owner',()=>meeting,()=>upload);await assert.rejects(state.resume(new File(['abcdeg'],'x.txt')));f.setPhase('FINALIZING');await assert.rejects(state.resume(new File(['abcdef'],'x.txt')));assert.equal(f.partCalls.length,0);assert.equal(f.completeBodies.length,0);state.dispose();
});

test('uncertain completion retries the exact original manifest/key and accepted parts are not sent again',async()=>{
 const f=await fixture(),complete=f.api.complete;let first=true;const attempts:Array<{key?:string;body:string}>=[];f.api.complete=async command=>{attempts.push({...command});if(first){first=false;f.setPhase('FINALIZING');throw new Error('before completion commit');}return complete(command);};
 const state=useUploadRecovery(f.api,()=> 'owner',()=>meeting,()=>upload),file=new File(['abcdef'],'x.txt');await assert.rejects(state.resume(file));const original=state.completion.snapshot.value;assert.equal(state.completion.state.value,'UNCERTAIN');await state.resume(file);assert.equal(attempts.length,2);assert.equal(attempts[0]?.body,attempts[1]?.body);assert.equal(state.completion.snapshot.value?.key,original?.key);assert.deepEqual(f.partCalls,[1,2]);state.dispose();
});
test('completion ACK survives a failed fresh observation and inspect never resends accepted work',async()=>{
 const f=await fixture(),read=f.api.receipt;let reads=0;f.api.receipt=async()=>{if(++reads===3)throw new Error('read unavailable after ACK');return read();};const state=useUploadRecovery(f.api,()=> 'owner',()=>meeting,()=>upload);await assert.rejects(state.resume(new File(['abcdef'],'x.txt')));assert.equal(state.completion.ack.value?.fileId,fileId);assert.equal(state.stage.value,'COMPLETION_ACKNOWLEDGED');await state.inspect();assert.equal(state.stage.value,'OBSERVED_COMPLETED');assert.equal(f.completeBodies.length,1);state.dispose();
});
test('one resume flight may join the same file but cannot silently consume a different selection',async()=>{
 const f=await fixture(),read=f.api.receipt;let release!:()=>void;const gate=new Promise<void>(r=>release=r);f.api.receipt=async()=>{await gate;return read();};const state=useUploadRecovery(f.api,()=> 'owner',()=>meeting,()=>upload),file=new File(['abcdef'],'x.txt'),first=state.resume(file);assert.equal(state.resume(file),first);const other=assert.rejects(state.resume(new File(['abcdef'],'other.txt')));release();await first;await other;assert.deepEqual(f.partCalls,[1,2]);state.dispose();
});

test('a synchronous receipt watcher changing upload cannot publish old completion into the new scope',async()=>{
 const f=await fixture();f.setPhase('COMPLETED');const selected=shallowRef(upload),state=useUploadRecovery(f.api,()=> 'owner',()=>meeting,()=>selected.value);const stop=watch(()=>state.receipt.value,value=>{if(value)selected.value=fileId;},{flush:'sync'});
 try{await assert.rejects(state.inspect(),{name:'AbortError'});assert.equal(state.stage.value,'IDLE');assert.equal(state.receipt.value,null);}finally{stop();state.dispose();}
});

test('synchronous busy watcher cannot start a different file before the original flight is installed',async()=>{
 const f=await fixture(),state=useUploadRecovery(f.api,()=> 'owner',()=>meeting,()=>upload),file=new File(['abcdef'],'first.txt');let second:Promise<void>|undefined;const stop=watch(()=>state.busy.value,value=>{if(value&&!second)second=state.resume(new File(['abcdef'],'second.txt'));},{flush:'sync'});
 try{const first=state.resume(file);assert.ok(second);const results=await Promise.allSettled([first,second]);assert.equal(results[0]?.status,'fulfilled');assert.equal(results[1]?.status,'rejected');assert.deepEqual(f.partCalls,[1,2]);}finally{stop();state.dispose();}
});

test('scope change from completion SENDING prevents the old POST before transport starts',async()=>{
 const f=await fixture(),selected=shallowRef(upload),state=useUploadRecovery(f.api,()=> 'owner',()=>meeting,()=>selected.value),stop=watch(()=>state.completion.state.value,value=>{if(value==='SENDING')selected.value=fileId;},{flush:'sync'});
 try{await assert.rejects(state.resume(new File(['abcdef'],'x.txt')),{name:'AbortError'});assert.equal(f.completeBodies.length,0);assert.equal(state.stage.value,'IDLE');assert.equal(state.receipt.value,null);}finally{stop();state.dispose();}
});
test('ACK watcher scope change leaves the new upload empty after one already-completed POST',async()=>{
 const f=await fixture(),selected=shallowRef(upload),state=useUploadRecovery(f.api,()=> 'owner',()=>meeting,()=>selected.value),stop=watch(()=>state.completion.ack.value,value=>{if(value)selected.value=fileId;},{flush:'sync'});
 try{await assert.rejects(state.resume(new File(['abcdef'],'x.txt')),{name:'AbortError'});assert.equal(f.completeBodies.length,1);assert.equal(state.completion.state.value,'EMPTY');assert.equal(state.stage.value,'IDLE');assert.equal(state.receipt.value,null);}finally{stop();state.dispose();}
});
test('error watcher scope change cannot write the old failure stage back into the new upload',async()=>{
 const f=await fixture();f.losePart();const selected=shallowRef(upload),state=useUploadRecovery(f.api,()=> 'owner',()=>meeting,()=>selected.value),stop=watch(()=>state.error.value,value=>{if(value)selected.value=fileId;},{flush:'sync'});
 try{await assert.rejects(state.resume(new File(['abcdef'],'x.txt')),{name:'AbortError'});assert.equal(f.completeBodies.length,0);assert.equal(state.stage.value,'IDLE');assert.equal(state.error.value,null);assert.equal(state.receipt.value,null);}finally{stop();state.dispose();}
});
