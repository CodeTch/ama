import {test} from 'node:test';import assert from 'node:assert/strict';import {shallowRef} from 'vue';
import {useMeetingCreate} from '../../src/features/meetings/useMeetingCreate.js';
const id='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee';
function storage(){const rows=new Map<string,string>();return {rows,getItem:(key:string)=>rows.get(key)??null,setItem:(key:string,value:string)=>{rows.set(key,value);},removeItem:(key:string)=>{rows.delete(key);}};}
test('creation persists original command before send and refresh restores uncertainty without ACK',async()=>{
 const store=storage();let firstBody='',firstKey='';const first=useMeetingCreate(async request=>{assert.equal([...store.rows.keys()].filter(k=>k.startsWith('ama.journal.v1:')).length,1);firstBody=request.body;firstKey=request.key;throw new Error('lost response');},()=> 'owner',store,'tab-1');await assert.rejects(first.submit());assert.equal(first.command.state.value,'UNCERTAIN');first.dispose();
 let secondBody='',secondKey='';const second=useMeetingCreate(async request=>{secondBody=request.body;secondKey=request.key;return {id,revision:0,revisionToken:'0',stage:'ENTRY' as const};},()=> 'owner',store,'tab-1');assert.equal(second.command.state.value,'UNCERTAIN');assert.equal(second.command.ack.value,null);await second.submit();assert.equal(secondBody,firstBody);assert.equal(secondKey,firstKey);assert.equal([...store.rows.keys()].filter(k=>k.startsWith('ama.journal.v1:')).length,1);second.navigationComplete();assert.equal(store.rows.size,0);second.dispose();
});
test('actor loss hides pending command; actor change clears journal and late ACK cannot reveal old meeting',async()=>{
 const store=storage(),actor=shallowRef<string|null>('owner');let resolve!:(value:{id:string;revision:number;revisionToken:string;stage:'ENTRY'})=>void;
 const state=useMeetingCreate(()=>new Promise(r=>{resolve=r;}),()=>actor.value,store,'tab-1');const pending=state.submit();actor.value=null;assert.equal([...store.rows.keys()].filter(k=>k.startsWith('ama.journal.v1:')).length,1);resolve({id,revision:0,revisionToken:'0',stage:'ENTRY'});await assert.rejects(pending);assert.equal(state.command.ack.value,null);actor.value='other';assert.equal(store.rows.size,0);assert.equal(state.command.state.value,'EMPTY');state.dispose();
});
test('unavailable storage is explicit while in-memory original command survives uncertain send',async()=>{
 const state=useMeetingCreate(async()=>{throw new Error('offline');},()=> 'owner',null,'tab-1','NEW_NAMESPACE');await assert.rejects(state.submit());assert.equal(state.storageStatus.value,'UNAVAILABLE');assert.equal(state.command.state.value,'UNCERTAIN');assert.ok(state.command.snapshot.value?.key);state.dispose();
});
test('tampered restored create path blocks new submission instead of overwriting unknown command',async()=>{
 const store=storage();const first=useMeetingCreate(async()=>{throw new Error('offline');},()=> 'owner',store,'tab-1');await assert.rejects(first.submit());first.dispose();const key=[...store.rows.keys()].find(k=>k.startsWith('ama.journal.v1:'))!,value=JSON.parse(store.rows.get(key)!);value.value.path='/api/v1/meetings/foreign';store.rows.set(key,JSON.stringify(value));let sent=0;const next=useMeetingCreate(async()=>{sent++;return {id,revision:0,revisionToken:'0',stage:'ENTRY' as const};},()=> 'owner',store,'tab-1');assert.equal(next.storageStatus.value,'INVALID');await assert.rejects(next.submit());assert.equal(sent,0);next.dispose();
});

test('temporary session loss and same-actor reauthentication retain original uncertain key',async()=>{
 for(const store of [storage(),null]){const actor=shallowRef<string|null>('owner'),keys:string[]=[];const state=useMeetingCreate(async request=>{keys.push(request.key);throw new Error('lost');},()=>actor.value,store,'tab-1',store?'RESUME_NAMESPACE':'NEW_NAMESPACE');await assert.rejects(state.submit());actor.value=null;assert.equal(state.command.snapshot.value,null);actor.value='owner';assert.equal(state.command.state.value,'UNCERTAIN');await assert.rejects(state.submit());assert.equal(keys.length,2);assert.equal(keys[0],keys[1]);state.dispose();}
});

test('explicit logout clears stored/memory recovery even after the session was lost',async()=>{
 const store=storage(),actor=shallowRef<string|null>('owner');const state=useMeetingCreate(async()=>{throw new Error('lost');},()=>actor.value,store,'tab-1');await assert.rejects(state.submit());actor.value=null;state.logout();assert.equal(store.rows.size,0);assert.equal(state.command.snapshot.value,null);actor.value='owner';assert.equal(state.command.state.value,'EMPTY');state.dispose();
});
test('failed cleanup is visible after actor change without exposing the prior command',async()=>{
 const base=storage(),store={...base,removeItem(){throw new Error('denied');}},actor=shallowRef<string|null>('owner');const state=useMeetingCreate(async()=>{throw new Error('lost');},()=>actor.value,store,'tab-1');await assert.rejects(state.submit());actor.value='other';assert.equal(state.cleanupFailed.value,true);assert.equal(state.command.snapshot.value,null);assert.equal(state.command.state.value,'EMPTY');state.dispose();
});

test('failed recovery read cannot replace an existing uncertain command even when writes work',async()=>{
 const base=storage();const first=useMeetingCreate(async()=>{throw new Error('lost');},()=> 'owner',base,'tab-1');await assert.rejects(first.submit());first.dispose();const before=[...base.rows];let sent=0;
 const next=useMeetingCreate(async()=>{sent++;return {id,revision:0,revisionToken:'0'};},()=> 'owner',{...base,getItem(){throw new Error('temporary read failure');}},'tab-1');await assert.rejects(next.submit());assert.equal(sent,0);assert.deepEqual([...base.rows],before);next.dispose();
});
test('cold unauthenticated logout locates and clears the previous tab command',async()=>{
 const base=storage();const first=useMeetingCreate(async()=>{throw new Error('lost');},()=> 'owner',base,'tab-1');await assert.rejects(first.submit());first.dispose();const next=useMeetingCreate(async()=>({id,revision:0,revisionToken:'0'}),()=>null,base,'tab-1');next.logout();assert.equal(base.rows.size,0);next.dispose();
});
test('cold first authentication as another actor removes the old tab command',async()=>{
 const base=storage();const first=useMeetingCreate(async()=>{throw new Error('lost');},()=> 'owner',base,'tab-1');await assert.rejects(first.submit());first.dispose();const next=useMeetingCreate(async()=>({id,revision:0,revisionToken:'0'}),()=> 'other',base,'tab-1');assert.equal(base.rows.size,0);assert.equal(next.command.state.value,'EMPTY');next.dispose();
});

test('retrying failed recovery reads recovers the original key without rewriting it',async()=>{
 const base=storage();let original='';const first=useMeetingCreate(async request=>{original=request.key;throw new Error('lost');},()=> 'owner',base,'tab-1');await assert.rejects(first.submit());first.dispose();let denied=true,sent='';const next=useMeetingCreate(async request=>{sent=request.key;return {id,revision:0,revisionToken:'0'};},()=> 'owner',{...base,getItem(key){if(denied)throw new Error('read unavailable');return base.getItem(key);}},'tab-1');assert.equal(next.recoveryBlocked.value,true);denied=false;next.retryRecovery();assert.equal(next.command.state.value,'UNCERTAIN');assert.equal(next.command.ack.value,null);await next.submit();assert.equal(sent,original);next.dispose();
});
test('unresolved tab namespace never permits a new create after reload',async()=>{
 let sent=0;const state=useMeetingCreate(async()=>{sent++;return {id,revision:0,revisionToken:'0'};},()=> 'owner',null,'new-but-unresolved','UNAVAILABLE');assert.equal(state.recoveryBlocked.value,true);await assert.rejects(state.submit());assert.equal(sent,0);state.dispose();
});
test('failed first ownership write after an empty read retains only the original in-memory command',async()=>{
 const base=storage(),keys:string[]=[];const state=useMeetingCreate(async request=>{keys.push(request.key);throw new Error('lost');},()=> 'owner',{...base,setItem(){throw new Error('quota');}},'tab-1');await assert.rejects(state.submit());await assert.rejects(state.submit());assert.equal(keys.length,2);assert.equal(keys[0],keys[1]);assert.equal(base.rows.size,0);assert.equal(state.storageStatus.value,'UNAVAILABLE');state.dispose();
});

test('persisted ownership with missing command after quota and lost ACK is unresolved on reload',async()=>{
 const base=storage();const limited={...base,setItem(key:string,value:string){if(key.startsWith('ama.journal.v1:'))throw new Error('command quota');base.setItem(key,value);}};
 const first=useMeetingCreate(async()=>{throw new Error('lost response');},()=> 'owner',limited,'tab-1');await assert.rejects(first.submit());assert.equal(base.rows.size,1);first.dispose();let sent=0;const before=[...base.rows];
 const next=useMeetingCreate(async()=>{sent++;return {id,revision:0,revisionToken:'0'};},()=> 'owner',base,'tab-1');await assert.rejects(next.submit());assert.equal(sent,0);assert.deepEqual([...base.rows],before);next.dispose();
});

test('used fresh namespace retains failed actor cleanup across failed recovery reads',async()=>{
 const base=storage(),actor=shallowRef<string|null>('owner');let readDenied=false,removeDenied=false,sent=0;
 const store={...base,getItem(key:string){if(readDenied)throw new Error('read denied');return base.getItem(key);},removeItem(key:string){if(removeDenied)throw new Error('remove denied');base.removeItem(key);}};
 const state=useMeetingCreate(async()=>{sent++;throw new Error('lost');},()=>actor.value,store,'tab-1','NEW_NAMESPACE');await assert.rejects(state.submit());const before=[...base.rows];removeDenied=true;actor.value='other';assert.equal(state.recoveryBlocked.value,true);
 readDenied=true;state.retryRecovery();await assert.rejects(state.submit());assert.equal(sent,1);assert.deepEqual([...base.rows],before);assert.equal(state.recoveryBlocked.value,true);assert.equal(state.command.snapshot.value,null);
 readDenied=false;removeDenied=false;state.retryRecovery();assert.equal(base.rows.size,0);assert.equal(state.command.state.value,'EMPTY');assert.equal(state.recoveryBlocked.value,false);state.dispose();
});
test('failed explicit logout cleanup cannot restore old command on same actor reauthentication',async()=>{
 const base=storage(),actor=shallowRef<string|null>('owner');let denied=false,sent=0;
 const store={...base,removeItem(key:string){if(denied)throw new Error('remove denied');base.removeItem(key);}};
 const state=useMeetingCreate(async()=>{sent++;throw new Error('lost');},()=>actor.value,store,'tab-1');await assert.rejects(state.submit());const before=[...base.rows];actor.value=null;denied=true;state.logout();actor.value='owner';
 assert.equal(state.command.state.value,'EMPTY');assert.equal(state.command.snapshot.value,null);assert.equal(state.recoveryBlocked.value,true);await assert.rejects(state.submit());assert.equal(sent,1);assert.deepEqual([...base.rows],before);
 state.retryRecovery();assert.equal(state.command.state.value,'EMPTY');assert.equal(state.recoveryBlocked.value,true);denied=false;state.retryRecovery();assert.equal(base.rows.size,0);assert.equal(state.recoveryBlocked.value,false);state.dispose();
});
