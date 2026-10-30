import {test} from 'node:test';import assert from 'node:assert/strict';
import {createTranscriptsApi} from '../../src/shared/api/transcripts.js';
import {createBusinessClient,type Session} from '../../src/shared/api/business-client.js';
const id='00000000-0000-4000-8000-000000000001',revision='00000000-0000-4000-8000-000000000002',segment='00000000-0000-4000-8000-000000000003',file='00000000-0000-4000-8000-000000000004';
const session:Session={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'csrf',expiresAt:'2099-01-01T00:00:00Z'};
const observation={meetingRevision:9007199254740992,revisionToken:'9007199254740993',accessRevision:0,accessRevisionToken:'0',currentSavedRevisionId:revision,currentConfirmationId:null};
const current={meetingId:id,observation,storedInspection:'AVAILABLE',sourceBytes:[{fileId:file,eligibility:'ELIGIBLE',reason:'AVAILABLE'}],correction:{mode:'DISABLED_NOT_INSTALLED',readiness:'NOT_APPLICABLE',qualificationId:'controlled'},capabilities:{canReadHistory:false,canSave:false,canRestore:false,canReadSources:true,canConfirm:false},needsGeneration:false,confirmation:null,sources:[{fileId:file,sha256:'a'.repeat(64),sizeBytes:1,sizeBytesToken:'1',ordinal:0}],saved:{id:revision,kind:'WORKING',revision:1,revisionToken:'1',segments:[{segmentId:segment,ordinal:0,body:'原文 😀',sourceFileId:file,sourceParagraph:0,startMs:null,endMs:null,speakerId:null,marks:[]}]}};
test('transcript current accepts saved-only member data and rejects foreign pointers or duplicate segments',async()=>{
 const old=fetch,api=createTranscriptsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');
 try{globalThis.fetch=async()=>Response.json(current);const value=await api.current(id,new AbortController().signal);assert.equal(value.saved?.segments[0]?.body,'原文 😀');assert.equal(value.observation.revisionToken,'9007199254740993');assert.equal('createdBy' in value.saved!,false);
 globalThis.fetch=async()=>Response.json({...current,observation:{...observation,currentSavedRevisionId:file}});await assert.rejects(api.current(id,new AbortController().signal));
 globalThis.fetch=async()=>Response.json({...current,saved:{...current.saved,segments:[...current.saved.segments,...current.saved.segments]}});await assert.rejects(api.current(id,new AbortController().signal));
 }finally{globalThis.fetch=old;}
});
test('transcript save uses exact full ordered segments and preserves historical ACK versus current observation',async()=>{
 const old=fetch,api=createTranscriptsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner'),body=JSON.stringify({expectedRevisionToken:'9007199254740993',sourceRevisionId:revision,segments:[{segmentId:segment,body:'人工修改',marks:['KEY']}]});
 try{globalThis.fetch=async(url,options)=>{assert.equal(String(url),`https://ama.test/api/v1/meetings/${id}/transcript`);assert.equal(options?.method,'PUT');assert.equal(options?.body,body);return Response.json({result:{kind:'SAVED_REVISION',meetingId:id,revisionId:revision,historyRevision:2,historyRevisionToken:'2'},replayed:true,resultIsCurrent:false,observation:{...observation,revisionToken:'9007199254740999'}});};const result=await api.send({actor:'owner',resource:id,operation:'TRANSCRIPT_SAVE',path:`/api/v1/meetings/${id}/transcript`,key:'original',body});assert.equal(result.resultIsCurrent,false);assert.equal(result.observation.revisionToken,'9007199254740999');assert.equal(result.result.kind,'SAVED_REVISION');
 }finally{globalThis.fetch=old;}
});

test('current accepts actual empty-owner and profile-bearing member producer envelopes',async()=>{
 const old=fetch,api=createTranscriptsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');try{
 globalThis.fetch=async()=>Response.json({...current,saved:null,observation:{...observation,currentSavedRevisionId:null},sources:[],sourceBytes:[],selectedConfirmation:null,capabilities:{...current.capabilities,canReadHistory:true}});assert.equal((await api.current(id,new AbortController().signal)).saved,null);
 globalThis.fetch=async()=>Response.json({...current,parserVersion:'txt.v1',processingConfigVersion:'v1',confirmationHashProfile:'ama.transcript-confirmation.v1'});assert.equal((await api.current(id,new AbortController().signal)).saved?.id,revision);
 }finally{globalThis.fetch=old;}
});

test('media source route uses exact validated file revision and original byte hash',async()=>{
 const old=fetch;let calls=0;const bytes=new TextEncoder().encode('RIFF0000WAVE real bytes'),sha=Buffer.from(await crypto.subtle.digest('SHA-256',bytes)).toString('hex');const api=createTranscriptsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');
 try{globalThis.fetch=async path=>{calls++;assert.equal(String(path),`https://ama.test/api/v1/meetings/${id}/transcript/revisions/${revision}/sources/${file}`);return new Response(bytes);};const value=await api.source(id,{fileId:file,sha256:sha,sizeBytes:bytes.length,revisionId:revision},new AbortController().signal);assert.equal(value.blob.size,bytes.length);assert.equal(value.blob.type,'audio/wav');await assert.rejects(api.source(id,{fileId:file,sha256:'0'.repeat(64),sizeBytes:bytes.length,revisionId:revision},new AbortController().signal));const before=calls;await assert.rejects(api.source(id,{fileId:'../foreign',sha256:sha,sizeBytes:bytes.length,revisionId:revision},new AbortController().signal));assert.equal(calls,before);
 }finally{globalThis.fetch=old;}
});

test('real declared transcript root conflicts are known rejections instead of uncloseable uncertainty',async()=>{
 const old=fetch,api=createTranscriptsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner'),body=JSON.stringify({expectedRevisionToken:'1',sourceRevisionId:revision,segments:[{segmentId:segment,body:'local draft',marks:[]}]});
 try{globalThis.fetch=async()=>Response.json({error:{code:'REVISION_CONFLICT',message:'private database details',traceId:'00000000-0000-4000-8000-000000000001'}},{status:409});await assert.rejects(api.send({actor:'owner',resource:id,operation:'TRANSCRIPT_SAVE',path:`/api/v1/meetings/${id}/transcript`,key:'known-conflict',body}),(e:any)=>e.kind==='http'&&e.status===409&&e.code==='REVISION_CONFLICT'&&!e.message.includes('database'));}finally{globalThis.fetch=old;}
});
test('a transcript rejection contract never blesses malformed errors or a mismatched status',async()=>{
 const old=fetch,api=createTranscriptsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner'),body=JSON.stringify({expectedRevisionToken:'1',sourceRevisionId:revision,segments:[{segmentId:segment,body:'local draft',marks:[]}]}),command={actor:'owner',resource:id,operation:'TRANSCRIPT_SAVE',path:`/api/v1/meetings/${id}/transcript`,key:'original',body};
 try{for(const [status,error] of [[500,{code:'REVISION_CONFLICT',message:'secret',traceId:'00000000-0000-4000-8000-000000000001'}],[409,{code:'REVISION_CONFLICT',message:'secret',traceId:'not-a-trace'}],[409,{code:'UNKNOWN_TRANSCRIPT_CONFLICT',message:'secret',traceId:'00000000-0000-4000-8000-000000000001'}],[409,{code:'REVISION_CONFLICT',message:'secret',traceId:'00000000-0000-4000-8000-000000000001',extra:true}] ] as const){globalThis.fetch=async()=>Response.json({error},{status});await assert.rejects(api.send(command),(e:any)=>e.kind==='response'&&!e.code&&!e.message.includes('secret'));}}finally{globalThis.fetch=old;}
});
