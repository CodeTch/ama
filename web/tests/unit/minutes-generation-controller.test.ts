import {test} from 'node:test';import assert from 'node:assert/strict';
import {useMinutesGeneration} from '../../src/features/minutes/useMinutesGeneration.js';
import type {GenerationCurrent,GenerationReceipt} from '../../src/shared/api/minutes-generation.js';
import type {CommandSnapshot} from '../../src/shared/composables/useCommand.js';
const meeting='00000000-0000-4000-8000-000000000001',id='00000000-0000-4000-8000-000000000002';
const current=(eligible:boolean):GenerationCurrent=>({saved:{transcriptConfirmationId:eligible?id:null,templateRevisionId:id,exclusionRevisionId:id,elementSetId:null},scope:'FULL',materials:'EMPTY',activeTaskIds:[],eligible,reasons:eligible?[]:['TRANSCRIPT_CONFIRMATION_REQUIRED'],observation:{revision:3,revisionToken:'3',accessRevision:0,accessRevisionToken:'0',currentConfirmationId:null,currentMinutesRevisionId:null}});
const receipt:GenerationReceipt={result:{kind:'GENERATION_TASK',meetingId:meeting,inputId:id,taskId:id,inputFingerprint:'a'.repeat(64)},replayed:true,observation:current(true).observation};
const storage=()=>{const data=new Map<string,string>();return {getItem:(k:string)=>data.get(k)??null,setItem:(k:string,v:string)=>{data.set(k,v);},removeItem:(k:string)=>{data.delete(k);}};};
test('generation refresh recovers exact original command before new unavailable eligibility checks',async()=>{
 const seen:CommandSnapshot[]=[],context={tab:'tab',storage:storage(),recovery:'RESUME_NAMESPACE' as const};let ready=true;
 const api={current:async()=>current(ready),templates:async()=>({items:[],nextCursor:null,observation:current(ready).observation}),send:async(c:CommandSnapshot)=>{seen.push(c);if(seen.length===1)throw new Error('lost');return receipt;}};
 const a=useMinutesGeneration(api,()=> 'owner',()=>meeting,()=>true,context);await a.reload();await a.generate();assert.equal(a.command.state.value,'UNCERTAIN');a.dispose();ready=false;
 const b=useMinutesGeneration(api,()=> 'owner',()=>meeting,()=>true,context);await b.reload();await b.retry();assert.deepEqual(seen[1],seen[0]);assert.equal(b.command.state.value,'ACKNOWLEDGED');await b.generate();assert.equal(seen.length,2);b.dispose();
});
test('new generation does not submit without real current readiness even when task list is empty',async()=>{
 let sends=0;const state=useMinutesGeneration({current:async()=>current(false),templates:async()=>({items:[],nextCursor:null,observation:current(false).observation}),send:async()=>{sends++;return receipt;}},()=> 'owner',()=>meeting,()=>true);await state.reload();await state.generate();assert.equal(sends,0);assert.equal(state.current.value?.reasons[0],'TRANSCRIPT_CONFIRMATION_REQUIRED');state.dispose();
});
