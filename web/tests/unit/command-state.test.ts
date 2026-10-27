import {watch} from 'vue';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {useCommand} from '../../src/shared/composables/useCommand.js';
import {ApiError} from '../../src/shared/api/client.js';
const input={operation:'intent',actor:' Owner ',resource:'meeting-1',path:'/api/v1/meetings/meeting-1/minutes/editor-intents',key:'original-key',body:{expectedRevisionToken:'9223372036854775806'}};
test('unknown acceptance retains immutable exact request; concurrent submit is single flight',async()=>{
 const calls:Array<{path:string,body:string,key:string}>=[];let release:(x:unknown)=>void=()=>{};let fail=true;
 const c=useCommand<unknown>(async r=>{calls.push({path:r.path,body:r.body,key:r.key});if(fail)throw new ApiError('network',0,'unavailable');return new Promise(resolve=>{release=resolve;});});
 const body={...input.body};c.prepare({...input,body});body.expectedRevisionToken='0';await assert.rejects(c.send());assert.equal(c.state.value,'UNCERTAIN');assert.throws(()=>c.prepare({...input,key:'new-key'}));
 fail=false;const a=c.send(),b=c.send();assert.equal(a,b);release({accepted:true});await a;
 assert.equal(c.state.value,'ACKNOWLEDGED');assert.equal(calls.length,2);assert.deepEqual(calls[0],calls[1]);assert.equal(JSON.parse(calls[1]!.body).expectedRevisionToken,'9223372036854775806');assert.deepEqual(c.ack.value,{accepted:true});
});
test('409 retains frozen input and cannot silently change base; explicit reconciliation creates new key',async()=>{
 const c=useCommand(async()=>{throw new ApiError('http',409,'conflict');});c.prepare(input);await assert.rejects(c.send());assert.equal(c.state.value,'CONFLICT');assert.equal(c.snapshot.value?.body,JSON.stringify(input.body));assert.throws(()=>c.prepare({...input,key:'new'}));c.reconcileRejected();c.prepare({...input,key:'new',body:{expectedRevisionToken:'9007199254740993'}});assert.equal(c.snapshot.value?.key,'new');
});
test('actor invalidation suppresses late ACK and retains no prior actor command',async()=>{
 let release:(x:unknown)=>void=()=>{};const c=useCommand<unknown>(()=>new Promise(resolve=>{release=resolve;}));c.prepare(input);const pending=c.send();c.invalidate();release({secret:'old'});await assert.rejects(pending,{name:'AbortError'});assert.equal(c.state.value,'EMPTY');assert.equal(c.ack.value,null);assert.equal(c.snapshot.value,null);
});
test('503 and invalid success responses remain uncertain; forbidden requests do not automatically retry',async()=>{
 for(const error of [new ApiError('http',503,'unavailable'),new ApiError('response',200,'bad response')]){const c=useCommand(async()=>{throw error;});c.prepare(input);await assert.rejects(c.send());assert.equal(c.state.value,'UNCERTAIN');assert.throws(()=>c.reconcileRejected());}
 const c=useCommand(async()=>{throw new ApiError('http',403,'forbidden');});c.prepare(input);await assert.rejects(c.send());assert.equal(c.state.value,'DENIED');assert.throws(()=>c.send());
});

test('synchronous transport setup failure does not pin the rejected promise forever',async()=>{
 let calls=0;const c=useCommand(()=>{calls++;throw new Error('setup');});c.prepare(input);await assert.rejects(c.send());await assert.rejects(c.send());assert.equal(calls,2);
});

test('malformed HTTP rejection cannot release immutable command input',async()=>{
 const {request}=await import('../../src/shared/api/client.js');const original=globalThis.fetch;
 try{for(const status of [400,409])for(const body of ['{broken','{}',JSON.stringify({error:{code:'MEETING_CONFLICT',message:'m',traceId:'bad'}})]){
  globalThis.fetch=async()=>new Response(body,{status});const c=useCommand(r=>request(r.path,{method:'POST',body:r.body}));c.prepare(input);await assert.rejects(c.send());assert.equal(c.state.value,'UNCERTAIN');assert.throws(()=>c.reconcileRejected());assert.equal(c.snapshot.value?.key,input.key);
 }}finally{globalThis.fetch=original;}
});

test('synchronous ACK watcher cannot switch a snapshot while its old flight still settles',async()=>{
 const {watch}=await import('vue');const seen:string[]=[];const c=useCommand(async r=>{seen.push(r.key);return r.key;});c.prepare(input);let rejected=false;const stop=watch(c.state,state=>{if(state==='ACKNOWLEDGED'){try{c.prepare({...input,key:'next'});}catch{rejected=true;}}},{flush:'sync'});await c.send();stop();assert.equal(rejected,true);assert.equal(c.snapshot.value?.key,'original-key');c.prepare({...input,key:'next'});assert.equal(await c.send(),'next');assert.deepEqual(seen,['original-key','next']);
});

test('synchronous SENDING invalidation prevents transport that has not started',async()=>{
 let sends=0;const command=useCommand(async()=>{sends++;return 'accepted';});command.prepare({actor:'owner',operation:'SAVE',resource:'meeting',path:'/api/v1/meetings/meeting',key:'key',body:{}});const stop=watch(()=>command.state.value,value=>{if(value==='SENDING')command.invalidate();},{flush:'sync'});
 try{await assert.rejects(command.send(),{name:'AbortError'});assert.equal(sends,0);assert.equal(command.state.value,'EMPTY');assert.equal(command.ack.value,null);}finally{stop();}
});
test('synchronous ACK invalidation cannot publish ACKNOWLEDGED for a cleared scope',async()=>{
 const command=useCommand(async()=> 'accepted');command.prepare({actor:'owner',operation:'SAVE',resource:'meeting',path:'/api/v1/meetings/meeting',key:'key',body:{}});const stop=watch(()=>command.ack.value,value=>{if(value)command.invalidate();},{flush:'sync'});
 try{await assert.rejects(command.send(),{name:'AbortError'});assert.equal(command.state.value,'EMPTY');assert.equal(command.snapshot.value,null);assert.equal(command.ack.value,null);}finally{stop();}
});
