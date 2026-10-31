import {test} from 'node:test';import assert from 'node:assert/strict';
import {useFeedbackSubmission} from '../../src/features/meetings/useFeedbackSubmission.js';
const meeting='00000000-0000-4000-8000-000000000001',id='00000000-0000-4000-8000-000000000002',value={meetingId:meeting,category:'WORDING',comment:'精确的建议😀'};
function fixture(){const store=new Map<string,string>();return {storage:{getItem:(k:string)=>store.get(k)??null,setItem:(k:string,v:string)=>{store.set(k,v);},removeItem:(k:string)=>{store.delete(k);}},tab:'00000000-0000-4000-8000-000000000003',recovery:'RESUME_NAMESPACE' as const};}
test('uncertain feedback survives reload and retries the original body/key before accepting edits',async()=>{
 const context=fixture(),seen:any[]=[];let lost=true;const api={create:async(body:typeof value,key:string)=>{seen.push({body,key});if(lost)throw Error('response lost');return {id,...body,state:'PENDING' as const,revisionToken:'0'};}};
 const first=useFeedbackSubmission(api,()=> 'owner',context);first.open(meeting);await assert.rejects(first.submit(value));assert.equal(first.pending.value,true);
 const reloaded=useFeedbackSubmission(api,()=> 'owner',context);assert.deepEqual(reloaded.open(meeting),value);lost=false;
 assert.equal((await reloaded.submit({...value,comment:'later edit'})).id,id);assert.deepEqual(seen[1],seen[0]);assert.equal(reloaded.pending.value,false);
 assert.equal(useFeedbackSubmission(api,()=> 'owner',context).open(meeting),null);
});
test('journal refusal blocks sending and another actor cannot resume prior feedback',async()=>{
 const context=fixture();let calls=0;const api={create:async(body:typeof value)=>{calls++;return {id,...body,state:'PENDING' as const,revisionToken:'0'};}};
 context.storage.setItem=()=>{throw Error('quota');};const submission=useFeedbackSubmission(api,()=> 'owner',context);submission.open(meeting);await assert.rejects(submission.submit(value));assert.equal(calls,0);
 const valid=fixture(),first=useFeedbackSubmission({create:async()=>{throw Error('offline');}},()=> 'owner',valid);first.open(meeting);await assert.rejects(first.submit(value));
 const foreign=useFeedbackSubmission(api,()=> 'other',valid);assert.equal(foreign.open(meeting),null);assert.equal(calls,0);
});
