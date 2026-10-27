import {test} from 'node:test';import assert from 'node:assert/strict';import {shallowRef} from 'vue';
import {createCommandDiscoveryApi} from '../../src/shared/api/command-discovery.js';
import {useCommandDiscovery} from '../../src/shared/composables/useCommandDiscovery.js';
import {useCommand} from '../../src/shared/composables/useCommand.js';
import {createBusinessClient,type Session} from '../../src/shared/api/business-client.js';
const id='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee',resultId='00000000-0000-4000-8000-000000000001';
const session:Session={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};
const identity={actor:'owner',operation:'CREATE',resource:'meetings',key:'original key 😀'};
const found={status:'FOUND' as const,association:{operation:'CREATE' as const,meetingId:id,resultKind:'INFO_REVISION' as const,resultId,acceptedRevisionToken:'0',originalHttpStatus:201 as const},current:{meetingId:id,revisionToken:'9007199254740993',accessRevisionToken:'7',stage:'MINUTES_PENDING' as const}};
test('command lookup sends exact identity in CSRF POST body and separates original/current tokens',async()=>{
 const old=fetch,api=createCommandDiscoveryApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');
 try{globalThis.fetch=async(url,options)=>{assert.equal(String(url),'https://ama.test/api/v1/meetings/command-discovery');assert.equal(options?.method,'POST');assert.equal(new Headers(options?.headers).get('X-CSRF-Token'),session.csrfToken);assert.deepEqual(JSON.parse(String(options?.body)),{operation:'CREATE',resource:'meetings',key:identity.key});return Response.json(found);};const result=await api.lookup(identity,new AbortController().signal);assert.equal(result.current?.revisionToken,'9007199254740993');assert.equal(result.association?.acceptedRevisionToken,'0');assert.ok(Object.isFrozen(result));assert.ok(Object.isFrozen(result.association));}finally{globalThis.fetch=old;}
});
test('lookup rejects malformed or cross-resource associations and illegal family/status/revision pairs',async()=>{
 const old=fetch,api=createCommandDiscoveryApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');
 try{for(const value of [{...found,extra:true},{...found,current:null},{...found,association:{...found.association,operation:'SAVE_INFO'}},{...found,association:{...found.association,resultKind:'UPLOAD_SESSION'}},{...found,association:{...found.association,originalHttpStatus:200}},{...found,association:{...found.association,acceptedRevisionToken:'1'}},{...found,current:{...found.current,meetingId:resultId}},{...found,current:{...found.current,revisionToken:'9223372036854775807'}},{...found,current:{...found.current,stage:['ENTRY']}},{status:'ABSENT',association:found.association,current:null},{status:'ABSENT',association:null,current:found.current}]){globalThis.fetch=async()=>Response.json(value);await assert.rejects(api.lookup(identity,new AbortController().signal));}
 globalThis.fetch=async()=>Response.json({...found,association:{...found.association,operation:'SAVE_INFO',acceptedRevisionToken:'2',originalHttpStatus:200}});await assert.rejects(api.lookup({...identity,operation:'SAVE_INFO',resource:resultId},new AbortController().signal));
 }finally{globalThis.fetch=old;}
});
test('scope and encoding bounds fail before transport; scoped absence requires a current observation',async()=>{
 const old=fetch;let calls=0;const api=createCommandDiscoveryApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');
 try{globalThis.fetch=async()=>{calls++;return Response.json({status:'ABSENT',association:null,current:null});};for(const value of [{...identity,actor:'other'},{...identity,operation:'OTHER'},{...identity,resource:id},{...identity,key:'😀'.repeat(1025)},{...identity,key:'\ud800'},{...identity,key:' '},{...identity,key:'\u0000'.repeat(4096)}])await assert.rejects(api.lookup(value,new AbortController().signal));assert.equal(calls,0);assert.equal((await api.lookup(identity,new AbortController().signal)).status,'ABSENT');await assert.rejects(api.lookup({...identity,operation:'UPLOAD_BEGIN',resource:id},new AbortController().signal));}finally{globalThis.fetch=old;}
});
test('discovery never changes an uncertain command, even for FOUND or ABSENT',async()=>{
 const command=useCommand(async()=>{throw new Error('lost');});command.prepare({...identity,path:'/api/v1/meetings',body:{}});await assert.rejects(command.send());const snapshot=command.snapshot.value;
 let response:typeof found|{status:'ABSENT';association:null;current:null}=found;const state=useCommandDiscovery(async()=>response,()=> 'owner',()=>command.snapshot.value);
 await state.reload();assert.equal(state.result.value?.status,'FOUND');assert.equal(command.state.value,'UNCERTAIN');assert.equal(command.ack.value,null);response={status:'ABSENT',association:null,current:null};await state.reload();assert.equal(state.result.value?.status,'ABSENT');assert.deepEqual(command.snapshot.value,snapshot);assert.equal(command.state.value,'UNCERTAIN');state.dispose();
});
test('late lookup response is hidden after actor/key changes and current failure clears old association',async()=>{
 const actor=shallowRef<string|null>('owner'),scope=shallowRef({...identity});let finish!:(v:typeof found)=>void;let signal:AbortSignal|undefined;
 const state=useCommandDiscovery((_request,s)=>{signal=s;return new Promise(r=>finish=r);},()=>actor.value,()=>scope.value);const first=state.reload();actor.value=null;assert.equal(signal?.aborted,true);finish(found);await first;assert.equal(state.result.value,null);actor.value='owner';const second=state.reload();scope.value={...identity,key:'changed'};finish(found);await second;assert.equal(state.result.value,null);state.dispose();
 let denied=false;const fresh=useCommandDiscovery(async()=>{if(denied)throw new Error('denied');return found;},()=> 'owner',()=>identity);await fresh.reload();assert.equal(fresh.result.value?.status,'FOUND');denied=true;await fresh.reload();assert.equal(fresh.result.value,null);assert.ok(fresh.error.value);fresh.dispose();
});

test('all scoped lookup families preserve original association and require exact current resource',async()=>{
 const old=fetch,api=createCommandDiscoveryApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');
 try{for(const operation of ['SAVE_INFO','CONFIRM_ROSTER','UPLOAD_BEGIN'] as const){const upload=operation==='UPLOAD_BEGIN';const wire={...found,association:{...found.association,operation,resultKind:upload?'UPLOAD_SESSION':operation==='CONFIRM_ROSTER'?'ROSTER_REVISION':'INFO_REVISION',acceptedRevisionToken:upload?null:'9007199254740992',originalHttpStatus:upload?201:200}};globalThis.fetch=async()=>Response.json(wire);const value=await api.lookup({...identity,operation,resource:id},new AbortController().signal);assert.equal(value.association?.operation,operation);assert.equal(value.association?.acceptedRevisionToken,wire.association.acceptedRevisionToken);globalThis.fetch=async()=>Response.json({status:'ABSENT',association:null,current:found.current});assert.equal((await api.lookup({...identity,operation,resource:id},new AbortController().signal)).status,'ABSENT');}}finally{globalThis.fetch=old;}
});
