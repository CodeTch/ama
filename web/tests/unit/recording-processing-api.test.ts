import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createBusinessClient} from '../../src/shared/api/business-client';
import {recordingProcessingApi} from '../../src/features/recording/processing-api';
const session='00000000-0000-4000-8000-000000000001',meeting='00000000-0000-4000-8000-000000000002',key='00000000-0000-4000-8000-000000000003',task='00000000-0000-4000-8000-000000000004';
const identity:any={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};
const observed={sessionId:session,meetingId:meeting,state:'TRANSCRIBING',materialSnapshotId:null,taskId:task,errorCode:null};
test('recording processing retains snapshot and command identity and retries the observed task',async()=>{
 const original=fetch;const requests:Request[]=[];try{
  globalThis.fetch=async(input,init)=>{requests.push(new Request(String(input),init));return Response.json(observed);};
  const api=recordingProcessingApi(createBusinessClient('https://ama.test',()=>identity,()=>{}));
  await api.start(session,key,'12',null);await api.start(session,key,'12',null);await api.retry(session,task);await api.read(session);
  assert.equal(requests.length,4);
  assert.equal(requests[0]!.headers.get('idempotency-key'),key);assert.equal(requests[1]!.headers.get('idempotency-key'),key);
  assert.deepEqual(await requests[0]!.json(),{expectedRevisionToken:'12',materialSnapshotId:null});
  assert.deepEqual(await requests[1]!.json(),{expectedRevisionToken:'12',materialSnapshotId:null});
  assert.deepEqual(await requests[2]!.json(),{expectedTaskId:task});assert.equal(requests[2]!.method,'POST');assert.match(requests[2]!.url,/\/processing\/retry$/);assert.equal(requests[3]!.method,'GET');
 }finally{globalThis.fetch=original;}
});
test('foreign, unknown or malformed processing facts never become accepted UI state',async()=>{
 const original=fetch;try{
  const api=recordingProcessingApi(createBusinessClient('https://ama.test',()=>identity,()=>{}));
  for(const value of [{...observed,sessionId:key},{...observed,state:'DONE'},{...observed,meetingId:'foreign'},{...observed,taskId:'invalid'},{...observed,errorCode:{}},{...observed,secret:'unexpected'},{...observed,materialsChanged:'yes'},{...observed,materialFiles:[{materialRevisionId:key,name:'未解析',state:'UNKNOWN'}]}]){
   globalThis.fetch=async()=>Response.json(value);await assert.rejects(api.read(session));
  }
 }finally{globalThis.fetch=original;}
});
test('frozen material rows retain the submitted knowledge version and support older receipts',async()=>{
 const original=fetch;try{
  const api=recordingProcessingApi(createBusinessClient('https://ama.test',()=>identity,()=>{}));
  const legacy={materialRevisionId:key,name:'议程',state:'READY'};
  const versioned={...legacy,source:'KNOWLEDGE',sourceVersion:'a'.repeat(64),sourceLibraryId:'library',error:null};
  for(const row of [legacy,versioned]){
   globalThis.fetch=async()=>Response.json({...observed,materialFiles:[row]});
   assert.deepEqual((await api.read(session)).materialFiles,[row]);
  }
  for(const rows of [[{...versioned,sourceVersion:'latest'}],[{...versioned,sourceLibraryId:{}}],[legacy,legacy]]){
   globalThis.fetch=async()=>Response.json({...observed,materialFiles:rows});
   await assert.rejects(api.read(session));
  }
 }finally{globalThis.fetch=original;}
});
