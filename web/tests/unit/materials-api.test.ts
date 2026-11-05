import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createMaterialsApi} from '../../src/shared/api/materials';
import {createBusinessClient, type Session} from '../../src/shared/api/business-client';

const meeting='00000000-0000-4000-8000-000000000001',file='00000000-0000-4000-8000-000000000002',material='00000000-0000-4000-8000-000000000003',hash='a'.repeat(64);
const session:Session={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'csrf',expiresAt:'2030-01-01T00:00:00Z'};
const item={id:material,materialRevisionId:meeting,parsedDocumentId:file,fileId:file,name:'材料.pdf',source:'LOCAL',sourceVersion:null,sizeBytesToken:'3',sha256:hash,state:'READY',error:null};
const api=()=>createMaterialsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');

test('material commands keep exact bytes and keys; background begin cannot become a transcript input',async()=>{
 const original=fetch,a=api(),body=`{ "fileId": "${file}" }`,key=crypto.randomUUID();
 try{
  globalThis.fetch=async(url,init)=>{assert.equal(String(url),`https://ama.test/api/v1/meetings/${meeting}/materials`);assert.equal(init?.body,body);assert.equal(new Headers(init?.headers).get('Idempotency-Key'),key);assert.equal(new Headers(init?.headers).get('X-CSRF-Token'),'csrf');return Response.json(item,{status:201});};
  assert.equal((await a.send({actor:'owner',resource:meeting,operation:'MATERIAL_ADD',path:`/api/v1/meetings/${meeting}/materials`,key,body})).id,material);
  globalThis.fetch=async(_url,init)=>{assert.equal(JSON.parse(String(init?.body)).purpose,'MATERIAL_SOURCE');return Response.json({uploadId:file,partSize:3,partSizeToken:'3'},{status:201});};
  const begin={actor:'owner',resource:meeting,operation:'UPLOAD_BEGIN',path:`/api/v1/meetings/${meeting}/uploads`,key,body:JSON.stringify({purpose:'MATERIAL_SOURCE',name:'背景.pdf',size:3,sha256:hash})};
  assert.equal((await a.begin(begin)).uploadId,file);
  await assert.rejects(a.begin({...begin,body:begin.body.replace('MATERIAL_SOURCE','TRANSCRIPT_INPUT')}));
 }finally{globalThis.fetch=original;}
});

test('material listing rejects contradictory readiness and preserves opaque numeric tokens',async()=>{
 const original=fetch,a=api();try{
  const listing={items:[item],materialSnapshotId:meeting,materialsPending:false,meetingRevision:'9223372036854775806'};
  globalThis.fetch=async()=>Response.json(listing);assert.equal((await a.list(meeting,new AbortController().signal)).meetingRevision,'9223372036854775806');
  for(const invalid of [{...listing,items:[item,item]},{...listing,materialsPending:true},{...listing,items:[{...item,state:'FAILED',error:'BAD_FILE'}]},{...listing,items:[{...item,sha256:'bad'}]}]){globalThis.fetch=async()=>Response.json(invalid);await assert.rejects(a.list(meeting,new AbortController().signal));}
 }finally{globalThis.fetch=original;}
});

test('knowledge pages bind meeting, escaped library, search and opaque cursor; retain exact provider version',async()=>{
 const original=fetch,a=api(),version='b'.repeat(64),document={id:'document-1',libraryId:'library-1',name:'真实文档',version,sha256:version,sizeBytesToken:'42'};
 try{
  globalThis.fetch=async(url)=>{const u=new URL(String(url));assert.equal(u.searchParams.get('meetingId'),meeting);assert.equal(u.searchParams.get('search'),'风险 & 决策');assert.equal(u.searchParams.get('cursor'),'opaque+/=');return Response.json({items:[document],cursor:null});};
  const page=await a.documents(meeting,'library-1','风险 & 决策','opaque+/=',new AbortController().signal);assert.equal(page.items[0]?.version,version);
  globalThis.fetch=async()=>Response.json({items:[{...document,libraryId:'other'}],cursor:null});await assert.rejects(a.documents(meeting,'library-1','',null,new AbortController().signal));
 }finally{globalThis.fetch=original;}
});

test('batch receipts can defer a snapshot at the original revision and must match every known source',async()=>{
 const original=fetch,a=api();
 const selection={libraryId:'library',documentId:'document',version:hash};
 const command={actor:'owner',resource:meeting,key:crypto.randomUUID(),operation:'MATERIAL_BATCH',path:`/api/v1/meetings/${meeting}/materials/batches`,body:JSON.stringify({expectedRevisionToken:'8',items:[selection]})};
 const bound={...item,source:'KNOWLEDGE',sourceVersion:hash,sourceLibraryId:'library',sourceDocumentId:'document'};
 try{
  for(const revision of ['8','9']){
   globalThis.fetch=async()=>Response.json({items:[bound],materialSnapshotId:meeting,meetingRevision:revision},{status:201});
   assert.equal((await a.batch(command)).meetingRevision,revision);
  }
  for(const [revision,row] of [['7',bound],['10',bound],['8',{...bound,sourceDocumentId:'different-document'}]]){
   globalThis.fetch=async()=>Response.json({items:[row],materialSnapshotId:meeting,meetingRevision:revision},{status:201});await assert.rejects(a.batch(command));
  }
 }finally{globalThis.fetch=original;}
});
