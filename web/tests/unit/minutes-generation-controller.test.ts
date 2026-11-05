import {test} from 'node:test';import {watch} from 'vue';import assert from 'node:assert/strict';
import {useMinutesGeneration} from '../../src/features/minutes/useMinutesGeneration.js';
import type {GenerationCurrent,GenerationReceipt} from '../../src/shared/api/minutes-generation.js';
import type {CommandSnapshot} from '../../src/shared/composables/useCommand.js';
const meeting='00000000-0000-4000-8000-000000000001',id='00000000-0000-4000-8000-000000000002';
const current=(eligible:boolean):GenerationCurrent=>({saved:{transcriptConfirmationId:eligible?id:null,templateRevisionId:id,exclusionRevisionId:id,elementSetId:null},scope:'FULL',materials:'EMPTY',activeTaskIds:[],eligible,reasons:eligible?[]:['TRANSCRIPT_CONFIRMATION_REQUIRED'],observation:{revision:3,revisionToken:'3',accessRevision:0,accessRevisionToken:'0',currentConfirmationId:null,currentMinutesRevisionId:null}});
const receipt:GenerationReceipt={result:{kind:'GENERATION_TASK',meetingId:meeting,inputId:id,taskId:id,inputFingerprint:'a'.repeat(64)},replayed:true,observation:current(true).observation};
const storage=()=>{const data=new Map<string,string>();return {getItem:(k:string)=>data.get(k)??null,setItem:(k:string,v:string)=>{data.set(k,v);},removeItem:(k:string)=>{data.delete(k);}};};
test('a recovered running task prevents another generation even before eligibility refresh',async()=>{
 let sends=0;const state=useMinutesGeneration({current:async()=>({...current(true),activeTaskIds:[id]}),templates:async()=>({items:[],nextCursor:null,observation:current(true).observation}),send:async()=>{sends++;return receipt;}},()=> 'owner',()=>meeting,()=>true);
 await state.reload();await state.generate();assert.equal(sends,0);assert.match(String(state.error.value),/任务/);state.dispose();
});
test('region recovery preserves original category instructions and base after the source changes',async()=>{
 const seen:CommandSnapshot[]=[],context={tab:'region',storage:storage(),recovery:'RESUME_NAMESPACE' as const};let base=id;
 const api={current:async()=>({...current(true),saved:{...current(true).saved,elementSetId:base}}),templates:async()=>({items:[],nextCursor:null,observation:current(true).observation}),send:async(c:CommandSnapshot)=>{seen.push(c);if(seen.length===1)throw new Error('lost region ACK');return receipt;}};
 const a=useMinutesGeneration(api,()=> 'owner',()=>meeting,()=>true,context);await a.reload();await a.generateRegion({baseElementSetId:id,category:'ACTION',instructions:'只整理已有行动，不补日期'});assert.equal(a.command.state.value,'UNCERTAIN');a.dispose();base=meeting;
 const b=useMinutesGeneration(api,()=> 'owner',()=>meeting,()=>true,context);await b.reload();await b.retry();assert.deepEqual(seen[1],seen[0]);await b.generateRegion({baseElementSetId:id,category:'ACTION',instructions:'新请求'});assert.equal(seen.length,2);b.dispose();
});
test('generation refresh recovers exact original command before new unavailable eligibility checks',async()=>{
 const seen:CommandSnapshot[]=[],context={tab:'tab',storage:storage(),recovery:'RESUME_NAMESPACE' as const};let ready=true;
 const api={current:async()=>current(ready),templates:async()=>({items:[],nextCursor:null,observation:current(ready).observation}),send:async(c:CommandSnapshot)=>{seen.push(c);if(seen.length===1)throw new Error('lost');return receipt;}};
 const a=useMinutesGeneration(api,()=> 'owner',()=>meeting,()=>true,context);await a.reload();await a.generate();assert.equal(a.command.state.value,'UNCERTAIN');a.dispose();ready=false;
 const b=useMinutesGeneration(api,()=> 'owner',()=>meeting,()=>true,context);await b.reload();await b.retry();assert.deepEqual(seen[1],seen[0]);assert.equal(b.command.state.value,'ACKNOWLEDGED');await b.generate();assert.equal(seen.length,2);b.dispose();
});
test('new generation does not submit without real current readiness even when task list is empty',async()=>{
 let sends=0;const state=useMinutesGeneration({current:async()=>current(false),templates:async()=>({items:[],nextCursor:null,observation:current(false).observation}),send:async()=>{sends++;return receipt;}},()=> 'owner',()=>meeting,()=>true);await state.reload();await state.generate();assert.equal(sends,0);assert.equal(state.current.value?.reasons[0],'TRANSCRIPT_CONFIRMATION_REQUIRED');state.dispose();
});

test('accepted generation clears recovery before a receipt observer unmounts the first-result panel',async()=>{
 const context={tab:'ack-unmount',storage:storage(),recovery:'RESUME_NAMESPACE' as const},seen:CommandSnapshot[]=[];
 const api={current:async()=>current(true),templates:async()=>({items:[],nextCursor:null,observation:current(true).observation}),send:async(c:CommandSnapshot)=>{seen.push(c);return receipt;}};
 const first=useMinutesGeneration(api,()=> 'owner',()=>meeting,()=>true,context);await first.reload();
 const stop=watch(()=>first.command.ack.value,ack=>{if(ack)first.dispose();},{flush:'sync'});
 await first.generate();stop();assert.equal(seen.length,1);
 const resumed=useMinutesGeneration(api,()=> 'owner',()=>meeting,()=>true,context);await resumed.reload();
 assert.equal(resumed.command.state.value,'EMPTY','An acknowledged terminal task must not resurrect as an uncertain command');
 await resumed.generate();assert.equal(seen.length,2);assert.notEqual(seen[1]!.key,seen[0]!.key);resumed.dispose();
});
test('late accepted response from a disposed panel cannot clear a newer meeting command',async()=>{
 const context={tab:'late-ack',storage:storage(),recovery:'RESUME_NAMESPACE' as const};let finishOld!:(value:GenerationReceipt)=>void;
 const api={current:async()=>current(true),templates:async()=>({items:[],nextCursor:null,observation:current(true).observation}),send:async()=>new Promise<GenerationReceipt>(resolve=>{finishOld=resolve;})};
 const first=useMinutesGeneration(api,()=> 'owner',()=>meeting,()=>true,context);await first.reload();const running=first.generate();
 while(!finishOld)await new Promise(resolve=>setImmediate(resolve));first.dispose();
 const newer=useMinutesGeneration({...api,send:async()=>{throw Error('new response lost');}},()=> 'owner',()=>id,()=>true,context);await newer.reload();await newer.generate();assert.equal(newer.command.state.value,'UNCERTAIN');const original=newer.command.snapshot.value;newer.dispose();
 finishOld(receipt);await running;
 const restored=useMinutesGeneration(api,()=> 'owner',()=>id,()=>true,context);await restored.reload();assert.equal(restored.command.state.value,'UNCERTAIN');assert.deepEqual(restored.command.snapshot.value,original);restored.dispose();
});
test('failed acknowledged-journal cleanup blocks replacement and fresh task read still prevents duplication',async()=>{
 const backing=storage();let denyRemoval=false,sends=0;
 const context={tab:'cleanup-failure',storage:{...backing,removeItem:(key:string)=>{if(denyRemoval)throw Error('storage denied');backing.removeItem(key);}},recovery:'RESUME_NAMESPACE' as const};
 const state=useMinutesGeneration({current:async()=>({...current(true),activeTaskIds:sends?[id]:[]}),templates:async()=>({items:[],nextCursor:null,observation:current(true).observation}),send:async()=>{sends++;return receipt;}},()=> 'owner',()=>meeting,()=>true,context);
 await state.reload();denyRemoval=true;await state.generate();assert.equal(state.command.state.value,'ACKNOWLEDGED');assert.equal(state.blocked.value,true);
 await state.generate();assert.equal(sends,1);denyRemoval=false;await state.reload();assert.equal(state.blocked.value,false);await state.generate();assert.equal(sends,1);state.dispose();
});
