import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createFeedbackApi} from '../../src/shared/api/feedback.js';
import {createBusinessClient} from '../../src/shared/api/business-client.js';
const session:any={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};
test('feedback facade validates persisted list and submit response',async()=>{
 const old=globalThis.fetch;try{globalThis.fetch=async(_input,init)=>init?.method==='POST'?Response.json({id:'00000000-0000-4000-8000-000000000002',meetingId:'00000000-0000-4000-8000-000000000001',category:'WORDING',comment:'建议',state:'PENDING',revisionToken:'0'},{status:201}):Response.json({items:[],nextCursor:null});const api=createFeedbackApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');const result=await api.create({meetingId:'00000000-0000-4000-8000-000000000001',category:'WORDING',comment:'建议'});assert.equal(result.state,'PENDING');assert.deepEqual(await api.list(null,new AbortController().signal),{items:[],nextCursor:null});}finally{globalThis.fetch=old;}
});
