import {test} from 'node:test';import assert from 'node:assert/strict';import {shallowRef,watch} from 'vue';
import {useMeetingInfoEditor} from '../../src/features/meetings/useMeetingInfoEditor.js';
import {ApiError} from '../../src/shared/api/client.js';
import type {SavedMeeting,MeetingMutation,InfoFields} from '../../src/shared/api/meeting-info.js';
import type {CommandSnapshot} from '../../src/shared/composables/useCommand.js';
const id='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee',other='00000000-0000-4000-8000-000000000000';
const saved:SavedMeeting={id,revision:9007199254740992,revisionToken:'9007199254740993',accessRevision:0,accessRevisionToken:'0',stage:'ENTRY',name:'original',category:null,summary:null,occurredOn:null,confirmedRosterId:id,confirmedRoster:[{memberId:other,memberKind:'ENTERPRISE',enterpriseId:'member',displayName:'成员'}],rosterDraftId:null,rosterDraft:null};
function fields(name:string):InfoFields{return {name,category:null,summary:'保留 😀',occurredOn:null,rosterDraftIds:['member'],externalRosterNames:[]};}
test('information save freezes token-only full fields; later keystrokes survive its ACK and readback',async()=>{
 const seen:CommandSnapshot[]=[];let accepted!:(v:MeetingMutation)=>void,current=saved;const editor=useMeetingInfoEditor({saved:async()=>current,send:request=>{seen.push(request);return new Promise(resolve=>{accepted=resolve;});}},()=> 'owner',()=>id,()=>true);
 await editor.reload();assert.deepEqual(editor.draft.value?.value.value.rosterDraftIds,['member']);editor.edit(fields('first'));const send=editor.save();editor.edit(fields('later'));current={...saved,name:'first',summary:'保留 😀',revisionToken:'9007199254740994'};accepted({id,revision:9007199254740994,revisionToken:'9007199254740994'});await send;
 assert.deepEqual(JSON.parse(seen[0]!.body),{expectedRevisionToken:'9007199254740993',...fields('first')});assert.equal(editor.draft.value?.value.value.name,'later');assert.equal(editor.draft.value?.dirty.value,true);assert.equal(editor.draft.value?.conflict.value,null);assert.equal(editor.draft.value?.baseToken.value,'9007199254740994');editor.dispose();
});
test('uncertain save retries original bytes while retaining subsequently edited draft',async()=>{
 const seen:CommandSnapshot[]=[];let lost=true,current=saved;const editor=useMeetingInfoEditor({saved:async()=>current,send:async request=>{seen.push(request);if(lost)throw new Error('lost');current={...saved,name:'first',revisionToken:'9007199254740994'};return {id,revision:1,revisionToken:current.revisionToken};}},()=> 'owner',()=>id,()=>true);
 await editor.reload();editor.edit(fields('first'));await editor.save();editor.edit(fields('later'));assert.equal(editor.command.state.value,'UNCERTAIN');await editor.reload();lost=false;await editor.save();assert.equal(seen.length,2);assert.equal(seen[0]!.key,seen[1]!.key);assert.equal(seen[0]!.body,seen[1]!.body);assert.equal(editor.draft.value?.value.value.name,'later');assert.equal(editor.draft.value?.dirty.value,true);editor.dispose();
});
test('conflict retains exact draft/base until explicit reapply then sends a new key at latest base',async()=>{
 const seen:CommandSnapshot[]=[];let conflict=true,current=saved;const editor=useMeetingInfoEditor({saved:async()=>current,send:async request=>{seen.push(request);if(conflict)throw new ApiError('http',409,'conflict');return {id,revision:5,revisionToken:'5'};}},()=> 'owner',()=>id,()=>true);
 await editor.reload();editor.edit(fields('mine'));await editor.save();current={...saved,name:'theirs',revisionToken:'4'};await editor.reload();assert.equal(editor.draft.value?.baseToken.value,'9007199254740993');assert.equal(editor.draft.value?.value.value.name,'mine');assert.equal(editor.draft.value?.conflict.value?.token,'4');editor.reapply();assert.equal(editor.draft.value?.baseToken.value,'4');conflict=false;current={...current,name:'mine',revisionToken:'5'};await editor.save();assert.notEqual(seen[0]!.key,seen[1]!.key);assert.equal(JSON.parse(seen[1]!.body).expectedRevisionToken,'4');editor.dispose();
});
test('actor/scope or edit eligibility loss hides private drafts and late ACK cannot reattach them',async()=>{
 const actor=shallowRef<string|null>('owner'),allowed=shallowRef(true);let accepted!:(v:MeetingMutation)=>void;const editor=useMeetingInfoEditor({saved:async()=>saved,send:()=>new Promise(resolve=>{accepted=resolve;})},()=>actor.value,()=>id,()=>allowed.value);await editor.reload();editor.edit(fields('private'));const send=editor.save();allowed.value=false;assert.equal(editor.draft.value===null,true);accepted({id,revision:2,revisionToken:'2'});await send;assert.equal(editor.draft.value===null,true);assert.equal(editor.command.ack.value,null);allowed.value=true;await editor.reload();actor.value=null;assert.equal(editor.draft.value===null,true);editor.dispose();
});
test('save duplicates coalesce; uncertain command cannot be discarded to generate another key',async()=>{
 let decline!:(e:unknown)=>void,calls=0;const editor=useMeetingInfoEditor({saved:async()=>saved,send:()=>{calls++;return new Promise((_resolve,reject)=>{decline=reject;});}},()=> 'owner',()=>id,()=>true);await editor.reload();editor.edit(fields('pending'));const a=editor.save(),b=editor.save();assert.equal(a,b);assert.equal(calls,1);decline(new Error('lost'));await a;assert.throws(editor.discard);assert.equal(editor.command.state.value,'UNCERTAIN');editor.dispose();
});

test('explicit conflict reapply cannot synchronously send the old base while resetting command state',async()=>{
 let sends=0,current=saved;const editor=useMeetingInfoEditor({saved:async()=>current,send:async()=>{sends++;throw new ApiError('http',409,'conflict');}},()=> 'owner',()=>id,()=>true);await editor.reload();editor.edit(fields('mine'));await editor.save();current={...saved,name:'theirs',revisionToken:'9007199254740994'};await editor.reload();let reentrant:Promise<void>|undefined;const stop=watch(editor.command.state,value=>{if(value==='EMPTY')reentrant=editor.save();},{flush:'sync'});editor.reapply();await reentrant;assert.equal(sends,1);assert.equal(editor.draft.value?.baseToken.value,'9007199254740994');assert.equal(editor.command.state.value,'EMPTY');stop();editor.dispose();
});
test('authorization failure cleanup cannot publish its old error over a synchronous replacement read',async()=>{
 let reads=0,resolve!:(value:SavedMeeting)=>void;const editor=useMeetingInfoEditor({saved:()=>{reads++;if(reads===1)return Promise.resolve(saved);if(reads===2)return Promise.reject(new ApiError('http',403,'old denial'));return new Promise(r=>{resolve=r;});},send:async()=>({id,revision:1,revisionToken:'1'})},()=> 'owner',()=>id,()=>true);await editor.reload();let replacement:Promise<void>|undefined,once=false;const stop=watch(editor.draft,value=>{if(value===null&&!once){once=true;replacement=editor.reload();}},{flush:'sync'});await editor.reload();assert.equal(editor.error.value,null);assert.equal(editor.loading.value,true);resolve(saved);await replacement;assert.equal(editor.draft.value?.value.value.name,'original');stop();editor.dispose();
});

test('authorization cleanup cannot synchronously dispatch another save from the old draft',async()=>{
 let denied=false,sends=0;const editor=useMeetingInfoEditor({saved:async()=>{if(denied)throw new ApiError('http',403,'denied');return saved;},send:async()=>{sends++;return {id,revision:9007199254740992,revisionToken:saved.revisionToken};}},()=> 'owner',()=>id,()=>true);await editor.reload();editor.edit(fields('first'));await editor.save();editor.edit(fields('old private draft'));let reentrant:Promise<void>|undefined;const stop=watch(editor.command.state,state=>{if(state==='EMPTY')reentrant=editor.save();},{flush:'sync'});denied=true;await editor.reload();await reentrant;assert.equal(sends,1);assert.equal(editor.draft.value===null,true);assert.equal(editor.command.state.value,'EMPTY');stop();editor.dispose();
});

for(const reason of ['session','eligibility','denial'] as const)test(`temporary ${reason} loss hides pending information but same scope must retry the original key/body`,async()=>{
 const actor=shallowRef<string|null>('owner'),eligible=shallowRef(true),seen:CommandSnapshot[]=[];let lost=true,denied=false;
 const editor=useMeetingInfoEditor({saved:async()=>{if(denied)throw new ApiError('http',403,'denied');return saved;},send:async request=>{seen.push(request);if(lost)throw new Error('lost ACK');return {id,revision:9007199254740992,revisionToken:saved.revisionToken};}},()=>actor.value,()=>id,()=>eligible.value);
 await editor.reload();editor.edit(fields('original uncertain'));await editor.save();editor.edit(fields('later draft'));
 if(reason==='session')actor.value=null;else if(reason==='eligibility')eligible.value=false;else{denied=true;await editor.reload();}
 assert.equal(editor.draft.value===null,true);assert.equal(editor.command.snapshot.value,null);actor.value='owner';eligible.value=true;denied=false;await editor.reload();
 assert.equal(editor.command.state.value,'UNCERTAIN');assert.equal(editor.draft.value?.value.value.name,'later draft');lost=false;await editor.save();assert.equal(seen.length,2);assert.equal(seen[1]!.key,seen[0]!.key);assert.equal(seen[1]!.body,seen[0]!.body);editor.dispose();
});
for(const reason of ['actor','denial'] as const)test(`command EMPTY reload during ${reason} cleanup never exposes a prior private draft`,async()=>{
 const actor=shallowRef<string|null>('owner');let pending=false,denied=false,resolve!:(value:SavedMeeting)=>void;
 const editor=useMeetingInfoEditor({saved:()=>{if(denied){denied=false;return Promise.reject(new ApiError('http',403,'denied'));}if(pending)return new Promise(r=>{resolve=r;});return Promise.resolve(saved);},send:async()=>({id,revision:9007199254740992,revisionToken:saved.revisionToken})},()=>actor.value,()=>id,()=>true);
 await editor.reload();editor.edit(fields('accepted'));await editor.save();editor.edit(fields('A private dirty'));let reentrant:Promise<void>|undefined;const stop=watch(editor.command.state,state=>{if(state==='EMPTY')reentrant=editor.reload();},{flush:'sync'});pending=true;
 if(reason==='actor')actor.value='B';else{denied=true;await editor.reload();}
 assert.equal(editor.draft.value===null,true);await new Promise(r=>setImmediate(r));assert.equal(editor.draft.value===null,true);assert.equal(editor.loading.value,true);
 resolve({...saved,name:'fresh B'});await reentrant;
 if(reason==='actor'){assert.equal(editor.draft.value?.value.value.name,'fresh B');assert.equal(editor.draft.value?.conflict.value,null);}
 stop();editor.dispose();
});

test('failed reauthorization keeps pending save hidden and unsendable; a different actor never inherits it',async()=>{
 const actor=shallowRef<string|null>('owner'),seen:CommandSnapshot[]=[];let denied=false;
 const editor=useMeetingInfoEditor({saved:async()=>{if(denied)throw new ApiError('http',403,'denied');return saved;},send:async request=>{seen.push(request);throw new Error('lost');}},()=>actor.value,()=>id,()=>true);
 await editor.reload();editor.edit(fields('A private pending'));await editor.save();actor.value=null;actor.value='owner';denied=true;await editor.reload();await editor.save();assert.equal(seen.length,1);assert.equal(editor.draft.value===null,true);
 actor.value='B';denied=false;await editor.reload();assert.equal(editor.command.state.value,'EMPTY');assert.equal(editor.draft.value?.value.value.name,'original');editor.edit(fields('B own draft'));await editor.save();assert.equal(seen[1]!.actor,'B');assert.notEqual(seen[1]!.key,seen[0]!.key);assert.equal(JSON.parse(seen[1]!.body).name,'B own draft');editor.dispose();
});
test('a different meeting destroys the privately frozen original save even after returning',async()=>{
 const meeting=shallowRef(id),actor=shallowRef<string|null>('owner');const editor=useMeetingInfoEditor({saved:async requested=>({...saved,id:requested}),send:async()=>{throw new Error('lost');}},()=>actor.value,()=>meeting.value,()=>true);
 await editor.reload();editor.edit(fields('private pending'));await editor.save();actor.value=null;meeting.value=other;meeting.value=id;actor.value='owner';await editor.reload();assert.equal(editor.command.state.value,'EMPTY');assert.equal(editor.draft.value?.value.value.name,'original');editor.dispose();
});

test('identity loss during draft ACK publication never reuses a consumed ticket and preserves later typing',async()=>{
 const actor=shallowRef<string|null>('owner'),seen:CommandSnapshot[]=[];let current=saved,accept!:(value:MeetingMutation)=>void;
 const editor=useMeetingInfoEditor({saved:async()=>current,send:request=>{seen.push(request);if(seen.length===1)return new Promise(resolve=>{accept=resolve;});current={...current,name:JSON.parse(request.body).name,revisionToken:'9007199254740995'};return Promise.resolve({id,revision:9007199254740996,revisionToken:current.revisionToken});}},()=>actor.value,()=>id,()=>true);
 await editor.reload();editor.edit(fields('first'));const sending=editor.save();editor.edit(fields('later'));const stop=watch(editor.draft.value!.baseToken,()=>{actor.value=null;},{flush:'sync',once:true});current={...saved,name:'first',revisionToken:'9007199254740994'};accept({id,revision:9007199254740994,revisionToken:current.revisionToken});await sending;
 assert.equal(editor.draft.value===null,true);actor.value='owner';await editor.reload();assert.equal(editor.draft.value?.value.value.name,'later');assert.equal(editor.draft.value?.baseToken.value,'9007199254740994');assert.equal(editor.draft.value?.dirty.value,true);
 await editor.save();assert.notEqual(seen[1]!.key,seen[0]!.key);assert.equal(JSON.parse(seen[1]!.body).name,'later');assert.equal(JSON.parse(seen[1]!.body).expectedRevisionToken,'9007199254740994');assert.equal(editor.error.value,null);assert.equal(editor.draft.value?.dirty.value,false);stop();editor.dispose();
});
