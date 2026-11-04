import {test} from 'node:test';
import assert from 'node:assert/strict';
import {shallowRef,nextTick} from 'vue';
import {createCurrentUserProfile} from '../../src/shared/composables/current-user-profile';
import type {Person} from '../../src/shared/api/meeting-options';

const person=(id:string):Person=>({stableId:id,displayName:`姓名-${id}`,department:null,active:true});
const settle=async()=>{await Promise.resolve();await Promise.resolve();await nextTick();};

test('profile stays loaded when the same account receives a new session object',async()=>{
 const session=shallowRef({id:'one'});let reads=0;
 const profile=createCurrentUserProfile(()=>session.value.id,async id=>{reads++;return {items:[person(id)],nextCursor:null};});
 try{await settle();assert.equal(profile.name.value,'姓名-one');assert.equal(profile.loading.value,false);
  session.value={id:'one'};await settle();assert.equal(profile.name.value,'姓名-one');assert.equal(profile.loading.value,false);assert.equal(reads,1);
 }finally{profile.dispose();}
});

test('identity changes clear old information synchronously and discard late directory replies',async()=>{
 const actor=shallowRef<string|null>('one');const pending:{id:string;signal:AbortSignal;resolve:(value:{items:Person[];nextCursor:null})=>void}[]=[];
 const profile=createCurrentUserProfile(()=>actor.value,(id,_cursor,signal)=>new Promise(resolve=>pending.push({id,signal,resolve})));
 try{actor.value='two';assert.equal(pending[0]!.signal.aborted,true);assert.equal(profile.name.value,'two');
  pending[0]!.resolve({items:[person('one')],nextCursor:null});await settle();assert.equal(profile.name.value,'two');assert.equal(profile.loading.value,true);
  pending[1]!.resolve({items:[person('two')],nextCursor:null});await settle();assert.equal(profile.name.value,'姓名-two');
  actor.value=null;assert.equal(profile.name.value,'尚未登录');assert.equal(profile.loading.value,false);
 }finally{profile.dispose();}
});

test('failed directory lookup settles once and disposal cancels an in-flight lookup',async()=>{
 const actor=shallowRef<string|null>('one');let signal:AbortSignal|undefined;
 const profile=createCurrentUserProfile(()=>actor.value,async(_id,_cursor,request)=>{signal=request;throw new Error('directory unavailable');});
 await settle();assert.equal(profile.name.value,'one');assert.equal(profile.loading.value,false);
 actor.value='two';profile.dispose();assert.equal(signal!.aborted,true);await settle();assert.equal(profile.name.value,'two');
});
