import {test} from 'node:test';import assert from 'node:assert/strict';import {shallowRef,watch} from 'vue';
import {useMeetingCreate} from '../../src/features/meetings/useMeetingCreate.js';
import {useMeetingCreateFlow} from '../../src/features/meetings/useMeetingCreateFlow.js';
const id='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee';
function store(){const rows=new Map<string,string>();return {rows,getItem:(k:string)=>rows.get(k)??null,setItem:(k:string,v:string)=>{rows.set(k,v);},removeItem:(k:string)=>{rows.delete(k);}};}
test('creation opens only the acknowledged ID and clears journal only after confirmed navigation',async()=>{
 const storage=store();let sends=0,navigated='';const create=useMeetingCreate(async()=>{sends++;return {id,revision:0,revisionToken:'0'};},()=> 'owner',storage,'tab');let arrive!:(ok:boolean)=>void;
 const flow=useMeetingCreateFlow(create,()=> 'owner',async meeting=>{navigated=meeting;return new Promise(resolve=>{arrive=resolve;});});const a=flow.open();await new Promise(resolve=>setImmediate(resolve));assert.equal(navigated,id);assert.equal(create.command.state.value,'ACKNOWLEDGED');assert.ok(storage.rows.size>0);assert.equal(flow.open(),a);arrive(true);await a;assert.equal(sends,1);assert.equal(storage.rows.size,0);assert.equal(create.command.state.value,'EMPTY');assert.equal(flow.busy.value,false);flow.dispose();create.dispose();
});
test('failed navigation retains the ACK and retries navigation without sending a new creation',async()=>{
 let sends=0,allowed=false;const storage=store(),create=useMeetingCreate(async()=>{sends++;return {id,revision:0,revisionToken:'0'};},()=> 'owner',storage,'tab');const flow=useMeetingCreateFlow(create,()=> 'owner',async()=>allowed);await flow.open();assert.ok(flow.error.value);assert.equal(create.command.state.value,'ACKNOWLEDGED');assert.ok(storage.rows.size>0);allowed=true;await flow.open();assert.equal(sends,1);assert.equal(storage.rows.size,0);flow.dispose();create.dispose();
});
test('lost creation response keeps exact command and never navigates before an original ACK',async()=>{
 let lost=true;const keys:string[]=[],destinations:string[]=[],storage=store();const create=useMeetingCreate(async command=>{keys.push(command.key);if(lost)throw new Error('lost');return {id,revision:0,revisionToken:'0'};},()=> 'owner',storage,'tab');const flow=useMeetingCreateFlow(create,()=> 'owner',async value=>{destinations.push(value);return true;});await flow.open();assert.deepEqual(destinations,[]);assert.equal(create.command.state.value,'UNCERTAIN');lost=false;await flow.open();assert.deepEqual(destinations,[id]);assert.equal(keys.length,2);assert.equal(keys[0],keys[1]);flow.dispose();create.dispose();
});
test('actor change while awaiting ACK suppresses old navigation and leaves the new scope clean',async()=>{
 const actor=shallowRef<string|null>('owner');let accepted!:(v:{id:string,revision:number,revisionToken:string})=>void;const create=useMeetingCreate(()=>new Promise(resolve=>{accepted=resolve;}),()=>actor.value,store(),'tab');let opens=0;const flow=useMeetingCreateFlow(create,()=>actor.value,async()=>{opens++;return true;});const a=flow.open();actor.value='other';accepted({id,revision:0,revisionToken:'0'});await a;assert.equal(opens,0);assert.equal(flow.error.value,null);assert.equal(flow.busy.value,false);flow.dispose();create.dispose();
});
test('synchronous busy observer cannot dispatch creation under the old actor',async()=>{
 const actor=shallowRef<string|null>('owner');let sends=0;const create=useMeetingCreate(async()=>{sends++;return {id,revision:0,revisionToken:'0'};},()=>actor.value,store(),'tab');const flow=useMeetingCreateFlow(create,()=>actor.value,async()=>true);const stop=watch(flow.busy,v=>{if(v)actor.value=null;},{flush:'sync'});await flow.open();assert.equal(sends,0);assert.equal(flow.busy.value,false);stop();flow.dispose();create.dispose();
});

test('source page unmount after starting navigation allows cleanup only on verified arrival',async()=>{
 const storage=store(),create=useMeetingCreate(async()=>({id,revision:0,revisionToken:'0'}),()=> 'owner',storage,'tab');let arrive!:(ok:boolean)=>void;const flow=useMeetingCreateFlow(create,()=> 'owner',()=>new Promise(resolve=>{arrive=resolve;}));const a=flow.open();await new Promise(resolve=>setImmediate(resolve));flow.dispose();arrive(true);await a;assert.equal(storage.rows.size,0);await flow.open();assert.equal(create.command.state.value,'EMPTY');create.dispose();
});
test('leaving before creation ACK preserves journal and prevents unsolicited navigation',async()=>{
 const storage=store();let accepted!:(v:{id:string,revision:number,revisionToken:string})=>void,opens=0;const create=useMeetingCreate(()=>new Promise(resolve=>{accepted=resolve;}),()=> 'owner',storage,'tab');const flow=useMeetingCreateFlow(create,()=> 'owner',async()=>{opens++;return true;});const a=flow.open();flow.dispose();accepted({id,revision:0,revisionToken:'0'});await a;assert.equal(opens,0);assert.equal(create.command.state.value,'ACKNOWLEDGED');assert.ok(storage.rows.size>0);create.dispose();
});
