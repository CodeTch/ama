import {test} from 'node:test';
import assert from 'node:assert/strict';
import {useFinalConfirmation} from '../../src/features/minutes/useFinalConfirmation';
for(const failure of ['information','roster','readiness','confirmation'] as const){
 test(`confirmation stops at ${failure}, preserves completed stages and never navigates`,async()=>{
  const calls:string[]=[];
  const step=(name:string)=>async()=>{calls.push(name);return name!==failure;};
  const flow=useFinalConfirmation({validate:()=>true,information:step('information'),roster:step('roster'),readiness:step('readiness'),confirmation:step('confirmation'),complete:()=>calls.push('complete')});
  assert.equal(await flow.confirm(),false);
  assert.deepEqual(calls,['information','roster','readiness','confirmation'].slice(0,['information','roster','readiness','confirmation'].indexOf(failure)+1));
  assert.equal(flow.failedPhase.value,failure);assert.equal(flow.busy.value,false);
 });
}
test('the whole confirmation chain is single-flight, including the gaps between API calls',async()=>{
 let release!:()=>void;const calls:string[]=[];
 const flow=useFinalConfirmation({validate:()=>true,information:async()=>{calls.push('save');await new Promise<void>(r=>release=r);return true;},roster:async()=>{calls.push('roster');return true;},readiness:async()=>true,confirmation:async()=>true,complete:()=>{calls.push('complete');}});
 const first=flow.confirm(),second=flow.confirm();assert.equal(first,second);assert.equal(flow.busy.value,true);release();assert.equal(await first,true);assert.deepEqual(calls,['save','roster','complete']);
});
test('invalid fields and disposed scope never submit',async()=>{
 let sends=0;const options={validate:()=>false,information:async()=>{sends++;return true;},roster:async()=>true,readiness:async()=>true,confirmation:async()=>true,complete:()=>{sends++;}};
 const flow=useFinalConfirmation(options);assert.equal(await flow.confirm(),false);flow.dispose();assert.equal(await flow.confirm(),false);assert.equal(sends,0);
});
test('scope invalidation during information save never confirms the roster or navigates',async()=>{
 let release!:(v:boolean)=>void,sends=0;
 const flow=useFinalConfirmation({validate:()=>true,information:()=>new Promise(r=>release=r),roster:async()=>{sends++;return true;},readiness:async()=>true,confirmation:async()=>true,complete:()=>{sends++;}});
 const running=flow.confirm();flow.invalidate();release(true);assert.equal(await running,false);assert.equal(sends,0);assert.equal(flow.busy.value,false);
});
test('completed confirmation releases the navigation guard before entering publication',async()=>{
 let allowed=false;
 const flow=useFinalConfirmation({validate:()=>true,information:async()=>true,roster:async()=>true,readiness:async()=>true,confirmation:async()=>true,complete:()=>{allowed=!flow.busy.value;}});
 assert.equal(await flow.confirm(),true);assert.equal(allowed,true);
});
