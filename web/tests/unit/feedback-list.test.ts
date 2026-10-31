import {test} from 'node:test';import assert from 'node:assert/strict';
import {useFeedbackList} from '../../src/features/meetings/useFeedbackList.js';
const row=(comment:string)=>({id:'00000000-0000-4000-8000-000000000001',meetingId:'00000000-0000-4000-8000-000000000002',category:'WORDING',comment,state:'PENDING' as const,revisionToken:'0'});
test('late feedback from the previous actor cannot replace current actor data',async()=>{
 let actor:string|null='a',release!:(v:any)=>void;const list=useFeedbackList({list:async()=>actor==='a'?new Promise(resolve=>{release=resolve;}):{items:[row('B')],nextCursor:null}},()=>actor);
 const old=list.load();actor='b';list.invalidate();await list.load();release({items:[row('A')],nextCursor:null});await old;assert.equal(list.items.value[0]?.comment,'B');
 actor=null;list.invalidate();assert.deepEqual(list.items.value,[]);assert.equal(list.loading.value,false);
});
test('feedback pagination forwards the original opaque cursor and scope disposal aborts the request',async()=>{
 const requests:any[]=[];let release!:(v:any)=>void;const list=useFeedbackList({list:async(cursor,signal)=>{requests.push({cursor,signal});return new Promise(resolve=>{release=resolve;});}},()=> 'owner');
 const pending=list.load('opaque');list.invalidate();assert.equal(requests[0].cursor,'opaque');assert.equal(requests[0].signal.aborted,true);release({items:[row('old')],nextCursor:'next'});await pending;assert.deepEqual(list.items.value,[]);assert.equal(list.next.value,null);
});
