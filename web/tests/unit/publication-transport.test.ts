import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createBusinessClient} from '../../src/shared/api/business-client.js';
import {createPublicationsApi} from '../../src/shared/api/publications.js';
import {ApiError,request} from '../../src/shared/api/client.js';

const meeting='00000000-0000-4000-8000-000000000001';
const publication='00000000-0000-4000-8000-000000000002';
const root=`/api/v1/meetings/${meeting}`;
const session={actor:{enterpriseId:'owner',status:'ACTIVE' as const,roles:['USER' as const]},csrfToken:'T'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};
test('configuration save reaches the authenticated transport and preserves a declared version conflict',async()=>{
 const original=fetch;let calls=0;
 globalThis.fetch=async(input,init)=>{calls++;assert.equal(new URL(String(input)).pathname,root+'/publication-configuration');assert.equal(init?.method,'PUT');assert.equal(new Headers(init?.headers).get('X-CSRF-Token'),session.csrfToken);return Response.json({error:{code:'PUBLICATION_CONFIGURATION_CONFLICT',message:'private provider payload',traceId:meeting}},{status:409});};
 try{const api=createPublicationsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=>session.actor.enterpriseId);
  await assert.rejects(api.saveConfiguration(meeting,{expectedConfigurationVersionToken:'1',providerId:'kb',libraryId:'library',folderId:'root',tags:['验收']}),(error:unknown)=>error instanceof ApiError&&error.kind==='http'&&error.code==='PUBLICATION_CONFIGURATION_CONFLICT'&&error.message.includes('其他会话')&&!error.message.includes('private'));
  assert.equal(calls,1);
 }finally{globalThis.fetch=original;}
});
test('all three publication recovery routes reach transport but unrelated or malformed routes remain rejected',async()=>{
 const original=fetch;let calls=0;
 globalThis.fetch=async()=>{calls++;return Response.json({error:{code:'PUBLICATION_RETRY_INPUT_CHANGED',message:'private',traceId:meeting}},{status:409});};
 try{
  for(const action of ['zero-effect-retries','resubmissions','local-recoveries'])await assert.rejects(request('https://ama.test'+root+`/publications/${publication}/${action}`,{method:'POST',errorContract:'final-publication'}),(error:unknown)=>error instanceof ApiError&&error.code==='PUBLICATION_RETRY_INPUT_CHANGED');
  for(const [path,method] of [[root+'/info','PUT'],[root+'/publication-configuration','POST'],[root+'/publications/not-a-publication/resubmissions','POST'],[root+`/publications/${publication}/resubmissions`,'GET']])await assert.rejects(request('https://ama.test'+path,{method,errorContract:'final-publication'}),TypeError);
  assert.equal(calls,3);
 }finally{globalThis.fetch=original;}
});
