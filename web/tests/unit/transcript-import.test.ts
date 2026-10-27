import {test} from 'node:test';import assert from 'node:assert/strict';
import {createTranscriptImportApi} from '../../src/shared/api/transcript-import.js';
import {createBusinessClient,type Session} from '../../src/shared/api/business-client.js';
const id='00000000-0000-4000-8000-000000000001',file='00000000-0000-4000-8000-000000000002';
const session:Session={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'csrf',expiresAt:'2099-01-01T00:00:00Z'};
test('transcript intake sends original key ordered files and lossless token; acceptance remains task-only',async()=>{
 const old=fetch,seen:Array<{url:string;body:unknown;key:string|null}>=[],api=createTranscriptImportApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');
 const command={actor:'owner',operation:'TRANSCRIPT_IMPORT',resource:id,path:`/api/v1/meetings/${id}/transcript-imports`,key:'original',body:JSON.stringify({expectedRevisionToken:'9007199254740993',orderedFileIds:[file]})};
 try{globalThis.fetch=async(url,options)=>{seen.push({url:String(url),body:options?.body,key:new Headers(options?.headers).get('Idempotency-Key')});return Response.json({meetingId:id,batchId:id,inputId:id,taskId:id,revision:9007199254740994,revisionToken:'9007199254740994',inputFingerprint:'a'.repeat(64)},{status:202});};const ack=await api.send(command);assert.equal(ack.taskId,id);assert.equal(ack.revisionToken,'9007199254740994');assert.equal(seen[0]!.body,command.body);assert.equal(seen[0]!.key,'original');
 await assert.rejects(api.send({...command,body:JSON.stringify({expectedRevisionToken:'1',orderedFileIds:[file,file]})}));assert.equal(seen.length,1);
 globalThis.fetch=async()=>Response.json({meetingId:file,batchId:id,inputId:id,taskId:id,revision:2,revisionToken:'2',inputFingerprint:'a'.repeat(64)},{status:202});await assert.rejects(api.send(command));
 }finally{globalThis.fetch=old;}
});
test('intake capabilities retain unavailable execution and current string revision',async()=>{
 const old=fetch,api=createTranscriptImportApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');
 try{globalThis.fetch=async url=>{assert.match(String(url),/processing-capabilities$/);return Response.json({revision:1,revisionToken:'9007199254740993',accessRevision:0,accessRevisionToken:'0',canSubmit:true,readOnlyReason:null,intake:'AVAILABLE',execution:'NOT_INSTALLED',businessReady:false,formats:['TXT'],purpose:'TRANSCRIPT_INPUT',materialMode:'EMPTY',budgets:{maxFiles:4}});};const value=await api.capabilities(id,new AbortController().signal);assert.equal(value.businessReady,false);assert.equal(value.execution,'NOT_INSTALLED');assert.equal(value.revisionToken,'9007199254740993');
 }finally{globalThis.fetch=old;}
});
