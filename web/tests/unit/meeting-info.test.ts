import {test} from 'node:test';import assert from 'node:assert/strict';
import {createMeetingInfoApi,saveInfoBody} from '../../src/shared/api/meeting-info.js';
import {createBusinessClient,type Session} from '../../src/shared/api/business-client.js';
const id='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee';
const session:Session={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};
const saved={id,revision:9007199254740992,revisionToken:'9007199254740993',accessRevision:0,accessRevisionToken:'0',stage:'ENTRY',name:'会议😀',category:null,summary:null,occurredOn:null,confirmedRosterId:null,confirmedRoster:[],rosterDraftId:null,rosterDraft:null};
test('saved info facade preserves token and explicit roster absence; rejects malformed or foreign response',async()=>{
 const old=fetch,api=createMeetingInfoApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=>session.actor.enterpriseId);
 try{globalThis.fetch=async()=>Response.json(saved);const value=await api.saved(id,new AbortController().signal);assert.equal(value.revisionToken,'9007199254740993');assert.equal(value.rosterDraft,null);assert.ok(Object.isFrozen(value));
 for(const bad of [{...saved,id:'00000000-0000-4000-8000-000000000000'},{...saved,stage:['ENTRY']},{...saved,revisionToken:9007199254740992},{...saved,confirmedRoster:[{memberId:id,enterpriseId:null,displayName:'x',memberKind:'ENTERPRISE'}]},{...saved,rosterDraft:[]},{...saved,occurredOn:'2026-02-30'}]){globalThis.fetch=async()=>Response.json(bad);await assert.rejects(api.saved(id,new AbortController().signal));}}finally{globalThis.fetch=old;}
});
test('save builder projects token-only complete fields at all exact counter boundaries',()=>{
 for(const token of ['9007199254740991','9007199254740992','9007199254740993','9223372036854775806']){
 const fields={name:' exact 😀 ',category:null,summary:'line\nline',occurredOn:null,rosterDraftIds:[' Owner '],externalRosterNames:['访客'],expectedRevision:1};const body=saveInfoBody(token,fields);assert.equal(body.expectedRevisionToken,token);assert.ok(!('expectedRevision' in body));assert.deepEqual(body.rosterDraftIds,[' Owner ']);fields.rosterDraftIds.push('later');assert.deepEqual(body.rosterDraftIds,[' Owner ']);
 }assert.throws(()=>saveInfoBody('9223372036854775807',{name:null,category:null,summary:null,occurredOn:null,rosterDraftIds:[],externalRosterNames:[]}));
});
test('meeting command facade retries exact bytes and validates original mutation resource',async()=>{
 const old=fetch,seen:Array<{method:string|undefined;body:unknown;key:string|null}>=[],api=createMeetingInfoApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=>session.actor.enterpriseId);const body='{ "expectedRevisionToken": "9007199254740993", "rosterDraftIds": [] }';
 try{globalThis.fetch=async(_url,options)=>{seen.push({method:options?.method,body:options?.body,key:new Headers(options?.headers).get('Idempotency-Key')});return Response.json({id,revision:9007199254740994,revisionToken:'9007199254740994'});};await api.send({actor:'owner',resource:id,operation:'SAVE_INFO',path:`/api/v1/meetings/${id}/info`,key:'original-key',body});assert.deepEqual(seen,[{method:'PUT',body,key:'original-key'}]);
 await assert.rejects(api.send({actor:'owner',resource:id,operation:'SAVE_INFO',path:'/api/v1/meetings/other/info',key:'original-key',body}));assert.equal(seen.length,1);
 globalThis.fetch=async()=>Response.json({id:'00000000-0000-4000-8000-000000000000',revision:1,revisionToken:'1'});await assert.rejects(api.send({actor:'owner',resource:id,operation:'CONFIRM_ROSTER',path:`/api/v1/meetings/${id}/roster-confirmations`,key:'key',body:'{"expectedRevisionToken":"0"}'}));
 }finally{globalThis.fetch=old;}
});

test('unsupported command family is rejected before transport even with collection resource',async()=>{
 const old=fetch;let calls=0;const api=createMeetingInfoApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=>session.actor.enterpriseId);
 try{globalThis.fetch=async()=>{calls++;return Response.json({id,revision:0,revisionToken:'0'});};await assert.rejects(api.send({operation:'OTHER',actor:'owner',resource:'meetings',path:'/api/v1/meetings/meetings/roster-confirmations',key:'key',body:'{"expectedRevisionToken":"0"}'}));assert.equal(calls,0);}finally{globalThis.fetch=old;}
});

test('oversized stable identity is rejected before Unicode iterator allocation',()=>{
 const huge='汉'.repeat(400000),iterate=String.prototype[Symbol.iterator];let expanded=false;
 String.prototype[Symbol.iterator]=function(){if(this.toString()===huge){expanded=true;throw new Error('oversized iterator');}return iterate.call(this);};
 try{for(const field of ['category','rosterDraftIds']){expanded=false;const value={name:null,category:null,summary:null,occurredOn:null,rosterDraftIds:[],externalRosterNames:[],[field]:field==='category'?huge:[huge]};assert.throws(()=>saveInfoBody('0',value));assert.equal(expanded,false);}}finally{String.prototype[Symbol.iterator]=iterate;}
});

test('saved meeting requires HTTP 200 before accepting a valid-shaped body',async()=>{
 const old=fetch,api=createMeetingInfoApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=>session.actor.enterpriseId);
 try{globalThis.fetch=async()=>Response.json(saved,{status:202});await assert.rejects(api.saved(id,new AbortController().signal));}finally{globalThis.fetch=old;}
});
for(const [operation,resource,path,body,response,status] of [
 ['CREATE','meetings','/api/v1/meetings','{}',{id,revision:0,revisionToken:'0',stage:'ENTRY'},200],
 ['SAVE_INFO',id,`/api/v1/meetings/${id}/info`,'{"expectedRevisionToken":"0","rosterDraftIds":[]}',{id,revision:1,revisionToken:'1'},201],
 ['CONFIRM_ROSTER',id,`/api/v1/meetings/${id}/roster-confirmations`,'{"expectedRevisionToken":"0"}',{id,revision:1,revisionToken:'1'},202]
] as const)test(`${operation} rejects wrong HTTP success status even with valid result body`,async()=>{
 const old=fetch,api=createMeetingInfoApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=>session.actor.enterpriseId);
 try{globalThis.fetch=async()=>Response.json(response,{status});await assert.rejects(api.send({actor:'owner',operation,resource,path,key:'same-key',body}));}finally{globalThis.fetch=old;}
});
