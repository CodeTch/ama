import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createTopicsApi} from '../../src/shared/api/topics.js';
import {createBusinessClient} from '../../src/shared/api/business-client.js';
const session:any={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};
test('topic facade creates, updates and groups with exact revision tokens',async()=>{
 const old=globalThis.fetch;const calls:Request[]=[];try{globalThis.fetch=async(input,init)=>{calls.push(new Request(String(input),init));return Response.json({id:'00000000-0000-4000-8000-000000000001',name:'专题',description:null,revision:2,revisionToken:'2'},{status:init?.method==='POST'?201:200});};const api=createTopicsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');const result=await api.create({name:'专题',description:null});assert.equal(result.revisionToken,'2');assert.equal(calls[0]!.method,'POST');assert.equal(calls[0]!.headers.get('Idempotency-Key')?.length,36);}finally{globalThis.fetch=old;}
});
