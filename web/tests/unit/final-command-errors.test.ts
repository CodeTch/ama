import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createBusinessClient} from '../../src/shared/api/business-client';
import {createPublicationsApi} from '../../src/shared/api/publications';
import {ApiError,request} from '../../src/shared/api/client';
import {useCommand} from '../../src/shared/composables/useCommand';

const meeting='00000000-0000-4000-8000-000000000001';
const traceId='00000000-0000-4000-8000-000000000002';
const session:any={actor:{enterpriseId:'owner'},csrfToken:'test-csrf'};
const api=createPublicationsApi(createBusinessClient('http://localhost',()=>session,()=>{}),()=> 'owner');

for(const [status,code,state] of [[409,'FINAL_NOT_READY','CONFLICT'],[409,'REVISION_CONFLICT','CONFLICT'],[422,'INVALID_OPTIONAL_REVIEW','REJECTED']] as const){
 test(`final ${code} is a definite rejection that permits correction`,async t=>{
  t.mock.method(globalThis,'fetch',async()=>new Response(JSON.stringify({error:{code,message:'rejected',traceId}}),{status}));
  const command=useCommand(api.send);
  command.prepare({actor:'owner',resource:meeting,operation:'FINAL_CONFIRM',path:`/api/v1/meetings/${meeting}/final-confirmations`,key:crypto.randomUUID(),body:{expectedRevisionToken:'3',optionalReviewIds:[]}});
  await assert.rejects(command.send(),(e:unknown)=>e instanceof ApiError&&e.code===code&&e.kind==='http');
  assert.equal(command.state.value,state);
  command.reconcileRejected();
  assert.equal(command.state.value,'EMPTY');
 });
}

for(const [status,code] of [[503,'PUBLICATION_UNAVAILABLE'],[422,'FINAL_NOT_READY'],[409,'UNDECLARED_ERROR']] as const){
 test(`final ${status}/${code} preserves uncertain acceptance`,async t=>{
  t.mock.method(globalThis,'fetch',async()=>new Response(JSON.stringify({error:{code,message:'failure',traceId}}),{status}));
  const command=useCommand(api.send);
  command.prepare({actor:'owner',resource:meeting,operation:'FINAL_CONFIRM',path:`/api/v1/meetings/${meeting}/final-confirmations`,key:crypto.randomUUID(),body:{expectedRevisionToken:'3',optionalReviewIds:[]}});
  const original=command.snapshot.value;
  await assert.rejects(command.send());
  assert.equal(command.state.value,'UNCERTAIN');
  assert.throws(()=>command.reconcileRejected());
  await assert.rejects(command.send());
  assert.equal(command.snapshot.value,original);
 });
}

test('final-specific rejection is not trusted on an unrelated API route',async t=>{
 t.mock.method(globalThis,'fetch',async()=>new Response(JSON.stringify({error:{code:'FINAL_NOT_READY',message:'failure',traceId}}),{status:409}));
 await assert.rejects(request('/api/v1/meetings'),(e:unknown)=>e instanceof ApiError&&e.kind==='response'&&e.code===undefined);
});

test('stalled final reads terminate with a retryable read error and do not prepare a command',async t=>{
 for(const suffix of ['final-confirmations/readiness','publications/current','info-suggestions']){
  const timeout=new AbortController();let received:AbortSignal|undefined;
  t.mock.method(AbortSignal,'timeout',(ms:number)=>{assert.equal(ms,20000);return timeout.signal;});
  t.mock.method(globalThis,'fetch',async(_path:RequestInfo|URL,init?:RequestInit)=>{received=init?.signal??undefined;return new Promise<Response>((_resolve,reject)=>received?.addEventListener('abort',()=>reject(received?.reason),{once:true}));});
  const result=request(`/api/v1/meetings/${meeting}/${suffix}`);
  timeout.abort(new DOMException('read deadline','TimeoutError'));
  await assert.rejects(result,(e:unknown)=>e instanceof ApiError&&e.kind==='network'&&e.message.includes('超时'));
  assert(received?.aborted);t.mock.restoreAll();
 }
});
