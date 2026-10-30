import {test} from 'node:test';
import assert from 'node:assert/strict';
import {ref} from 'vue';
import {useMaterials, useKnowledgeMaterials} from '../../src/features/import/useMaterials';
import {createMaterialsApi} from '../../src/shared/api/materials';
import {createBusinessClient,type Session} from '../../src/shared/api/business-client';
import type {TabJournalContext} from '../../src/shared/composables/tab-journal';

const meeting='00000000-0000-4000-8000-000000000001',upload='00000000-0000-4000-8000-000000000002',file='00000000-0000-4000-8000-000000000003',id='00000000-0000-4000-8000-000000000004';
const session:Session={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'csrf',expiresAt:'2030-01-01T00:00:00Z'};
function context():TabJournalContext{const data=new Map<string,string>();return{tab:crypto.randomUUID(),recovery:'NEW_NAMESPACE',storage:{getItem:k=>data.get(k)??null,setItem:(k,v)=>{data.set(k,v);},removeItem:k=>{data.delete(k);}}};}
function api(actor:()=>string|null=()=> 'owner'){return createMaterialsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),actor);}
const tick=()=>new Promise(resolve=>setTimeout(resolve,0));

test('local materials use actual hashed bytes, part receipts and MATERIAL_SOURCE before association',async()=>{
 const original=fetch,bytes=new File(['real PDF bytes'],'背景.pdf'),sha=Buffer.from(await crypto.subtle.digest('SHA-256',await bytes.arrayBuffer())).toString('hex');
 let completed=false,added=false;const partReceipts:Array<{part:number;sizeBytes:number;sizeBytesToken:string;sha256:string}>=[],seen:string[]=[];
 const item={id,materialRevisionId:id,parsedDocumentId:id,fileId:file,name:bytes.name,source:'LOCAL',sourceVersion:null,sizeBytesToken:String(bytes.size),sha256:sha,state:'READY',error:null};
 globalThis.fetch=async(url,init)=>{
  const path=new URL(String(url)).pathname,method=init?.method??'GET';seen.push(method+' '+path);
  if(path.endsWith('/materials')){if(method==='POST'){assert.deepEqual(JSON.parse(String(init?.body)),{fileId:file});assert(completed);added=true;return Response.json(item,{status:201});}return Response.json({items:added?[item]:[],materialSnapshotId:added?id:null,materialsPending:false,meetingRevision:'1'});}
  if(path.endsWith('/uploads')){assert.deepEqual(JSON.parse(String(init?.body)),{purpose:'MATERIAL_SOURCE',name:bytes.name,size:bytes.size,sha256:sha});return Response.json({uploadId:upload,partSize:100,partSizeToken:'100'},{status:201});}
  if(path.endsWith('/parts/1')){assert.equal(await (init?.body as Blob).text(),'real PDF bytes');assert.equal(new Headers(init?.headers).get('X-Checksum-SHA256'),sha);partReceipts.push({part:1,sizeBytes:bytes.size,sizeBytesToken:String(bytes.size),sha256:sha});return new Response(null,{status:204});}
  if(path.endsWith('/parts'))return Response.json({items:partReceipts,nextCursor:null});
  if(path.endsWith('/completion')){assert.deepEqual(JSON.parse(String(init?.body)),{parts:[{part:1,sha256:sha}],sha256:sha});completed=true;return Response.json({fileId:file});}
  if(path.endsWith('/'+upload))return Response.json({uploadId:upload,purpose:'MATERIAL_SOURCE',displayName:bytes.name,state:completed?'COMPLETED':'OPEN',expectedSize:bytes.size,expectedSizeToken:String(bytes.size),expectedSha256:sha,createdAt:'2026-10-29T00:00:00Z',updatedAt:'2026-10-29T00:00:00Z',completedFileId:completed?file:null,accepted:{uploadId:upload,partSize:100,partSizeToken:'100'},allowedActions:completed?[]:['UPLOAD_PARTS','COMPLETE','ABORT'],idempotentRetries:completed?['COMPLETE','ABORT']:[],blockedReason:null,completed:completed?{fileId:file,uploadId:upload,displayName:bytes.name,sizeBytes:bytes.size,sizeBytesToken:String(bytes.size),sha256:sha,fileState:'AVAILABLE',originalPurpose:'MATERIAL_SOURCE',intakeEligibility:{state:'INELIGIBLE',reason:'SOURCE_PURPOSE'},businessReadEligibility:'NOT_EVALUATED'}:null});
  throw new Error('Unexpected route '+path);
 };
 const state=useMaterials(api(),()=> 'owner',()=>meeting,context());try{await state.reload();await state.addLocal([bytes]);assert.equal(state.items.value[0]?.id,id);assert.equal(state.state.value.blocked,false);assert.equal(state.jobs.value.length,0);assert(!seen.some(p=>p.includes('transcript-import')));}finally{state.dispose();globalThis.fetch=original;}
});

test('lost knowledge acknowledgement survives remount with same key/body and cannot be removed',async()=>{
 const original=fetch,journal=context(),version='a'.repeat(64),selection={id:'doc-1',libraryId:'lib-1',name:'真实文档',version,sha256:version,sizeBytesToken:'20'},requests:Array<{key:string|null;body:unknown}>=[];let lose=true;
 const item={id,materialRevisionId:id,parsedDocumentId:id,fileId:file,name:'真实文档',source:'KNOWLEDGE',sourceVersion:version,sizeBytesToken:'20',sha256:version,state:'READY',error:null};
 globalThis.fetch=async(url,init)=>{if(init?.method==='POST'){requests.push({key:new Headers(init.headers).get('Idempotency-Key'),body:init.body});if(lose)throw new TypeError('lost acknowledgement');return Response.json(item,{status:201});}return Response.json({items:lose?[]:[item],materialSnapshotId:lose?null:id,materialsPending:false,meetingRevision:'2'});};
 let state=useMaterials(api(),()=> 'owner',()=>meeting,journal);
 try{await state.reload();await state.addKnowledge([selection]);assert.equal(state.state.value.blocked,true);const key=state.jobs.value[0]!.id;await assert.rejects(state.removeJob(key));state.dispose();state=useMaterials(api(),()=> 'owner',()=>meeting,{...journal,recovery:'RESUME_NAMESPACE'});await state.reload();assert.equal(state.jobs.value.length,1);lose=false;await state.retryJob(key);assert.equal(state.jobs.value.length,0);assert.deepEqual(requests[1],requests[0]);assert.equal(state.items.value[0]?.sourceVersion,version);}finally{state.dispose();globalThis.fetch=original;}
});

test('actor and meeting invalidation immediately hide private material and ignore late reads',async()=>{
 const original=fetch,actor=ref<string|null>('owner'),scope=ref(meeting);let resolve!:(r:Response)=>void;
 globalThis.fetch=async()=>new Promise<Response>(r=>{resolve=r;});const state=useMaterials(api(()=>actor.value),()=>actor.value,()=>scope.value,context());
 try{const pending=state.reload();actor.value=null;resolve(Response.json({items:[],materialSnapshotId:null,materialsPending:false,meetingRevision:'1'}));await pending;assert.deepEqual(state.items.value,[]);assert.equal(state.ready.value,false);assert.equal(state.state.value.blocked,true);}finally{state.dispose();globalThis.fetch=original;}
});

test('knowledge picker searches real pages, retains selections across libraries and uses opaque cursors',async()=>{
 const original=fetch,version='b'.repeat(64);let queries:string[]=[];
 globalThis.fetch=async(url)=>{const u=new URL(String(url));queries.push(u.pathname+'?'+u.searchParams);if(u.pathname.endsWith('/material-libraries'))return Response.json({items:[{id:'lib-a',name:'产品库'},{id:'lib-b',name:'研发库'}],cursor:null});const library=u.pathname.includes('lib-a')?'lib-a':'lib-b';return Response.json({items:[{id:library+'-doc',libraryId:library,name:'真实资料',version,sha256:version,sizeBytesToken:'10'}],cursor:u.searchParams.has('cursor')?null:'opaque+/='});};
 const picker=useKnowledgeMaterials(api(),()=> 'owner',()=>meeting,context());
 try{await picker.open();picker.toggle(picker.documents.value[0]!);await picker.chooseLibrary('lib-b');picker.toggle(picker.documents.value[0]!);assert.equal(picker.selected.value.length,2);await picker.search('决策');assert(queries.some(q=>q.includes('search=%E5%86%B3%E7%AD%96')));await picker.moreDocuments();assert(queries.some(q=>q.includes('cursor=opaque%2B%2F%3D')));assert.equal(picker.selected.value[0]?.version,version);picker.settle([JSON.stringify(['lib-a','lib-a-doc',version])]);assert.equal(picker.selected.value[0]?.libraryId,'lib-b');}finally{picker.dispose();globalThis.fetch=original;}
});
