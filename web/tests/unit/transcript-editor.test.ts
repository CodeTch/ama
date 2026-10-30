import {test} from 'node:test';import assert from 'node:assert/strict';import {shallowRef} from 'vue';
import {useTranscriptEditor} from '../../src/features/transcript/useTranscriptEditor.js';
import type {TranscriptCurrent,TranscriptReceipt} from '../../src/shared/api/transcripts.js';
import {ApiError} from '../../src/shared/api/client.js';
import type {CommandSnapshot} from '../../src/shared/composables/useCommand.js';
const meeting='00000000-0000-4000-8000-000000000001',revision='00000000-0000-4000-8000-000000000002',segment='00000000-0000-4000-8000-000000000003',next='00000000-0000-4000-8000-000000000004';
const current=(token='1',id=revision,body='saved'):TranscriptCurrent=>({meetingId:meeting,observation:{meetingRevision:Number(token),revisionToken:token,accessRevision:0,accessRevisionToken:'0',currentSavedRevisionId:id,currentConfirmationId:null},storedInspection:'AVAILABLE',sourceBytes:[],sources:[],correction:{mode:'DISABLED_NOT_INSTALLED',readiness:'NOT_APPLICABLE',qualificationId:null},capabilities:{canReadHistory:true,canReadSources:true,canSave:true,canRestore:true,canConfirm:true},needsGeneration:false,confirmation:null,saved:{id,kind:'WORKING',revision:1,revisionToken:'1',segments:[{segmentId:segment,ordinal:0,body,sourceFileId:revision,sourceParagraph:0,startMs:null,endMs:null,speakerId:null,marks:[]}]}});
const edits=(body:string)=>[{segmentId:segment,body,marks:[] as []}];
const receipt=(id=next):TranscriptReceipt=>({result:{kind:'SAVED_REVISION',meetingId:meeting,revisionId:id,historyRevision:2,historyRevisionToken:'2'},replayed:true,resultIsCurrent:false,observation:current('8',revision).observation});
const storage=()=>{const data=new Map<string,string>();return {data,getItem:(k:string)=>data.get(k)??null,setItem:(k:string,v:string)=>{data.set(k,v);},removeItem:(k:string)=>{data.delete(k);}};};
test('transcript historical ACK cannot bless a newer observation; conflict preserves local draft',async()=>{
 let view=current();const editor=useTranscriptEditor({current:async()=>view,send:async()=>{view=current('8',revision,'other');return receipt();}},()=> 'owner',()=>meeting,()=>true);
 await editor.reload();editor.edit(edits('mine'));await editor.save();assert.equal(editor.command.state.value,'ACKNOWLEDGED');assert.equal(editor.draft.value?.value.value[0]?.body,'mine');assert.equal(editor.draft.value?.baseToken.value,'1');assert.equal(editor.draft.value?.conflict.value?.token,'8');editor.reapply();assert.equal(editor.draft.value?.baseToken.value,'8');editor.dispose();
});
test('transcript original command survives refresh and ACK keeps typing after original save',async()=>{
 const store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const},seen:CommandSnapshot[]=[];
 const first=useTranscriptEditor({current:async()=>current(),send:async c=>{seen.push(c);throw new Error('lost');}},()=> 'owner',()=>meeting,()=>true,context);await first.reload();first.edit(edits('sent'));await first.save();first.edit(edits('later'));first.dispose();
 const second=useTranscriptEditor({current:async()=>current('2',next,'sent'),send:async c=>{seen.push(c);return receipt();}},()=> 'owner',()=>meeting,()=>true,context);await second.reload();assert.equal(second.command.state.value,'UNCERTAIN');await second.save();assert.deepEqual(seen[1],seen[0]);assert.equal(second.draft.value?.value.value[0]?.body,'later');assert.equal(second.draft.value?.baseToken.value,'2');assert.equal(second.draft.value?.dirty.value,true);second.dispose();
});
test('transcript confirm and restore reject unsaved draft; changed actor hides and destroys private recovery',async()=>{
 const who=shallowRef('A'),store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const};let sends=0;
 const editor=useTranscriptEditor({current:async()=>current(),send:async()=>{sends++;return receipt();}},()=>who.value,()=>meeting,()=>true,context);await editor.reload();editor.edit(edits('private'));await editor.confirm();await editor.restore(revision);assert.equal(sends,0);who.value='B';assert.equal(editor.draft.value===null,true);assert.equal([...store.data.values()].some(x=>x.includes('private')),false);await editor.reload();assert.equal(editor.draft.value?.value.value[0]?.body,'saved');editor.dispose();
});

test('read-only current never restores prior owner private draft after permission downgrade',async()=>{
 const store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const};const first=useTranscriptEditor({current:async()=>current(),send:async()=>receipt()},()=> 'owner',()=>meeting,()=>true,context);await first.reload();first.edit(edits('private owner draft'));first.dispose();
 const readonly=current();readonly.capabilities={canSave:false,canConfirm:false,canReadHistory:false,canReadSources:true,canRestore:false};const second=useTranscriptEditor({current:async()=>readonly,send:async()=>receipt()},()=> 'owner',()=>meeting,()=>true,context);await second.reload();assert.equal(second.draft.value===null,true);assert.equal(second.saved.value?.saved?.segments[0]?.body,'saved');second.dispose();
});
test('ACK followed by failed current read retains original command for recovery',async()=>{
 let reads=0,sends=0;const editor=useTranscriptEditor({current:async()=>{reads++;if(reads===2)throw new Error('offline');return reads===1?current():current('2',next,'sent');},send:async()=>{sends++;return receipt();}},()=> 'owner',()=>meeting,()=>true);await editor.reload();editor.edit(edits('sent'));await editor.save();assert.equal(editor.draft.value?.baseToken.value,'1');await editor.retry();assert.equal(sends,1);assert.equal(editor.draft.value?.baseToken.value,'2');assert.equal(editor.draft.value?.dirty.value,false);editor.dispose();
});

test('fresh read-only observation hides an already open private draft',async()=>{
 let view=current();const editor=useTranscriptEditor({current:async()=>view,send:async()=>receipt()},()=> 'owner',()=>meeting,()=>true);await editor.reload();editor.edit(edits('private'));view={...current(),capabilities:{canSave:false,canConfirm:false,canReadHistory:false,canReadSources:true,canRestore:false}};await editor.reload();assert.equal(editor.draft.value===null,true);assert.equal(editor.saved.value?.saved?.segments[0]?.body,'saved');editor.dispose();
});

test('first RAW history can be restored into the initial saved WORKING revision',async()=>{
 let view:TranscriptCurrent={...current(),saved:null,observation:{...current().observation,currentSavedRevisionId:null},capabilities:{canSave:false,canRestore:false,canConfirm:false,canReadHistory:true,canReadSources:false}};let sent:CommandSnapshot|null=null;
 const editor=useTranscriptEditor({current:async()=>view,revision:async()=>({capabilities:{canRestore:true}} as any),send:async c=>{sent=c;view=current('2',next,'raw content');return receipt();}},()=> 'owner',()=>meeting,()=>true);await editor.reload();assert.equal(editor.draft.value===null,true);await editor.restore(revision);assert.equal((sent as CommandSnapshot|null)?.operation,'TRANSCRIPT_RESTORE');assert.equal(editor.draft.value?.value.value[0]?.body,'raw content');editor.dispose();
});

test('409 reload exposes latest conflict and discard/reapply use the latest saved base',async()=>{
 let view=current();const editor=useTranscriptEditor({current:async()=>view,send:async()=>{view=current('2',next,'other');throw new ApiError('http',409,'changed');}},()=> 'owner',()=>meeting,()=>true);await editor.reload();editor.edit(edits('mine'));await editor.save();await editor.reload();assert.equal(editor.draft.value?.conflict.value?.token,'2');assert.equal(editor.draft.value?.value.value[0]?.body,'mine');editor.discard();assert.equal(editor.draft.value?.baseToken.value,'2');assert.equal(editor.draft.value?.value.value[0]?.body,'other');editor.dispose();
});
test('fresh owner authorization allows cold original transcript replay while current producer is unavailable',async()=>{
 const store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const},seen:CommandSnapshot[]=[];
 const first=useTranscriptEditor({current:async()=>current(),send:async c=>{seen.push(c);throw new Error('lost');}},()=> 'owner',()=>meeting,()=>true,context);await first.reload();first.edit(edits('sent'));await first.save();first.dispose();
 const nextEditor=useTranscriptEditor({authorize:async()=>true,current:async()=>{throw new ApiError('http',503,'producer offline');},send:async c=>{seen.push(c);return receipt();}},()=> 'owner',()=>meeting,()=>true,context);await nextEditor.reload();assert.equal(nextEditor.command.state.value,'UNCERTAIN');await nextEditor.retry();assert.deepEqual(seen[1],seen[0]);assert.equal(nextEditor.command.state.value,'ACKNOWLEDGED');assert.equal(nextEditor.saved.value,null);assert.equal(nextEditor.draft.value?.baseToken.value,'1');nextEditor.dispose();
});
test('clean historical restore observation advances both source and root revision for the next edit',async()=>{
 const newer='00000000-0000-4000-8000-000000000009';let view=current(),sent:CommandSnapshot[]=[];
 const editor=useTranscriptEditor({current:async()=>view,revision:async()=>({capabilities:{canRestore:true}} as any),send:async c=>{sent.push(c);view=current('3',newer,'latest');return receipt();}},()=> 'owner',()=>meeting,()=>true);await editor.reload();await editor.restore(revision);editor.edit(edits('next edit'));await editor.save();assert.equal(JSON.parse(sent[1]!.body).sourceRevisionId,newer);assert.equal(JSON.parse(sent[1]!.body).expectedRevisionToken,'3');editor.dispose();
});
test('denial observed by sibling history reader immediately freezes all private editor state',async()=>{
 const editor=useTranscriptEditor({current:async()=>current(),send:async()=>receipt()},()=> 'owner',()=>meeting,()=>true);await editor.reload();editor.edit(edits('private'));editor.deny(new ApiError('http',403,'history denied'));assert.equal(editor.draft.value===null,true);assert.equal(editor.saved.value===null,true);assert.equal(editor.command.state.value,'EMPTY');editor.dispose();
});

test('member authorization reads public saved content with a live cancellation signal',async()=>{
 const view=current();view.capabilities={canReadHistory:false,canReadSources:true,canSave:false,canConfirm:false,canRestore:false};const editor=useTranscriptEditor({authorize:async()=>false,current:async(_id,signal)=>{assert.equal(signal.aborted,false);return view;},send:async()=>receipt()},()=> 'member',()=>meeting,()=>true);await editor.reload();assert.equal(editor.saved.value?.saved?.id,revision);assert.equal(editor.draft.value===null,true);editor.dispose();
});

test('pending save ticket survives a known ACK and failed readback until a successful GET settles it',async()=>{
 let view=current(),fail=false,sends=0;const editor=useTranscriptEditor({current:async()=>{if(fail)throw new Error('lost current read');return view;},send:async()=>{sends++;view=current('2',next,'sent');fail=true;return receipt();}},()=> 'owner',()=>meeting,()=>true);
 try{await editor.reload();editor.edit(edits('sent'));await editor.save();assert.equal(editor.command.state.value,'ACKNOWLEDGED');assert.equal(editor.pendingOperation(),'TRANSCRIPT_SAVE');assert.equal(editor.draft.value?.dirty.value,true);fail=false;await editor.retry();assert.equal(sends,1);assert.equal(editor.pendingOperation(),null);assert.equal(editor.draft.value?.dirty.value,false);assert.equal(editor.draft.value?.baseToken.value,'2');}finally{editor.dispose();}
});
