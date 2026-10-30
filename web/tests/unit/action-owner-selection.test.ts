import {test} from 'node:test';import assert from 'node:assert/strict';import {ref,watch} from 'vue';import {useActionOwnerSelection} from '../../src/features/minutes/useActionOwnerSelection.js';
const person={stableId:'member',displayName:'参会人',department:null,active:true};
test('only a returned active Person can be selected; saved-version and actor changes clear it and ignore late reads',async()=>{
 const actor=ref<string|null>('owner'),revision=ref('1'),enabled=ref(true);let resolve!:(v:any)=>void;const signals:AbortSignal[]=[];
 const state=useActionOwnerSelection((_q,_c,signal)=>{signals.push(signal);return new Promise(yes=>resolve=yes);},()=>actor.value,()=>['meeting','element',revision.value],()=>enabled.value);
 const read=state.search('member');resolve({items:[person,{...person,stableId:'inactive',active:false}],nextCursor:null});await read;
 assert.throws(()=>state.choose({...person}));assert.throws(()=>state.choose(state.people.value[1]!));state.choose(state.people.value[0]!);assert.equal(state.selected.value,person);
 revision.value='2';assert.equal(state.selected.value,null);assert.deepEqual(state.people.value,[]);
 const late=state.search('member');actor.value='other';assert.equal(signals.at(-1)?.aborted,true);resolve({items:[person],nextCursor:null});await late;assert.deepEqual(state.people.value,[]);
 enabled.value=false;await state.search('member');assert.equal(signals.length,2);assert.throws(()=>state.choose(person));state.dispose();
});
test('directory failure clears previous people and pagination retains the original searched query',async()=>{
 const calls:any[]=[];let fail=false;const state=useActionOwnerSelection(async(q,c,s)=>{calls.push([q,c,s]);if(fail)throw Error('unavailable');return{items:[person],nextCursor:'next'};},()=> 'owner',()=>['meeting','element','1'],()=>true);
 await state.search('member');state.choose(person);await state.search('member',true);assert.deepEqual(calls.map(x=>x.slice(0,2)),[['member',null],['member','next']]);await state.search('changed',true);assert.deepEqual(calls.at(-1).slice(0,2),['changed',null]);fail=true;await state.search('member');assert.equal(state.selected.value,null);assert.deepEqual(state.people.value,[]);assert.ok(state.error.value);state.dispose();
});

 test('synchronous busy observer cannot send a directory request from a replaced identity scope',async()=>{
 const actor=ref<string|null>('owner');let calls=0;const state=useActionOwnerSelection(async()=>{calls++;return{items:[person],nextCursor:null};},()=>actor.value,()=>['meeting','element','1'],()=>true);const stop=watch(state.busy,busy=>{if(busy)actor.value='replacement';},{flush:'sync'});await state.search('private query');assert.equal(calls,0);assert.equal(state.busy.value,false);assert.deepEqual(state.people.value,[]);stop();state.dispose();
});
