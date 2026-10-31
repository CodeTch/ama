import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createTopicsApi} from '../../src/shared/api/topics.js';
import {createBusinessClient} from '../../src/shared/api/business-client.js';
const session:any={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};
test('real 204 topic assignment and removal acknowledgements have no topic JSON body',async()=>{
 const previous=globalThis.fetch,calls:Request[]=[];try{
  globalThis.fetch=async(input,init)=>{calls.push(new Request(String(input),init));return new Response(null,{status:204});};
  const api=createTopicsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner'),meeting='00000000-0000-4000-8000-000000000001',topic='00000000-0000-4000-8000-000000000002';
  assert.equal(await api.assign(meeting,topic,'9007199254740993'),undefined);assert.equal(await api.unassign(meeting,topic,'9007199254740993'),undefined);
  assert.equal(calls[0]!.method,'POST');assert.deepEqual(await calls[0]!.json(),{topicId:topic,expectedRevisionToken:'9007199254740993'});assert.equal(calls[1]!.method,'DELETE');assert.deepEqual(await calls[1]!.json(),{expectedRevisionToken:'9007199254740993'});assert.equal(calls[0]!.headers.get('Idempotency-Key')?.length,36);
 }finally{globalThis.fetch=previous;}
});
test('topic facade creates, updates and groups with exact revision tokens',async()=>{
 const old=globalThis.fetch;const calls:Request[]=[];try{globalThis.fetch=async(input,init)=>{calls.push(new Request(String(input),init));return Response.json({id:'00000000-0000-4000-8000-000000000001',name:'专题',description:null,revision:2,revisionToken:'2'},{status:init?.method==='POST'?201:200});};const api=createTopicsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');const result=await api.create({name:'专题',description:null});assert.equal(result.revisionToken,'2');assert.equal(calls[0]!.method,'POST');assert.equal(calls[0]!.headers.get('Idempotency-Key')?.length,36);}finally{globalThis.fetch=old;}
});

test('caller-owned topic key survives retry and late prior-actor response is discarded',async()=>{
 const previous=globalThis.fetch,calls:Request[]=[];let actor:string|null='owner';const id='00000000-0000-4000-8000-000000000001',key='00000000-0000-4000-8000-000000000002';
 try{globalThis.fetch=async(input,init)=>{calls.push(new Request(String(input),init));return Response.json({id,name:'专题',description:null,revision:0,revisionToken:'0'},{status:201});};const api=createTopicsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=>actor);await api.create({name:'专题',description:null},key);await api.create({name:'专题',description:null},key);assert.equal(calls[0]!.headers.get('Idempotency-Key'),key);assert.equal(calls[1]!.headers.get('Idempotency-Key'),key);
 globalThis.fetch=async()=>{actor='other';return Response.json({id,name:'专题',description:null,revision:0,revisionToken:'0'},{status:201});};await assert.rejects(api.create({name:'专题',description:null},key),{name:'AbortError'});
 }finally{globalThis.fetch=previous;}
});

test('atomic topic grouping binds every original meeting revision and rejects duplicate targets',async()=>{
 const {validTopicCommand,TOPIC_WORKSPACE}=await import('../../src/shared/api/topics.js');const id='00000000-0000-4000-8000-000000000001',key='00000000-0000-4000-8000-000000000002';const command={actor:'owner',operation:'TOPIC_BATCH_ASSIGN',resource:TOPIC_WORKSPACE,path:'/api/v1/topics/assignments',key,body:JSON.stringify({topicId:null,meetings:[{meetingId:id,expectedRevisionToken:'9007199254740993'}]})};assert(validTopicCommand(command,'owner',TOPIC_WORKSPACE));assert.equal(validTopicCommand({...command,body:JSON.stringify({topicId:null,meetings:[{meetingId:id,expectedRevisionToken:'1'},{meetingId:id,expectedRevisionToken:'2'}]})},'owner',TOPIC_WORKSPACE),false);
 const previous=globalThis.fetch;try{globalThis.fetch=async(input,init)=>{const request=new Request(String(input),init);assert.equal(request.headers.get('Idempotency-Key'),key);assert.equal(await request.text(),command.body);return new Response(null,{status:204});};const api=createTopicsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');assert.equal(await api.send(command),null);}finally{globalThis.fetch=previous;}
});
