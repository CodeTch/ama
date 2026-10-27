import {test} from 'node:test';import assert from 'node:assert/strict';import {shallowRef} from 'vue';
import {useMeetingList} from '../../src/features/meetings/useMeetingList.js';
import {createMeetingsApi} from '../../src/shared/api/meetings.js';
import {createBusinessClient} from '../../src/shared/api/business-client.js';
import type {components} from '../../src/shared/api/generated.js';
const item:components['schemas']['MeetingItem']={id:'cd2e160e-6b11-4f4e-871e-bb8aa90ecdee',name:'会议',category:null,occurredOn:null,stage:'ENTRY',lifecycle:'ACTIVE',relation:'OWNER',writeState:'CHECK_WORKSPACE',revisionToken:'9007199254740993',accessRevisionToken:'0'};
test('scope switch suppresses stale reads and actor loss clears every visible row',async()=>{
 const actor=shallowRef<string|null>('owner');const calls:Array<{scope:string,resolve:(p:components['schemas']['MeetingPage'])=>void,signal:AbortSignal}>=[];
 const list=useMeetingList((scope,_cursor,signal)=>new Promise(resolve=>calls.push({scope,resolve,signal})),()=>actor.value);const first=list.reload();const second=list.selectScope('joined');assert.equal(calls[0]!.signal.aborted,true);calls[1]!.resolve({items:[],nextCursor:null,hasMore:false});await second;calls[0]!.resolve({items:[item],nextCursor:null,hasMore:false});await first;assert.deepEqual(list.page.value?.items,[]);
 const third=list.reload();calls[2]!.resolve({items:[item],nextCursor:null,hasMore:false});await third;actor.value=null;assert.equal(list.page.value,null);list.dispose();
});
test('empty authorized page with continuation remains navigable and reload preserves its cursor',async()=>{
 const seen:Array<string|null>=[];const list=useMeetingList(async(_scope,cursor)=>{seen.push(cursor);return cursor?{items:[item],nextCursor:null,hasMore:false}:{items:[],nextCursor:'opaque',hasMore:true};},()=> 'owner');await list.reload();await list.next();await list.reload();assert.deepEqual(seen,[null,'opaque','opaque']);assert.equal(list.page.value?.items[0]?.revisionToken,'9007199254740993');list.dispose();
});
test('typed facade rejects incomplete graph fields without inventing defaults',async()=>{
 const session:components['schemas']['Session']={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};const old=globalThis.fetch;const api=createMeetingsApi(createBusinessClient('https://ama.test',()=>session,()=>{}));
 try{globalThis.fetch=async()=>Response.json({items:[item],nextCursor:null,hasMore:false});const page=await api.list('owned',null,new AbortController().signal);assert.equal(page.items[0]?.revisionToken,'9007199254740993');globalThis.fetch=async()=>Response.json({items:[{...item,revisionToken:9007199254740992}],hasMore:false});await assert.rejects(api.list('owned',null,new AbortController().signal));}finally{globalThis.fetch=old;}
});

test('typed facade rejects non-string relation and writeState values',async()=>{
 const session:components['schemas']['Session']={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};const old=globalThis.fetch;const api=createMeetingsApi(createBusinessClient('https://ama.test',()=>session,()=>{}));
 try{for(const [field,values] of Object.entries({relation:[['OWNER'],null,1,{}],writeState:[['CHECK_WORKSPACE'],null,true,{}]})){for(const value of values){globalThis.fetch=async()=>Response.json({items:[{...item,[field]:value}],nextCursor:null,hasMore:false});await assert.rejects(api.list('owned',null,new AbortController().signal),field+': '+JSON.stringify(value));}}}finally{globalThis.fetch=old;}
});
