import {test} from 'node:test';
import assert from 'node:assert/strict';
import {request,ApiError} from '../../src/shared/api/client.js';
import {useEditorIntent} from '../../src/shared/composables/useEditorIntent.js';
const meeting='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee',path=`https://ama.test/api/v1/meetings/${meeting}/minutes/editor-intents`;
const current={meetingId:meeting,state:'KNOWN',intentEpochToken:'1',observation:{revision:9007199254740992,revisionToken:'9007199254740993',accessRevision:0,accessRevisionToken:'0',currentConfirmationId:null,currentMinutesRevisionId:null}};
test('only route-specific complete stale intent409 becomes a reconcilable conflict',async()=>{
 const original=globalThis.fetch;globalThis.fetch=async()=>Response.json({error:{code:'REVISION_CONFLICT'},current},{status:409});
 try{const a=useEditorIntent(()=>({actor:'owner',meetingId:meeting,session:original}),()=>false,r=>request(path,{method:'POST',body:r.body,headers:{'Idempotency-Key':r.key},errorContract:'saved-intent'}));await assert.rejects(a.request('0'),(e:unknown)=>e instanceof ApiError&&e.kind==='http'&&e.code==='REVISION_CONFLICT');assert.equal(a.state.value,'CONFLICT');assert.equal(a.canEdit.value,false);a.reconcileConflict();assert.equal(a.state.value,'EMPTY');
 await assert.rejects(request(path,{method:'POST'}),(e:unknown)=>e instanceof ApiError&&e.kind==='response');
 }finally{globalThis.fetch=original;}
});
test('incomplete or foreign stale projection remains uncertain and contract cannot be used on another route',async()=>{
 const original=globalThis.fetch;let calls=0;
 try{for(const projection of [{...current,meetingId:'other'},{...current,observation:{}},undefined]){globalThis.fetch=async()=>{calls++;return Response.json({error:{code:'REVISION_CONFLICT'},current:projection},{status:409});};await assert.rejects(request(path,{method:'POST',errorContract:'saved-intent'}),(e:unknown)=>e instanceof ApiError&&e.kind==='response');}
 const before=calls;await assert.rejects(request('https://ama.test/api/v1/meetings',{method:'POST',errorContract:'saved-intent'}),TypeError);assert.equal(calls,before);
 }finally{globalThis.fetch=original;}
});
