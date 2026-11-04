import {test} from 'node:test';import assert from 'node:assert/strict';
import {useMinutesTaskObservation} from '../../src/features/minutes/useMinutesTaskObservation';
const meeting='10000000-0000-4000-8000-000000000001',id='10000000-0000-4000-8000-000000000002';
const task=(state:string)=>({taskId:id,kind:'MINUTES_GENERATE_V1',state,inputId:id,attemptToken:'1',businessRevisionToken:'7',detailPath:''});
const flush=async()=>{for(let i=0;i<15;i++)await Promise.resolve();};
test('returning to a running task observes beyond thirty polls and notifies one terminal transition',async()=>{
 let ticks=0,done=0;const queue:Array<()=>void>=[];
 const state=useMinutesTaskObservation({list:async()=>({items:[task(++ticks<40?'RUNNING':'SUCCEEDED')],hasMore:false,nextCursor:null,verification:'PERSISTED_RELATION'} as any)},()=> 'owner',()=>meeting,()=>true,()=>done++,fn=>{queue.push(fn);return 1 as any;});
 try{await state.refresh();assert.equal(state.running.value,true);for(let i=0;i<39;i++){assert.ok(queue.length);queue.shift()!();await flush();}assert.equal(ticks,40);assert.equal(done,1);assert.equal(state.running.value,false);await state.refresh();assert.equal(done,1);}finally{state.dispose();}
});
test('failed observations retain actionable error and retry without sending a task',async()=>{
 let reads=0;const queue:Array<()=>void>=[];
 const state=useMinutesTaskObservation({list:async()=>{if(++reads===1)throw Error('网络暂不可用');return{items:[task('FAILED')],hasMore:false,nextCursor:null,verification:'PERSISTED_RELATION'} as any;}},()=> 'owner',()=>meeting,()=>true,()=>{},fn=>{queue.push(fn);return 1 as any;});
 try{await state.refresh();assert.match(state.error.value,/网络/);queue.shift()!();await flush();assert.equal(state.latest.value?.state,'FAILED');assert.equal(state.error.value,'');}finally{state.dispose();}
});
