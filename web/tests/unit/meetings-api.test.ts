import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createDeletionsApi,deletionJournalScope} from '../../src/shared/api/deletions.js';
import {createBusinessClient} from '../../src/shared/api/business-client.js';
import type {components} from '../../src/shared/api/generated.js';

const meeting='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee';
const session:components['schemas']['Session']={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};

test('delete sends the original key and opaque version and validates the accepted cleanup receipt',async()=>{
 const old=globalThis.fetch;let request:Request|undefined;
 try{
  globalThis.fetch=async(input,init)=>{request=new Request(String(input),init);return Response.json({deletionTaskId:meeting,meetingId:meeting,state:'PENDING',phase:'INVENTORY',revisionToken:'9007199254740994',attempts:0,errorCode:null},{status:202});};
  const api=createDeletionsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');
  const result=await api.send({actor:'owner',resource:deletionJournalScope,operation:'MEETING_DELETE',key:meeting,path:`/api/v1/meetings/${meeting}`,body:JSON.stringify({expectedRevisionToken:'9007199254740993'})});
  assert.equal(result.deletionTaskId,meeting);assert.equal(result.revisionToken,'9007199254740994');
  assert.equal(request?.method,'DELETE');
  assert.equal(request?.url,`https://ama.test/api/v1/meetings/${meeting}`);
  assert.equal(request?.headers.get('Idempotency-Key'),meeting);
  assert.equal(await request?.json().then((body:any)=>body.expectedRevisionToken),'9007199254740993');
 } finally {globalThis.fetch=old;}
});
