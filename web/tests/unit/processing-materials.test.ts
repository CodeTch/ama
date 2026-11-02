import {test} from 'node:test';import assert from 'node:assert/strict';import {effectScope,ref} from 'vue';import {useProcessingMaterials} from '../../src/features/import/useProcessingMaterials';
import type {MaterialListing} from '../../src/shared/api/materials';
function currentValue(state:ReturnType<typeof useProcessingMaterials>):MaterialListing|null{return state.value.value;}
const tick=()=>new Promise<void>(r=>setImmediate(r)),empty={items:[],materialSnapshotId:null,materialsPending:false,meetingRevision:'1'};
test('late processing materials cannot survive actor, meeting or active-stage changes',async()=>{const scope=effectScope(),actor=ref<string|null>('owner'),meeting=ref('one'),active=ref(true),flights:{signal:AbortSignal;resolve:(v:any)=>void}[]=[];const state=scope.run(()=>useProcessingMaterials((_id,signal)=>new Promise(resolve=>flights.push({signal,resolve})),()=>actor.value,()=>meeting.value,()=>active.value))!;try{assert.equal(flights.length,1);meeting.value='two';assert.equal(flights[0]!.signal.aborted,true);flights[0]!.resolve({...empty,meetingRevision:'99'});await tick();assert.equal(state.value.value,null);flights[1]!.resolve({...empty,meetingRevision:'2'});await tick();assert.equal(currentValue(state)?.meetingRevision,'2');actor.value=null;assert.equal(state.value.value,null);actor.value='next';assert.equal(flights.length,3);active.value=false;flights[2]!.resolve({...empty,meetingRevision:'3'});await tick();assert.equal(state.value.value,null);}finally{scope.stop();}});
test('failed material reads remain unavailable instead of becoming a false empty list',async()=>{const scope=effectScope();let fail=true;const state=scope.run(()=>useProcessingMaterials(async()=>{if(fail)throw Error('权限暂无法核实');return empty;},()=> 'owner',()=> 'one',()=>true))!;try{await tick();assert.equal(state.value.value,null);assert.match(state.error.value??'',/权限/);fail=false;await state.reload();assert.deepEqual(currentValue(state)?.items,[]);assert.equal(state.error.value,null);}finally{scope.stop();}});
test('a superseded response cannot overwrite a later processing material observation',async()=>{const scope=effectScope(),flights:((v:any)=>void)[]=[];const state=scope.run(()=>useProcessingMaterials(()=>new Promise(resolve=>flights.push(resolve)),()=> 'owner',()=> 'one',()=>true))!;try{const latest=state.reload();flights[1]!({...empty,meetingRevision:'2'});await latest;flights[0]!({...empty,meetingRevision:'1'});await tick();assert.equal(currentValue(state)?.meetingRevision,'2');}finally{scope.stop();}});
test('refresh keeps the current material observation visible until its replacement arrives',async()=>{
 const scope=effectScope(),flights:((v:MaterialListing)=>void)[]=[];
 const state=scope.run(()=>useProcessingMaterials(()=>new Promise(resolve=>flights.push(resolve)),()=> 'owner',()=> 'one',()=>true))!;
 try{
  flights[0]!({...empty,meetingRevision:'1'});await tick();const before=currentValue(state);
  const refresh=state.reload();assert.equal(state.busy.value,true);assert.equal(currentValue(state),before);
  flights[1]!({...empty,meetingRevision:'2'});await refresh;
  assert.equal(currentValue(state)?.meetingRevision,'2');assert.equal(state.busy.value,false);
 }finally{scope.stop();}
});
test('a failed refresh clears the previous observation and exposes the read error',async()=>{
 const scope=effectScope();let fail=false;
 const state=scope.run(()=>useProcessingMaterials(async()=>{if(fail)throw Error('权限暂无法核实');return empty;},()=> 'owner',()=> 'one',()=>true))!;
 try{await tick();assert.ok(currentValue(state));fail=true;await state.reload();assert.equal(currentValue(state),null);assert.match(state.error.value??'',/权限/);assert.equal(state.busy.value,false);}finally{scope.stop();}
});
test('scope changes clear a retained observation and reject the pending refresh',async()=>{
 const scope=effectScope(),actor=ref<string|null>('owner'),flights:{signal:AbortSignal;resolve:(v:MaterialListing)=>void}[]=[];
 const state=scope.run(()=>useProcessingMaterials((_id,signal)=>new Promise(resolve=>flights.push({signal,resolve})),()=>actor.value,()=> 'one',()=>true))!;
 try{
  flights[0]!.resolve(empty);await tick();const refresh=state.reload();assert.ok(currentValue(state));
  actor.value='next';assert.equal(currentValue(state),null);assert.equal(flights[1]!.signal.aborted,true);
  flights[1]!.resolve({...empty,meetingRevision:'99'});await refresh;assert.equal(currentValue(state),null);
  flights[2]!.resolve({...empty,meetingRevision:'2'});await tick();assert.equal(currentValue(state)?.meetingRevision,'2');
 }finally{scope.stop();}
});
