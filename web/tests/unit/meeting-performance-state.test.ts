import {test} from 'node:test';
import assert from 'node:assert/strict';
import {shallowRef} from 'vue';
import {useMeetingList} from '../../src/features/meetings/useMeetingList';
import {sharedRead} from '../../src/shared/api/shared-read';
import {ApiError} from '../../src/shared/api/client';
const row={id:'cd2e160e-6b11-4f4e-871e-bb8aa90ecdee',name:'会议',category:null,occurredOn:null,stage:'ENTRY',lifecycle:'ACTIVE',relation:'OWNER',writeState:'CHECK_WORKSPACE',revisionToken:'1',accessRevisionToken:'0'} as const;
const page=(items:any[]=[row]):any=>({items,hasMore:false,nextCursor:null,total:items.length,stageCounts:{ENTRY:items.length},topicGroups:{__none:items.length}});
test('scope read commits rows and columns together without blanking the prior authorized page',async()=>{
 const calls:any[]=[];const actor=shallowRef<string|null>('owner');
 const list=useMeetingList((scope,cursor,signal,filters)=>new Promise(resolve=>calls.push({scope,cursor,signal,filters,resolve})),()=>actor.value);
 const first=list.reload();calls[0].resolve(page());await first;
 list.view.topicFilter='00000000-0000-0000-0000-000000000001';list.view.keyword='保留搜索';list.view.scrollTop=324;
 const next=list.selectScope('joined');assert.equal(list.scope.value,'owned');assert.equal(list.requestedScope.value,'joined');assert.equal(list.page.value?.items.length,1);assert.equal(calls[1].filters.topic,undefined);
 calls[1].resolve(page([]));await next;assert.equal(list.scope.value,'joined');assert.equal(list.view.keyword,'保留搜索');assert.equal(list.view.scrollTop,324);
 actor.value='other';assert.equal(list.page.value,null);assert.equal(list.view.keyword,'');assert.equal(list.view.scrollTop,0);list.dispose();
});
test('identical in-flight reads are shared and unchanged results preserve the page object and selection',async()=>{
 let finish!:(value:any)=>void;let count=0;const list=useMeetingList(()=>{count++;return new Promise(resolve=>{finish=resolve;});},()=> 'owner');
 const a=list.reload(),b=list.reload();assert.equal(a,b);assert.equal(count,1);finish(page());await a;
 const original=list.page.value;list.view.selectedMeetingIds=[row.id];const c=list.reload();finish(page());await c;
 assert.equal(list.page.value,original);assert.deepEqual(list.view.selectedMeetingIds,[row.id]);list.dispose();
});
test('transient list failure preserves rows, authority denial clears them',async()=>{
 let failure:unknown=null;const list=useMeetingList(async()=>{if(failure)throw failure;return page();},()=> 'owner');await list.reload();
 failure=new ApiError('http',503,'暂不可用');await list.reload();assert.equal(list.page.value?.items.length,1);
 failure=new ApiError('http',403,'无权访问');await list.reload();assert.equal(list.page.value,null);list.dispose();
});
test('a pending or failed filter never resets the displayed cursor before a successful commit',async()=>{
 const pending:any[]=[];const list=useMeetingList((_scope,_cursor,_signal,filters)=>new Promise((resolve,reject)=>pending.push({resolve,reject,filters})),()=> 'owner');
 const first=list.reload();pending[0].resolve({...page(),hasMore:true,nextCursor:'second'});await first;
 const second=list.next();pending[1].resolve(page());await second;assert.equal(list.pageNumber.value,2);
 const filtered=list.selectFilters({q:'新关键词'});assert.equal(list.pageNumber.value,2);assert.equal(list.filters.value.q,'');
 pending[2].reject(new ApiError('http',503,'暂不可用'));await filtered;assert.equal(list.pageNumber.value,2);
 const retry=list.selectFilters({q:'新关键词'});pending[3].resolve(page());await retry;assert.equal(list.pageNumber.value,1);assert.equal(list.filters.value.q,'新关键词');list.dispose();
});
test('dictionary subscribers cancel independently and identity changes cannot resurrect a cached result',async()=>{
 let actor:unknown={id:'one'},finish!:(value:any)=>void,signal!:AbortSignal,count=0;
 const cache=sharedRead(()=>actor),run=(s:AbortSignal)=>{count++;signal=s;return new Promise<string>(resolve=>{finish=resolve;});};
 const a=new AbortController(),b=new AbortController();const one=cache.read('key',a.signal,run),two=cache.read('key',b.signal,run);await Promise.resolve();assert.equal(count,1);
 a.abort();await assert.rejects(one,{name:'AbortError'});assert.equal(signal.aborted,false);finish('verified');assert.equal(await two,'verified');assert.equal(await cache.read('key',b.signal,run),'verified');assert.equal(count,1);
 const pending=cache.read('second',b.signal,run);await Promise.resolve();actor={id:'two'};finish('old');await assert.rejects(pending,{name:'AbortError'});cache.clear();
});
test('switching scope during a pending filter carries the latest intent and ignores the old response',async()=>{
 const calls:any[]=[];const list=useMeetingList((scope,_cursor,signal,filters)=>new Promise(resolve=>calls.push({scope,signal,filters,resolve})),()=> 'owner');
 const initial=list.reload();calls[0].resolve(page());await initial;
 const filter=list.selectFilters({q:'正在输入的新搜索'}),switching=list.selectScope('joined');assert.equal(calls[1].signal.aborted,true);assert.equal(calls[2].filters.q,'正在输入的新搜索');
 calls[2].resolve(page([]));await switching;calls[1].resolve(page());await filter;assert.equal(list.scope.value,'joined');assert.equal(list.filters.value.q,'正在输入的新搜索');assert.equal(list.page.value?.items.length,0);list.dispose();
});
