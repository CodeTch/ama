import {test} from 'node:test';import assert from 'node:assert/strict';
import {createMinutesGenerationApi} from '../../src/shared/api/minutes-generation.js';
import {createBusinessClient,type Session} from '../../src/shared/api/business-client.js';
const meeting='00000000-0000-4000-8000-000000000001',id='00000000-0000-4000-8000-000000000002';
const session:Session={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'csrf',expiresAt:'2099-01-01T00:00:00Z'};
const observation={revision:9007199254740992,revisionToken:'9007199254740993',accessRevision:0,accessRevisionToken:'0',currentConfirmationId:null,currentMinutesRevisionId:null};
test('generation uses real producer current readiness and full-scope immutable command',async()=>{
 const api=createMinutesGenerationApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner'),old=fetch;try{
 globalThis.fetch=async url=>{assert.equal(String(url),`https://ama.test/api/v1/meetings/${meeting}/minutes/generation-inputs/current`);return Response.json({saved:{transcriptConfirmationId:null,templateRevisionId:id,exclusionRevisionId:id,elementSetId:null},scope:'FULL',materials:'EMPTY',activeTaskIds:[],eligible:false,reasons:['TRANSCRIPT_CONFIRMATION_REQUIRED'],observation});};const current=await api.current(meeting,new AbortController().signal);assert.equal(current.eligible,false);assert.equal(current.observation.revisionToken,'9007199254740993');
 const body=JSON.stringify({expectedRevisionToken:'9007199254740993',scope:'FULL'});globalThis.fetch=async(url,options)=>{assert.equal(String(url),`https://ama.test/api/v1/meetings/${meeting}/minutes/generations`);assert.equal(options?.body,body);return Response.json({result:{kind:'GENERATION_TASK',meetingId:meeting,inputId:id,taskId:id,inputFingerprint:'a'.repeat(64)},replayed:true,observation},{status:202});};const result=await api.send({actor:'owner',resource:meeting,operation:'MINUTES_GENERATE',path:`/api/v1/meetings/${meeting}/minutes/generations`,key:'original',body});assert.equal(result.result.kind,'GENERATION_TASK');
 }finally{globalThis.fetch=old;}
});
test('generation current accepts exact pinned or pending material states and rejects unknown states',async()=>{
 const api=createMinutesGenerationApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner'),old=fetch;
 try{for(const materials of ['PINNED','PENDING','UNKNOWN']){
  globalThis.fetch=async()=>Response.json({saved:{transcriptConfirmationId:id,templateRevisionId:id,exclusionRevisionId:id,elementSetId:null},scope:'FULL',materials,activeTaskIds:[],eligible:false,reasons:['CAPABILITY_UNAVAILABLE'],observation});
  if(materials==='UNKNOWN')await assert.rejects(()=>api.current(meeting,new AbortController().signal));else assert.equal((await api.current(meeting,new AbortController().signal)).materials,materials);
 }}finally{globalThis.fetch=old;}
});
