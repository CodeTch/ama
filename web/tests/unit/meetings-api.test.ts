import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createMeetingsApi} from '../../src/shared/api/meetings.js';
import {createBusinessClient} from '../../src/shared/api/business-client.js';
import type {components} from '../../src/shared/api/generated.js';

const meeting='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee';
const session:components['schemas']['Session']={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};

test('delete sends the opaque revision token and validates the mutation response',async()=>{
 const old=globalThis.fetch;let request:Request|undefined;
 try{
  globalThis.fetch=async(input,init)=>{request=new Request(String(input),init);return Response.json({id:meeting,revision:4,revisionToken:'4'});};
  const api=createMeetingsApi(createBusinessClient('https://ama.test',()=>session,()=>{}));
  const result=await api.remove(meeting,'9007199254740993',new AbortController().signal);
  assert.deepEqual(result,{id:meeting,revision:4,revisionToken:'4'});
  assert.equal(request?.method,'DELETE');
  assert.equal(request?.url,`https://ama.test/api/v1/meetings/${meeting}`);
  assert.match(request?.headers.get('Idempotency-Key')??'',/^[0-9a-f-]{36}$/);
  assert.equal(await request?.json().then((body:any)=>body.expectedRevisionToken),'9007199254740993');
 } finally {globalThis.fetch=old;}
});
