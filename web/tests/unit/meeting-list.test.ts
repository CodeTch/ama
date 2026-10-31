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
test('prototype filters reset cursor and stale reads cannot replace the new filtered page',async()=>{
 const calls:any[]=[];const list=useMeetingList((scope,cursor,signal,filters?:unknown)=>new Promise(resolve=>calls.push({scope,cursor,signal,filters,resolve})),()=> 'owner');
 const old=list.reload();const fresh=(list as any).selectFilters({q:'产品',stages:['PUBLISHED'],categories:[],from:'2026-10-01',to:''});assert.equal(calls[0].signal.aborted,true);assert.equal(calls[1].cursor,null);assert.equal(calls[1].filters.q,'产品');calls[1].resolve({items:[],nextCursor:null,hasMore:false});await fresh;calls[0].resolve({items:[item],nextCursor:'old',hasMore:true});await old;assert.deepEqual(list.page.value?.items,[]);list.dispose();
});
test('filter reads keep the current page visible until the replacement arrives',async()=>{
 const calls:any[]=[];const list=useMeetingList((scope,cursor,signal,filters?:unknown)=>new Promise(resolve=>calls.push({scope,cursor,signal,filters,resolve})),()=> 'owner');
 const initial=list.reload();calls[0].resolve({items:[item],nextCursor:null,hasMore:false});await initial;
 const filtered=list.selectFilters({q:'产品'});assert.deepEqual(list.page.value?.items,[item]);assert.equal(list.loading.value,true);
 calls[1].resolve({items:[],nextCursor:null,hasMore:false});await filtered;assert.deepEqual(list.page.value?.items,[]);list.dispose();
});

test('cursor history re-fetches 1→2→3→2 and failed navigation does not advance',async()=>{
 const calls:(string|null)[]=[];let fail=false;const actor=shallowRef<string|null>('owner');const list=useMeetingList(async(_scope,cursor)=>{calls.push(cursor);if(fail)throw Error('offline');return {items:[],hasMore:true,nextCursor:cursor===null?'c2':cursor==='c2'?'c3':'c4'};},()=>actor.value);
 await list.reload();await list.next();await list.next();assert.equal((list as any).pageNumber.value,3);await (list as any).previous();assert.deepEqual(calls,[null,'c2','c3','c2']);assert.equal((list as any).pageNumber.value,2);
 fail=true;await list.next();assert.equal((list as any).pageNumber.value,2);fail=false;await (list as any).previous();assert.equal(calls.at(-1),null);assert.equal((list as any).canPrevious.value,false);list.dispose();
});
test('histories isolate scopes, reset on filter/actor changes and stay bounded',async()=>{
 const actor=shallowRef<string|null>('owner');let serial=0;const calls:any[]=[];const list=useMeetingList(async(scope,cursor)=>{calls.push({scope,cursor});return {items:[],hasMore:true,nextCursor:'cursor-'+(++serial)};},()=>actor.value);
 await list.reload();await list.next();await list.selectScope('joined');assert.equal((list as any).canPrevious.value,false);await list.next();await list.next();await list.selectScope('owned');assert.equal((list as any).pageNumber.value,2);await (list as any).previous();assert.equal(calls.at(-1).cursor,null);
 await list.selectFilters({q:'new'});assert.equal((list as any).pageNumber.value,1);await list.selectScope('joined');assert.equal(calls.at(-1).cursor,null);actor.value='other';assert.equal(list.page.value,null);await list.reload();assert.equal((list as any).canPrevious.value,false);
 for(let n=0;n<70;n++)await list.next();let previousCount=0;while((list as any).canPrevious.value){await (list as any).previous();previousCount++;assert.ok(previousCount<65);}assert.equal(previousCount,63);list.dispose();
});
test('prototype page size is ten in the actual typed list request',async()=>{
 const session:any={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'},old=globalThis.fetch;let url='';try{globalThis.fetch=async input=>{url=String(input);return Response.json({items:[],nextCursor:null,hasMore:false});};const api=createMeetingsApi(createBusinessClient('https://ama.test',()=>session,()=>{}));await api.list('owned',null,new AbortController().signal);assert.equal(new URL(url).searchParams.get('limit'),'10');}finally{globalThis.fetch=old;}
});
