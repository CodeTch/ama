import {test} from 'node:test';import assert from 'node:assert/strict';import {ref} from 'vue';import {ApiError} from '../../src/shared/api/client';import {usePublishedMaterials} from '../../src/features/minutes/usePublishedMaterials';
const listing=(name:string)=>({items:[{name}],materialSnapshotId:'snapshot',materialsPending:false,meetingRevision:'1'}) as any;
test('published background reads clear on scope loss and ignore a late response',async()=>{
 const actor=ref<string|null>('owner'),meeting=ref('one');let finish!:(v:any)=>void;let signal:AbortSignal|undefined;let reads=0;const denied:unknown[]=[];
 const view=usePublishedMaterials(async(id,s)=>{assert.equal(id,'one');signal=s;reads++;return new Promise(resolve=>finish=resolve);},()=>actor.value,()=>meeting.value,e=>denied.push(e));
 try{const pending=view.open();assert.equal(reads,1);assert.equal(view.opened.value,true);meeting.value='two';assert.equal(signal?.aborted,true);assert.equal(view.opened.value,false);finish(listing('Old private material'));await pending;assert.equal(view.value.value,null);assert.deepEqual(denied,[]);}finally{view.dispose();}
});
test('failed and denied refreshes cannot retain earlier material names and can recover by explicit reading',async()=>{
 let next:any=listing('Authorized file');const denied:unknown[]=[];
 const view=usePublishedMaterials(async()=>{if(next instanceof Error)throw next;return next;},()=> 'owner',()=> 'meeting',e=>denied.push(e));
 const first=()=>view.value.value?.items[0]?.name;
 try{await view.open();assert.equal(first(),'Authorized file');next=new ApiError('http',503,'暂时不可用');await view.reload();assert.equal(view.value.value,null);assert.match(view.error.value??'',/暂时不可用/);assert.equal(denied.length,0);next=listing('Current file');await view.reload();assert.equal(first(),'Current file');next=new ApiError('http',403,'权限已失效');await view.reload();assert.equal(view.value.value,null);assert.equal(denied.length,1);}finally{view.dispose();}
});
