import {test} from 'node:test';
import assert from 'node:assert/strict';
import {shallowRef,watch} from 'vue';
import {useWorkspaceTasks} from '../../src/features/meetings/useWorkspaceTasks.js';
import type {WorkspaceTaskPage,WorkspaceTaskKind} from '../../src/shared/api/workspace-tasks.js';
const id='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee',other='00000000-0000-4000-8000-000000000000';
const empty:WorkspaceTaskPage={items:[],hasMore:false,nextCursor:null,verification:'PERSISTED_RELATION'};
const page:WorkspaceTaskPage={...empty,items:[{taskId:other,kind:'TRANSCRIPT_IMPORT_V1',state:'RUNNING',attemptToken:'1',businessRevisionToken:'9007199254740993',inputId:other,detailPath:`/api/v1/meetings/${id}/processing-tasks/${other}`}]};
function pending(){
 const calls:Array<{meeting:string,kind:WorkspaceTaskKind,cursor:string|null,signal:AbortSignal,resolve:(value:WorkspaceTaskPage)=>void,reject:(cause:unknown)=>void}>=[];
 const fetchPage=(meeting:string,kind:WorkspaceTaskKind,cursor:string|null,signal:AbortSignal)=>new Promise<WorkspaceTaskPage>((resolve,reject)=>calls.push({meeting,kind,cursor,signal,resolve,reject}));return {calls,fetchPage};
}
test('task scope change clears private observations and late old responses cannot overwrite the new meeting',async()=>{
 const actor=shallowRef<string|null>('owner'),meeting=shallowRef(id),{calls,fetchPage}=pending();
 const state=useWorkspaceTasks(fetchPage,()=>actor.value,()=>meeting.value);
 const a=state.reload();meeting.value=other;assert.equal(calls[0]!.signal.aborted,true);assert.equal(state.page.value,null);
 const b=state.reload();calls[1]!.resolve(empty);await b;calls[0]!.resolve(page);await a;assert.deepEqual(state.page.value,empty);
 actor.value=null;assert.equal(state.page.value,null);assert.equal(state.loading.value,false);await state.reload();assert.equal(calls.length,2);state.dispose();
});
test('task filter starts at first page, empty scanned pages retain continuation, refresh repeats the exact cursor',async()=>{
 const seen:unknown[]=[];const state=useWorkspaceTasks(async(meeting,kind,cursor)=>{seen.push([meeting,kind,cursor]);return cursor?empty:{...empty,nextCursor:'opaque',hasMore:true};},()=> 'owner',()=>id);
 await state.reload();assert.equal(state.page.value?.items.length,0);await state.next();await state.reload();await state.selectKind('MINUTES_GENERATE_V1');
 assert.deepEqual(seen,[[id,'ALL',null],[id,'ALL','opaque'],[id,'ALL','opaque'],[id,'MINUTES_GENERATE_V1',null]]);await state.first();assert.equal(state.page.value?.hasMore,true);state.dispose();
});
test('new task reads clear prior data and denial never leaves old rows visible',async()=>{
 const {calls,fetchPage}=pending(),state=useWorkspaceTasks(fetchPage,()=> 'owner',()=>id);
 const a=state.reload();calls[0]!.resolve(page);await a;assert.equal(state.page.value?.items[0]?.businessRevisionToken,'9007199254740993');
 const b=state.reload();assert.equal(state.page.value,null);calls[1]!.reject(new Error('denied'));await b;assert.equal(state.page.value,null);assert.ok(state.error.value);assert.equal(state.loading.value,false);state.dispose();
});
test('synchronous scope change on loading suppresses old transport and does not reset a newer read',async()=>{
 const actor=shallowRef<string|null>('old'),{calls,fetchPage}=pending();const state=useWorkspaceTasks(fetchPage,()=>actor.value,()=>id);let newer:Promise<void>|undefined;
 const stop=watch(state.loading,value=>{if(value&&actor.value==='old'){actor.value='new';newer=state.reload();}},{flush:'sync'});
 await state.reload();assert.equal(calls.length,1);assert.equal(state.loading.value,true);calls[0]!.resolve(empty);await newer;assert.deepEqual(state.page.value,empty);assert.equal(state.loading.value,false);stop();state.dispose();
});
test('synchronous page observer can invalidate scope without stale finally touching the new request',async()=>{
 const meeting=shallowRef(id),{calls,fetchPage}=pending(),state=useWorkspaceTasks(fetchPage,()=> 'owner',()=>meeting.value);let newer:Promise<void>|undefined;
 const stop=watch(state.page,value=>{if(value&&meeting.value===id){meeting.value=other;newer=state.reload();}},{flush:'sync'});
 const a=state.reload();calls[0]!.resolve(page);await a;assert.equal(state.page.value,null);assert.equal(state.loading.value,true);assert.equal(calls[1]!.meeting,other);calls[1]!.resolve(empty);await newer;assert.deepEqual(state.page.value,empty);stop();state.dispose();
});
test('disposal aborts pending tasks and prevents further reads; invalid filter cannot change scope',async()=>{
 const {calls,fetchPage}=pending(),state=useWorkspaceTasks(fetchPage,()=> 'owner',()=>id);
 await assert.rejects(state.selectKind('UNKNOWN' as WorkspaceTaskKind));assert.equal(state.kind.value,'ALL');assert.equal(calls.length,0);
 const a=state.reload();state.dispose();assert.equal(calls[0]!.signal.aborted,true);calls[0]!.resolve(page);await a;await state.reload();assert.equal(calls.length,1);assert.equal(state.page.value,null);assert.equal(state.loading.value,false);
});

test('retry after a failed continuation repeats that continuation instead of silently restarting',async()=>{
 const seen:Array<string|null>=[];let fail=true;const state=useWorkspaceTasks(async(_meeting,_kind,cursor)=>{seen.push(cursor);if(cursor&&fail)throw new Error('temporary');return {...empty,nextCursor:cursor?null:'next',hasMore:!cursor};},()=> 'owner',()=>id);
 await state.reload();await state.next();assert.ok(state.error.value);fail=false;await state.reload();assert.deepEqual(seen,[null,'next','next']);state.dispose();
});
test('filter invalidation cannot overwrite a synchronous newer filter selection',async()=>{
 const {calls,fetchPage}=pending(),state=useWorkspaceTasks(fetchPage,()=> 'owner',()=>id);const first=state.reload();calls[0]!.resolve(page);await first;let newer:Promise<void>|undefined,once=false;
 const stop=watch(state.page,value=>{if(value===null&&!once){once=true;newer=state.selectKind('KNOWLEDGE_PUBLISH_V1');}},{flush:'sync'});
 const older=state.selectKind('MINUTES_GENERATE_V1');assert.equal(state.kind.value,'KNOWLEDGE_PUBLISH_V1');assert.equal(calls.length,2);assert.equal(calls[1]!.signal.aborted,false);calls[1]!.resolve(empty);await older;await newer;assert.deepEqual(state.page.value,empty);stop();state.dispose();
});
