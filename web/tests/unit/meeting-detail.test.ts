import {test} from 'node:test';import assert from 'node:assert/strict';import {shallowRef} from 'vue';
import {useSavedMeeting} from '../../src/features/meetings/useSavedMeeting.js';
import type {SavedMeeting} from '../../src/shared/api/meeting-info.js';
const id='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee',other='00000000-0000-4000-8000-000000000000';
const saved:SavedMeeting={id,revision:1,revisionToken:'1',accessRevision:0,accessRevisionToken:'0',stage:'ENTRY',name:'会议',category:null,summary:null,occurredOn:null,confirmedRosterId:null,confirmedRoster:[],rosterDraftId:null,rosterDraft:null};
test('saved detail clears on route/actor changes and ignores old responses',async()=>{
 const actor=shallowRef<string|null>('owner'),meeting=shallowRef(id);const calls:Array<{signal:AbortSignal;resolve:(v:SavedMeeting)=>void}>=[];
 const state=useSavedMeeting((_id,signal)=>new Promise(resolve=>calls.push({signal,resolve})),()=>actor.value,()=>meeting.value);const first=state.reload();meeting.value=other;assert.equal(calls[0]!.signal.aborted,true);const second=state.reload();calls[1]!.resolve({...saved,id:other});await second;calls[0]!.resolve(saved);await first;assert.equal(state.saved.value?.id,other);actor.value=null;assert.equal(state.saved.value,null);assert.equal(state.loading.value,false);state.dispose();
});
test('failed current authorization never leaves earlier saved contents on screen',async()=>{
 let fail=false;const state=useSavedMeeting(async()=>{if(fail)throw new Error('denied');return saved;},()=> 'owner',()=>id);await state.reload();assert.equal(state.saved.value?.name,'会议');fail=true;await state.reload();assert.equal(state.saved.value,null);assert.ok(state.error.value);state.dispose();
});
