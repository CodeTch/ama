import {test} from 'node:test';
import assert from 'node:assert/strict';
import {useEditorIntent} from '../../src/shared/composables/useEditorIntent.js';
import {ApiError} from '../../src/shared/api/client.js';
const meetingId='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee';const session={};const scope={actor:'owner',meetingId,session};
const ack={result:{kind:'EDIT_INTENT' as const,meetingId},replayed:false,current:{meetingId,state:'KNOWN' as const,intentEpochToken:'9007199254740993',observation:{revision:0,revisionToken:'0',accessRevision:0,accessRevisionToken:'0',currentConfirmationId:null,currentMinutesRevisionId:null}}};
test('only this instance positive POST ACK enables edits; pending input and composition are blocked',async()=>{
 let release:(x:unknown)=>void=()=>{};const calls:string[]=[];const a=useEditorIntent(()=>scope,()=>false,async r=>{calls.push(r.body);return new Promise(resolve=>{release=resolve;});},()=>crypto.randomUUID());const b=useEditorIntent(()=>scope,()=>false,async()=>ack,()=>crypto.randomUUID());
 const pending=a.request('9007199254740993');assert.equal(a.canEdit.value,false);const event=new Event('beforeinput',{cancelable:true});a.guardInput(event);assert.equal(event.defaultPrevented,true);assert.equal(a.beginComposition(),false);release(ack);await pending;assert.equal(a.canEdit.value,true);assert.equal(b.canEdit.value,false);assert.deepEqual(JSON.parse(calls[0]!),{expectedRevisionToken:'9007199254740993'});assert.equal(a.beginComposition(),true);assert.equal(a.canAutosave.value,false);a.endComposition();assert.equal(a.canAutosave.value,true);
});
test('lost ACK retries exact key/body; changed token cannot replace unresolved command',async()=>{
 let calls=0;const sent:unknown[]=[];const a=useEditorIntent(()=>scope,()=>false,async r=>{sent.push(r);if(++calls===1)throw new ApiError('network',0,'lost');return ack;},()=>crypto.randomUUID());await assert.rejects(a.request('9223372036854775806'));assert.equal(a.canEdit.value,false);await assert.rejects(a.request('0'));await a.request('9223372036854775806');assert.equal(sent.length,2);assert.deepEqual(sent[0],sent[1]);assert.equal(a.canEdit.value,true);
});
test('session/meeting changes, frozen server state and invalid ACK cannot confer permission',async()=>{
 let current=scope,frozen=false;const a=useEditorIntent(()=>current,()=>frozen,async()=>ack,()=>crypto.randomUUID());await a.request('0');assert.equal(a.canEdit.value,true);frozen=true;assert.equal(a.canEdit.value,false);a.invalidate();frozen=false;assert.equal(a.canEdit.value,false);await a.request('1');current={...scope,session:{}};assert.equal(a.canEdit.value,false);
 const bad=useEditorIntent(()=>scope,()=>false,async()=>({...ack,result:{...ack.result,meetingId:'other'}}),()=>crypto.randomUUID());await assert.rejects(bad.request('0'));assert.equal(bad.canEdit.value,false);
});

test('observing server freeze permanently invalidates prior ACK even if later state is editable',async()=>{
 let frozen=false;const a=useEditorIntent(()=>scope,()=>frozen,async()=>ack,()=>crypto.randomUUID());await a.request('0');frozen=true;assert.equal(a.canEdit.value,false);frozen=false;assert.equal(a.canEdit.value,false);
});

test('Vue subscribers observe positive ACK and invalidation without another state read',async()=>{
 const {watchEffect,nextTick}=await import('vue');const a=useEditorIntent(()=>scope,()=>false,async()=>ack,()=>crypto.randomUUID());const seen:boolean[]=[];const stop=watchEffect(()=>{seen.push(a.canEdit.value);});try{await a.request('0');await nextTick();assert.deepEqual(seen,[false,true]);a.invalidate();await nextTick();assert.deepEqual(seen,[false,true,false]);}finally{stop();}
});

test('freeze observed during pending intent prevents its late ACK from granting authority',async()=>{
 let frozen=false,release:(x:unknown)=>void=()=>{};const a=useEditorIntent(()=>scope,()=>frozen,()=>new Promise(resolve=>{release=resolve;}),()=>crypto.randomUUID());const pending=a.request('0');frozen=true;assert.equal(a.canEdit.value,false);frozen=false;release(ack);await assert.rejects(pending,{name:'AbortError'});assert.equal(a.canEdit.value,false);
});
test('incomplete positive intent response remains uncertain and locked',async()=>{
 for(const response of [{result:ack.result,replayed:false},{...ack,current:{...ack.current,observation:{}}},{...ack,current:{...ack.current,intentEpochToken:null}}]){
 const a=useEditorIntent(()=>scope,()=>false,async()=>response,()=>crypto.randomUUID());await assert.rejects(a.request('0'));assert.equal(a.canEdit.value,false);assert.equal(a.state.value,'UNCERTAIN');}
});

test('request entry observing freeze also invalidates an older pending intent',async()=>{
 let frozen=false,release:(x:unknown)=>void=()=>{};const a=useEditorIntent(()=>scope,()=>frozen,()=>new Promise(resolve=>{release=resolve;}),()=>crypto.randomUUID());const pending=a.request('0');frozen=true;await assert.rejects(a.request('0'));frozen=false;release(ack);await assert.rejects(pending,{name:'AbortError'});assert.equal(a.canEdit.value,false);
});

test('a second waiter cannot reauthorize the old POST after freeze was observed',async()=>{
 let frozen=false,release:(x:unknown)=>void=()=>{},calls=0;const a=useEditorIntent(()=>scope,()=>frozen,()=>{calls++;return new Promise(resolve=>{release=resolve;});},()=>crypto.randomUUID());const first=a.request('0');frozen=true;assert.equal(a.canEdit.value,false);frozen=false;const second=a.request('0');release(ack);await assert.rejects(first,{name:'AbortError'});await assert.rejects(second,{name:'AbortError'});assert.equal(calls,1);assert.equal(a.canEdit.value,false);
});
