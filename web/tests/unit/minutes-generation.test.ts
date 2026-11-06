import {test} from 'node:test';import assert from 'node:assert/strict';
import {createMinutesGenerationApi,validGenerationCommand,visibleTemplates} from '../../src/shared/api/minutes-generation.js';
import {createBusinessClient,type Session} from '../../src/shared/api/business-client.js';
const meeting='00000000-0000-4000-8000-000000000001',id='00000000-0000-4000-8000-000000000002';
const session:Session={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'csrf',expiresAt:'2099-01-01T00:00:00Z'};
const observation={revision:9007199254740992,revisionToken:'9007199254740993',accessRevision:0,accessRevisionToken:'0',currentConfirmationId:null,currentMinutesRevisionId:null};
test('region command binds category base set and exact instructions; FULL cannot hide region values',()=>{
 const body={expectedRevisionToken:'7',scope:'REGION',baseElementSetId:id,category:'ACTION',instructions:'突出待确认负责人'};const command={actor:'owner',resource:meeting,operation:'MINUTES_GENERATE',path:`/api/v1/meetings/${meeting}/minutes/generations`,key:'original',body:JSON.stringify(body)};
 assert(validGenerationCommand(command,'owner',meeting));for(const changed of [{...body,scope:'FULL'},{...body,category:'UNKNOWN'},{...body,instructions:' '},{...body,instructions:'x'.repeat(4097)},{...body,baseElementSetId:'bad'}])assert(!validGenerationCommand({...command,body:JSON.stringify(changed)},'owner',meeting));
});
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
test('meeting template catalog accepts bounded personal themes while preserving the built-in category contract',async()=>{
 let item:any={templateId:id,revisionId:id,name:'个人模板',themes:['会议决定😀'],themeCount:1,selectable:true,default:false};
 const api=createMinutesGenerationApi({json:async()=>({items:[item],nextCursor:null,observation})} as any,()=> 'owner');
 assert.deepEqual((await api.templates(meeting,null,new AbortController().signal)).items[0]?.themes,['会议决定😀']);
 for(const invalid of [{...item,themeCount:8},{...item,themes:[]},{...item,themeCount:9,themes:Array(9).fill('主题')},{...item,themes:['\ud800']},{...item,default:true}]){item=invalid;await assert.rejects(api.templates(meeting,null,new AbortController().signal));}
 item={templateId:id,revisionId:id,name:'内置模板',themes:['OVERVIEW','DISCUSSION','CONCLUSION','ACTION','RISK','OPEN_QUESTION','DISAGREEMENT'],themeCount:7,selectable:true,default:true};
 assert.equal((await api.templates(meeting,null,new AbortController().signal)).items[0]?.default,true);
});

test('confirmed generation state survives strict decoding on current and historical responses',async t=>{
 const saved={elementSetId:null,exclusionRevisionId:null,templateRevisionId:null,transcriptConfirmationId:id};
 const header={activeTaskIds:[],eligible:true,materials:'EMPTY',observation,reasons:[],saved,scope:'FULL'};
 let wire:unknown=header;
 t.mock.method(globalThis,'fetch',async()=>Response.json(wire));
 const api=createMinutesGenerationApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=>'owner');
 const read=()=>api.current(meeting,new AbortController().signal);
 assert.equal((await read()).confirmationGeneration,undefined);
 for(const state of ['NONE','WAITING','RETRYING','SUBMITTED','SUPERSEDED']){
  wire={...header,confirmationGeneration:state};assert.equal((await read()).confirmationGeneration,state);
 }
 for(const invalid of ['QUEUED',null,42]){
  wire={...header,confirmationGeneration:invalid};await assert.rejects(read);
 }
 wire={...header,confirmationGeneration:'WAITING',extra:true};await assert.rejects(read);
});

test('installed choices remain visible when the UUID page consists of personal templates',()=>{
 const personal={templateId:id,revisionId:id,name:'个人总结',themes:['讨论'],themeCount:1,selectable:true,default:false};
 const builtin={...personal,templateId:meeting,revisionId:meeting,name:'正式会议纪要'};
 const page={items:[personal],builtins:[builtin],nextCursor:id,observation};
 assert.deepEqual(visibleTemplates(page).map(t=>t.name),['正式会议纪要','个人总结']);
 assert.deepEqual(visibleTemplates({...page,items:[personal,builtin]}).map(t=>t.name),['正式会议纪要','个人总结']);
});
